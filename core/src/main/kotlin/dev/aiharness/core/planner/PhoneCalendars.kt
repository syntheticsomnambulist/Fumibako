package dev.aiharness.core.planner

import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PhoneCalendarSettings
import dev.aiharness.core.model.PhoneDetail
import dev.aiharness.core.model.PlannerEntry

/** One of the phone's calendars: its name, account and color, and whether it's a holiday calendar. */
data class PhoneCalendar(val id: String, val name: String, val account: String, val color: Int? = null, val holidays: Boolean = false)

/** One occurrence of an event in a phone calendar (repeats already expanded by the phone). */
data class PhoneEvent(
    val eventId: Long,
    val calendarId: String,
    val title: String,
    val place: String? = null,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    /** Busy by its availability (tentative counts as busy); free otherwise. */
    val busy: Boolean = true,
    /** For an all-day event, its day (ISO), as the phone keeps all-day events in UTC. */
    val day: String? = null,
)

/**
 * The phone's calendars, read-only (the app supplies one; with no permission it reads nothing).
 * Events are read live each time and never copied (docs/design/calendar.md, "Read live, never copied").
 */
interface PhoneCalendars {
    /** Whether the phone lets the app read its calendars. */
    fun readable(): Boolean

    suspend fun calendars(): List<PhoneCalendar>

    /** Every occurrence overlapping [from] to [to] (epoch millis) in [calendarIds]. */
    suspend fun events(from: Long, to: Long, calendarIds: Set<String>): List<PhoneEvent>
}

/** Phone events as the planner holds them: a pencil entry each, at its calendar's detail level, with your stamps. */
object PhoneEntries {
    /** The entry id for an event's occurrence, which stamps are keyed by too. */
    fun id(eventId: Long, start: Long) = "phone:$eventId:$start"

    fun of(event: PhoneEvent, calendar: PhoneCalendar?, settings: PhoneCalendarSettings, plannerId: String): PlannerEntry? {
        val level = settings.level(event.calendarId)
        if (level == PhoneDetail.HIDDEN) return null
        val stamp = settings.stamps.lastOrNull { it.eventId == event.eventId && it.start == event.start }
        return PlannerEntry(
            id = id(event.eventId, event.start),
            plannerId = plannerId,
            kind = EntryKind.PLAN,
            title = if (level == PhoneDetail.BUSY) "" else event.title,
            start = if (event.allDay) null else event.start,
            end = if (event.allDay) null else event.end,
            day = if (event.allDay) event.day else null,
            allDay = event.allDay,
            place = event.place?.takeIf { level == PhoneDetail.PLACES && it.isNotBlank() },
            busy = event.busy && !event.allDay,
            state = stamp?.state ?: EntryState.OPEN,
            phone = calendar?.name ?: "Phone calendar",
            createdAt = 0,
            updatedAt = stamp?.at ?: 0,
        )
    }

    /** "phone:123:1700000000000" back to its event id and start. */
    fun parse(id: String): Pair<Long, Long>? {
        val parts = id.split(':')
        if (parts.size != 3 || parts[0] != "phone") return null
        return parts[1].toLongOrNull()?.let { e -> parts[2].toLongOrNull()?.let { s -> e to s } }
    }
}
