package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.LetterBoxes
import dev.aiharness.core.tools.ChooseLetterBoxTool
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The little fumibakos (docs/plan-desktop.md, "Building them"): each persona's box, and choosing a new one. */
class LetterBoxTest {

    private fun LlmRequest.note(): String = (messages.last() as LlmMessage.User).parts
        .filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }

    private fun toolTurn(name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall("c-$name", name, args)), stopReason = StopReason.TOOL_USE)

    /** A quiet conversation the persona may write into: the last letter was three hours ago. */
    private suspend fun quietChat(provider: ScriptedProvider = ScriptedProvider()): Pair<TestEnv, String> {
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Good luck tomorrow! [mood: hopeful]"))
        env.harness.agent.send(conversation.id, "Big exam tomorrow").toList()
        env.harness.markRead(conversation.id)
        env.clock.advanceMinutes(180)
        return env to conversation.id
    }

    @Test
    fun untilOneIsChosenEachPersonaHasABoxOfItsOwnTheSameEveryTime() {
        assertEquals(LetterBoxes.pick("aria"), LetterBoxes.pick("aria"))
        val picks = (1..80).map { LetterBoxes.pick("persona-$it") }
        assertEquals(BoxFinish.entries.toSet(), picks.map { it.finish }.toSet())
        assertEquals(BoxLid.entries.toSet(), picks.map { it.lid }.toSet())
        assertEquals(BoxTie.entries.toSet(), picks.map { it.tie }.toSet())
        assertTrue(picks.all { !it.chosenByYou && it.chosenAt == 0L })
    }

    @Test
    fun theBoxInWords() {
        assertEquals(
            "lacquered in your ink, your name in gold on the lid, tied with mizuhiki",
            LetterBoxes.describe(LetterBox(BoxFinish.LACQUER, BoxLid.GILDED, BoxTie.MIZUHIKI)),
        )
    }

    @Test
    fun inAQuietMomentItCanChooseANewBox() = runTest {
        val (env, _) = quietChat()
        env.provider
            .then(toolTurn(ChooseLetterBoxTool.NAME, """{"finish":"washi","tie":"tassel"}"""))
            .then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        val before = LetterBoxes.of(env.persona)

        assertIs<HeartbeatOutcome.Silent>(env.harness.heartbeat.run().single())

        val note = env.provider.requests.first { it.note().contains("<heartbeat>") }.note()
        assertContains(note, "kept in a little box of your own (a fumibako)")
        assertContains(note, LetterBoxes.describe(before))
        val box = env.harness.personas.get(env.persona.id)!!.letterBox!!
        assertEquals(BoxFinish.WASHI, box.finish)
        assertEquals(before.lid, box.lid, "what it left out stays as it was")
        assertEquals(BoxTie.TASSEL, box.tie)
        assertFalse(box.chosenByYou)
        assertEquals(env.clock.nowMillis(), box.chosenAt)
        val logged = env.activity.forPersona(env.persona.id, 0).last { it.kind == ActivityKind.CHOSE_LETTER_BOX }
        assertEquals(LetterBoxes.describe(box), logged.note)
    }

    @Test
    fun itNeverBringsAboutAMomentOnItsOwn() = runTest {
        val env = TestEnv(ScriptedProvider()).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Tell me everything. [mood: curious]"))
        env.harness.agent.send(conversation.id, "So much happened today").toList()
        val requestsBefore = env.provider.requests.size

        val outcome = env.harness.heartbeat.run().single()

        assertEquals("conversation is recent", assertIs<HeartbeatOutcome.Skipped>(outcome).reason)
        assertEquals(requestsBefore, env.provider.requests.size, "no model call just to offer a box")
    }

    @Test
    fun offeredNowAndThenAndNotSoonAfterAChoice() = runTest {
        val (env, conversationId) = quietChat()
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        env.harness.heartbeat.run()
        assertContains(env.provider.requests.last().note(), "a little box of your own")

        // Three hours later: not again so soon.
        env.clock.advanceMinutes(180)
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        env.harness.heartbeat.run()
        assertFalse(env.provider.requests.last().note().contains("a little box of your own"))

        // Chosen for it on its page a day ago: not offered for a while after that, however long since the last offer.
        env.clock.advanceMinutes(13 * 24 * 60)
        val persona = env.harness.personas.get(env.persona.id)!!
        env.harness.personas.upsert(persona.copy(letterBox = LetterBox(BoxFinish.PAULOWNIA, BoxLid.SEAL, BoxTie.BOW, chosenByYou = true, chosenAt = env.clock.nowMillis() - 86_400_000L)))
        env.harness.markRead(conversationId)
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        env.harness.heartbeat.run()
        assertFalse(env.provider.requests.last().note().contains("a little box of your own"))
    }

    @Test
    fun notWhileWritingALetter() = runTest {
        val env = TestEnv(ScriptedProvider()).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider
            .then(toolTurn(ChooseLetterBoxTool.NAME, """{"finish":"lacquer"}"""))
            .then(LlmTurnResult(text = "Never mind. [mood: calm]"))

        env.harness.agent.send(conversation.id, "Hello!").toList()

        assertNull(env.harness.personas.get(env.persona.id)!!.letterBox)
        val result = env.provider.requests.last().messages.filterIsInstance<LlmMessage.ToolResults>().flatMap { it.results }
            .last { it.name == ChooseLetterBoxTool.NAME }
        assertTrue(result.isError)
        assertContains(result.content, "a quiet moment of your own")
    }
}
