package dev.aiharness.app.ui.planner

import dev.aiharness.core.model.ClockStyle
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.Weekly
import dev.aiharness.core.planner.Occurrence
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The week page's hours with sleep folded, the clock, and a plan's card becoming an entry. */
class PlannerTimeTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 11, 2)
    private val week = PlannerDays.weekOf(monday.plusDays(3), startsSunday = false)

    private fun sleep(fromHour: Int, toHour: Int): List<Occurrence> {
        val e = PlannerEntry("s", "u", null, EntryKind.ROUTINE, "Sleep", weekly = Weekly((1..7).toList(), fromHour * 60, toHour * 60), routine = RoutineKind.SLEEP, createdAt = 0, updatedAt = 0)
        return dev.aiharness.core.planner.Planner(dev.aiharness.core.storage.inmemory.InMemoryPlannerRepository(), object : dev.aiharness.core.util.Clock {
            override fun nowMillis() = 0L
            override val zone = this@PlannerTimeTest.zone
        }).occurrences(listOf(e), week.first(), week.last())
    }

    @Test
    fun theWeekStartsOnTheDayYouChose() {
        assertEquals(monday, week.first())
        assertEquals(LocalDate.of(2026, 11, 1), PlannerDays.weekOf(monday.plusDays(3), startsSunday = true).first())
        assertEquals("木", PlannerDays.kanji(week[3].dayOfWeek))
        assertEquals("霜月" to "frost month", PlannerDays.months[10])
    }

    @Test
    fun sleepEveryNightFoldsAtBothEndsOfTheDay() {
        val folded = HourLayout.asleepEveryDay(sleep(23, 7), week, zone)
        assertEquals(listOf(0..6, 23..23), folded)
        val layout = HourLayout(folded)
        assertEquals(HourLayout.FOLD, layout.y(7 * 60), "midnight to seven is one thin band")
        assertEquals(HourLayout.FOLD + 2 * HourLayout.HOUR, layout.y(9 * 60))
        assertEquals(2 * HourLayout.FOLD + 16 * HourLayout.HOUR, layout.height)
    }

    @Test
    fun aNightShiftsSleepFoldsMidColumn() {
        assertEquals(listOf(8..14), HourLayout.asleepEveryDay(sleep(8, 15), week, zone))
        val layout = HourLayout(listOf(8..14))
        assertEquals(8 * HourLayout.HOUR, layout.y(8 * 60))
        assertEquals(8 * HourLayout.HOUR + HourLayout.FOLD, layout.y(15 * 60))
    }

    @Test
    fun theClockWritesTimesAsYouChose() {
        val twelve = ClockFormat(ClockStyle.H12, phoneIs24 = true)
        assertEquals("4:00 pm", twelve.time(16 * 60))
        assertEquals("12:30 am", twelve.time(30))
        assertEquals("4p", twelve.hour(16))
        assertEquals("16:00", ClockFormat(ClockStyle.PHONE, phoneIs24 = true).time(16 * 60))
        assertEquals("16", ClockFormat(ClockStyle.H24, phoneIs24 = false).hour(16))
    }

    @Test
    fun aPlansCardMakesTheEntry() {
        val blank = { kind: EntryKind -> PlannerEntry("n", "u", null, kind, createdAt = 0, updatedAt = 0) }
        val friday = monday.plusDays(4)
        // At a time, past midnight: it ends the next morning.
        val late = PlanDraft(null, friday, title = "Night market", from = LocalTime.of(22, 0), to = LocalTime.of(1, 0)).toEntry(blank, zone)!!
        assertEquals(friday.atTime(22, 0).toInstant(zone).toEpochMilli(), late.start)
        assertEquals(friday.plusDays(1).atTime(1, 0).toInstant(zone).toEpochMilli(), late.end)
        // Every week: part of your typical week, on that weekday.
        val swim = PlanDraft(null, friday, title = "Swimming", from = LocalTime.of(7, 0), to = LocalTime.of(8, 0), weekly = true).toEntry(blank, zone)!!
        assertEquals(EntryKind.ROUTINE, swim.kind)
        assertEquals(Weekly(listOf(5), 7 * 60, 8 * 60), swim.weekly)
        assertNull(swim.start)
        // Any time: a task for the day, never busy.
        val task = PlanDraft(null, friday, title = "Buy sencha", whenKind = PlanWhen.ANY, busy = true).toEntry(blank, zone)!!
        assertEquals("2026-11-06", task.day)
        assertEquals(false, task.allDay)
        assertEquals(false, task.busy)
        // Opened again, the card reads what it made.
        assertEquals(PlanWhen.ANY, PlanDraft.of(task, friday, zone).whenKind)
        assertEquals(LocalTime.of(22, 0), PlanDraft.of(late, friday, zone).from)
    }
}
