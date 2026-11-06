package dev.aiharness.core.planner

import dev.aiharness.core.TestEnv
import dev.aiharness.core.agent.HeartbeatEngine
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.seasons.Seasons
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.4 step 4: a persona writing in your planner, its time away, its reminders, and its activities. The clock reads Thursday 28 May 2026, 10:00 UTC. */
class PersonasPlannerTest {
    private fun toolTurn(id: String, name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall(id, name, args)), stopReason = StopReason.TOOL_USE)

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 5, day).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun lastNote(env: TestEnv) = (env.provider.requests.last().messages.last { it is LlmMessage.User } as LlmMessage.User)
        .parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }

    private suspend fun TestEnv.results(conversationId: String) = conversations.getMessages(conversationId).flatMap { it.toolResults }.map { it.content }

    @Test
    fun itWritesNotesSuggestionsRemindersAndItsSeal() = runTest {
        val env = TestEnv().apply { seed() }
        env.plannerEntries.upsert(PlannerEntry("i1", env.user.id, null, EntryKind.PLAN, "interview", start = at(29, 14), end = at(29, 15), createdAt = 0, updatedAt = 0))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "planner_write", """{"kind":"note","text":"ask how the interview went","day":"tomorrow"}"""))
        env.provider.then(toolTurn("c2", "planner_write", """{"kind":"suggestion","text":"the meteor shower","day":"Saturday","time":"10pm"}"""))
        env.provider.then(toolTurn("c3", "planner_write", """{"kind":"reminder","text":"write after the interview","after":"interview"}"""))
        env.provider.then(toolTurn("c4", "planner_write", """{"kind":"seal","text":"歌"}"""))
        env.provider.then(LlmTurnResult(text = "Good luck tomorrow!"))
        env.harness.agent.send(conversation.id, "Big interview tomorrow at 2").toList()

        val written = env.plannerEntries.writtenBy(env.persona.id)
        assertEquals(listOf(EntryKind.NOTE, EntryKind.SUGGESTION, EntryKind.REMINDER), written.map { it.kind })
        assertTrue(written.all { it.plannerId == env.user.id && it.conversationId == conversation.id })
        assertEquals("2026-05-29", written[0].day)
        assertEquals(at(30, 22), written[1].start)
        assertEquals(at(29, 15), written[2].start, "it wakes when the interview ends")
        assertContains(env.results(conversation.id)[2], "You'll wake tomorrow 15:00")
        assertEquals("歌", env.personas.get(env.persona.id)!!.planner.seal)

        // Its next letter is told what it wrote; and the seal is picked, so it isn't asked again.
        env.provider.then(LlmTurnResult(text = "Ok"))
        env.harness.agent.send(conversation.id, "Thanks").toList()
        val note = lastNote(env)
        assertContains(note, "Your notes in Sam's planner: ask how the interview went (tomorrow).")
        assertContains(note, "You suggested: the meteor shower, Saturday 22:00 (not answered yet).")
        assertTrue("signature seal" !in note)
    }

    @Test
    fun itCantWriteInAPlannerThatIsntSharedOrInThePast() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "planner_write", """{"kind":"note","text":"too late","day":"2026-05-01"}"""))
        env.provider.then(toolTurn("c2", "planner_write", """{"kind":"reminder","text":"no time"}"""))
        env.provider.then(LlmTurnResult(text = "Hm"))
        env.harness.agent.send(conversation.id, "Hi").toList()
        assertEquals(listOf("That day has gone; write on today or later.", "A reminder needs a time (and a day, if not today), or after one of Sam's plans."), env.results(conversation.id))

        // Without its planner shared, the tools aren't offered.
        env.personas.upsert(env.persona.copy(seesPlanner = false))
        env.provider.then(LlmTurnResult(text = "Ok"))
        env.harness.agent.send(conversation.id, "Again").toList()
        assertTrue(env.provider.requests.last().tools.none { it.name.startsWith("planner_") })
        assertTrue(env.plannerEntries.writtenBy(env.persona.id).isEmpty())
    }

    @Test
    fun awayItsHeartbeatsLeaveItBeAndErasingEndsIt() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "planner_write", """{"kind":"away","text":"busy with the garden","day":"Sunday","time":"9:00"}"""))
        env.provider.then(LlmTurnResult(text = "Off to the garden."))
        env.harness.agent.send(conversation.id, "Have fun").toList()
        assertNotNull(env.harness.planner.awayNow(env.persona.id))

        env.clock.advanceMinutes(240)
        assertEquals(HeartbeatOutcome.Skipped(env.persona.id, "away"), env.harness.heartbeat.run().single())

        env.provider.then(toolTurn("c2", "planner_write", """{"kind":"erase","text":"away"}"""))
        env.provider.then(LlmTurnResult(text = "Back already!"))
        env.harness.agent.send(conversation.id, "Rain?").toList()
        assertNull(env.harness.planner.awayNow(env.persona.id))
        assertContains(env.results(conversation.id).last(), "You're back")
    }

    @Test
    fun aDueReminderWakesItAndIsKeptWhenItWrites() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hi").toList()
        env.plannerEntries.upsert(PlannerEntry("r1", env.user.id, env.persona.id, EntryKind.REMINDER, "write after the interview", start = at(28, 10, 20), conversationId = conversation.id, createdAt = at(27, 9), updatedAt = at(27, 9)))
        // A heartbeat ran just now: not time yet for another.
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        env.harness.heartbeat.run(force = true)
        env.clock.advanceMinutes(25)

        env.provider.then { request ->
            val note = (request.messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
            assertContains(note, "It's time for something you set yourself: write after the interview (you set it")
            LlmTurnResult(text = "How did the interview go?")
        }
        assertIs<HeartbeatOutcome.Sent>(env.harness.heartbeat.run().single(), "a due reminder wakes it before its pace would")
        assertEquals(EntryState.KEPT, env.plannerEntries.get("r1")!!.state)
    }

    @Test
    fun aReminderWhoseWakeupNeverCameIsMissed() = runTest {
        val env = TestEnv().apply { seed() }
        env.plannerEntries.upsert(PlannerEntry("r1", env.user.id, env.persona.id, EntryKind.REMINDER, "wish luck", start = at(27, 8), createdAt = at(26, 9), updatedAt = at(26, 9)))
        assertTrue(env.harness.planner.dueReminders(env.persona.id).isEmpty())
        assertEquals(EntryState.MISSED, env.plannerEntries.get("r1")!!.state)
        assertContains(env.harness.planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!, "Your reminders: wish luck (Wed 27 May 08:00): missed.")
    }

    @Test
    fun theWeekAheadIsPutBeforeItOnceADay() = runTest {
        val env = TestEnv().apply { seed() }
        env.plannerEntries.upsert(PlannerEntry("p1", env.user.id, null, EntryKind.PLAN, "pottery", start = at(30, 19), end = at(30, 21), createdAt = 0, updatedAt = 0))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hi").toList()
        env.clock.advanceMinutes(240)
        env.provider.then { request ->
            val note = (request.messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
            assertContains(note, "A look at Sam's week ahead in their planner:")
            assertContains(note, "Saturday (Sat 30 May): pottery, 19:00 to 21:00")
            toolTurn("n1", "planner_write", """{"kind":"note","text":"have fun at pottery","day":"Saturday"}""")
        }
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))
        env.harness.heartbeat.run()
        assertEquals(listOf("have fun at pottery"), env.plannerEntries.writtenBy(env.persona.id).map { it.title })
        assertEquals(1, env.activity.forPersona(env.persona.id, 0).count { it.kind == ActivityKind.WROTE_IN_PLANNER })

        // Not again the same day.
        env.clock.advanceMinutes(120)
        env.provider.then { request ->
            val note = (request.messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
            assertTrue("A look at Sam's week ahead" !in note)
            LlmTurnResult(text = HeartbeatEngine.SILENT)
        }
        env.harness.heartbeat.run()
    }

    @Test
    fun onANewSeasonsFirstDayAHaikuTakesASlot() = runTest {
        val env = TestEnv().apply { seed() }
        // The next microseason's first day, at ten in the morning.
        val first = Seasons.on(LocalDate.of(2026, 5, 28), ZoneOffset.UTC, Hemisphere.NORTH).microLast.plusDays(1)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hi").toList()
        env.clock.millis = first.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(activityLimit = 1))) }
        env.provider.then { request ->
            val note = (request.messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
            assertContains(note, "Today a new season begins: 麦秋至, wheat ripens")
            toolTurn("h1", "planner_write", """{"kind":"haiku","text":"gold across the field\nthe wheat bows to the wind\nsummer's first harvest"}""")
        }
        env.provider.then(LlmTurnResult(text = "And a letter."))
        // The haiku took the one slot, so the letter is held back.
        assertIs<HeartbeatOutcome.Silent>(env.harness.heartbeat.run().single())
        val haiku = env.plannerEntries.writtenBy(env.persona.id).single()
        assertEquals(EntryKind.HAIKU, haiku.kind)
        assertEquals(first.toString(), haiku.day)
        assertEquals("麦秋至", env.activity.forPersona(env.persona.id, 0).last { it.kind == ActivityKind.WROTE_HAIKU }.note)
    }
}
