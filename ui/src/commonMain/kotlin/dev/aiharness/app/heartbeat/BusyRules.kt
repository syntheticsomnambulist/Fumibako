package dev.aiharness.app.heartbeat

import dev.aiharness.core.Harness
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.DuringBusy
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.util.newId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A persona's letters whose notification waits for a busy stretch to end. */
@Serializable
data class HeldLetter(val personaId: String, val personaName: String, val conversationId: String, val letters: Int, val doing: String)

/** What happens to a heartbeat letter's notification (Settings, Planner, During busy times). */
sealed interface Delivery {
    /** Notified now. */
    data object Notify : Delivery

    /** Not notified (the letter is in the letters all the same). */
    data object Silent : Delivery

    /** Held until [until], the end of the busy stretch you're [doing] ("at work"). */
    data class Hold(val doing: String, val until: Long) : Delivery
}

/**
 * Busy times (docs/design/calendar.md, "Notifications"), the same on the phone and the computer:
 * while your planner says you're busy, a persona's letter lands in the letters and its notification
 * waits; when the busy stretch ends, one notification per persona says it wrote ("Aria wrote while
 * you were at work"). Each platform keeps the held letters and wakes itself to release them.
 */
object BusyRules {
    const val HOUR = "hour"
    const val TONIGHT = "tonight"
    const val TOMORROW = "tomorrow"

    /** A heartbeat's letter: notified now, held until you're free, or not notified, as Settings, Planner says. */
    suspend fun delivery(harness: Harness): Delivery {
        val settings = harness.settings.current()
        val you = settings.activeUserPersonaId
        val busy = you?.let { runCatching { harness.planner.busyNow(it) }.getOrNull() }
        return when {
            busy == null || settings.planner.duringBusy == DuringBusy.NOTIFY -> Delivery.Notify
            settings.planner.duringBusy == DuringBusy.SILENT -> Delivery.Silent
            else -> Delivery.Hold(doingWords(harness, busy.first), busy.second)
        }
    }

    /** "at work", "asleep", or "busy". */
    private fun doingWords(harness: Harness, entry: PlannerEntry): String = when (entry.routine) {
        RoutineKind.WORK, RoutineKind.SLEEP -> harness.planner.doing(entry)
        else -> "busy"
    }

    /** [held] with [outcome]'s letter added: one entry per persona, counting its letters. */
    fun hold(held: List<HeldLetter>, outcome: HeartbeatOutcome.Sent, doing: String): List<HeldLetter> {
        val list = held.toMutableList()
        val i = list.indexOfFirst { it.personaId == outcome.persona.id }
        if (i >= 0) list[i] = list[i].copy(letters = list[i].letters + 1, conversationId = outcome.conversation.id, doing = doing)
        else list += HeldLetter(outcome.persona.id, outcome.persona.name, outcome.conversation.id, 1, doing)
        return list
    }

    /** When held letters should wait for (you're still busy and still holding), or null to release them now. */
    suspend fun stillHoldingUntil(harness: Harness): Long? {
        val settings = harness.settings.current()
        val you = settings.activeUserPersonaId
        val stillBusy = you?.let { runCatching { harness.planner.busyNow(it) }.getOrNull() }
        return if (stillBusy != null && settings.planner.duringBusy == DuringBusy.HOLD) stillBusy.second else null
    }

    /** The notification for a persona's held letters: "Aria wrote while you were at work". */
    fun heldText(held: HeldLetter): String =
        "${held.personaName} wrote ${if (held.letters == 1) "" else "${held.letters} letters "}while you were ${held.doing}"

    /** "Busy for now": a busy plan from now for [length], in your default persona's planner. */
    suspend fun busyForNow(harness: Harness, length: String) {
        val settings = harness.settings.current()
        val you = settings.activeUserPersonaId ?: return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val end = when (length) {
            HOUR -> now + 3_600_000L
            // Tonight: 10 pm (or an hour from now, if that's later).
            TONIGHT -> maxOf(today.atTime(22, 0).atZone(zone).toInstant().toEpochMilli(), now + 3_600_000L)
            // Tomorrow: until you wake (your typical week's sleep), else 9 am.
            else -> {
                val wake = harness.plannerEntries.forPlanner(you).firstOrNull { it.routine == RoutineKind.SLEEP }?.weekly?.to?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.of(9, 0)
                today.plusDays(1).atTime(wake).atZone(zone).toInstant().toEpochMilli()
            }
        }
        harness.plannerEntries.upsert(PlannerEntry(newId(), you, null, EntryKind.PLAN, "Busy for now", start = now, end = end, busy = true, createdAt = now, updatedAt = now))
    }
}
