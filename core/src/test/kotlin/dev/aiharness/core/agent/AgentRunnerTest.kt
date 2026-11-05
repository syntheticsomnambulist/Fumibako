package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.RefusalInfo
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentRunnerTest {

    private fun LlmRequest.userTexts() = messages.filterIsInstance<LlmMessage.User>()
        .flatMap { it.parts }.filterIsInstance<LlmPart.Text>().map { it.text }

    @Test
    fun runsToolLoopAndPersistsEveryStep() = runTest {
        val provider = ScriptedProvider()
            .then(
                LlmTurnResult(
                    text = "I'll remember that.",
                    toolCalls = listOf(LlmToolCall("c1", "memory_save", """{"content":"Sam loves green tea","weight":"worth_keeping"}""")),
                    stopReason = StopReason.TOOL_USE,
                    replay = "replay-1",
                ),
            )
            .then(LlmTurnResult(text = "Green tea it is!", replay = "replay-2"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)

        val events = env.harness.agent.send(conversation.id, "I love green tea").toList()

        val finished = events.last() as AgentEvent.Finished
        assertEquals("Green tea it is!", finished.finalText)
        assertTrue(events.any { it is AgentEvent.ToolFinished && it.name == "memory_save" && !it.outcome.isError })
        assertEquals("Sam loves green tea", env.harness.memory.all(env.persona.id).single().content)

        val messages = env.conversations.getMessages(conversation.id)
        assertEquals(
            listOf(MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL, MessageRole.ASSISTANT),
            messages.map { it.role },
        )
        assertEquals("Hi Sam!", messages.first().text, "greeting is posted with placeholders filled")

        // Round two of the tool loop replays round one's provider data (same epoch, same provider).
        val second = provider.requests[1]
        val replayed = second.messages.filterIsInstance<LlmMessage.Assistant>().map { it.replay }
        assertEquals(listOf(null, "replay-1"), replayed, "greeting has no replay; tool turn replays verbatim")
        assertTrue(second.messages.first() is LlmMessage.User, "a synthetic opener precedes the greeting")
        assertEquals(provider.requests[0].system, second.system)
    }

    @Test
    fun keepsTheEpochAcrossTurnsAndStartsANewOneWhenThePersonaChanges() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "one", replay = "r1"))
            .then(LlmTurnResult(text = "two", replay = "r2"))
            .then(LlmTurnResult(text = "three", replay = "r3"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)

        env.harness.agent.send(conversation.id, "first").toList()
        val epoch1 = env.conversations.get(conversation.id)!!.context!!.epochId
        env.clock.advanceMinutes(1)
        env.harness.agent.send(conversation.id, "second").toList()
        assertEquals(epoch1, env.conversations.get(conversation.id)!!.context!!.epochId)
        assertEquals(provider.requests[0].system, provider.requests[1].system, "system prompt frozen within an epoch")
        assertEquals("r1", provider.requests[1].messages.filterIsInstance<LlmMessage.Assistant>().last().replay)

        env.personas.upsert(env.persona.copy(personality = "Now very formal.", updatedAt = env.clock.millis + 1))
        env.clock.advanceMinutes(1)
        env.harness.agent.send(conversation.id, "third").toList()
        val epoch2 = env.conversations.get(conversation.id)!!.context!!.epochId
        assertNotEquals(epoch1, epoch2)
        assertContains(provider.requests[2].system, "Now very formal.")
        assertTrue(
            provider.requests[2].messages.filterIsInstance<LlmMessage.Assistant>().all { it.replay == null },
            "replay data from an older epoch is never sent",
        )
    }

    @Test
    fun turnContextCarriesTimeAndRecalledMemories() = runTest {
        val provider = ScriptedProvider().then(LlmTurnResult(text = "ok"))
        val env = TestEnv(provider).apply { seed() }
        env.harness.memory.save(env.persona.id, "Sam's dog is called Biscuit", level = dev.aiharness.core.model.MemoryLevel.MATTERS_DEEPLY)
        val conversation = env.harness.createConversation(env.persona.id)

        env.harness.agent.send(conversation.id, "Biscuit chewed my shoes again").toList()

        val context = provider.requests.single().userTexts().first { it.startsWith("<context>") }
        assertContains(context, "Current time:")
        assertContains(context, "Sam's dog is called Biscuit")
        // Stored with the message so later replays are identical.
        val stored = env.conversations.getMessages(conversation.id).first { it.role == MessageRole.USER }
        assertContains(stored.turnContext!!, "Biscuit")
    }

    @Test
    fun compactsLongHistoryIntoSummaryAndEpisodeMemory() = runTest {
        var summaries = 0
        val provider = ScriptedProvider()
        provider.fallback = { request ->
            if (request.system.startsWith("You write concise")) {
                summaries++
                LlmTurnResult(text = "SUMMARY: they chatted a lot.")
            } else {
                LlmTurnResult(text = "reply " + "words ".repeat(120))
            }
        }
        val env = TestEnv(provider).apply { seed() }
        env.profiles.upsert(env.profile.copy(contextBudgetTokens = 2_000, maxOutputTokens = 500))
        val conversation = env.harness.createConversation(env.persona.id)
        repeat(12) { i ->
            env.harness.agent.send(conversation.id, "message $i " + "chatter ".repeat(120)).toList()
            env.clock.advanceMinutes(2)
        }

        assertTrue(summaries > 0, "compaction should have summarized older turns")
        val state = env.conversations.get(conversation.id)!!.context!!
        assertContains(state.summary.orEmpty(), "they chatted a lot")
        assertContains(state.systemPrompt, "Earlier in this conversation")
        val last = provider.requests.last()
        assertContains(last.system, "SUMMARY: they chatted a lot.")
        assertTrue(last.userTexts().none { it.startsWith("message 0") }, "old turns left the window")
        assertTrue(TokenEstimator.estimate(last.system) + last.userTexts().sumOf { TokenEstimator.estimate(it) } < 2_000)
        val memories = env.harness.memory.all(env.persona.id)
        assertTrue(memories.isNotEmpty() && memories.all { it.kind == MemoryKind.EPISODE })
        val window = env.conversations.getMessages(conversation.id).first { it.id == state.windowStartMessageId }
        assertEquals(MessageRole.USER, window.role, "the window always starts at a user turn")
    }

    @Test
    fun refusalsAreRecordedButExcludedFromContext() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "", stopReason = StopReason.REFUSAL, refusal = RefusalInfo("cyber", null)))
            .then(LlmTurnResult(text = "sure"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)

        val first = env.harness.agent.send(conversation.id, "something").toList().last() as AgentEvent.Finished
        assertTrue(first.refused)
        val refusal = env.conversations.getMessages(conversation.id).last()
        assertTrue(refusal.excludedFromContext)
        assertContains(refusal.errorMessage!!, "cyber")

        env.harness.agent.send(conversation.id, "something else").toList()
        assertEquals(1, provider.requests[1].messages.filterIsInstance<LlmMessage.Assistant>().size, "only the greeting")
    }

    @Test
    fun regenerateReplacesOnlyTheLastReply() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "first try"))
            .then(LlmTurnResult(text = "second try"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "hello").toList()
        val epoch = env.conversations.get(conversation.id)!!.context!!.epochId

        env.harness.agent.regenerate(conversation.id).toList()

        val texts = env.conversations.getMessages(conversation.id).map { it.text }
        assertEquals(listOf("Hi Sam!", "hello", "second try"), texts)
        assertEquals(epoch, env.conversations.get(conversation.id)!!.context!!.epochId, "dropping the tail keeps the epoch")
        assertFalse(provider.requests[1].messages.any { it is LlmMessage.Assistant && it.text == "first try" })
    }

    @Test
    fun editingHistoryForcesANewEpochButKeepsTheSummary() = runTest {
        val provider = ScriptedProvider().then(LlmTurnResult(text = "a", replay = "ra")).then(LlmTurnResult(text = "b"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "hello").toList()
        val before = env.conversations.get(conversation.id)!!.context!!
        val userMessage = env.conversations.getMessages(conversation.id).first { it.role == MessageRole.USER }

        env.harness.editMessage(userMessage, "hello there")
        env.harness.agent.send(conversation.id, "next").toList()

        val after = env.conversations.get(conversation.id)!!.context!!
        assertNotEquals(before.epochId, after.epochId)
        assertNull(provider.requests[1].messages.filterIsInstance<LlmMessage.Assistant>().last().replay)
    }

    @Test
    fun missingModelIsReportedAsAFailureNotAnException() = runTest {
        val env = TestEnv().apply { seed() }
        env.profiles.delete(env.profile.id)
        val conversation = env.harness.createConversation(env.persona.id)

        val events = env.harness.agent.send(conversation.id, "hello?").toList()

        val failure = events.single() as AgentEvent.Failed
        assertContains(failure.message, "model profile")
        assertFalse(failure.retryable)
    }

    @Test
    fun toolCallsMissingRequiredArgumentsAreRejected() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall("c1", "memory_save", "{}")), stopReason = StopReason.TOOL_USE))
            .then(LlmTurnResult(text = "oops"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)

        val events = env.harness.agent.send(conversation.id, "remember this").toList()

        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertTrue(finished.outcome.isError)
        assertContains(finished.outcome.content, "'content'")
        assertTrue(env.harness.memory.all(env.persona.id).isEmpty())
    }
}
