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
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Busy times on the phone (the rules are [BusyRules], shared with the desktop): held letters are
 * kept in the `busy_times` preferences, and a WorkManager job releases them when the stretch ends.
 */
object BusyTimes {
    private const val PREFS = "busy_times"
    private const val KEY_HELD = "held"
    private const val WORK = "held-letters"

    /** A heartbeat's letter: notified now, held until you're free, or not notified, as Settings, Planner says. */
    suspend fun deliver(context: Context, harness: Harness, notifier: Notifier, outcome: HeartbeatOutcome.Sent) {
        when (val delivery = BusyRules.delivery(harness)) {
            Delivery.Notify -> notifier.notifyMessage(outcome)
            Delivery.Silent -> Unit
            is Delivery.Hold -> hold(context, outcome, delivery.doing, delivery.until)
        }
    }

    private fun hold(context: Context, outcome: HeartbeatOutcome.Sent, doing: String, until: Long) {
        write(context, BusyRules.hold(read(context), outcome, doing))
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
        BusyRules.stillHoldingUntil(harness)?.let { until ->
            schedule(context, until)
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
    suspend fun busyForNow(harness: Harness, length: String) = BusyRules.busyForNow(harness, length)

    const val HOUR = BusyRules.HOUR
    const val TONIGHT = BusyRules.TONIGHT
    const val TOMORROW = BusyRules.TOMORROW
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
