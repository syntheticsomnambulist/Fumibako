package dev.aiharness.core.agent

import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.planner.PlannerWriteTool
import dev.aiharness.core.seasons.Seasons
import dev.aiharness.core.theme.ThemeClock
import java.time.Instant
import java.time.LocalDate

/**
 * A reminder or promise the persona set itself has come due (its wakeup, or the next heartbeat):
 * it's put in front of the persona, and kept if it writes. Free: writing is what takes the slot.
 */
class RemindersActivity : HeartbeatActivity {
    override val name = "reminders"

    override suspend fun invite(beat: Beat): String? {
        val planner = beat.h.planner
        val due = planner.dueReminders(beat.persona.id).takeIf { it.isNotEmpty() } ?: return null
        return "It's time for " + (if (due.size == 1) "something" else "things") + " you set yourself: " +
            due.joinToString("; ") { "${it.title} (you set it ${planner.daySaid(LocalDate.ofInstant(Instant.ofEpochMilli(it.createdAt), beat.h.clock.zone))})" } +
            ". If it still feels right, writing to ${beat.userName} is how you keep it."
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        if (moment?.wroteLetter != true) return
        val h = beat.h
        h.planner.dueReminders(beat.persona.id).filter { (it.start ?: Long.MAX_VALUE) <= beat.now }.forEach {
            h.plannerEntries.upsert(it.copy(state = EntryState.KEPT, updatedAt = h.clock.nowMillis()))
        }
    }
}

/**
 * Now and then (once a day at most), the week ahead in the planner of the person the persona
 * writes to, when they share it: a chance to leave a note, suggest a plan or set a reminder. All
 * free (suggestions too, as agreed for v0.4).
 */
class LookAheadActivity : HeartbeatActivity {
    override val name = "look-ahead"

    override suspend fun invite(beat: Beat): String? {
        val h = beat.h
        val you = beat.setup.userPersona ?: return null
        if (!h.planner.shared(beat.persona, you, beat.setup.settings)) return null
        if ((beat.lastInvited(name) ?: 0L) > beat.now - 20 * HOUR) return null
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(beat.now), h.clock.zone)
        val week = h.planner.lookFor(beat.persona, you, beat.setup.settings, today, today.plusDays(6)) ?: return null
        return "A look at ${beat.userName}'s week ahead in their planner:\n$week\n" +
            "If something there calls for it, you could leave ${beat.userName} a note on a day, suggest a plan they might like, or set yourself a reminder, with planner_write. None of these count as things of your own."
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        moment ?: return
        val written = moment.succeeded("planner_write").count { it.str("kind")?.lowercase() in setOf("note", "suggestion", "reminder", "away") }
        if (written > 0) beat.h.logActivity(beat.persona.id, ActivityKind.WROTE_IN_PLANNER, beat.setup.conversation.id, written)
    }
}

/**
 * On the first day of a new microseason (or solar term), a persona may write a haiku on a slip in
 * the planner (docs/design/calendar.md, "The persona's side"). A making activity, so it takes a
 * slot; each persona can be switched off it.
 */
class SeasonHaikuActivity : HeartbeatActivity {
    override val name = PlannerWriteTool.HAIKU
    override val usesSlot = true

    override suspend fun invite(beat: Beat): String? {
        val h = beat.h
        val settings = beat.setup.settings
        val persona = beat.persona
        val you = beat.setup.userPersona ?: return null
        if (!persona.planner.haiku || !settings.planner.seasonBand || !h.planner.shared(persona, you, settings)) return null
        if ((beat.lastInvited(name) ?: 0L) > beat.now - 20 * HOUR) return null
        val zone = h.clock.zone
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(beat.now), zone)
        val season = Seasons.on(today, zone, ThemeClock.hemisphere(settings.theme, zone))
        if (season.microFirst != today) return null
        if (h.planner.mine(persona.id).any { it.kind == EntryKind.HAIKU && it.day == today.toString() }) return null
        val micro = season.micro
        return "Today a new season begins: ${micro.kanji}, ${micro.english.replaceFirstChar { it.lowercase() }} (the ${micro.part} of ${micro.term.kanji}, " +
            "${micro.term.english.lowercase()}). If it moves you, you could write a haiku for it on a slip in ${beat.userName}'s planner with planner_write (kind haiku): three short lines of your own."
    }

    override suspend fun after(beat: Beat, moment: Moment?) {
        moment ?: return
        if (moment.succeeded("planner_write").none { it.str("kind")?.lowercase() == "haiku" }) return
        val zone = beat.h.clock.zone
        val micro = Seasons.microOn(LocalDate.ofInstant(Instant.ofEpochMilli(beat.now), zone), zone, ThemeClock.hemisphere(beat.setup.settings.theme, zone))
        beat.h.logActivity(beat.persona.id, ActivityKind.WROTE_HAIKU, beat.setup.conversation.id, note = micro.kanji)
    }
}

private const val HOUR = 3_600_000L
