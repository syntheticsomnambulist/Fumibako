package dev.aiharness.core.stickers

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.StickerTargetKind
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.storage.StickerRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** The stickers a persona can use: its own sheet, then the shared library. */
data class StickerShelf(val own: List<StickerArt>, val shared: List<StickerArt>) {
    val all: List<StickerArt> get() = own + shared
    val isEmpty: Boolean get() = own.isEmpty() && shared.isEmpty()
}

/** Where a persona can put a sticker on a letter, by name (it can't see the page). */
enum class StickerSpot(val label: String, val x: Float, val y: Float) {
    TOP_LEFT("top left", 0.08f, 0.12f),
    TOP("top", 0.5f, 0.06f),
    TOP_RIGHT("top right", 0.92f, 0.12f),
    LEFT("left", 0.05f, 0.5f),
    MIDDLE("middle", 0.5f, 0.5f),
    RIGHT("right", 0.95f, 0.5f),
    BOTTOM_LEFT("bottom left", 0.08f, 0.9f),
    BOTTOM("bottom", 0.5f, 0.95f),
    BOTTOM_RIGHT("bottom right", 0.92f, 0.88f),
    ;

    companion object {
        fun parse(text: String?): StickerSpot? {
            val t = text?.trim()?.lowercase()?.replace('-', ' ')?.replace('_', ' ') ?: return null
            return entries.firstOrNull { it.label == t || it.name.lowercase().replace('_', ' ') == t }
                ?: if (t == "center" || t == "centre") MIDDLE else null
        }
    }
}

sealed interface StickResult {
    data class Stuck(val sticker: Sticker) : StickResult
    data class Refused(val reason: String) : StickResult
}

/**
 * Stickers: what each one is called, putting them on letters and peeling them off within the
 * limits (how many fit on a letter, who may peel whose), and the short notes a persona reads about
 * what you stuck or peeled since its last turn.
 */
class StickerService(
    private val stickers: StickerRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val random: Random = Random.Default,
) {
    suspend fun shelf(personaId: String): StickerShelf = StickerShelf(stickers.art(personaId), stickers.art(null))

    /**
     * What `:aria-wave:`, `aria-wave`, `:tada:` or 🎉 means for this persona: its own sticker first,
     * then the shared library, then emoji. Null when nothing goes by that name.
     */
    suspend fun resolve(personaId: String, text: String): StickerRef? = resolve(shelf(personaId), text)

    fun resolve(shelf: StickerShelf, text: String): StickerRef? {
        val raw = text.trim()
        val name = raw.trim(':').trim().lowercase()
        shelf.own.firstOrNull { it.name.lowercase() == name }?.let { return StickerRef.Art(it.id) }
        shelf.shared.firstOrNull { it.name.lowercase() == name }?.let { return StickerRef.Art(it.id) }
        Emoji.forName(name)?.let { return StickerRef.Emoji(it.emoji, it.name) }
        Emoji.forEmoji(raw)?.let { return StickerRef.Emoji(it.emoji, it.name) }
        if (Emoji.looksLikeEmoji(raw)) return StickerRef.Emoji(raw, "emoji")
        return null
    }

    /** `:name:` for a sticker, as people and personas write it. */
    suspend fun nameOf(ref: StickerRef): String = when (ref) {
        is StickerRef.Emoji -> if (ref.name == "emoji") ref.emoji else ":${ref.name}:"
        is StickerRef.Art -> stickers.getArt(ref.artId)?.let { ":${it.name}:" } ?: ":sticker:"
    }

    /** Stickers on [target] that are still stuck, in the order they were stuck. */
    suspend fun on(conversationId: String, target: StickerTarget): List<Sticker> =
        stickers.forConversation(conversationId).filter { it.target == target && !it.isPeeled }

    /** Sticks [ref] on [target], unless the letter already has as many as the settings allow. */
    suspend fun stick(
        conversationId: String,
        target: StickerTarget,
        ref: StickerRef,
        byPersonaId: String?,
        x: Float,
        y: Float,
        rotationDeg: Float = 0f,
        scale: Float = 1f,
    ): StickResult {
        // A planner's day can be covered: it takes twice what a letter does.
        val limit = settings.current().stickers.maxPerLetter * if (target.kind == StickerTargetKind.DAY) 2 else 1
        if (on(conversationId, target).size >= limit) {
            return StickResult.Refused("That one already has $limit stickers, as many as fit.")
        }
        val now = clock.nowMillis()
        val sticker = Sticker(
            id = newId(),
            conversationId = conversationId,
            target = target,
            ref = ref,
            byPersonaId = byPersonaId,
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            rotationDeg = rotationDeg,
            scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
            createdAt = now,
        )
        stickers.upsert(sticker)
        return StickResult.Stuck(sticker)
    }

    /** Sticks one at a named spot, nudged and tilted a little so no two look placed by a ruler. */
    suspend fun stickAt(conversationId: String, target: StickerTarget, ref: StickerRef, byPersonaId: String?, spot: StickerSpot): StickResult {
        val nudge = { random.nextFloat() * 0.08f - 0.04f }
        return stick(conversationId, target, ref, byPersonaId, spot.x + nudge(), spot.y + nudge(), rotationDeg = random.nextFloat() * 28f - 14f)
    }

    suspend fun move(sticker: Sticker, x: Float, y: Float, rotationDeg: Float, scale: Float): Sticker {
        val moved = sticker.copy(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f),
            rotationDeg = rotationDeg,
            scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
            updatedAt = clock.nowMillis(),
        )
        stickers.upsert(moved)
        return moved
    }

    /**
     * Peels [sticker] off, by a persona ([byPersonaId]) or you (null). Peeling keeps the row until
     * the persona has heard about it. A sticker on your draft just goes: there's nothing to tell.
     */
    suspend fun peel(sticker: Sticker, byPersonaId: String?) {
        if (sticker.target.kind == StickerTargetKind.DRAFT) {
            stickers.delete(sticker.id)
            return
        }
        val now = clock.nowMillis()
        stickers.upsert(sticker.copy(peeledAt = now, peeledByPersonaId = byPersonaId, updatedAt = now))
    }

    /** Whether a persona with [canPeelYours] may peel [sticker]: always its own, yours only if allowed. */
    fun personaMayPeel(sticker: Sticker, personaId: String, canPeelYours: Boolean): Boolean =
        sticker.byPersonaId == personaId || (sticker.byPersonaId == null && canPeelYours)

    /** Whether you may peel [sticker]: always your own, a persona's only if it allows it. */
    fun youMayPeel(sticker: Sticker, youCanPeelPersona: Boolean): Boolean = sticker.byPersonaId == null || youCanPeelPersona

    /** Moves the stickers on your draft onto the note you just sent. */
    suspend fun sendDraft(conversationId: String, messageId: String) {
        val now = clock.nowMillis()
        on(conversationId, StickerTarget.draft(conversationId)).forEach {
            stickers.upsert(it.copy(target = StickerTarget.message(messageId), createdAt = now, updatedAt = now))
        }
    }

    /**
     * Notes for the persona's next turn about stickers you stuck or peeled since its last turn
     * ([since]), e.g. `[Sam stuck :tada: on your Fri 00:14 letter]`. Its own doings aren't news to it.
     * [currentMessageId] is the note being sent, which the notes call "this note".
     */
    suspend fun notesSince(
        conversationId: String,
        since: Long,
        history: List<ChatMessage>,
        currentMessageId: String?,
        userName: String,
    ): List<String> {
        val byId = history.associateBy { it.id }
        return stickers.changedSince(conversationId, since).mapNotNull { s ->
            val where = when (s.target.kind) {
                StickerTargetKind.MESSAGE -> place(s.target.id, byId, currentMessageId, userName)
                StickerTargetKind.ATTACHMENT -> history.lastOrNull { m -> m.attachments.any { it.id == s.target.id } || m.images.any { it.ref == s.target.id } }?.let { m ->
                    val thing = m.attachments.firstOrNull { it.id == s.target.id }?.let { frameName(it.kind) } ?: "photo"
                    place(m.id, byId, currentMessageId, userName)?.let { "the $thing in $it" }
                }
                // The planner's stickers are kept under the planner, not a letter (what a persona hears of them comes with step 4).
                StickerTargetKind.DRAFT, StickerTargetKind.DAY, StickerTargetKind.ENTRY -> null
            } ?: return@mapNotNull null
            val stuckNow = s.byPersonaId == null && s.createdAt > since
            val peeledNow = s.isPeeled && s.peeledByPersonaId == null && (s.peeledAt ?: 0L) > since
            val name = nameOf(s.ref)
            when {
                stuckNow && peeledNow -> null
                stuckNow -> "[$userName stuck $name on $where]"
                peeledNow -> "[$userName peeled ${if (s.byPersonaId == null) "" else "your "}$name off $where]"
                else -> null
            }
        }
    }

    /** "this note", "your Fri 00:14 letter" or "Sam's Fri 00:13 note"; null for messages it can't see. */
    private fun place(messageId: String, byId: Map<String, ChatMessage>, currentMessageId: String?, userName: String): String? {
        if (messageId == currentMessageId) return "this note"
        val message = byId[messageId] ?: return null
        if (message.hidden || message.excludedFromContext) return null
        val time = label(message.createdAt)
        return when (message.role) {
            MessageRole.ASSISTANT -> "your $time letter"
            MessageRole.USER -> "$userName's $time note"
            MessageRole.TOOL -> null
        }
    }

    private fun frameName(kind: dev.aiharness.core.model.AttachmentKind) = when (kind) {
        dev.aiharness.core.model.AttachmentKind.FILE -> "luggage tag"
        dev.aiharness.core.model.AttachmentKind.SONG -> "ticket"
        dev.aiharness.core.model.AttachmentKind.PLAYLIST -> "cassette"
        dev.aiharness.core.model.AttachmentKind.VIDEO -> "film strip"
        dev.aiharness.core.model.AttachmentKind.LINK -> "cutting"
    }

    /** "Fri 00:14" within the week, "3 Nov" before that. */
    fun label(time: Long): String {
        val at = Instant.ofEpochMilli(time).atZone(clock.zone)
        val recent = clock.nowMillis() - time < 6 * DAY
        return (if (recent) DAY_TIME else DATE).format(at)
    }

    /** Stickers that joined the persona's shelf after [since] (it learns their names as they come). */
    suspend fun newSince(personaId: String, since: Long): List<StickerArt> = shelf(personaId).all.filter { it.createdAt > since }

    /** Peeled stickers older than a month: everyone who needed to hear has. */
    suspend fun tidy() = stickers.deletePeeledBefore(clock.nowMillis() - 30 * DAY)

    companion object {
        /**
         * The sticker a persona's unread letters wear: the one it was given, else its sticker for
         * "hello" (by feeling, then by name), else none (the seal).
         */
        fun unreadMark(persona: dev.aiharness.core.model.Persona, sheet: List<StickerArt>): StickerArt? =
            if (persona.unreadMark == SEAL) null
            else sheet.firstOrNull { it.id == persona.unreadMark }
                ?: sheet.firstOrNull { it.feeling == "hello" }
                ?: sheet.firstOrNull { "hello" in it.name || "wave" in it.name }

        /** The unread mark that means the 新 seal, not a sticker. */
        const val SEAL = "seal"

        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.6f
        private const val DAY = 24 * 60 * 60 * 1000L
        private val DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
        private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    }
}
