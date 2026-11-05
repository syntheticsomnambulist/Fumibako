package dev.aiharness.core.theme

import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Which of the eight madoromi themes to show, and when to check again. */
data class ThemeChoice(val season: Season, val time: TimeOfDay, val nextChangeAt: Long?) {
    /** The palette key, e.g. `haru-hiru`. */
    val key: String get() = "${season.name.lowercase()}-${time.name.lowercase()}"
}

/** Picks the theme from the date, the clock and [ThemeSettings]. Pure, so it is easy to test. */
object ThemeClock {

    fun choose(settings: ThemeSettings, nowMillis: Long, zone: ZoneId): ThemeChoice {
        if (settings.dayNight == DayNightMode.STATIC) return ThemeChoice(settings.staticSeason, settings.staticTime, null)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val season = seasonFor(today, settings.latitude)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val lat = settings.latitude
        val lon = settings.longitude
        if (settings.dayNight == DayNightMode.SUN && lat != null && lon != null) {
            val todaySun = Sun.riseAndSet(today, lat, lon)
            val (time, boundary) = when (todaySun) {
                is SunDay.PolarDay -> TimeOfDay.HIRU to null
                is SunDay.PolarNight -> TimeOfDay.YORU to null
                is SunDay.Normal -> when {
                    nowMillis < todaySun.riseMillis -> TimeOfDay.YORU to todaySun.riseMillis
                    nowMillis < todaySun.setMillis -> TimeOfDay.HIRU to todaySun.setMillis
                    else -> TimeOfDay.YORU to (Sun.riseAndSet(today.plusDays(1), lat, lon) as? SunDay.Normal)?.riseMillis
                }
            }
            return ThemeChoice(season, time, minOf(boundary ?: nextMidnight, nextMidnight))
        }

        val minute = now.hour * 60 + now.minute
        val start = settings.dayStartMinute.mod(MINUTES_PER_DAY)
        val end = settings.dayEndMinute.mod(MINUTES_PER_DAY)
        val day = when {
            start == end -> true
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }
        val nextBoundary = if (start == end) {
            null
        } else {
            fun minutesUntil(target: Int) = (target - minute).mod(MINUTES_PER_DAY).let { if (it == 0) MINUTES_PER_DAY else it }
            val ahead = minOf(minutesUntil(start), minutesUntil(end))
            now.withSecond(0).withNano(0).plusMinutes(ahead.toLong()).toInstant().toEpochMilli()
        }
        return ThemeChoice(season, if (day) TimeOfDay.HIRU else TimeOfDay.YORU, minOf(nextBoundary ?: nextMidnight, nextMidnight))
    }

    /** Meteorological seasons, flipped south of the equator. */
    fun seasonFor(date: LocalDate, latitude: Double?): Season {
        val month = if (latitude != null && latitude < 0) (date.monthValue + 5) % 12 + 1 else date.monthValue
        return when (month) {
            3, 4, 5 -> Season.HARU
            6, 7, 8 -> Season.NATSU
            9, 10, 11 -> Season.AKI
            else -> Season.FUYU
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

sealed interface SunDay {
    data class Normal(val riseMillis: Long, val setMillis: Long) : SunDay
    data object PolarDay : SunDay
    data object PolarNight : SunDay
}

/** Sunrise and sunset from the standard sunrise equation (accurate to a minute or two). No network. */
object Sun {
    fun riseAndSet(date: LocalDate, latitude: Double, longitude: Double): SunDay {
        // Days since J2000 noon, from the Julian date at the start (00:00 UTC) of the date.
        val n = ceil(date.toEpochDay() + UNIX_EPOCH_JULIAN - J2000 + 0.0008)
        val meanSolarTime = n - longitude / 360.0
        val m = norm(357.5291 + 0.98560028 * meanSolarTime)
        val c = 1.9148 * sinD(m) + 0.0200 * sinD(2 * m) + 0.0003 * sinD(3 * m)
        val lambda = norm(m + c + 180.0 + 102.9372)
        val transit = J2000 + meanSolarTime + 0.0053 * sinD(m) - 0.0069 * sinD(2 * lambda)
        val sinDecl = sinD(lambda) * sinD(23.4397)
        val cosDecl = cos(asinSafe(sinDecl))
        val cosHourAngle = (sinD(-0.833) - sinD(latitude) * sinDecl) / (cosD(latitude) * cosDecl)
        if (cosHourAngle > 1) return SunDay.PolarNight
        if (cosHourAngle < -1) return SunDay.PolarDay
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        return SunDay.Normal(toMillis(transit - hourAngle / 360.0), toMillis(transit + hourAngle / 360.0))
    }

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JULIAN = 2440587.5

    private fun toMillis(julian: Double) = ((julian - UNIX_EPOCH_JULIAN) * 86_400_000.0).toLong()
    private fun norm(deg: Double) = deg.mod(360.0)
    private fun sinD(deg: Double) = sin(Math.toRadians(deg))
    private fun cosD(deg: Double) = cos(Math.toRadians(deg))
    private fun asinSafe(x: Double) = kotlin.math.asin(x.coerceIn(-1.0, 1.0))
}
