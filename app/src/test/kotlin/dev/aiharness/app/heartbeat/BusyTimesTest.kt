package dev.aiharness.app.heartbeat

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.HarnessApp
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.PlannerEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v0.4 step 6: a letter during a busy stretch waits, then one notification says who wrote; "Busy for now" adds a busy plan. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BusyTimesTest {
    private val app = ApplicationProvider.getApplicationContext<HarnessApp>()
    private val container get() = app.container
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun sent(): HeartbeatOutcome.Sent = runBlocking {
        val h = container.harness
        repeat(50) { if (h.personas.getAll().isEmpty() || h.settings.current().activeUserPersonaId == null) Thread.sleep(100) }
        val persona = h.personas.getAll().first()
        val now = System.currentTimeMillis()
        val conversation = Conversation("c1", persona.id, title = "Letters", createdAt = now, updatedAt = now)
        HeartbeatOutcome.Sent(conversation, persona, ChatMessage("m1", "c1", MessageRole.ASSISTANT, listOf(ContentPart.Text("Thinking of you.")), now, MessageOrigin.HEARTBEAT))
    }

    @Test
    fun aLetterWhileYoureBusyWaitsThenSaysWhoWrote() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val outcome = sent()
        val h = container.harness
        val you = h.settings.current().activeUserPersonaId!!
        val now = System.currentTimeMillis()
        h.plannerEntries.upsert(PlannerEntry("b1", you, null, EntryKind.PLAN, "Exam", start = now - 60_000, end = now + 3_600_000, busy = true, createdAt = now, updatedAt = now))

        BusyTimes.deliver(app, h, container.notifier, outcome)
        assertTrue(notifications.isEmpty(), "held while busy")

        // Still busy when the job comes: held on.
        BusyTimes.release(app, h, container.notifier)
        assertTrue(notifications.isEmpty())

        // Free: one notification says who wrote.
        h.plannerEntries.delete("b1")
        BusyTimes.release(app, h, container.notifier)
        val posted = notifications.single()
        assertEquals("${outcome.persona.name} wrote while you were busy", posted.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals("[]", app.getSharedPreferences("busy_times", Context.MODE_PRIVATE).getString("held", ""))
    }

    @Test
    fun notBusyItNotifiesWithBusyForNow() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        BusyTimes.deliver(app, container.harness, container.notifier, sent())
        val posted = notifications.single()
        assertEquals(listOf("Busy for an hour", "Till tonight", "Till tomorrow"), posted.actions.map { it.title.toString() })

        BusyTimes.busyForNow(container.harness, BusyTimes.HOUR)
        val you = container.harness.settings.current().activeUserPersonaId!!
        val busy = container.harness.plannerEntries.forPlanner(you).single { it.title == "Busy for now" }
        assertTrue(busy.busy && (busy.end!! - busy.start!!) in 3_590_000L..3_610_000L)
        assertTrue(container.harness.planner.busyNow(you) != null)
    }
}
