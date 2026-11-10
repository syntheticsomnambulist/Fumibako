package dev.aiharness.core.letters

import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.WayLine
import dev.aiharness.core.tools.RewriteWayLinesTool
import dev.aiharness.core.tools.ToolContext
import dev.aiharness.core.model.Conversation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Her lines on the way (v0.5): the postmark's line, hers and yours, and her rewriting them. */
class WayLinesTest {
    @Test
    fun aPersonaStartsWithPlainLinesInHerOwnPronouns() {
        val env = TestEnv()
        assertEquals("Mira's looking for a stamp", WayLines.plain(env.persona)[1], "without pronouns, her name")
        val she = env.persona.copy(pronouns = Pronouns(PronounKind.SHE))
        assertEquals("she's addressing the envelope", WayLines.of(she).first())
        assertEquals("they're on the way to the post box", WayLines.plain(env.persona.copy(pronouns = Pronouns(PronounKind.THEY))).last())
    }

    @Test
    fun oneLineALetterTheSameWhereverItShows() {
        val env = TestEnv()
        val mira = env.persona.copy(wayLines = listOf("a", "b", "c", "d", "e").map { WayLine("she's $it") })
        val first = WayLines.pick(mira, "thread", 1_000)
        assertEquals(first, WayLines.pick(mira, "thread", 1_000), "the same note, the same line")
        val picked = (0 until 40).map { WayLines.pick(mira, "thread", it * 60_000L) }.toSet()
        assertTrue(picked.size >= 3, "other letters get other lines: $picked")
    }

    @Test
    fun whatSheWritesIsTidiedAndYoursStay() {
        val mira = TestEnv().persona.copy(wayLines = listOf(WayLine("she's feeding the gulls", yours = true), WayLine("she's old")))
        val parsed = WayLines.parse(
            "Here are four:\n1. \"she's licking the stamp\"\n- Mira's reply is on its way · she's racing the tide.\n* she's racing the tide\n\nshe's waving at the ferry",
            "Mira",
        )
        assertEquals(listOf("she's licking the stamp", "she's racing the tide", "she's waving at the ferry"), parsed)
        val updated = WayLines.withTheirs(mira, parsed, at = 5)
        assertEquals(listOf("she's feeding the gulls", "she's licking the stamp", "she's racing the tide", "she's waving at the ferry"), updated.wayLines.map { it.text })
        assertTrue(updated.wayLines.first().yours)
        assertEquals(5, updated.wayLinesWrittenAt)
    }

    @Test
    fun letHerWriteHerOwnIsAShortTurnInHerVoice() = runTest {
        val env = TestEnv()
        env.seed()
        env.provider.then(LlmTurnResult(text = "Mira's tucking in a pressed fern\nMira's arguing with the post box\nMira's humming on the stairs"))
        val mira = assertNotNull(env.harness.writeOwnWayLines("p1"))
        assertEquals(3, mira.wayLines.size)
        assertEquals("Mira's arguing with the post box", mira.wayLines[1].text)
        val request = env.provider.requests.last()
        assertEquals(1_000, request.profile.maxOutputTokens, "under 1,000 tokens")
        assertTrue(request.system.contains("Mira"), "in her own voice: her own role section")
        assertEquals(env.clock.millis, env.personas.get("p1")?.wayLinesWrittenAt)
    }

    @Test
    fun sheRewritesHerOwnOnlyInAQuietMomentAndAtMostWeekly() = runTest {
        val env = TestEnv()
        env.seed()
        val tool = env.harness.tools.available(env.settingsRepo.current(), env.persona).first { it.spec.name == RewriteWayLinesTool.NAME }
        val conversation = Conversation(id = "c", personaId = "p1", title = "Mira", createdAt = 0, updatedAt = 0)
        val settings = env.settingsRepo.current()
        fun context(background: Boolean) = ToolContext(conversation, env.persona, env.user, settings, background)
        val args = JsonObject(mapOf("lines" to JsonArray(listOf(JsonPrimitive("Mira's sealing it with wax"), JsonPrimitive("Mira's off to the harbor")))))

        assertTrue(tool.execute(args, context(background = false)).isError, "never in a letter")
        val done = tool.execute(args, context(background = true))
        assertTrue(!done.isError, done.content)
        assertEquals(listOf("Mira's sealing it with wax", "Mira's off to the harbor"), env.personas.get("p1")!!.wayLines.map { it.text })
        assertEquals(2, env.activity.forPersona("p1", 0).single { it.kind == ActivityKind.REWROTE_LINES }.count)

        env.clock.millis += 3 * 86_400_000L
        assertTrue(tool.execute(args, context(background = true)).isError, "not again within the week")
        env.clock.millis += 5 * 86_400_000L
        assertTrue(!tool.execute(args, context(background = true)).isError, "a week on, it may")
    }
}
