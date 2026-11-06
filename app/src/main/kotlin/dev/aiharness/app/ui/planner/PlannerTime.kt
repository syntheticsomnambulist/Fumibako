package dev.aiharness.app.ui.planner

import dev.aiharness.core.model.ClockStyle
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.planner.Occurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * The clock setting, app-wide: the theme keeps it current, and anything writing a time of day
 * (a letter's stamp, a sticker's slip, sunrise) reads it. It's snapshot state, so a change redraws.
 */
object AppClock {
    var format: ClockFormat by androidx.compose.runtime.mutableStateOf(ClockFormat(ClockStyle.PHONE, true))

    /** [millis] as a time of day in [zone], as the setting writes it. */
    fun time(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).let { format.time(it.hour * 60 + it.minute) }
}

/** Times as the clock setting writes them: "16:00", or "4:00 pm" and, on the week page's side, "4p". */
class ClockFormat(style: ClockStyle, phoneIs24: Boolean) {
    val is24: Boolean = when (style) {
        ClockStyle.H24 -> true
        ClockStyle.H12 -> false
        ClockStyle.PHONE -> phoneIs24
    }

    /** A time of day from minutes after midnight (1440 is midnight at the end of the day). */
    fun time(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return if (is24) "%d:%02d".format(h, m) else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "am" else "pm")
    }

    fun time(t: LocalTime): String = time(t.hour * 60 + t.minute)

    /** An hour's label at the side of the week page: "16", or "4p". */
    fun hour(h: Int): String = if (is24) "${h % 24}" else "${if (h % 12 == 0) 12 else h % 12}${if (h % 24 < 12) "a" else "p"}"
}

/** The planner's week: its days, from the week's first day. */
object PlannerDays {
    fun weekOf(day: LocalDate, startsSunday: Boolean): List<LocalDate> {
        val first = day.with(TemporalAdjusters.previousOrSame(if (startsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY))
        return (0L until 7L).map { first.plusDays(it) }
    }

    /** 月火水木金土日, as Japanese diaries write the days. */
    fun kanji(day: DayOfWeek): String = "月火水木金土日"[day.value - 1].toString()

    /** The months' traditional names, with what they mean, as the page titles them: 霜月, "frost month". */
    val months: List<Pair<String, String>> = listOf(
        "睦月" to "harmony month", "如月" to "layered-robes month", "弥生" to "new-growth month", "卯月" to "deutzia month",
        "皐月" to "rice-planting month", "水無月" to "water month", "文月" to "letters month", "葉月" to "leaf month",
        "長月" to "long-nights month", "神無月" to "gods' month", "霜月" to "frost month", "師走" to "running month",
    )

    /** Minutes after the start of [day] at [millis] (clamped to the day). */
    fun minuteOf(millis: Long, day: LocalDate, zone: ZoneId): Int {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return ((millis - start) / 60_000L).toInt().coerceIn(0, 24 * 60)
    }

    fun dayOf(millis: Long, zone: ZoneId): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone)
}

/**
 * Where each time of day sits down the week page: every hour [HOUR] tall, except the hours you're
 * asleep on every day shown, which (folded) shrink to a thin band of [FOLD] for each stretch. A
 * night shift's sleep folds mid-column the same way.
 */
class HourLayout(val folded: List<IntRange>, private val hour: Float = HOUR, private val fold: Float = FOLD) {
    /** Down the page at [minutes] after midnight. */
    fun y(minutes: Int): Float {
        var y = 0f
        var h = 0
        while (h < 24) {
            val band = folded.firstOrNull { h in it }
            if (band != null) {
                val span = band.last - band.first + 1
                val inBand = (minutes - band.first * 60).coerceIn(0, span * 60)
                if (minutes < (band.last + 1) * 60) return y + fold * inBand / (span * 60f)
                y += fold
                h = band.last + 1
            } else {
                if (minutes < (h + 1) * 60) return y + hour * (minutes - h * 60).coerceAtLeast(0) / 60f
                y += hour
                h++
            }
        }
        return y
    }

    val height: Float get() = y(24 * 60)

    fun isFolded(h: Int): Boolean = folded.any { h in it }

    companion object {
        const val HOUR = 22f
        const val FOLD = 16f

        /**
         * The hours you're asleep for the whole of on every one of [days], as stretches (0..6 for
         * midnight to seven), from the days' sleep occurrences.
         */
        fun asleepEveryDay(occurrences: List<Occurrence>, days: List<LocalDate>, zone: ZoneId): List<IntRange> {
            if (days.isEmpty()) return emptyList()
            val asleep = (0 until 24).filter { h ->
                days.all { d ->
                    occurrences.any { o ->
                        val start = o.start
                        val end = o.end
                        o.day == d && o.entry.routine == RoutineKind.SLEEP && start != null && end != null &&
                            PlannerDays.minuteOf(start, d, zone) <= h * 60 && PlannerDays.minuteOf(end, d, zone) >= (h + 1) * 60
                    }
                }
            }
            val runs = mutableListOf<IntRange>()
            for (h in asleep) {
                val last = runs.lastOrNull()
                if (last != null && last.last == h - 1) runs[runs.size - 1] = last.first..h else runs += h..h
            }
            return runs
        }
    }
}
