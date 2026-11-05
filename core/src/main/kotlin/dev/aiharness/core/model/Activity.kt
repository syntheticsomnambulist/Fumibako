package dev.aiharness.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityKind {
    /** A heartbeat turn ended with a letter to the user. */
    WROTE_LETTER,

    /** A heartbeat turn ended with `[SILENT]`. */
    STAYED_QUIET,

    /** Memories saved during a heartbeat turn ([ActivityEntry.count] of them). */
    SAVED_MEMORIES,

    /** Background upkeep folded recent conversation into an episode memory. */
    RECORDED_EPISODE,
}

/** One line of what a persona did in the background, for the "While you were away" card. */
@Serializable
data class ActivityEntry(
    val id: String,
    val personaId: String,
    val kind: ActivityKind,
    val createdAt: Long,
    val conversationId: String? = null,
    val count: Int = 1,
)
