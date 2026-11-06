package dev.aiharness.core.media

import dev.aiharness.core.Harness
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.llm.complete
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ToolMode
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Writes the short description of a picture or file that later context epochs carry instead of
 * the thing itself (docs/design/media.md, "Tokens"). Best effort: what can't be described keeps
 * its stand-in.
 *
 * - **On arrival**, before the persona first reads the note, for what its own model can't take in
 *   but the utility model can (a text-only model hears what's in a photo). Stamped with the note's
 *   time, so every epoch reads it.
 * - **Afterwards** for the rest, by the utility model or the persona's own, for later epochs.
 */
class MediaDescriber(private val h: Harness) {
    private val parts = MediaParts(h.blobs)

    /** What couldn't be described this run, so it isn't asked about again every turn. */
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** Describes [message]'s pictures and files that [personaProfile] can't take in, and stores them. */
    suspend fun onArrival(message: ChatMessage, personaProfile: ModelProfile, settings: AppSettings): ChatMessage {
        if (!settings.media.describe) return message
        val describer = utility(settings)?.takeIf { it.id != personaProfile.id } ?: return message
        val written = describeAll(message, stamp = message.createdAt) { part ->
            describer.takeIf { level(part, it) > level(part, personaProfile) }
        }
        return store(message, written)
    }

    /** Describes what's still undescribed in the conversation's recent notes. */
    suspend fun catchUp(conversationId: String) {
        val setup = runCatching { h.resolveTurn(conversationId) }.getOrNull() ?: return
        if (!setup.settings.media.describe) return
        val candidates = listOfNotNull(utility(setup.settings), setup.profile).distinctBy { it.id }
        val recent = h.conversations.getMessages(conversationId)
            .filter { it.role == MessageRole.USER && !it.excludedFromContext }
            .takeLast(RECENT)
        for (message in recent) {
            val written = describeAll(message, stamp = h.clock.nowMillis()) { part -> candidates.firstOrNull { level(part, it) > 0 } }
            if (written.isNotEmpty()) store(message, written)
        }
    }

    /** What's worth describing in a note: pictures, PDFs, videos, songs, and text files longer than a glance. */
    private fun needs(part: ContentPart): Boolean = when (part) {
        is ContentPart.Image -> part.description == null
        is ContentPart.Attachment -> part.description == null && when (part.kind) {
            AttachmentKind.FILE -> MediaSupport.isPdf(part) || (MediaSupport.isText(part) && (part.sizeBytes ?: 0L) > LONG_TEXT)
            AttachmentKind.SONG, AttachmentKind.VIDEO -> true
            AttachmentKind.PLAYLIST, AttachmentKind.LINK -> false
        }
        else -> false
    }

    /** How fully [profile]'s model takes [part] in: 2 whole, 1 by its pictures, 0 not at all. */
    private fun level(part: ContentPart, profile: ModelProfile): Int {
        val senses = MediaSupport.of(profile)
        return when (part) {
            is ContentPart.Image -> if (senses.pictures) 2 else 0
            is ContentPart.Attachment -> when {
                MediaSupport.takesWhole(part, profile, senses) -> 2
                MediaSupport.picturesFor(part, senses).isNotEmpty() -> 1
                else -> 0
            }
            else -> 0
        }
    }

    private suspend fun describeAll(
        message: ChatMessage,
        stamp: Long,
        describerFor: (ContentPart) -> ModelProfile?,
    ): Map<String, Pair<String, Long>> {
        val written = mutableMapOf<String, Pair<String, Long>>()
        for (part in message.parts) {
            if (!needs(part)) continue
            val key = keyOf(part) ?: continue
            if (key in failed) continue
            val profile = describerFor(part) ?: continue
            val text = try {
                describe(part, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                h.log("Couldn't describe ${kindOf(part)}: ${e.message}")
                null
            }
            if (text.isNullOrBlank()) failed += key else written[key] = text.trim() to stamp
        }
        return written
    }

    private suspend fun describe(part: ContentPart, profile: ModelProfile): String? {
        val whole = part is ContentPart.Image || (part is ContentPart.Attachment && MediaSupport.takesWhole(part, profile))
        val content: List<LlmPart> = when (part) {
            is ContentPart.Image -> listOf(LlmPart.Image(h.blobs.get(part.ref) ?: return null, part.mimeType))
            is ContentPart.Attachment -> {
                val senses = MediaSupport.of(profile)
                if (whole) {
                    val bytes = part.ref?.let { h.blobs.get(it) } ?: return null
                    parts.attachmentParts(part, MediaParts.Way.WHOLE, listOf(bytes), describedNow = false, profile = profile)
                } else {
                    val pictures = MediaSupport.picturesFor(part, senses).mapNotNull { h.blobs.get(it) }
                    if (pictures.isEmpty()) return null
                    parts.attachmentParts(part, MediaParts.Way.PICTURES, pictures, describedNow = false, profile = profile)
                }
            }
            else -> return null
        }
        val result = h.providers.forProfile(profile.copy(toolMode = ToolMode.NONE)).complete(
            LlmRequest(
                profile = profile.copy(maxOutputTokens = profile.maxOutputTokens.coerceAtMost(2_000)),
                apiKey = h.apiKey(profile.credentialId),
                system = SYSTEM,
                messages = listOf(LlmMessage.User(content + LlmPart.Text(instruction(part, whole)))),
            ),
        )
        return result.text.takeIf { result.stopReason != StopReason.REFUSAL }
    }

    private fun instruction(part: ContentPart, whole: Boolean): String = when {
        part is ContentPart.Image -> "Describe this photo in two or three sentences: what and who it shows, the setting and mood, and any words in it."
        part !is ContentPart.Attachment -> ""
        part.kind == AttachmentKind.VIDEO && whole -> "Describe this video in up to four sentences: what happens, who and what is in it, and how it sounds."
        part.kind == AttachmentKind.VIDEO -> "These are three frames from the video named above. In two or three sentences, describe what it seems to show."
        part.kind == AttachmentKind.SONG -> "Describe this song in two or three sentences: how it sounds, its mood, its language, and what the words are about."
        MediaSupport.isPdf(part) && whole -> "Describe this PDF in up to five sentences: what it is, what it says, and anything worth referring back to (names, dates, numbers)."
        MediaSupport.isPdf(part) -> "This is the first page of the PDF named above. In up to four sentences, describe what the document is and what this page says."
        else -> "Describe this text file in up to five sentences: what it is, what it says, and anything worth referring back to (names, dates, numbers)."
    } + " Write only the description, in plain sentences."

    /** Puts [written] descriptions into the stored note (read again, in case it changed meanwhile). */
    private suspend fun store(message: ChatMessage, written: Map<String, Pair<String, Long>>): ChatMessage {
        if (written.isEmpty()) return message
        val current = h.conversations.getMessages(message.conversationId).firstOrNull { it.id == message.id } ?: return message
        val updated = current.copy(
            parts = current.parts.map { part ->
                val (text, at) = keyOf(part)?.let { written[it] } ?: return@map part
                when (part) {
                    is ContentPart.Image -> part.copy(description = text, describedAt = at)
                    is ContentPart.Attachment -> part.copy(description = text, describedAt = at)
                    else -> part
                }
            },
        )
        h.conversations.updateMessage(updated)
        return updated
    }

    private suspend fun utility(settings: AppSettings): ModelProfile? = settings.utilityModelProfileId?.let { h.profiles.get(it) }

    private fun keyOf(part: ContentPart): String? = when (part) {
        is ContentPart.Image -> part.ref
        is ContentPart.Attachment -> part.id
        else -> null
    }

    private fun kindOf(part: ContentPart) = when (part) {
        is ContentPart.Image -> "a photo"
        is ContentPart.Attachment -> part.name ?: part.title ?: part.kind.name.lowercase()
        else -> "a part"
    }

    companion object {
        /** Notes looked over after each turn. */
        const val RECENT = 12

        /** A text file shorter than this is sent whole every time; it isn't worth describing. */
        const val LONG_TEXT = 6_000L

        const val SYSTEM = "You describe things people send each other with their letters, plainly and faithfully, " +
            "for someone who will read your description instead of seeing the thing."
    }
}
