package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodAndActivityTest {

    @Test
    fun repliesCarryTheirMoodAndMarkTheConversationUnread() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val created = env.harness.createConversation(env.persona.id)
        assertFalse(created.hasUnread, "the greeting is not unread")

        env.clock.advanceMinutes(1)
        provider.then(LlmTurnResult(text = "Oh, a fern! [mood: delighted]"))
        env.harness.agent.send(created.id, "Look what I found").toList()

        val reply = env.conversations.getMessages(created.id).last()
        assertEquals("delighted", reply.mood)
        assertEquals("Oh, a fern!", reply.displayText)
        assertEquals("Oh, a fern! [mood: delighted]", reply.text, "the tag stays in stored history")
        var conversation = env.conversations.get(created.id)!!
        assertEquals("delighted", conversation.mood)
        assertTrue(conversation.hasUnread)

        env.clock.advanceMinutes(1)
        env.harness.markRead(created.id)
        assertFalse(env.conversations.get(created.id)!!.hasUnread)

        env.clock.advanceMinutes(1)
        provider.then(LlmTurnResult(text = "Ferns are older than trees, you know."))
        env.harness.agent.send(created.id, "Really?").toList()
        conversation = env.conversations.get(created.id)!!
        assertEquals("delighted", conversation.mood, "a reply without a tag keeps the last mood")

        assertContains(provider.requests[0].system, "[mood: curious]")
    }

    @Test
    fun moodInstructionCanBeTurnedOff() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider, AppSettings(moodTags = false)).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Hi."))
        env.harness.agent.send(conversation.id, "Hello").toList()
        assertFalse(provider.requests[0].system.contains("## Mood"))
    }

    @Test
    fun changingAPersonasLookKeepsTheContextEpoch() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "One."))
        env.harness.agent.send(conversation.id, "First").toList()
        val epoch = env.conversations.get(conversation.id)!!.context!!.epochId

        env.personas.upsert(env.persona.copy(ink = "kikyo", tapePattern = TapePattern.GINGHAM, updatedAt = env.clock.millis + 5))
        provider.then(LlmTurnResult(text = "Two."))
        env.harness.agent.send(conversation.id, "Second").toList()
        assertEquals(epoch, env.conversations.get(conversation.id)!!.context!!.epochId)
    }

    @Test
    fun heartbeatsLeaveAnActivityLog() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Good luck tomorrow!"))
        env.harness.agent.send(conversation.id, "Big exam tomorrow").toList()
        env.harness.markRead(conversation.id)
        val awaySince = env.clock.millis
        env.clock.advanceMinutes(180)

        // First wakeup: saves a memory, then stays quiet.
        provider.then(
            LlmTurnResult(
                text = "",
                toolCalls = listOf(LlmToolCall("c1", "memory_save", """{"content":"Sam has an exam"}""")),
                stopReason = StopReason.TOOL_USE,
            ),
        )
        provider.then(LlmTurnResult(text = "[SILENT]"))
        env.harness.heartbeat.run()
        assertFalse(env.conversations.get(conversation.id)!!.hasUnread)

        // Second wakeup: writes a letter.
        env.clock.advanceMinutes(60)
        provider.then(LlmTurnResult(text = "Thinking of you before the exam. [mood: hopeful]"))
        val outcome = env.harness.heartbeat.run().single()
        assertTrue(outcome is HeartbeatOutcome.Sent, "got $outcome")

        val log = env.activity.observeSince(awaySince).first()
        assertEquals(
            listOf(ActivityKind.SAVED_MEMORIES, ActivityKind.STAYED_QUIET, ActivityKind.WROTE_LETTER),
            log.map { it.kind },
        )
        assertEquals(1, log.first().count)
        val updated = env.conversations.get(conversation.id)!!
        assertTrue(updated.hasUnread)
        assertEquals("hopeful", updated.mood)
    }
}
