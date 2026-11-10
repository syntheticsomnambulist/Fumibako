package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.FoundLetter
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.TokenUsage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextSlipTest {
    private val context = """
        Current time: Friday, 6 November 2026, 16:04.
        Time since the previous message: 5 hours.
        Your model changed: you were Claude Sonnet 5.5 until now and are Claude Opus 5.5 from here. Your memories and letters are the same.
        You're wearing: a yellow raincoat (not your usual clothes).
        Sam's planner: interview until 16:00. Later today: dinner with Jo, 19:00 to 21:00.
        Thursday is a public holiday: Thanksgiving.
        Season: 立冬 (winter begins), camellias open.
        [Sam stuck :heart: on your letter from Thursday]
        New stickers you can use: :aria-proud: (proud).
        Recently updated core memories:
        - Sam and Aria write every evening.
        Memories that come to mind:
        - (6 Nov 2026, felt worried, now hopeful) Sam has an interview on Friday at three.
        - (12 Oct 2026, felt fond, a little hazy) Sam likes drawings that look half-finished.
        - (2 Oct 2026) The neighbor's cat sits in the same window.
    """.trimIndent()

    @Test
    fun theContextBlockIsSortedIntoTheCardsSections() {
        val note = ChatMessage(
            "n", "c", MessageRole.USER,
            listOf(ContentPart.Text("Wish me luck!"), ContentPart.Image("blob:1", caption = "my desk", description = "A tidy desk with a plant.")),
            createdAt = 1, turnContext = context,
        )
        val reply = ChatMessage(
            "r", "c", MessageRole.ASSISTANT, listOf(ContentPart.Text("Good luck!")), createdAt = 2,
            providerType = ProviderType.ANTHROPIC, usage = TokenUsage(inputTokens = 400, outputTokens = 410, cacheReadTokens = 18_000),
        )
        val slip = ContextSlip.of(note, listOf(reply))

        assertEquals(
            listOf("Current time: Friday, 6 November 2026, 16:04.", "Time since the previous message: 5 hours.", "Season: 立冬 (winter begins), camellias open."),
            slip.time,
        )
        assertEquals(1, slip.model.size)
        assertEquals(listOf("You're wearing: a yellow raincoat (not your usual clothes)."), slip.also)
        assertEquals(
            listOf("Sam's planner: interview until 16:00. Later today: dinner with Jo, 19:00 to 21:00.", "Thursday is a public holiday: Thanksgiving."),
            slip.planner,
        )
        assertEquals(listOf("Sam stuck :heart: on your letter from Thursday", "New stickers you can use: :aria-proud: (proud)."), slip.stickers)
        assertEquals(
            listOf(
                ContextSlip.Recalled(null, "Sam and Aria write every evening.", core = true),
                ContextSlip.Recalled("hopeful", "Sam has an interview on Friday at three."),
                ContextSlip.Recalled("fond", "Sam likes drawings that look half-finished."),
                ContextSlip.Recalled(null, "The neighbor's cat sits in the same window."),
            ),
            slip.memories,
        )
        assertEquals(listOf("my desk: A tidy desk with a plant."), slip.read)
        assertEquals(18_400L, slip.tokensRead, "Anthropic's cached tokens count as read")
        assertEquals(410L, slip.tokensWritten)
    }

    @Test
    fun aRealTurnsSlipHasItsTimeMemoriesAndSearches() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall("t1", "letter_search", """{"query":"kinako"}""")), stopReason = StopReason.TOOL_USE))
            .then(LlmTurnResult(text = "It's Kinako!"))
        val env = TestEnv(provider).apply { seed() }
        env.harness.memory.save(env.persona.id, "Sam's window cat is called Kinako", feltMood = "fond")
        val conversation = env.harness.createConversation(env.persona.id)
        env.conversations.appendMessage(
            ChatMessage("old", conversation.id, MessageRole.USER, listOf(ContentPart.Text("Her name is Kinako.")), createdAt = env.clock.millis - 86_400_000),
        )

        env.harness.agent.send(conversation.id, "What was the window cat called? Kinako?").toList()

        val messages = env.conversations.getMessages(conversation.id)
        val note = messages.last { it.role == MessageRole.USER }
        val slip = ContextSlip.of(note, messages.dropWhile { it.id != note.id }.drop(1))
        assertTrue(slip.time.first().startsWith("Current time:"))
        assertEquals(listOf(ContextSlip.Recalled("fond", "Sam's window cat is called Kinako")), slip.memories)
        assertEquals("kinako", slip.searches.single().query)
        assertEquals(
            listOf(FoundLetter("old", conversation.id, byYou = true, at = env.clock.millis - 86_400_000, passage = "Her name is Kinako.")),
            slip.searches.single().found,
        )
    }
}
