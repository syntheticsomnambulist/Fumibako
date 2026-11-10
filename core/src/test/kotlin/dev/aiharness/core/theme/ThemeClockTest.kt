package dev.aiharness.core.theme

import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeClockTest {
    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId = newYork) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun fixedHoursSwitchAtTheConfiguredTimes() {
        val settings = ThemeSettings()
        val morning = ThemeClock.choose(settings, at("2026-11-05T09:30"), newYork)
        assertEquals(Season.AKI, morning.season)
        assertEquals(TimeOfDay.HIRU, morning.time)
        assertEquals(at("2026-11-05T18:00"), morning.nextChangeAt)
        assertEquals("aki-hiru", morning.key)

        val evening = ThemeClock.choose(settings, at("2026-11-05T18:00"), newYork)
        assertEquals(TimeOfDay.YORU, evening.time)
        assertEquals(at("2026-11-06T00:00"), evening.nextChangeAt, "re-checks at midnight for the season")

        val early = ThemeClock.choose(settings, at("2026-11-06T05:59"), newYork)
        assertEquals(TimeOfDay.YORU, early.time)
        assertEquals(at("2026-11-06T06:00"), early.nextChangeAt)
    }

    @Test
    fun fixedHoursCanWrapPastMidnight() {
        val nightOwl = ThemeSettings(dayStartMinute = 10 * 60, dayEndMinute = 2 * 60)
        assertEquals(TimeOfDay.HIRU, ThemeClock.choose(nightOwl, at("2026-11-05T23:00"), newYork).time)
        assertEquals(TimeOfDay.HIRU, ThemeClock.choose(nightOwl, at("2026-11-06T01:30"), newYork).time)
        assertEquals(TimeOfDay.YORU, ThemeClock.choose(nightOwl, at("2026-11-06T03:00"), newYork).time)
    }

    @Test
    fun staticThemeNeverChanges() {
        val pinned = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.FUYU, staticTime = TimeOfDay.YORU)
        val choice = ThemeClock.choose(pinned, at("2026-07-01T12:00"), newYork)
        assertEquals("fuyu-yoru", choice.key)
        assertNull(choice.nextChangeAt)
    }

    @Test
    fun seasonsFollowTheCalendarAndFlipSouthOfTheEquator() {
        val utc = ZoneId.of("UTC")
        fun at(lat: Double?) = ThemeSettings(latitude = lat)
        assertEquals(Season.HARU, ThemeClock.seasonFor(LocalDate.of(2026, 3, 1), at(null), utc))
        assertEquals(Season.NATSU, ThemeClock.seasonFor(LocalDate.of(2026, 8, 31), at(40.0), utc))
        assertEquals(Season.FUYU, ThemeClock.seasonFor(LocalDate.of(2026, 12, 1), at(40.0), utc))
        assertEquals(Season.FUYU, ThemeClock.seasonFor(LocalDate.of(2026, 2, 28), at(null), utc))
        assertEquals(Season.FUYU, ThemeClock.seasonFor(LocalDate.of(2026, 7, 1), at(-33.9), utc))
        assertEquals(Season.HARU, ThemeClock.seasonFor(LocalDate.of(2026, 10, 1), at(-33.9), utc))
    }

    @Test
    fun sunriseAndSunsetMatchPublishedTimes() {
        // Edinburgh, 5 Nov 2026: sunrise about 07:27, sunset about 16:24 (GMT).
        val edinburgh = Sun.riseAndSet(LocalDate.of(2026, 11, 5), 55.9533, -3.1883) as SunDay.Normal
        assertNear(at("2026-11-05T07:27", london), edinburgh.riseMillis)
        assertNear(at("2026-11-05T16:24", london), edinburgh.setMillis)

        // Tokyo, 21 Jun 2026: sunrise about 04:25, sunset about 19:00 (JST).
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val tokyo = Sun.riseAndSet(LocalDate.of(2026, 6, 21), 35.6762, 139.6503) as SunDay.Normal
        assertNear(at("2026-06-21T04:25", tokyoZone), tokyo.riseMillis)
        assertNear(at("2026-06-21T19:00", tokyoZone), tokyo.setMillis)

        assertEquals(SunDay.PolarDay, Sun.riseAndSet(LocalDate.of(2026, 6, 21), 78.2, 15.6))
        assertEquals(SunDay.PolarNight, Sun.riseAndSet(LocalDate.of(2026, 12, 21), 78.2, 15.6))
    }

    @Test
    fun sunModeUsesTheLocationAndFallsBackToFixedHours() {
        val edinburgh = ThemeSettings(dayNight = DayNightMode.SUN, latitude = 55.9533, longitude = -3.1883)
        assertEquals(TimeOfDay.YORU, ThemeClock.choose(edinburgh, at("2026-11-05T06:00", london), london).time)
        assertEquals(TimeOfDay.HIRU, ThemeClock.choose(edinburgh, at("2026-11-05T08:00", london), london).time)
        val dusk = ThemeClock.choose(edinburgh, at("2026-11-05T17:00", london), london)
        assertEquals(TimeOfDay.YORU, dusk.time, "dark before the fixed 18:00 boundary")
        assertEquals(at("2026-11-06T00:00", london), dusk.nextChangeAt)

        val noLocation = ThemeSettings(dayNight = DayNightMode.SUN)
        assertEquals(TimeOfDay.HIRU, ThemeClock.choose(noLocation, at("2026-11-05T17:00"), newYork).time)
    }

    private fun assertNear(expected: Long, actual: Long, toleranceMinutes: Long = 5) {
        val diff = abs(expected - actual) / 60_000
        assertTrue(diff <= toleranceMinutes, "off by $diff minutes")
    }
}
