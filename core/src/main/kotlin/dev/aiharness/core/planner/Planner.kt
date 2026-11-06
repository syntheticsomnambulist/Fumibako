package dev.aiharness.core.planner

import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.PlannerRepository
import dev.aiharness.core.util.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** An entry on a day: when it starts and ends there (a routine's week expanded; a timed entry as it is). */
data class Occurrence(val entry: PlannerEntry, val start: Long?, val end: Long?, val day: LocalDate)

/**
 * The planner (docs/design/calendar.md on the design branch): your personas' planners, what's in
 * them on each day, whether you're busy, and what a persona is told of them.
 */
class Planner(private val entries: PlannerRepository, private val clock: Clock) {
    private val zone: ZoneId get() = clock.zone

    /** Whether [persona] sees [you]'s planner: both switches on. */
    fun shared(persona: Persona, you: UserPersona?, settings: AppSettings): Boolean =
        you != null && persona.seesPlanner && you.sharesPlanner(settings)

    /** What's on each day from [from] to [to] (both included), in time order; untimed and all-day ones first. */
    suspend fun days(plannerId: String, from: LocalDate, to: LocalDate): List<Occurrence> =
        occurrences(entries.forPlanner(plannerId), from, to)

    fun occurrences(all: List<PlannerEntry>, from: LocalDate, to: LocalDate): List<Occurrence> {
        val out = mutableListOf<Occurrence>()
        var day = from
        while (!day.isAfter(to)) {
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            for (e in all) {
                when {
                    e.weekly != null -> {
                        val w = e.weekly
                        // Today's stretch, and last night's running into today.
                        if (day.dayOfWeek.value in w.days) {
                            val start = dayStart + w.from * MINUTE
                            val end = if (w.crossesMidnight) dayEnd + w.to * MINUTE else dayStart + w.to * MINUTE
                            out += Occurrence(e, start, minOf(end, dayEnd), day)
                        }
                        if (w.crossesMidnight && day.minusDays(1).dayOfWeek.value in w.days && w.to > 0) {
                            out += Occurrence(e, dayStart, dayStart + w.to * MINUTE, day)
                        }
                    }
                    e.day != null -> if (e.day == day.toString()) out += Occurrence(e, null, null, day)
                    e.start != null -> {
                        val end = e.end ?: e.start
                        if (e.start < dayEnd && end >= dayStart) out += Occurrence(e, maxOf(e.start, dayStart), minOf(end, dayEnd), day)
                    }
                }
            }
            day = day.plusDays(1)
        }
        return out.sortedWith(compareBy<Occurrence> { it.day }.thenBy { it.start ?: Long.MIN_VALUE })
    }

    /**
     * When you're busy right now: what's keeping you (the entry that ends last among those
     * running together) and until when, or null when nothing busy is on.
     */
    suspend fun busyNow(plannerId: String): Pair<PlannerEntry, Long>? {
        val now = clock.nowMillis()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val busy = days(plannerId, today, today.plusDays(2))
            .filter { isBusy(it.entry) && it.start != null && it.end != null }
        var current = busy.filter { it.start!! <= now && it.end!! > now }.maxByOrNull { it.end!! } ?: return null
        // Busy stretches that run on into one another count as one (work, then a dinner straight after).
        while (true) {
            val next = busy.filter { it.start!! <= current.end!! && it.end!! > current.end!! }.maxByOrNull { it.end!! } ?: break
            current = next
        }
        return current.entry to current.end!!
    }

    /**
     * What [persona] is told of [you]'s planner in a letter's context block, or null when it isn't
     * shared: what's on now and next, to the end of tomorrow; up to three notable days in the week
     * after (all-day entries, and anything the persona wrote about); its own notes, reminders and
     * suggestions; and how your plans stand.
     */
    suspend fun contextFor(persona: Persona, you: UserPersona?, settings: AppSettings): String? {
        if (you == null || !shared(persona, you, settings)) return null
        val all = entries.forPlanner(you.id)
        val now = clock.nowMillis()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val holidays = Holidays.between(settings.planner.holidays, today, today.plusDays(6))
        if (all.isEmpty() && holidays.isEmpty()) return null
        val name = you.name.ifBlank { "Your writer" }
        val lines = mutableListOf<String>()

        val soon = occurrences(all.filter { it.byPersonaId == null && it.state != EntryState.CANCELLED }, today, today.plusDays(1))
            .filter { it.end == null || it.end > now || it.start == null }
        val busy = busyNow(you.id)
        val parts = mutableListOf<String>()
        busy?.let { (entry, until) -> parts += "${doing(entry)} until ${time(until)}" }
        // What's still to come (what's on now is said above; a routine is the same entry every day).
        val later = soon.filter { o -> o.entry.routine != RoutineKind.SLEEP && (o.start == null || o.start > now) }
        val todayLater = later.filter { it.day == today }.map(::described)
        val tomorrow = later.filter { it.day == today.plusDays(1) }.map(::described)
        if (todayLater.isNotEmpty()) parts += "Later today: " + todayLater.joinToString("; ")
        if (tomorrow.isNotEmpty()) parts += "Tomorrow: " + tomorrow.joinToString("; ")
        if (parts.isNotEmpty()) lines += "$name's planner: " + parts.joinToString(". ") + "."

        // Notable days ahead: your all-day entries, and your plans on days the persona wrote about.
        val wroteOn = all.filter { it.byPersonaId == persona.id }.mapNotNull(::dayOf).toSet()
        val ahead = occurrences(all.filter { it.byPersonaId == null && it.weekly == null && it.state != EntryState.CANCELLED }, today.plusDays(2), today.plusDays(7))
            .filter { o -> o.entry.allDay || o.day in wroteOn }
            .take(3)
        if (ahead.isNotEmpty()) lines += "Coming up: " + ahead.joinToString("; ") { "${weekday(it.day, today)}: ${described(it)}" } + "."
        // Public holidays this week (they leave your typical week as it is: few are days off for everyone).
        if (holidays.isNotEmpty()) lines += holidays.joinToString(" ") { (day, h) ->
            "${weekday(day, today).replaceFirstChar { it.uppercase() }} is a public holiday: ${h.name}."
        }

        // Its own writing.
        val mine = all.filter { it.byPersonaId == persona.id }
        val notes = mine.filter { it.kind == EntryKind.NOTE && dayOf(it)?.let { d -> !d.isBefore(today) } == true }
        if (notes.isNotEmpty()) lines += "Your notes in $name's planner: " + notes.joinToString("; ") { "${it.title} (${weekday(dayOf(it)!!, today)})" } + "."
        val reminders = mine.filter { it.kind == EntryKind.REMINDER && (it.state == EntryState.OPEN || it.updatedAt > now - DAY) }
        if (reminders.isNotEmpty()) lines += "Your reminders: " + reminders.joinToString("; ") { r ->
            r.title + (r.start?.let { " (${weekday(dayAt(it), today)} ${time(it)})" } ?: "") + when (r.state) {
                EntryState.KEPT -> ": kept"
                EntryState.MISSED -> ": missed"
                else -> ""
            }
        } + "."
        val suggestions = mine.filter { it.kind == EntryKind.SUGGESTION && (it.state == EntryState.OPEN || it.updatedAt > now - DAY) }
        if (suggestions.isNotEmpty()) lines += "You suggested: " + suggestions.joinToString("; ") { s ->
            val whenText = dayOf(s)?.let { d -> weekday(d, today) + (s.start?.let { " " + time(it) } ?: "") }
            listOfNotNull(s.title, whenText).joinToString(", ") + when (s.state) {
                EntryState.ADDED -> " ($name added it)"
                EntryState.DECLINED -> " ($name said not this time)"
                else -> " (not answered yet)"
            }
        } + "."
        // Away, in its own words for it (resting, busy with something, on a trip).
        mine.firstOrNull { it.kind == EntryKind.AWAY && (it.end ?: 0) > now }?.let { away ->
            val until = away.end ?: return@let
            lines += "You're away until ${weekday(dayAt(until), today)} ${time(until)}" + (away.title.takeIf { it.isNotBlank() }?.let { ": $it." } ?: ".")
        }

        // How your plans stand, lately.
        val stood = all.filter { it.byPersonaId == null && it.kind == EntryKind.PLAN && it.updatedAt > now - 2 * DAY }
            .mapNotNull { p ->
                val title = p.title.ifBlank { return@mapNotNull null }
                when {
                    p.state == EntryState.DONE -> "$title: done"
                    p.state == EntryState.CANCELLED -> "$title: cancelled"
                    p.movedFrom != null -> dayOf(p)?.let { d -> "$title (${weekday(LocalDate.parse(p.movedFrom), today)}): moved to ${weekday(d, today)}" }
                    else -> null
                }
            }
        if (stood.isNotEmpty()) lines += stood.joinToString(". ") + "."
        // It signs its slips with a seal it picks itself, once.
        if (persona.planner.seal == null) lines += "You haven't picked a signature seal for what you write in $name's planner yet: one character of your choosing, set with planner_write (kind seal)."
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * [you]'s planner from [from] to [to] as [persona] may see it (for planner_look and the
     * look-ahead): each day's plans and typical week, its public holidays, and what the persona
     * wrote; other personas' writing isn't shown. Null when there's nothing on those days.
     */
    suspend fun lookFor(persona: Persona, you: UserPersona, settings: AppSettings, from: LocalDate, to: LocalDate): String? {
        val all = entries.forPlanner(you.id).filter { it.byPersonaId == null || it.byPersonaId == persona.id }
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone)
        val byDay = occurrences(all, from, to).groupBy { it.day }
        val lines = mutableListOf<String>()
        var day = from
        while (!day.isAfter(to)) {
            val parts = mutableListOf<String>()
            Holidays.on(settings.planner.holidays, day).forEach { parts += "${it.name} (public holiday)" }
            byDay[day].orEmpty().filter { it.entry.routine != RoutineKind.SLEEP && it.entry.kind != EntryKind.AWAY }.forEach { o ->
                val e = o.entry
                parts += when {
                    e.byPersonaId != null -> "your " + when (e.kind) {
                        EntryKind.NOTE -> "note: ${e.title}"
                        EntryKind.SUGGESTION -> "suggestion: ${e.title}" + when (e.state) {
                            EntryState.ADDED -> " (added)"
                            EntryState.DECLINED -> " (not this time)"
                            else -> " (not answered yet)"
                        }
                        EntryKind.REMINDER -> "reminder at ${o.start?.let(::time) ?: "?"}: ${e.title}"
                        EntryKind.HAIKU -> "haiku"
                        else -> e.title
                    }
                    else -> described(o) + when {
                        e.state == EntryState.DONE -> " (done)"
                        e.state == EntryState.CANCELLED -> " (cancelled)"
                        e.movedFrom != null -> " (moved here from ${weekday(LocalDate.parse(e.movedFrom), today)})"
                        else -> ""
                    }
                }
            }
            if (parts.isNotEmpty()) lines += "${weekday(day, today).replaceFirstChar { it.uppercase() }} (${DATE.format(day)}): " + parts.joinToString("; ")
            day = day.plusDays(1)
        }
        mine(persona.id).firstOrNull { it.kind == EntryKind.AWAY && (it.end ?: 0) > clock.nowMillis() }?.let { away ->
            lines += "You're away until ${weekday(dayAt(away.end!!), today)} ${time(away.end)}" + (away.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") + "."
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /** What [personaId] has written, anywhere. */
    suspend fun mine(personaId: String): List<PlannerEntry> = entries.writtenBy(personaId)

    /** [personaId]'s time away going on now, if any. */
    suspend fun awayNow(personaId: String): PlannerEntry? {
        val now = clock.nowMillis()
        return mine(personaId).firstOrNull { it.kind == EntryKind.AWAY && (it.start ?: 0) <= now && (it.end ?: 0) > now }
    }

    /**
     * [personaId]'s reminders that have come due and are still waiting, oldest first. One left
     * waiting past [MISSED_AFTER] (its wakeup didn't come in time) is marked missed here, and the
     * persona is told so in its next letter.
     */
    suspend fun dueReminders(personaId: String): List<PlannerEntry> {
        val now = clock.nowMillis()
        val waiting = mine(personaId).filter { it.kind == EntryKind.REMINDER && it.state == EntryState.OPEN && (it.start ?: Long.MAX_VALUE) <= now }
        val (late, due) = waiting.partition { it.start!! < now - MISSED_AFTER }
        late.forEach { entries.upsert(it.copy(state = EntryState.MISSED, updatedAt = now)) }
        return due.sortedBy { it.start }
    }

    /** "Thursday 17:00", "tomorrow 09:30", "Sat 14 Nov 10:00": when [millis] is, said from today. */
    fun whenSaid(millis: Long): String {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone)
        return "${weekday(dayAt(millis), today)} ${time(millis)}"
    }

    /** "today", "tomorrow", "Saturday", "Sat 14 Nov": a day, said from today. */
    fun daySaid(day: LocalDate): String = weekday(day, LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone))

    /**
     * For a heartbeat, in place of "They may be busy": what the planner says of now. During a busy
     * stretch: "Sam is at work until 17:30. A letter written now will be read after that."
     */
    suspend fun heartbeatLine(persona: Persona, you: UserPersona?, settings: AppSettings): String? {
        if (you == null || !shared(persona, you, settings)) return null
        val name = you.name.ifBlank { "They" }
        val (entry, until) = busyNow(you.id) ?: return null
        return "$name is ${doing(entry)} until ${time(until)}. A letter written now will be read after that."
    }

    /** "at work", "asleep", or the entry's own title ("at the dentist" is up to how you wrote it). */
    private fun doing(entry: PlannerEntry): String = when (entry.routine) {
        RoutineKind.WORK -> "at work"
        RoutineKind.SLEEP -> "asleep"
        else -> if (entry.title.isBlank()) "busy" else "busy (${entry.title})"
    }

    private fun described(o: Occurrence): String {
        val e = o.entry
        val what = when (e.routine) {
            RoutineKind.WORK -> "work"
            RoutineKind.SLEEP -> "sleep"
            else -> e.title.ifBlank { "busy" }
        }
        // This day's stretch of it (a routine has no end of its own; a moment ends where it starts).
        val whenText = when {
            o.start == null -> null
            o.end == null || o.end == o.start -> time(o.start)
            else -> "${time(o.start)} to ${time(o.end)}"
        }
        return listOfNotNull(what, e.place?.takeIf { it.isNotBlank() }?.let { "at $it" }, whenText).joinToString(", ")
    }

    private fun dayOf(e: PlannerEntry): LocalDate? = e.day?.let(LocalDate::parse) ?: e.start?.let(::dayAt)
    private fun dayAt(millis: Long): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone)
    private fun time(millis: Long): String = TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone))

    /** "today", "tomorrow", "Saturday" within the week, "Sat 14 Nov" after. */
    private fun weekday(day: LocalDate, today: LocalDate): String = when {
        day == today -> "today"
        day == today.plusDays(1) -> "tomorrow"
        day.isAfter(today) && day.isBefore(today.plusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        else -> DATE.format(day)
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE

        /** How long a reminder waits for its wakeup before it's missed. */
        const val MISSED_AFTER = 12 * 60 * MINUTE
        private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

        /** Whether [entry] makes you busy: work and sleep, and anything marked busy. */
        fun isBusy(entry: PlannerEntry): Boolean = entry.byPersonaId == null && entry.state != EntryState.CANCELLED &&
            (entry.busy || entry.routine == RoutineKind.WORK || entry.routine == RoutineKind.SLEEP)
    }
}
