package dev.aiharness.core.planner

import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.storage.PlannerRepository
import dev.aiharness.core.tools.AgentTool
import dev.aiharness.core.tools.ToolContext
import dev.aiharness.core.tools.ToolOutcome
import dev.aiharness.core.tools.int
import dev.aiharness.core.tools.requireString
import dev.aiharness.core.tools.schema
import dev.aiharness.core.tools.string
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** The planner tools are offered to a persona that sees your planner; each checks the planner it's writing to is shared. */
private fun offered(persona: Persona) = persona.seesPlanner

/** Reads a day as a persona would write it: today, tomorrow, a weekday (the next one, today included), or YYYY-MM-DD. */
internal fun parseDay(text: String?, today: LocalDate): LocalDate? {
    val t = text?.trim()?.lowercase(Locale.US)?.removePrefix("on ")?.removePrefix("this ")?.removePrefix("next ") ?: return null
    if (t.isEmpty()) return null
    if (t == "today" || t == "tonight") return today
    if (t == "tomorrow") return today.plusDays(1)
    DayOfWeek.entries.firstOrNull { d -> d.getDisplayName(TextStyle.FULL, Locale.US).lowercase(Locale.US).let { it == t || it.take(3) == t } }?.let { d ->
        return today.plusDays(((d.value - today.dayOfWeek.value + 7) % 7).toLong())
    }
    return runCatching { LocalDate.parse(t) }.getOrNull()
}

/** Reads a time: "17:30", "9:00", "7pm", "7:30 pm", "noon". */
internal fun parseTime(text: String?): LocalTime? {
    val t = text?.trim()?.lowercase(Locale.US)?.replace(".", "") ?: return null
    if (t.isEmpty()) return null
    if (t == "noon") return LocalTime.NOON
    if (t == "midnight") return LocalTime.MIDNIGHT
    val m = Regex("""^(\d{1,2})(?::(\d{2}))?\s*(am|pm)?$""").matchEntire(t) ?: return null
    var hour = m.groupValues[1].toInt()
    val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
    when (m.groupValues[3]) {
        "am" -> if (hour == 12) hour = 0
        "pm" -> if (hour < 12) hour += 12
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

/** Looks at the planner of the person a persona writes to, for some days. */
class PlannerLookTool(private val planner: Planner, private val clock: Clock) : AgentTool {
    override val spec = ToolSpec(
        name = "planner_look",
        description = "Look at the planner of the person you write to, for some days: their plans and typical week, public holidays, and what you've written there. " +
            "Your letters already show what's on today and tomorrow; this is for looking further.",
        parameters = schema {
            string("from", "The first day: today, tomorrow, a weekday, or YYYY-MM-DD. Default today.")
            integer("days", "How many days, 1 to 31. Default 7.", min = 1, max = 31)
        },
    )

    override fun isAvailableFor(settings: AppSettings, persona: Persona) = offered(persona)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val you = context.userPersona
        if (you == null || !planner.shared(context.persona, you, context.settings)) return ToolOutcome("This planner isn't shared with you.", isError = true)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), clock.zone)
        val from = args.string("from")?.let { parseDay(it, today) ?: return ToolOutcome("I couldn't read '$it' as a day. Use today, tomorrow, a weekday, or YYYY-MM-DD.", isError = true) } ?: today
        val days = (args.int("days") ?: 7).coerceIn(1, 31)
        val name = you.name.ifBlank { "Their" }
        return ToolOutcome(planner.lookFor(context.persona, you, context.settings, from, from.plusDays(days - 1L))?.let { "$name's planner:\n$it" } ?: "Nothing in $name's planner on those days.")
    }
}

/**
 * Writes in the planner of the person a persona writes to, in its own ink: a note on a day, a plan
 * it suggests (they add it or say not this time), a reminder or promise to itself (which wakes it
 * then), its time away (heartbeats leave it be until then), a haiku, or its signature seal; and
 * erases what it wrote.
 */
class PlannerWriteTool(
    private val planner: Planner,
    private val entries: PlannerRepository,
    private val personas: PersonaRepository,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        name = "planner_write",
        description = "Write in the planner of the person you write to, in your own ink. Kinds: " +
            "note (a slip on a day, for them to find); suggestion (a plan you'd like them to consider; they add it or say not this time); " +
            "reminder (something you mean to do, which wakes you then: give a day and time, or after one of their plans); " +
            "away (your own time away, in your words: resting, busy with something, on a trip; heartbeats leave you be until it ends, though their letters still reach you); " +
            "haiku (three short lines of your own on a new season); seal (the one character you sign your slips with); erase (something you wrote).",
        parameters = schema {
            string("kind", "What to write.", required = true, enum = listOf("note", "suggestion", "reminder", "away", "haiku", "seal", "erase"))
            string(
                "text",
                "The words: the note; the plan you suggest; what the reminder is for; your time away in your own words; the haiku; your seal's one character; or, to erase, words from what you wrote.",
                required = true,
            )
            string("day", "today, tomorrow, a weekday, or YYYY-MM-DD: the note's or suggestion's day; the reminder's; the day your time away ends. Default today.")
            string("time", "A time (17:30 or 5:30 pm): the note's or suggestion's; when the reminder wakes you; when your time away ends.")
            string("after", "For a reminder: one of their plans (its title); you wake when it ends.")
        },
    )

    override fun isAvailableFor(settings: AppSettings, persona: Persona) = offered(persona)

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val kind = args.requireString("kind").lowercase(Locale.US)
        val text = args.requireString("text").trim()
        val persona = personas.get(context.persona.id) ?: context.persona
        if (kind == "seal") return seal(persona, text)
        val you = context.userPersona
        if (you == null || !planner.shared(persona, you, context.settings)) return ToolOutcome("This planner isn't shared with you.", isError = true)
        val name = you.name.ifBlank { "They" }
        val zone = clock.zone
        val now = clock.nowMillis()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val day = args.string("day")?.let { parseDay(it, today) ?: return ToolOutcome("I couldn't read '$it' as a day. Use today, tomorrow, a weekday, or YYYY-MM-DD.", isError = true) }
        val time = args.string("time")?.let { parseTime(it) ?: return ToolOutcome("I couldn't read '$it' as a time. Use 17:30 or 5:30 pm.", isError = true) }
        fun at(d: LocalDate, t: LocalTime) = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        fun entry(k: EntryKind, build: (PlannerEntry) -> PlannerEntry) = build(
            PlannerEntry(newId(), you.id, persona.id, k, text.take(MAX_TEXT), conversationId = context.conversation.id, messageId = context.messageId, createdAt = now, updatedAt = now),
        )
        return when (kind) {
            "note", "suggestion" -> {
                val d = day ?: today
                if (d.isBefore(today)) return ToolOutcome("That day has gone; write on today or later.", isError = true)
                if (d.isAfter(today.plusDays(FAR))) return ToolOutcome("That's too far ahead; keep it within two months.", isError = true)
                if (time != null && at(d, time) < now) return ToolOutcome("That time has passed.", isError = true)
                val k = if (kind == "note") EntryKind.NOTE else EntryKind.SUGGESTION
                fun dayOf(e: PlannerEntry) = e.day?.let(LocalDate::parse) ?: e.start?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it), zone) }
                val sameDay = planner.mine(persona.id).count { it.plannerId == you.id && it.kind == k && it.state == EntryState.OPEN && dayOf(it) == d }
                if (sameDay >= PER_DAY) return ToolOutcome("You've already written $PER_DAY of those on that day; erase one first.", isError = true)
                entries.upsert(entry(k) { if (time != null) it.copy(start = at(d, time)) else it.copy(day = d.toString()) })
                val whenText = planner.daySaid(d) + (time?.let { " at ${it}" } ?: "")
                ToolOutcome(if (k == EntryKind.NOTE) "Your note is in $name's planner for $whenText." else "Suggested for $whenText. $name will add it or say not this time; you'll hear which.")
            }
            "haiku" -> {
                // A making activity: in a heartbeat it takes one of the slots.
                if (context.slots?.take(HAIKU) == false) return ToolOutcome("You've no more things of your own this time; the haiku will have to wait.", isError = true)
                entries.upsert(entry(EntryKind.HAIKU) { it.copy(day = today.toString(), title = text.take(MAX_HAIKU)) })
                ToolOutcome("Your haiku is on a slip in $name's planner, today.")
            }
            "reminder" -> {
                val after = args.string("after")
                val wake = if (after != null) {
                    val plan = planner.days(you.id, today, today.plusDays(14))
                        .filter { it.entry.byPersonaId == null && it.entry.state != EntryState.CANCELLED && it.entry.title.contains(after, ignoreCase = true) }
                        .firstOrNull { (it.end ?: it.start ?: 0) > now } ?: return ToolOutcome("I couldn't find a plan of $name's called '$after' in the next two weeks.", isError = true)
                    plan.end ?: plan.start ?: return ToolOutcome("'${plan.entry.title}' has no time to wake after.", isError = true)
                } else {
                    val t = time ?: return ToolOutcome("A reminder needs a time (and a day, if not today), or after one of $name's plans.", isError = true)
                    at(day ?: today, t).let { if (day == null && it <= now) at(today.plusDays(1), t) else it }
                }
                if (wake <= now) return ToolOutcome("That time has passed.", isError = true)
                if (wake > now + FAR * DAY) return ToolOutcome("That's too far ahead; keep it within two months.", isError = true)
                entries.upsert(entry(EntryKind.REMINDER) { it.copy(start = wake) })
                ToolOutcome("You'll wake ${planner.whenSaid(wake)} for this: $text. It shows in $name's planner.")
            }
            "away" -> {
                val d = day ?: return ToolOutcome("Say until when: a day (and a time, if not the morning).", isError = true)
                val until = at(d, time ?: LocalTime.of(8, 0))
                if (until <= now) return ToolOutcome("That time has passed.", isError = true)
                if (until > now + AWAY_MAX * DAY) return ToolOutcome("Two weeks away at most.", isError = true)
                // One time away at once: a new one replaces what's left of the last.
                planner.mine(persona.id).filter { it.kind == EntryKind.AWAY && (it.end ?: 0) > now }.forEach { entries.upsert(it.copy(end = now, updatedAt = now)) }
                entries.upsert(entry(EntryKind.AWAY) { it.copy(start = now, end = until) })
                ToolOutcome("You're away until ${planner.whenSaid(until)}: $text. Heartbeats leave you be till then; $name's letters still reach you.")
            }
            "erase" -> {
                val words = text.lowercase(Locale.US)
                val yours = planner.mine(persona.id).filter { it.plannerId == you.id }
                val match = if (words == "away") yours.filter { it.kind == EntryKind.AWAY && (it.end ?: 0) > now }
                else yours.filter { it.title.lowercase(Locale.US).contains(words) && (it.kind != EntryKind.AWAY || (it.end ?: 0) > now) }
                when {
                    match.isEmpty() -> ToolOutcome("Nothing you wrote there matches '$text'.", isError = true)
                    match.size > 1 && match.map { it.title }.distinct().size > 1 ->
                        ToolOutcome("Several match: " + match.joinToString("; ") { "\"${it.title}\"" } + ". Use more of the words.", isError = true)
                    else -> {
                        val gone = match.first()
                        // Time away ends now; anything else is gone.
                        if (gone.kind == EntryKind.AWAY) entries.upsert(gone.copy(end = now, updatedAt = now)) else entries.delete(gone.id)
                        ToolOutcome(if (gone.kind == EntryKind.AWAY) "You're back; your time away has ended." else "Erased: ${gone.title}.")
                    }
                }
            }
            else -> ToolOutcome("Unknown kind '$kind'.", isError = true)
        }
    }

    private suspend fun seal(persona: Persona, text: String): ToolOutcome {
        val seal = text.trim()
        // One character (a kanji, a letter, an emoji as one glyph).
        if (seal.isEmpty() || seal.codePointCount(0, seal.length) > 2 || seal.any { it.isWhitespace() }) return ToolOutcome("A seal is one character.", isError = true)
        personas.upsert(persona.copy(planner = persona.planner.copy(seal = seal), updatedAt = clock.nowMillis()))
        return ToolOutcome("Your seal is $seal; it signs your slips.")
    }

    companion object {
        /** The heartbeat activity a haiku belongs to (its slot). */
        const val HAIKU = "haiku"
        private const val MAX_TEXT = 300
        private const val MAX_HAIKU = 200
        private const val PER_DAY = 3
        private const val FAR = 62L
        private const val AWAY_MAX = 14L
        private const val DAY = 86_400_000L
    }
}
