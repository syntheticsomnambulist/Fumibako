package dev.aiharness.core.transfer

import dev.aiharness.core.TestEnv
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A card's other greetings (v0.5): a new thread's first letter can be swapped until you write. */
class GreetingsTest {
    @Test
    fun aNewThreadsFirstLetterCanBeSwappedForAnotherUntilYouWrite() = runTest {
        val env = TestEnv()
        env.seed()
        env.personas.upsert(env.persona.copy(alternateGreetings = listOf("The ferry's late again, {{user}}.", "Storm tonight.")))
        val h = env.harness
        val thread = h.createConversation("p1")
        val first = env.conversations.getMessages(thread.id).single()
        assertEquals("Hi Sam!", first.text)
        val mira = env.personas.get("p1")!!
        assertEquals(0, h.greetingIndex(mira, first.text, "Sam"))

        assertTrue(h.swapGreeting(thread.id, 1))
        val swapped = env.conversations.getMessages(thread.id).single()
        assertEquals(first.id, swapped.id, "the same letter, written again")
        assertEquals("The ferry's late again, Sam.", swapped.text)
        assertEquals(1, h.greetingIndex(mira, swapped.text, "Sam"))
        assertEquals("The ferry's late again, Sam.", env.conversations.get(thread.id)?.preview)

        env.conversations.appendMessage(ChatMessage(id = "n1", conversationId = thread.id, role = MessageRole.USER, parts = listOf(ContentPart.Text("Hello!")), createdAt = env.clock.millis + 1))
        assertFalse(h.swapGreeting(thread.id, 2), "once you've written, the letter is kept")
        assertEquals("The ferry's late again, Sam.", env.conversations.getMessages(thread.id).first().text)
    }
}
