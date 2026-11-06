package dev.aiharness.core.media

import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.storage.BlobStore

/**
 * What a note's pictures and attachments become for the model (docs/design/media.md, "What the
 * persona receives"):
 *
 * - **Whole** where the model takes it in: pictures, PDFs, songs and videos as themselves, a
 *   text file as text.
 * - **Pictures** standing in where it can't but sees: a video's three frames, a PDF's first page.
 * - **The stand-in** otherwise: `[ticket: October Walk by Hana Sato, 3:42]`.
 * - **The description** once one was written, by the epochs that begin after it (history stays
 *   append-only within an epoch), or from the start when it was written on arrival, before the
 *   persona first read the note.
 *
 * Whole things are chosen newest first, as long as the request stays within the provider's size.
 */
class MediaParts(private val blobs: BlobStore) {
    enum class Way { WHOLE, PICTURES }

    /** What goes whole (or as pictures) this time, by picture ref or attachment id, with the bytes. */
    data class Plan(val chosen: Map<String, Pair<Way, List<ByteArray>>> = emptyMap()) {
        fun way(key: String): Way? = chosen[key]?.first
        fun bytes(key: String): List<ByteArray> = chosen[key]?.second.orEmpty()
    }

    suspend fun plan(window: List<ChatMessage>, epochStartedAt: Long, profile: ModelProfile): Plan {
        val senses = MediaSupport.of(profile)
        var room = MediaSupport.inlineBudget(profile)
        val chosen = mutableMapOf<String, Pair<Way, List<ByteArray>>>()
        for (message in window.asReversed()) {
            if (message.role != MessageRole.USER) continue
            for (image in message.images) {
                if (!senses.pictures || described(image.describedAt, message.createdAt, epochStartedAt)) continue
                val bytes = blobs.get(image.ref) ?: continue
                if (bytes.size > room) continue
                room -= bytes.size
                chosen[image.ref] = Way.WHOLE to listOf(bytes)
            }
            for (a in message.attachments) {
                if (described(a.describedAt, message.createdAt, epochStartedAt)) continue
                if (MediaSupport.takesWhole(a, profile, senses)) {
                    val bytes = MediaSupport.sentRef(a)?.let { blobs.get(it) }
                    if (bytes != null && (MediaSupport.isText(a) || bytes.size <= room)) {
                        if (!MediaSupport.isText(a)) room -= bytes.size
                        chosen[a.id] = Way.WHOLE to listOf(bytes)
                        continue
                    }
                }
                val pictures = MediaSupport.picturesFor(a, senses).mapNotNull { blobs.get(it) }
                if (pictures.isNotEmpty() && pictures.sumOf { it.size } <= room) {
                    room -= pictures.sumOf { it.size }
                    chosen[a.id] = Way.PICTURES to pictures
                }
            }
        }
        return Plan(chosen)
    }

    /** A note's pictures and attachments as parts for the model, following [plan]. */
    fun parts(message: ChatMessage, plan: Plan, epochStartedAt: Long, profile: ModelProfile): List<LlmPart> = buildList {
        message.images.forEach { image ->
            when {
                described(image.describedAt, message.createdAt, epochStartedAt) -> add(LlmPart.Text("[photo] ${image.description}"))
                plan.way(image.ref) == Way.WHOLE -> add(LlmPart.Image(plan.bytes(image.ref).first(), image.mimeType))
                else -> add(LlmPart.Text("[photo${image.caption?.let { ": $it" } ?: ""}]"))
            }
        }
        message.attachments.forEach { a -> addAll(attachmentParts(a, plan.way(a.id), plan.bytes(a.id), described(a.describedAt, message.createdAt, epochStartedAt), profile)) }
    }

    /** One attachment as parts: its description, itself, its pictures or its stand-in. */
    fun attachmentParts(a: ContentPart.Attachment, way: Way?, bytes: List<ByteArray>, describedNow: Boolean, profile: ModelProfile): List<LlmPart> = when {
        describedNow -> listOf(LlmPart.Text("${a.standIn} ${a.description}"))
        way == Way.WHOLE && MediaSupport.isText(a) -> listOf(LlmPart.Text(textFile(a, bytes.first(), MediaSupport.textChars(profile))))
        // A song from a music search is sent as its preview; only a model hearing it is told so
        // (one that can't hear it has no use for a preview it isn't sent).
        way == Way.WHOLE && a.kind == AttachmentKind.SONG && a.previewOnly ->
            listOf(LlmPart.Text("${a.standIn} (its 30-second preview:)"), LlmPart.Media(bytes.first(), wireType(a), a.title))
        way == Way.WHOLE -> listOf(LlmPart.Text(a.standIn), LlmPart.Media(bytes.first(), wireType(a), a.name ?: a.title))
        way == Way.PICTURES -> listOf(LlmPart.Text("${a.standIn} ${if (a.kind == AttachmentKind.VIDEO) "(three frames from it:)" else "(its first page:)"}")) +
            bytes.map { LlmPart.Image(it, "image/jpeg") }
        else -> listOf(LlmPart.Text(standInWithSnippet(a)))
    }

    /** What the model is told the thing is, as the provider wants it named. */
    private fun wireType(a: ContentPart.Attachment): String = when (a.kind) {
        AttachmentKind.SONG -> MediaSupport.sentType(a).let { MediaSupport.geminiAudioType(it) ?: it.orEmpty() }
        AttachmentKind.VIDEO -> MediaSupport.sentType(a).let { MediaSupport.geminiVideoType(it) ?: it.orEmpty() }
        else -> if (MediaSupport.isPdf(a)) "application/pdf" else a.mimeType ?: "application/octet-stream"
    }

    companion object {
        /**
         * Whether this epoch reads the description: it was written on arrival (no later than the
         * note), or before the epoch began. Otherwise the epoch keeps sending what it first sent.
         */
        fun described(describedAt: Long?, createdAt: Long, epochStartedAt: Long): Boolean =
            describedAt != null && (describedAt <= createdAt || describedAt < epochStartedAt)

        fun textFile(a: ContentPart.Attachment, bytes: ByteArray, maxChars: Int): String {
            val text = bytes.toString(Charsets.UTF_8)
            val cut = text.length > maxChars
            val shown = if (cut) text.take(maxChars) else text
            val tail = if (cut) "\n[The rest is cut off: ${shown.length * 100 / text.length}% shown.]" else ""
            return "${a.standIn}\n<file name=\"${a.name ?: "file"}\">\n$shown$tail\n</file>"
        }

        /** A cutting's stand-in with the page's own description, which the reader of a newspaper sees too. */
        fun standInWithSnippet(a: ContentPart.Attachment): String =
            if (a.kind == AttachmentKind.LINK && !a.snippet.isNullOrBlank()) "${a.standIn} ${a.snippet}" else a.standIn
    }
}
