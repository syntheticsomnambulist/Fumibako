package dev.aiharness.core.model

import kotlinx.serialization.Serializable

/**
 * What a planner entry is (docs/design/calendar.md on the design branch). Yours: a plan, or part
 * of your typical week. A persona's: a note on a day, a plan it suggests, a reminder or promise
 * (which wakes it), or its own rest.
 */
@Serializable
enum class EntryKind { PLAN, ROUTINE, NOTE, SUGGESTION, REMINDER, REST }

/** What part of your typical week a routine is. Work and sleep are busy; sleep also folds the week page and sets quiet hours. */
@Serializable
enum class RoutineKind { WORK, SLEEP, OTHER }

/**
 * Where an entry stands. A plan is [OPEN] until it's [DONE] or [CANCELLED] (a moved plan stays
 * open, with [PlannerEntry.movedFrom]); a suggestion is open until it's [ADDED] or [DECLINED]
 * ("not this time"); a reminder until it's [KEPT] or [MISSED]. Notes and rests stay open.
 */
@Serializable
enum class EntryState { OPEN, DONE, CANCELLED, ADDED, DECLINED, KEPT, MISSED }

/** The days and hours a routine takes up each week: [from] to [to] in minutes after midnight, running past midnight when [to] is earlier. */
@Serializable
data class Weekly(
    /** ISO days of the week, 1 (Monday) to 7 (Sunday). */
    val days: List<Int>,
    val from: Int,
    val to: Int,
) {
    val crossesMidnight: Boolean get() = to <= from
}

/**
 * One thing in a planner. Every one of your personas keeps a planner of its own ([plannerId] is a
 * user persona's id), so a fictional one can have a fictional week; what a persona writes goes in
 * the planner of the persona of yours it's writing to.
 *
 * When: a timed entry has [start] and [end] (an end of null is a moment: a reminder, a note's
 * time); an all-day or untimed one has [day]; a routine has [weekly].
 */
@Serializable
data class PlannerEntry(
    val id: String,
    val plannerId: String,
    /** Who wrote it: null for you, else the persona's id. */
    val byPersonaId: String? = null,
    val kind: EntryKind,
    val title: String = "",
    val start: Long? = null,
    val end: Long? = null,
    /** An all-day or untimed entry's day, as ISO ("2026-11-07"). */
    val day: String? = null,
    /** True for an all-day entry on [day]; false for an untimed one (a task for that day). */
    val allDay: Boolean = false,
    val weekly: Weekly? = null,
    val routine: RoutineKind? = null,
    val place: String? = null,
    val note: String? = null,
    /** Whether it makes you busy (a hatched mark in the margin; held notifications later). */
    val busy: Boolean = false,
    val state: EntryState = EntryState.OPEN,
    /** The day it was moved from (ISO), for the ghost left there. */
    val movedFrom: String? = null,
    /** A plan made by adding a persona's suggestion: the suggestion's id (its slip stays beside it). */
    val fromSuggestion: String? = null,
    /** Where a persona wrote it from. */
    val conversationId: String? = null,
    val messageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
