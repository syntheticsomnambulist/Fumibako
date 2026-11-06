package dev.aiharness.app.heartbeat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aiharness.app.HarnessApp
import dev.aiharness.app.MainActivity
import dev.aiharness.app.R
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatSettings
import java.util.concurrent.TimeUnit

private const val KEY_FORCE = "force"
private const val KEY_PERSONA = "persona"

/** WorkManager entry point for the persona heartbeat (reflection, memory upkeep, proactive messages). */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HarnessApp).container
        val outcomes = container.harness.heartbeat.run(
            force = inputData.getBoolean(KEY_FORCE, false),
            onlyPersonaId = inputData.getString(KEY_PERSONA),
        )
        outcomes.filterIsInstance<HeartbeatOutcome.Sent>().forEach { container.notifier.notifyMessage(it) }
        return Result.success()
    }
}

object HeartbeatScheduler {
    private const val PERIODIC = "persona-heartbeat"
    private const val ONE_SHOT = "persona-heartbeat-now"

    /**
     * Schedules the background job at [intervalMinutes]: the shortest interval any persona needs
     * (each persona then keeps its own pace inside [dev.aiharness.core.agent.HeartbeatEngine]).
     */
    fun apply(context: Context, settings: HeartbeatSettings, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (settings.mode != HeartbeatMode.PROACTIVE) {
            workManager.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints(settings))
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs a heartbeat right away for every persona, or just [personaId] (for trying a persona out). */
    fun runNow(context: Context, settings: HeartbeatSettings, personaId: String? = null) {
        val data = androidx.work.Data.Builder().putBoolean(KEY_FORCE, true).apply { personaId?.let { putString(KEY_PERSONA, it) } }.build()
        val request = OneTimeWorkRequestBuilder<HeartbeatWorker>().setConstraints(constraints(settings)).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }

    /** A persona's reminder: when it wakes it. */
    data class Wake(val reminderId: String, val personaId: String, val at: Long)

    /**
     * One wakeup for each reminder still to come, at its time (a little after, so it's due). A
     * wakeup runs that persona's heartbeat, which its due reminder lets through whatever its pace.
     */
    fun wakeFor(context: Context, settings: HeartbeatSettings, wakes: Set<Wake>) {
        if (settings.mode != HeartbeatMode.PROACTIVE) return
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        wakes.filter { it.at > now }.forEach { wake ->
            val request = OneTimeWorkRequestBuilder<HeartbeatWorker>()
                .setInitialDelay(wake.at - now + 30_000L, TimeUnit.MILLISECONDS)
                .setConstraints(constraints(settings))
                .setInputData(androidx.work.Data.Builder().putString(KEY_PERSONA, wake.personaId).build())
                .addTag(REMINDER_TAG)
                .build()
            workManager.enqueueUniqueWork("reminder-${wake.reminderId}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    private const val REMINDER_TAG = "persona-reminder"

    private fun constraints(settings: HeartbeatSettings) = Constraints.Builder()
        .setRequiredNetworkType(if (settings.requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
}

class Notifier(private val context: Context) {
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL, "Messages from personas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Messages your personas send on their own"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyMessage(outcome: HeartbeatOutcome.Sent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, outcome.conversation.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            outcome.conversation.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = outcome.message.displayText
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(outcome.persona.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(outcome.conversation.id.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL = "persona_messages"
    }
}
