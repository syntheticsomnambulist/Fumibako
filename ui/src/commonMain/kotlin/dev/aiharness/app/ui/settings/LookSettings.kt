package dev.aiharness.app.ui.settings

import dev.aiharness.app.ui.paper.PaperChoice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.StepperRow
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.FrameStyle
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.Lamplight
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import dev.aiharness.core.theme.Sun
import dev.aiharness.core.theme.SunDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import dev.aiharness.app.platform.rememberApproximateLocation

/** How the theme switches between day and night. */
@Composable
fun LookDayNight(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val theme = s.theme
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) = update { it.copy(theme = transform(it.theme)) }

    Text("Day and night", style = PaperType.entryTitle, color = paper.sumi)
    val modes = listOf(DayNightMode.FIXED_HOURS to "Set hours", DayNightMode.SUN to "Sun", DayNightMode.STATIC to "Fixed")
    PaperChoice(modes, theme.dayNight, { mode -> updateTheme { it.copy(dayNight = mode) } })
    when (theme.dayNight) {
        DayNightMode.FIXED_HOURS -> {
            StepperRow("Day starts", theme.dayStartMinute / 60, { h -> updateTheme { it.copy(dayStartMinute = h * 60) } }, 0..23, describe = { "%02d:00".format(it) })
            StepperRow("Night starts", theme.dayEndMinute / 60, { h -> updateTheme { it.copy(dayEndMinute = h * 60) } }, 0..23, describe = { "%02d:00".format(it) })
        }
        DayNightMode.SUN -> SunLocation(theme, ::updateTheme)
        DayNightMode.STATIC -> {
            val seasons = listOf(Season.HARU to "春", Season.NATSU to "夏", Season.AKI to "秋", Season.FUYU to "冬")
            PaperChoice(seasons, theme.staticSeason, { season -> updateTheme { it.copy(staticSeason = season) } })
            val times = listOf(TimeOfDay.HIRU to "昼 day", TimeOfDay.YORU to "夜 night")
            PaperChoice(times, theme.staticTime, { time -> updateTheme { it.copy(staticTime = time) } })
        }
    }

}

/** Sticker outline, polaroid frames and mood words. */
@Composable
fun LookPaper(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val theme = s.theme
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) = update { it.copy(theme = transform(it.theme)) }

    Text("Paper and frames", style = PaperType.entryTitle, color = paper.sumi)
    Text("Polaroid frames", style = PaperType.label, color = paper.kasure2)
    val frames = listOf(FrameStyle.MATCH_OUTLINE to "Outline color", FrameStyle.PAPER to "Paper")
    PaperChoice(frames, theme.polaroidFrame, { style -> updateTheme { it.copy(polaroidFrame = style) } })
    Text("Lamplight at night", style = PaperType.label, color = paper.kasure2)
    val lamps = listOf(Lamplight.DIM_ALL to "Everything", Lamplight.KEEP_PHOTOS to "Not photos", Lamplight.NONE to "Off")
    PaperChoice(lamps, theme.lamplight, { l -> updateTheme { it.copy(lamplight = l) } })
    Text(
        "At night, stickers (and later photos and other things on the desk) keep their day colors, " + when (theme.lamplight) {
            Lamplight.DIM_ALL -> "dimmed and warmed as if by a desk lamp."
            Lamplight.KEEP_PHOTOS -> "dimmed and warmed as if by a desk lamp, except photos."
            Lamplight.NONE -> "just as they are."
        },
        style = PaperType.noteBody.copy(fontSize = 12.sp),
        color = paper.usuzumi0,
    )
    SwitchRow(
        "Mood words",
        s.moodTags,
        { v -> update { it.copy(moodTags = v) } },
        subtitle = "Personas choose a mood for each reply, shown beside their name.",
    )
}

@Composable
private fun SunLocation(theme: ThemeSettings, updateTheme: ((ThemeSettings) -> ThemeSettings) -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    fun save(latitude: Double, longitude: Double) {
        // About 10 km is plenty for sunrise times, and keeps the saved location coarse.
        val lat = (latitude * 10).roundToInt() / 10.0
        val lon = (longitude * 10).roundToInt() / 10.0
        updateTheme { it.copy(latitude = lat, longitude = lon, locationLabel = "Near $lat, $lon") }
        problem = null
    }
    val locate = rememberApproximateLocation(::save) { problem = it }

    val lat = theme.latitude
    val lon = theme.longitude
    if (lat != null && lon != null) {
        val today = LocalDate.now()
        val line = when (val sun = Sun.riseAndSet(today, lat, lon)) {
            is SunDay.Normal -> "Today: sunrise ${clock(sun.riseMillis)}, sunset ${clock(sun.setMillis)}."
            SunDay.PolarDay -> "Today the sun doesn't set."
            SunDay.PolarNight -> "Today the sun doesn't rise."
        }
        Text("${theme.locationLabel ?: "$lat, $lon"}. $line", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1)
    } else {
        Text("Needs a location. Until then the set hours are used.", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1)
    }
    if (locate != null) OutlinedButton(onClick = locate) { Text("Use my approximate location") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var latText by remember(lat) { mutableStateOf(lat?.toString().orEmpty()) }
        var lonText by remember(lon) { mutableStateOf(lon?.toString().orEmpty()) }
        dev.aiharness.app.ui.paper.IndexCardField("Latitude", latText, { v ->
            latText = v
            v.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { x -> updateTheme { it.copy(latitude = x, locationLabel = null) } }
        }, Modifier.weight(1f), singleLine = true, style = PaperType.noteBody)
        dev.aiharness.app.ui.paper.IndexCardField("Longitude", lonText, { v ->
            lonText = v
            v.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { x -> updateTheme { it.copy(longitude = x, locationLabel = null) } }
        }, Modifier.weight(1f), singleLine = true, style = PaperType.noteBody)
    }
    problem?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1) }
}

private fun clock(millis: Long): String = dev.aiharness.app.ui.planner.AppClock.time(millis)
