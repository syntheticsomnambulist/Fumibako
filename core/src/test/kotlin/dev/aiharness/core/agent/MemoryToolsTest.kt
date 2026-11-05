package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryToolsTest {

    private fun toolTurn(id: String, name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall(id, name, args)), stopReason = StopReason.TOOL_USE)

    @Test
    fun savedMemoriesCarryLevelFeelingAndTheLetterTheyCameFrom() = runTest {
        val provider = ScriptedProvider()
            .then(toolTurn("c1", "memory_save", """{"content":"Sam got the job at the botanic garden","weight":"matters_deeply","mood":"proud"}"""))
            .then(toolTurn("c2", "memory_save", """{"content":"Sam's interview was on a Tuesday","weight":"2"}"""))
            .then(LlmTurnResult(text = "I knew you would! [mood: delighted]"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)

        env.harness.agent.send(conversation.id, "I got the job!").toList()

        val memories = env.harness.memory.all(env.persona.id).associateBy { it.content }
        val job = memories.getValue("Sam got the job at the botanic garden")
        assertEquals(MemoryLevel.MATTERS_DEEPLY, job.level)
        assertEquals("proud", job.feltMood)
        assertTrue(job.feltMoodChosen)
        val tuesday = memories.getValue("Sam's interview was on a Tuesday")
        assertEquals(MemoryLevel.IN_PASSING, tuesday.level, "old 1 to 10 numbers are still understood")
        assertEquals("delighted", tuesday.feltMood, "no mood given: it takes the letter's mood")

        val assistantIds = env.conversations.getMessages(conversation.id).filter { it.role == MessageRole.ASSISTANT }.map { it.id }.toSet()
        assertTrue(job.sourceMessageId in assistantIds, "links back to the persona's message")
    }

    @Test
    fun recalledMemoriesReachThePersonaWithTheirFeelings() = runTest {
        val provider = ScriptedProvider().then(LlmTurnResult(text = "ok"))
        val env = TestEnv(provider).apply { seed() }
        val kept = env.harness.memory.save(env.persona.id, "Sam was nervous about the botanic garden interview", feltMood = "worried").entry
        env.harness.memory.reviseFeeling(kept.id, "hopeful", "It went well.", byUser = true)
        val conversation = env.harness.createConversation(env.persona.id)

        env.harness.agent.send(conversation.id, "Remember the botanic garden interview?").toList()

        val context = (provider.requests.single().messages.last() as LlmMessage.User).parts
            .filterIsInstance<LlmPart.Text>().first { it.text.startsWith("<context>") }.text
        assertContains(context, "felt worried, now hopeful")
    }

    @Test
    fun feelingChangesNeedAReasonAndFactsCanBeReplaced() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val old = env.harness.memory.save(env.persona.id, "Sam lives on Mill Road").entry
        provider
            .then(toolTurn("c1", "memory_update", """{"id":"${old.id.take(8)}","mood":"wistful"}"""))
            .then(toolTurn("c2", "memory_save", """{"content":"Sam moved to a house by the river","replaces":"${old.id.take(8)}"}"""))
            .then(LlmTurnResult(text = "A house by the river!"))
        val conversation = env.harness.createConversation(env.persona.id)

        val events = env.harness.agent.send(conversation.id, "We moved!").toList()

        val results = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertTrue(results[0].outcome.isError)
        assertContains(results[0].outcome.content, "reason")
        assertContains(results[1].outcome.content, "replaced")
        assertEquals(env.harness.memory.all(env.persona.id).single { !it.isReplaced }.id, env.harness.memory.get(old.id)!!.replacedById)
    }
}
