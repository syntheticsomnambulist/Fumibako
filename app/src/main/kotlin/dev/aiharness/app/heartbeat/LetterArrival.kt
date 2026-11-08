package dev.aiharness.app.heartbeat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aiharness.app.HarnessApp
import java.util.concurrent.TimeUnit

/**
 * Letters in the post: when a reply reaches you, its notification ([Arrivals], shared with the
 * computer), at the time a job wakes the app for it.
 */
object LetterArrival {
    const val KEY_CONVERSATION = "conversation"
    const val KEY_AT = "at"

    fun schedule(context: Context, conversationId: String, at: Long) {
        val request = OneTimeWorkRequestBuilder<LetterArrivalWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0L) + 5_000L, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KEY_CONVERSATION, conversationId).putLong(KEY_AT, at).build())
            .build()
        // One job per letter, so a second reply on its way doesn't replace the first one's.
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork("arrival-$conversationId-$at", ExistingWorkPolicy.KEEP, request) }
            .onFailure { android.util.Log.w("Harness", "Couldn't schedule a letter's arrival", it) }
    }
}

class LetterArrivalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(LetterArrival.KEY_CONVERSATION) ?: return Result.success()
        val at = inputData.getLong(LetterArrival.KEY_AT, 0L)
        val container = (applicationContext as HarnessApp).container
        val letter = Arrivals.letter(container.harness, conversationId, at) ?: return Result.success()
        BusyTimes.deliver(applicationContext, container.harness, container.notifier, letter)
        return Result.success()
    }
}
