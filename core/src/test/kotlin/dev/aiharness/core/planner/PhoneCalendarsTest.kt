package dev.aiharness.core.planner

import dev.aiharness.core.TestEnv
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PhoneDetail
import dev.aiharness.core.model.PhoneStamp
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.4 step 5: the phone's calendars, read live into your default persona's planner. The clock reads Thursday 28 May 2026, 10:00 UTC. */
class PhoneCalendarsTest {
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 5, day).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private class FakePhone(var readable: Boolean = true) : PhoneCalendars {
        val calendars = listOf(
            PhoneCalendar("work", "Work", "sam@example.com"),
            PhoneCalendar("home", "Home", "sam@example.com"),
            PhoneCalendar("hol", "Holidays in United States", "en.usa#holiday@group.v.calendar.google.com", holidays = true),
        )
        var events = listOf<PhoneEvent>()
        override fun readable() = readable
        override suspend fun calendars() = calendars
        override suspend fun events(from: Long, to: Long, calendarIds: Set<String>) = events.filter { it.calendarId in calendarIds && it.start < to && it.end > from }
    }

    private suspend fun TestEnv.levels(vararg pairs: Pair<String, PhoneDetail>) =
        settingsRepo.update { it.copy(planner = it.planner.copy(phone = it.planner.phone.copy(levels = pairs.toMap()))) }

    @Test
    fun eachCalendarShowsAtItsDetailAndPersonasSeeTheSame() = runTest {
        val phone = FakePhone()
        phone.events = listOf(
            PhoneEvent(1, "work", "standup", "Room 4", start = at(28, 11), end = at(28, 12)),
            PhoneEvent(2, "home", "dentist", "Main St", start = at(28, 15), end = at(28, 16)),
        )
        val env = TestEnv(phone = phone).apply { seed() }
        val planner = env.harness.planner
        // Nothing shows until you choose.
        assertNull(planner.contextFor(env.persona, env.user, env.settingsRepo.current()))

        env.levels("work" to PhoneDetail.BUSY, "home" to PhoneDetail.PLACES)
        val context = planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!
        assertContains(context, "Later today: busy, 11:00 to 12:00; dentist, at Main St, 15:00 to 16:00.")
        assertTrue("standup" !in context && "Room 4" !in context, "a busy-only calendar gives no titles or places")

        // Titles only: no places.
        env.levels("work" to PhoneDetail.TITLES)
        val titles = planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!
        assertContains(titles, "standup, 11:00 to 12:00")
        assertTrue("Room 4" !in titles && "dentist" !in titles)
    }

    @Test
    fun aBusyEventKeepsYouBusyAndStampsShow() = runTest {
        val phone = FakePhone()
        phone.events = listOf(PhoneEvent(7, "work", "all-hands", start = at(28, 9), end = at(28, 11)))
        val env = TestEnv(phone = phone).apply { seed() }
        env.levels("work" to PhoneDetail.TITLES)
        assertEquals("Sam is busy (all-hands) until 11:00. A letter written now will be read after that.", env.harness.planner.heartbeatLine(env.persona, env.user, env.settingsRepo.current()))

        // Stamped not going: no longer busy.
        env.settingsRepo.update { it.copy(planner = it.planner.copy(phone = it.planner.phone.copy(stamps = listOf(PhoneStamp(7, at(28, 9), EntryState.CANCELLED, at(28, 9, 30)))))) }
        assertNull(env.harness.planner.heartbeatLine(env.persona, env.user, env.settingsRepo.current()))
        val day = env.harness.planner.days(env.user.id, LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 28)).single { it.entry.phone != null }
        assertEquals(EntryState.CANCELLED, day.entry.state)
        assertEquals("Work", day.entry.phone)
    }

    @Test
    fun aShownHolidayCalendarStandsInForNagerDate() = runTest {
        val phone = FakePhone()
        phone.events = listOf(PhoneEvent(9, "hol", "Memorial Day", start = at(29, 0), end = at(30, 0), allDay = true, day = "2026-05-29", busy = false))
        val env = TestEnv(phone = phone).apply { seed() }
        // Nager.Date's (kept) list says otherwise; the phone's calendar wins once shown.
        env.settingsRepo.update {
            it.copy(planner = it.planner.copy(holidays = dev.aiharness.core.model.HolidaySettings(show = true, country = "US", years = listOf(
                dev.aiharness.core.model.HolidayYear("US", 2026, at(28, 0), listOf(dev.aiharness.core.model.Holiday("2026-05-30", "Some Other Day"))),
            ))))
        }
        assertContains(env.harness.planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!, "Saturday is a public holiday: Some Other Day.")
        env.levels("hol" to PhoneDetail.TITLES)
        val context = env.harness.planner.contextFor(env.persona, env.user, env.settingsRepo.current())!!
        assertContains(context, "Tomorrow is a public holiday: Memorial Day.")
        assertTrue("Some Other Day" !in context)
    }

    @Test
    fun onlyYourDefaultPersonasPlannerAndOnlyWhenReadable() = runTest {
        val phone = FakePhone()
        phone.events = listOf(PhoneEvent(1, "work", "standup", start = at(28, 11), end = at(28, 12)))
        val env = TestEnv(phone = phone).apply { seed() }
        env.levels("work" to PhoneDetail.TITLES)
        val planner = env.harness.planner
        assertTrue(planner.phoneRead("someone-else", env.settingsRepo.current(), LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 28)).entries.isEmpty())
        phone.readable = false
        assertNull(planner.contextFor(env.persona, env.user, env.settingsRepo.current()), "no permission, nothing read")
    }
}
