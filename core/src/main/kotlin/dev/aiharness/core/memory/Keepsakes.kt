package dev.aiharness.core.memory

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.util.Clock

/**
 * What a persona keeps with a memory (docs/design/keepsakes.md): the pictures and attachments in
 * the note it's answering and its own letter, named by the handles it sees in its context
 * (`[cassette: rainy day mix]`, "the photo"). Naming nothing keeps the first thing in the note,
 * since weaker models often skip the parameter; "nothing" keeps nothing.
 */
class KeepsakeFinder(private val conversations: ConversationRepository, private val clock: Clock) {
    private class Candidate(val messageId: String, val part: ContentPart, val inNote: Boolean)

    /**
     * The keepsakes [keep] names in the conversation's latest exchanges, newest first; with [keep]
     * null and [byDefault], the first thing in the note being answered.
     */
    suspend fun find(conversationId: String, keep: List<String>?, byDefault: Boolean): List<Keepsake> {
        if (keep != null && keep.any { it.trim().lowercase() in NOTHING }) return emptyList()
        val history = conversations.getMessages(conversationId).filter { !it.hidden && !it.excludedFromContext }
        val lastNote = history.indexOfLast { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }
        val recent = history.drop((lastNote - LOOK_BACK).coerceAtLeast(0))
        val candidates = recent.flatMap { m -> (m.images + m.attachments).map { Candidate(m.id, it, m.role == MessageRole.USER) } }
        val now = clock.nowMillis()
        if (keep.isNullOrEmpty()) {
            if (!byDefault || lastNote < 0) return emptyList()
            val note = history[lastNote]
            return candidates.firstOrNull { it.messageId == note.id }?.let { listOf(Keepsake(it.messageId, it.part, now, fromYou = it.inNote)) }.orEmpty()
        }
        return keep.mapNotNull { handle -> candidates.asReversed().firstOrNull { matches(handle, it.part) } }
            .distinctBy { keyOf(it.part) }
            .map { Keepsake(it.messageId, it.part, now, fromYou = it.inNote) }
    }

    companion object {
        private const val LOOK_BACK = 6
        private val NOTHING = setOf("nothing", "none", "no", "")

        private fun keyOf(part: ContentPart) = (part as? ContentPart.Attachment)?.id ?: (part as? ContentPart.Image)?.ref

        /** The words something goes by: its frame and its kind. */
        fun kindWords(part: ContentPart): Set<String> = when (part) {
            is ContentPart.Image -> setOf("photo", "picture", "polaroid", "pic", "image")
            is ContentPart.Attachment -> when (part.kind) {
                AttachmentKind.FILE -> setOf("tag", "luggage tag", "file", "document", "pdf")
                AttachmentKind.SONG -> setOf("ticket", "song", "track", "ticket stub")
                AttachmentKind.PLAYLIST -> setOf("cassette", "playlist", "mix", "mixtape", "tape")
                AttachmentKind.VIDEO -> setOf("film", "video", "film strip", "clip")
                AttachmentKind.LINK -> setOf("cutting", "link", "page", "article")
            }
            else -> emptySet()
        }

        /** Whether [handle] ("[cassette: rainy day mix]", "the photo", "October Walk") names [part]. */
        fun matches(handle: String, part: ContentPart): Boolean {
            val h = handle.trim().trim('[', ']', '(', ')').trim().lowercase()
            if (h.isEmpty()) return false
            val words = kindWords(part)
            val split = h.split(':', limit = 2)
            val (kind, rest) = if (split.size == 2) split[0].trim() to split[1].trim() else null to h
            if (kind != null && kind !in words) return false
            val bare = rest.removePrefix("the ").removePrefix("a ").removePrefix("your ").removePrefix("their ").trim()
            if (bare in words || rest.isEmpty()) return true
            val hay = when (part) {
                is ContentPart.Attachment -> listOfNotNull(part.standIn, part.title, part.name, part.description)
                is ContentPart.Image -> listOfNotNull(part.caption, part.description)
                else -> emptyList()
            }.joinToString(" ").lowercase()
            // The handle as the persona saw it, or the start of it (a long stand-in, cut short).
            return hay.contains(bare) || (bare.length >= 6 && hay.contains(bare.take(24)))
        }
    }
}

/** "(kept with [ticket: October Walk by Hana Sato, 3:42])", for a memory's line in the persona's context. */
val MemoryEntry.keptNote: String
    get() = if (keepsakes.isEmpty()) "" else " (kept with " + keepsakes.joinToString(" and ") { it.label } + ")"
