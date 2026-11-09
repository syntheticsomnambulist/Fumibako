package dev.aiharness.core.model

import kotlinx.serialization.Serializable

/**
 * What a persona did in the background. Each heartbeat logs at least one of [WROTE_LETTER],
 * [STAYED_QUIET] or [RESTED], so the log also says when a persona last woke. New heartbeat activities
 * add their own kinds here.
 */
@Serializable
enum class ActivityKind {
    /** A private moment that ended in a letter to the user. */
    WROTE_LETTER,

    /** A private moment that ended without writing. */
    STAYED_QUIET,

    /** Woke, tended things, but nothing called for a private moment ([ActivityEntry.note] says why). */
    RESTED,

    /** Memories kept during a heartbeat ([ActivityEntry.count] of them). */
    SAVED_MEMORIES,

    /** Folded recent conversation into an episode memory. */
    RECORDED_EPISODE,

    /** Wrote a reflection on what has happened lately. */
    REFLECTED,

    /** Changed how some memories feel ([ActivityEntry.count] of them). */
    REVISITED_FEELINGS,

    /** Let some memories settle from "matters deeply" to "worth keeping". */
    SETTLED_MEMORIES,

    /** Let memories go for good. */
    LET_GO,

    /** Changed what it's wearing ([ActivityEntry.note]: the outfit, empty for its usual clothes). */
    CHANGED_OUTFIT,

    /** Stuck a sticker on your note while you were away ([ActivityEntry.note]: its `:name:`). */
    STUCK_STICKER,

    /** Drew new stickers for its sheet ([ActivityEntry.count] of them; [ActivityEntry.tags]: their art ids). */
    DREW_STICKERS,

    /** Wrote in your planner while you were away: notes, suggestions, reminders ([ActivityEntry.count] of them). */
    WROTE_IN_PLANNER,

    /** Wrote a haiku in your planner on a new season's first day ([ActivityEntry.note]: the microseason). */
    WROTE_HAIKU,

    /** Chose a new box for its letters ([ActivityEntry.note]: the box in words). */
    CHOSE_LETTER_BOX,
    ;

    /** Kinds that mark a heartbeat having happened. */
    val isBeat: Boolean get() = this == WROTE_LETTER || this == STAYED_QUIET || this == RESTED
}

/** One line of what a persona did in the background, for "While you were away" and its page. */
@Serializable
data class ActivityEntry(
    val id: String,
    val personaId: String,
    val kind: ActivityKind,
    val createdAt: Long,
    val conversationId: String? = null,
    val count: Int = 1,
    /** A short detail, e.g. why a heartbeat rested ("quiet hours"). */
    val note: String? = null,
    /** Machine-readable details, e.g. which heartbeat activities were part of a private moment. */
    val tags: List<String> = emptyList(),
)
