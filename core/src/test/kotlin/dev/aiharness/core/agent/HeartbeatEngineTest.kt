package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartbeatEngineTest {

    private suspend fun envWithChat(provider: ScriptedProvider): Pair<TestEnv, String> {
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Good luck with the exam!"))
        env.harness.agent.send(conversation.id, "I have an exam tomorrow").toList()
        env.clock.advanceMinutes(180)
        return env to conversation.id
    }

    @Test
    fun sendsAProactiveMessageAndPersistsTheHiddenPrompt() = runTest {
        val provider = ScriptedProvider()
        val (env, conversationId) = envWithChat(provider)
        provider.then(
            LlmTurnResult(
                text = "",
                toolCalls = listOf(LlmToolCall("c", "memory_save", """{"content":"Sam had an exam","kind":"episode"}""")),
                stopReason = StopReason.TOOL_USE,
            ),
        )
        provider.then(LlmTurnResult(text = "How did the exam go?"))

        val outcome = env.harness.heartbeat.run().single()

        assertTrue(outcome is HeartbeatOutcome.Sent, "got $outcome")
        assertEquals("How did the exam go?", outcome.message.text)
        val heartbeatRequest = provider.requests[1]
        val prompt = heartbeatRequest.messages.last() as LlmMessage.User
        assertTrue(prompt.parts.filterIsInstance<LlmPart.Text>().any { it.text.contains("<heartbeat>") })
        assertContains(prompt.parts.filterIsInstance<LlmPart.Text>().first().text, "3 hours")

        val messages = env.conversations.getMessages(conversationId)
        val note = messages.first { it.origin == MessageOrigin.HEARTBEAT && it.role == MessageRole.USER }
        assertTrue(note.hidden)
        assertEquals(MessageOrigin.HEARTBEAT, messages.last().origin)
        assertEquals("Sam had an exam", env.harness.memory.all(env.persona.id).single().content)

        // A second wakeup waits for the user to answer.
        env.clock.advanceMinutes(240)
        val again = env.harness.heartbeat.run().single()
        assertEquals("waiting for a reply", (again as HeartbeatOutcome.Skipped).reason)
    }

    @Test
    fun silentHeartbeatsLeaveNoTrace() = runTest {
        val provider = ScriptedProvider()
        val (env, conversationId) = envWithChat(provider)
        val before = env.conversations.getMessages(conversationId)
        provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        val outcome = env.harness.heartbeat.run().single()

        assertTrue(outcome is HeartbeatOutcome.Silent)
        assertEquals(before, env.conversations.getMessages(conversationId))
    }

    @Test
    fun respectsQuietHoursRecencyAndMode() = runTest {
        val provider = ScriptedProvider()
        val (env, _) = envWithChat(provider)

        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(quietHoursStart = 0, quietHoursEnd = 23))) }
        assertEquals("quiet hours", (env.harness.heartbeat.run().single() as HeartbeatOutcome.Skipped).reason)

        // Straight away again: the persona keeps its own pace, unless forced ("Run a heartbeat now").
        assertEquals("not time yet", (env.harness.heartbeat.run().single() as HeartbeatOutcome.Skipped).reason)
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(quietHoursStart = null, minSilenceMinutes = 600))) }
        assertEquals("conversation is recent", (env.harness.heartbeat.run(force = true).single() as HeartbeatOutcome.Skipped).reason)

        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(mode = HeartbeatMode.TIME_AWARENESS)) }
        assertTrue(env.harness.heartbeat.run().isEmpty())
    }

    @Test
    fun foldsLongConversationsIntoEpisodeMemories() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        env.settingsRepo.update { it.copy(memory = it.memory.copy(episodeEveryMessages = 4)) }
        val conversation = env.harness.createConversation(env.persona.id)
        repeat(2) { i ->
            provider.then(LlmTurnResult(text = "reply $i"))
            env.harness.agent.send(conversation.id, "message $i").toList()
        }
        env.clock.advanceMinutes(300)
        provider.then(LlmTurnResult(text = "Recap: small talk."))
        provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        env.harness.heartbeat.run()

        val episode = env.harness.memory.all(env.persona.id).single()
        assertContains(episode.content, "Recap: small talk.")
        val lastId = env.conversations.getMessages(conversation.id).last().id
        assertEquals(lastId, env.conversations.get(conversation.id)!!.lastEpisodeMessageId)
    }
}
