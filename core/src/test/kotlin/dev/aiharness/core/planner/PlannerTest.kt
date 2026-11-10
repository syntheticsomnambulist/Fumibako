package dev.aiharness.core.planner

import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.model.Weekly
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.4 step 1: what the planner holds, and what a persona is told of it. The clock reads Thursday 28 May 2026, 10:00 UTC. */
class PlannerTest {
    private var n = 0

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 5, day).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun entry(
        kind: EntryKind,
        title: String = "",
        start: Long? = null,
        end: Long? = null,
        day: String? = null,
        allDay: Boolean = false,
        weekly: Weekly? = null,
        routine: RoutineKind? = null,
        by: String? = null,
        state: EntryState = EntryState.OPEN,
        movedFrom: String? = null,
        planner: String = "u1",
        updatedAt: Long = at(28, 9),
    ) = PlannerEntry(
        "e${n++}", planner, by, kind, title, start, end, day, allDay, weekly, routine,
        state = state, movedFrom = movedFrom, createdAt = at(27, 9), updatedAt = updatedAt,
    )

    /** Sam's typical week: work Monday to Friday, 9:00 to 17:30; asleep 23:00 to 7:00 every night. */
    private suspend fun TestEnv.typicalWeek() {
        plannerEntries.upsert(entry(EntryKind.ROUTINE, "Work", weekly = Weekly(listOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60 + 30), routine = RoutineKind.WORK))
        plannerEntries.upsert(entry(EntryKind.ROUTINE, "Sleep", weekly = Weekly((1..7).toList(), 23 * 60, 7 * 60), routine = RoutineKind.SLEEP))
    }

    @Test
    fun aLetterIsToldWhatsOnNowAndNext() = runTest {
        val env = TestEnv().apply { seed() }
        env.typicalWeek()
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "dinner with Jo", start = at(28, 19), end = at(28, 21)))
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "dentist", start = at(29, 10)))
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "Jo's birthday", day = "2026-06-01", allDay = true))
        // With its seal picked (else it's asked to pick one).
        val context = env.harness.planner.contextFor(env.persona.copy(planner = dev.aiharness.core.model.PersonaPlanner(seal = "歌")), env.user, env.settingsRepo.current())!!
        assertEquals(
            "Sam's planner: at work until 17:30. Later today: dinner with Jo, 19:00 to 21:00. Tomorrow: work, 09:00 to 17:30; dentist, 10:00.\n" +
                "Coming up: Monday: Jo's birthday.",
            context,
        )

        // It goes in the letter's context block.
        env.provider.then(LlmTurnResult(text = "Good luck today."))
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "Morning!").toList()
        val note = env.provider.requests.last().messages.last { it is LlmMessage.User } as LlmMessage.User
        assertContains(note.parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }, "Sam's planner: at work until 17:30.")
    }

    @Test
    fun aHeartbeatIsToldWhyYoureQuiet() = runTest {
        val env = TestEnv().apply { seed() }
        env.typicalWeek()
        val planner = env.harness.planner
        val settings = env.settingsRepo.current()
        assertEquals("Sam is at work until 17:30. A letter written now will be read after that.", planner.heartbeatLine(env.persona, env.user, settings))

        // Asleep across midnight: until 7:00 the next morning.
        env.clock.millis = at(28, 23, 30)
        assertEquals("Sam is asleep until 07:00. A letter written now will be read after that.", planner.heartbeatLine(env.persona, env.user, settings))

        // Free in the evening.
        env.clock.millis = at(28, 19)
        assertNull(planner.heartbeatLine(env.persona, env.user, settings))
    }

    @Test
    fun onlyAPlannerThatsSharedIsSeen() = runTest {
        val env = TestEnv().apply { seed() }
        env.typicalWeek()
        val settings = env.settingsRepo.current()
        val planner = env.harness.planner
        assertTrue(planner.shared(env.persona, env.user, settings), "your default persona shares its planner")
        assertNull(planner.contextFor(env.persona.copy(seesPlanner = false), env.user, settings))

        // Another persona of yours keeps its own planner, unshared until you say.
        val ness = UserPersona("u2", "Ness", createdAt = 0, updatedAt = 0)
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "dragon hunt", start = at(28, 14), planner = "u2"))
        assertNull(planner.contextFor(env.persona, ness, settings))
        assertContains(planner.contextFor(env.persona, ness.copy(sharesPlanner = true), settings)!!, "Ness's planner: Later today: dragon hunt, 14:00.")
    }

    @Test
    fun aPersonaIsToldWhatItWroteAndHowYourPlansStand() = runTest {
        val env = TestEnv().apply { seed() }
        val p = env.persona.id
        env.plannerEntries.upsert(entry(EntryKind.NOTE, "ask how the dentist went", day = "2026-05-29", by = p))
        env.plannerEntries.upsert(entry(EntryKind.SUGGESTION, "the meteor shower", start = at(30, 22), day = "2026-05-30", by = p))
        env.plannerEntries.upsert(entry(EntryKind.REMINDER, "write after the interview", start = at(28, 16), by = p))
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "dentist", start = at(28, 8), state = EntryState.DONE))
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "pottery", day = "2026-05-31", movedFrom = "2026-05-29"))
        env.plannerEntries.upsert(entry(EntryKind.PLAN, "yoga", start = at(28, 18), state = EntryState.CANCELLED))
        // Away, in its own words for it.
        env.plannerEntries.upsert(entry(EntryKind.AWAY, "busy with the garden", start = at(28, 9), end = at(31, 9), by = p))
        val context = env.harness.planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!

        assertContains(context, "Your notes in Sam's planner: ask how the dentist went (tomorrow).")
        assertContains(context, "Your reminders: write after the interview (today 16:00).")
        assertContains(context, "You suggested: the meteor shower, Saturday 22:00 (not answered yet).")
        assertContains(context, "dentist: done. pottery (tomorrow): moved to Sunday. yoga: cancelled.")
        assertTrue("yoga, 18:00" !in context, "a cancelled plan isn't coming up")
        assertContains(context, "You're away until Sunday 09:00: busy with the garden.")
    }
}
