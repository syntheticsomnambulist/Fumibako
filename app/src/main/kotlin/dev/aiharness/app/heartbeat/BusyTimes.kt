package dev.aiharness.app.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aiharness.app.HarnessApp
import dev.aiharness.core.Harness
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.DuringBusy
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** A persona's letters whose notification waits for a busy stretch to end. */
@Serializable
data class HeldLetter(val personaId: String, val personaName: String, val conversationId: String, val letters: Int, val doing: String)

/**
 * Busy times (docs/design/calendar.md, "Notifications"): while your planner says you're busy, a
 * persona's letter lands in the letters and its notification waits; when the busy stretch ends,
 * one notification per persona says it wrote ("Aria wrote while you were at work").
 */
object BusyTimes {
    private const val PREFS = "busy_times"
    private const val KEY_HELD = "held"
    private const val WORK = "held-letters"

    /** A heartbeat's letter: notified now, held until you're free, or not notified, as Settings, Planner says. */
    suspend fun deliver(context: Context, harness: Harness, notifier: Notifier, outcome: HeartbeatOutcome.Sent) {
        val settings = harness.settings.current()
        val you = settings.activeUserPersonaId
        val busy = you?.let { runCatching { harness.planner.busyNow(it) }.getOrNull() }
        when {
            busy == null || settings.planner.duringBusy == DuringBusy.NOTIFY -> notifier.notifyMessage(outcome)
            settings.planner.duringBusy == DuringBusy.SILENT -> Unit
            else -> hold(context, outcome, doingWords(harness, busy.first), busy.second)
        }
    }

    /** "at work", "asleep", or "busy". */
    private fun doingWords(harness: Harness, entry: PlannerEntry): String = when (entry.routine) {
        RoutineKind.WORK, RoutineKind.SLEEP -> harness.planner.doing(entry)
        else -> "busy"
    }

    private fun hold(context: Context, outcome: HeartbeatOutcome.Sent, doing: String, until: Long) {
        val held = read(context).toMutableList()
        val i = held.indexOfFirst { it.personaId == outcome.persona.id }
        if (i >= 0) held[i] = held[i].copy(letters = held[i].letters + 1, conversationId = outcome.conversation.id, doing = doing)
        else held += HeldLetter(outcome.persona.id, outcome.persona.name, outcome.conversation.id, 1, doing)
        write(context, held)
        schedule(context, until)
    }

    private fun schedule(context: Context, at: Long) {
        val request = OneTimeWorkRequestBuilder<HeldLettersWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0L) + 30_000L, TimeUnit.MILLISECONDS)
            .build()
        // Best effort, like the heartbeat; a held letter is still in the letters either way.
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request) }
            .onFailure { android.util.Log.w("Harness", "Couldn't schedule held notifications", it) }
    }

    /** At the end of a busy stretch: one notification per persona that wrote, or held again if you're still busy. */
    suspend fun release(context: Context, harness: Harness, notifier: Notifier) {
        val held = read(context).takeIf { it.isNotEmpty() } ?: return
        val you = harness.settings.current().activeUserPersonaId
        val stillBusy = you?.let { runCatching { harness.planner.busyNow(it) }.getOrNull() }
        if (stillBusy != null && harness.settings.current().planner.duringBusy == DuringBusy.HOLD) {
            schedule(context, stillBusy.second)
            return
        }
        held.forEach { notifier.notifyHeld(it) }
        write(context, emptyList())
    }

    private fun read(context: Context): List<HeldLetter> = runCatching {
        HarnessJson.decodeFromString<List<HeldLetter>>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HELD, null) ?: "[]")
    }.getOrDefault(emptyList())

    private fun write(context: Context, held: List<HeldLetter>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HELD, HarnessJson.encodeToString(held)).apply()
    }

    /** "Busy for now" from a persona's notification: a busy plan from now for [length], in your default persona's planner. */
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

    const val HOUR = "hour"
    const val TONIGHT = "tonight"
    const val TOMORROW = "tomorrow"
    const val EXTRA_LENGTH = "length"
    const val EXTRA_NOTIFICATION = "notification"
    const val ACTION_BUSY = "dev.aiharness.app.BUSY_FOR_NOW"
}

/** Posts the held notifications when a busy stretch ends. */
class HeldLettersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HarnessApp).container
        BusyTimes.release(applicationContext, container.harness, container.notifier)
        return Result.success()
    }
}

/** A persona notification's "Busy for now" action: adds the busy plan, and clears that notification's actions. */
class BusyForNowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BusyTimes.ACTION_BUSY) return
        val container = (context.applicationContext as HarnessApp).container
        val length = intent.getStringExtra(BusyTimes.EXTRA_LENGTH) ?: BusyTimes.HOUR
        val pending = goAsync()
        container.appScope.launch {
            try {
                BusyTimes.busyForNow(container.harness, length)
                container.notifier.markedBusy(intent.getIntExtra(BusyTimes.EXTRA_NOTIFICATION, 0), length)
            } finally {
                pending.finish()
            }
        }
    }
}
