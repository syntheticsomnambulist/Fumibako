package dev.aiharness.app.heartbeat

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.aiharness.app.HarnessApp
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Letters in the post: a reply's notification comes when it arrives, unless it was read as it came. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LetterArrivalTest {
    private val app = ApplicationProvider.getApplicationContext<HarnessApp>()
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    /** A conversation with a reply that arrived a moment ago; its arrival time. */
    private fun arrived(): Pair<String, Long> = runBlocking {
        val h = app.container.harness
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val c = h.createConversation(h.personas.getAll().first().id)
        val at = System.currentTimeMillis() - 1_000
        h.conversations.appendMessage(ChatMessage("r1", c.id, MessageRole.ASSISTANT, listOf(ContentPart.Text("Here it is, at last.")), createdAt = at - 120_000, deliverAt = at))
        h.updateConversation(c.id) { it.copy(lastAgentMessageAt = at, lastReadAt = at - 300_000) }
        c.id to at
    }

    private fun runFor(conversationId: String, at: Long): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<LetterArrivalWorker>(app)
            .setInputData(Data.Builder().putString(LetterArrival.KEY_CONVERSATION, conversationId).putLong(LetterArrival.KEY_AT, at).build())
            .build().doWork()
    }

    @Test
    fun anArrivedReplySaysSo() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val (id, at) = arrived()
        runFor(id, at)
        assertEquals("Here it is, at last.", notifications.single().extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun oneReadAsItCameDoesnt() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val (id, at) = arrived()
        runBlocking { app.container.harness.markRead(id) }
        runFor(id, at)
        assertTrue(notifications.isEmpty())
    }
}
