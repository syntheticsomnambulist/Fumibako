package dev.aiharness.core.model

import kotlinx.serialization.Serializable

/**
 * Something that happened to an attachment after it was sent: a ticket punched the first time it
 * played, a persona's read stamp on a cutting. Kept apart from the message, like stickers, so the
 * parts sent to providers never change.
 */
@Serializable
data class AttachmentMark(
    val attachmentId: String,
    val kind: MarkKind,
    /** The conversation it's in, so the mark goes with it. */
    val conversationId: String? = null,
    /** Whose mark: a persona's id, or null for you. */
    val byPersonaId: String? = null,
    val at: Long,
) {
    /** One mark of a kind per attachment and person, so marking again changes nothing. */
    val id: String get() = "${kind.name.lowercase()}:$attachmentId:${byPersonaId ?: "you"}"
}

@Serializable
enum class MarkKind {
    /** A ticket punched the first time its song played. */
    PUNCHED,

    /** A persona opened the link (its 読 stamp lands on the cutting). */
    READ,
}
