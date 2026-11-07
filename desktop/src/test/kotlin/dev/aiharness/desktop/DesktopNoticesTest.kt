package dev.aiharness.desktop

import dev.aiharness.app.AppContainer
import dev.aiharness.app.heartbeat.BusyRules
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.DuringBusy
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

/** A heartbeat letter's notification on a computer, by the phone's busy rules. */
class DesktopNoticesTest {
    private val platform = DesktopPlatform(Files.createTempDirectory("fumibako-notices").toFile(), keychain = { null })
    private val container = AppContainer(platform)
    private val h = container.harness
    private val shown = mutableListOf<Notice>()
    private val notices = DesktopNotices(h, platform.appState) { shown += it }

    private suspend fun you(): String {
        val user = UserPersona(id = "u1", name = "Sam", createdAt = 1, updatedAt = 1)
        h.userPersonas.upsert(user)
        h.settings.update { it.copy(activeUserPersonaId = user.id) }
        return user.id
    }

    private fun letter(): HeartbeatOutcome.Sent {
        val aria = Persona(id = "p1", name = "Aria", createdAt = 1, updatedAt = 1)
        val conversation = Conversation(id = "c1", personaId = aria.id, title = "Aria", createdAt = 1, updatedAt = 1)
        val message = ChatMessage("m1", "c1", MessageRole.ASSISTANT, listOf(ContentPart.Text("I saw the first frost today.")), createdAt = 2)
        return HeartbeatOutcome.Sent(conversation, aria, message)
    }

    @Test
    fun aLetterIsNotifiedWhenYoureFree() = runBlocking {
        you()
        notices.deliver(listOf(letter()))
        assertEquals(listOf(Notice("Aria", "I saw the first frost today.", "c1")), shown)
    }

    @Test
    fun whileYoureBusyItWaitsThenOneNoticeSaysWhoWrote() = runBlocking {
        val sam = you()
        BusyRules.busyForNow(h, BusyRules.HOUR)
        notices.deliver(listOf(letter(), letter()))
        assertEquals(emptyList(), shown, "held while busy")

        // The busy stretch ends.
        val busy = h.plannerEntries.forPlanner(sam).single { it.title == "Busy for now" }
        h.plannerEntries.upsert(busy.copy(end = System.currentTimeMillis() - 1_000))
        notices.release()
        assertEquals(listOf(Notice("Aria", "Aria wrote 2 letters while you were busy", "c1")), shown)
        notices.release()
        assertEquals(1, shown.size, "told once")
    }

    @Test
    fun silentMeansNoNotice() = runBlocking {
        you()
        BusyRules.busyForNow(h, BusyRules.HOUR)
        h.settings.update { it.copy(planner = it.planner.copy(duringBusy = DuringBusy.SILENT)) }
        notices.deliver(listOf(letter()))
        notices.release()
        assertEquals(emptyList(), shown)
    }
}
