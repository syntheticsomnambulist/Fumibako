package dev.aiharness.app.heartbeat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aiharness.app.HarnessApp
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.MessageRole
import java.util.concurrent.TimeUnit

/**
 * Letters in the post: when a reply reaches you, its notification, as a heartbeat letter's (and
 * held the same way while your planner says you're busy). Not when you've read it in the app as
 * it arrived.
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
        val h = container.harness
        val conversation = h.conversations.get(conversationId) ?: return Result.success()
        if ((conversation.lastReadAt ?: 0L) >= at) return Result.success()
        val persona = h.personas.get(conversation.personaId) ?: return Result.success()
        val reply = h.conversations.getMessages(conversationId)
            .lastOrNull { it.deliverAt == at && it.role == MessageRole.ASSISTANT && !it.hidden && it.displayText.isNotBlank() }
            ?: return Result.success()
        BusyTimes.deliver(applicationContext, h, container.notifier, HeartbeatOutcome.Sent(conversation, persona, reply))
        return Result.success()
    }
}
