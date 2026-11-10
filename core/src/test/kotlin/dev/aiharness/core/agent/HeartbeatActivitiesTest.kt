package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.memory.Feeling
import dev.aiharness.core.memory.FeelingFamily
import dev.aiharness.core.memory.Intensity
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.HeartbeatRhythm
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeartbeatActivitiesTest {

    /** A persona with a conversation that's still going (so it may not write: "conversation is recent"). */
    private suspend fun activeChat(provider: ScriptedProvider = ScriptedProvider()): Pair<TestEnv, String> {
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Tell me everything. [mood: curious]"))
        env.harness.agent.send(conversation.id, "So much happened today").toList()
        return env to conversation.id
    }

    private fun LlmRequest.note(): String = (messages.last() as LlmMessage.User).parts
        .filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }

    private fun toolTurn(name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall("c-$name", name, args)), stopReason = StopReason.TOOL_USE)

    private suspend fun log(env: TestEnv) = env.activity.forPersona(env.persona.id, 0)

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
    fun theMomentSaysHowManyThingsItCanDoAndALetterUsesOne() = runTest {
        val (env, _) = quietChat()
        env.provider.then(LlmTurnResult(text = "Thinking of you before the exam. [mood: hopeful]"))

        val outcome = env.harness.heartbeat.run().single()

        assertIs<HeartbeatOutcome.Sent>(outcome)
        val note = env.provider.requests.last().note()
        assertContains(note, "You can do up to 3 things of your own this time, and writing to")
        assertContains(note, "Reflecting and tending your memories don't count")
    }

    @Test
    fun theLimitIsAdjustableAndActionsOutsideTheMomentUseItUp() = runTest {
        val (env, _) = quietChat()
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(activityLimit = 1))) }
        // Something that acts in the persona's own voice before the moment (like writing to another persona).
        var tookSlot = false
        env.harness.heartbeat.activities += object : HeartbeatActivity {
            override val name = "pen-pal"
            override val usesSlot = true
            override suspend fun prepare(beat: Beat) {
                tookSlot = beat.takeSlot()
                assertFalse(beat.takeSlot(), "only one slot to take")
            }
        }
        val requestsBefore = env.provider.requests.size

        val outcome = env.harness.heartbeat.run().single()

        assertTrue(tookSlot)
        assertEquals("no activities left this time", assertIs<HeartbeatOutcome.Skipped>(outcome).reason)
        assertEquals(requestsBefore, env.provider.requests.size, "no private moment when nothing is due and no slot is left")
    }

    @Test
    fun freeActivitiesStillHappenWhenNoSlotIsLeft() = runTest {
        val (env, _) = quietChat()
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(activityLimit = 1))) }
        env.harness.heartbeat.activities += object : HeartbeatActivity {
            override val name = "pen-pal"
            override val usesSlot = true
            override suspend fun prepare(beat: Beat) {
                beat.takeSlot()
            }
        }
        env.harness.heartbeat.activities += object : HeartbeatActivity {
            override val name = "tidy"
            override suspend fun invite(beat: Beat) = "Tidy your memories, if you like."
        }
        env.provider.then(LlmTurnResult(text = "[SILENT]"))

        val outcome = env.harness.heartbeat.run().single()

        assertIs<HeartbeatOutcome.Silent>(outcome)
        val note = env.provider.requests.last().note()
        assertContains(note, "Tidy your memories, if you like.")
        assertContains(note, "You won't write to")
        assertContains(note, "(no activities left this time)")
    }

    @Test
    fun restsWhenNothingIsDueAndItMayNotWrite() = runTest {
        val (env, _) = activeChat()
        val requestsBefore = env.provider.requests.size

        val outcome = env.harness.heartbeat.run().single()

        assertEquals("conversation is recent", assertIs<HeartbeatOutcome.Skipped>(outcome).reason)
        assertEquals(requestsBefore, env.provider.requests.size, "no model call when nothing is due")
        assertEquals(ActivityKind.RESTED, log(env).last().kind)
        assertEquals("conversation is recent", log(env).last().note)
    }

    @Test
    fun eachPersonaKeepsItsOwnPaceAndCanUseItsOwnModel() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val slow = Persona("p2", "Kestrel", ownHeartbeat = HeartbeatRhythm(intervalMinutes = 180), heartbeatModelProfileId = "m2", createdAt = 0, updatedAt = 0)
        env.personas.upsert(slow)
        env.profiles.upsert(env.profile.copy(id = "m2", name = "Heartbeat model", modelId = "heartbeat-model"))
        listOf(env.persona.id, slow.id).forEach { id ->
            val c = env.harness.createConversation(id)
            provider.then(LlmTurnResult(text = "hello"))
            env.harness.agent.send(c.id, "hi").toList()
        }
        assertEquals(60, env.harness.heartbeat.shortestIntervalMinutes(env.personas.getAll(), env.settingsRepo.current().heartbeat.rhythm))

        env.clock.advanceMinutes(180)
        provider.fallback = { LlmTurnResult(text = HeartbeatEngine.SILENT) }
        env.harness.heartbeat.run()
        assertTrue(provider.requests.any { it.profile.modelId == "heartbeat-model" }, "Kestrel's heartbeat used its own model")

        env.clock.advanceMinutes(60)
        val outcomes = env.harness.heartbeat.run().associateBy { it.personaId }
        assertFalse(outcomes.getValue(env.persona.id) is HeartbeatOutcome.Skipped && (outcomes.getValue(env.persona.id) as HeartbeatOutcome.Skipped).reason == "not time yet")
        assertEquals("not time yet", (outcomes.getValue(slow.id) as HeartbeatOutcome.Skipped).reason, "every 3 hours, not every hour")
    }

    @Test
    fun invitesAReflectionWhenStrongFeelingsPileUp() = runTest {
        val (env, _) = activeChat()
        listOf("Sam got the job" to "elated", "Sam's cat is ill" to "worried sick", "Sam said I helped" to "touched").forEach { (text, mood) ->
            env.harness.memory.save(env.persona.id, text, kind = MemoryKind.EPISODE, feltMood = mood)
        }
        env.provider
            .then(toolTurn("memory_save", """{"content":"Sam leans on me when things are hard, and I like being leaned on","kind":"reflection"}"""))
            .then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        val outcome = env.harness.heartbeat.run().single()

        assertIs<HeartbeatOutcome.Silent>(outcome, "a private moment even though it may not write")
        val note = env.provider.requests.first { it.note().contains("<heartbeat>") }.note()
        assertContains(note, "A lot has happened since you last reflected")
        assertContains(note, "You won't write to Sam this time (conversation is recent)")
        assertTrue(log(env).any { it.kind == ActivityKind.REFLECTED })
        assertTrue("reflect" in log(env).last { it.kind == ActivityKind.STAYED_QUIET }.tags)
        val shown = env.harness.memory.all(env.persona.id).filter { it.kind == MemoryKind.EPISODE }
        assertTrue(shown.all { it.useCount > 0 }, "a reflection draws on what it was shown")

        // Not invited again within 12 hours.
        env.clock.advanceMinutes(120)
        env.harness.heartbeat.run()
        assertTrue(env.provider.requests.last().let { !it.note().contains("A lot has happened") })
    }

    @Test
    fun offersRecentMemoriesBackToRevisitHowTheyFeel() = runTest {
        val (env, _) = activeChat()
        val interview = env.harness.memory.save(env.persona.id, "Sam was nervous about the interview", feltMood = "worried").entry
        env.harness.memory.save(env.persona.id, "Sam started running again", feltMood = "glad")
        env.clock.advanceMinutes(90)
        env.harness.memory.retrieve(env.persona.id, null, "interview running", topK = 5, minRelevance = 0.01)
        env.provider
            .then(toolTurn("memory_update", """{"id":"${interview.id.take(8)}","mood":"proud","reason":"They got the job."}"""))
            .then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        env.harness.heartbeat.run()

        val note = env.provider.requests.first { it.note().contains("<heartbeat>") }.note()
        assertContains(note, "These memories came up lately")
        assertContains(note, "id ${interview.id.take(8)}")
        assertContains(note, "felt worried")
        val revisited = env.harness.memory.get(interview.id)!!
        assertEquals("proud", revisited.mood)
        assertEquals("worried", revisited.feltMood)
        assertNotNull(revisited.revisitedAt)
        assertEquals(1, log(env).single { it.kind == ActivityKind.REVISITED_FEELINGS }.count)
    }

    @Test
    fun asksWhichMemoriesCanSettleWhenTooManyMatterDeeply() = runTest {
        val (env, _) = activeChat()
        env.settingsRepo.update { it.copy(memory = it.memory.copy(deepLimit = 2)) }
        val deep = listOf(
            "Sam's grandmother raised them",
            "Sam told me about the accident",
            "Sam trusts me with the spare key",
            "Sam cried when the dog came home",
        ).map { env.harness.memory.save(env.persona.id, it, level = MemoryLevel.MATTERS_DEEPLY).entry }
        env.provider
            .then(toolTurn("memory_update", """{"id":"${deep.first().id.take(8)}","weight":"worth_keeping"}"""))
            .then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        env.harness.heartbeat.run()

        val note = env.provider.requests.first { it.note().contains("<heartbeat>") }.note()
        assertContains(note, "You hold 4 memories that matter deeply")
        assertEquals(MemoryLevel.WORTH_KEEPING, env.harness.memory.get(deep.first().id)!!.level)
        assertEquals(1, log(env).single { it.kind == ActivityKind.SETTLED_MEMORIES }.count)
    }

    @Test
    fun sortsUnknownMoodWordsOnceWithTheUtilityModel() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Well! [mood: flummoxed]"))
        env.harness.agent.send(conversation.id, "Guess what").toList()
        env.clock.advanceMinutes(180)
        provider
            .then(LlmTurnResult(text = HeartbeatEngine.SILENT))
            .then(LlmTurnResult(text = "flummoxed: wonder, mild"))

        env.harness.heartbeat.run()

        assertEquals(Feeling(FeelingFamily.WONDER, Intensity.MILD), env.settingsRepo.current().learnedFeelings["flummoxed"])
    }

    @Test
    fun parsesSortedFeelingsLeniently() {
        val parsed = SortFeelingsActivity.parse("- Flummoxed: wonder, mild\ngobsmacked: WONDER strong\nunasked: sad, mild\nnonsense", setOf("flummoxed", "gobsmacked"))
        assertEquals(
            mapOf(
                "flummoxed" to Feeling(FeelingFamily.WONDER, Intensity.MILD),
                "gobsmacked" to Feeling(FeelingFamily.WONDER, Intensity.STRONG),
            ),
            parsed,
        )
    }
}
