package dev.aiharness.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.aiharness.app.ui.NumberField
import dev.aiharness.app.ui.SectionTitle
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.parseHex
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.FrameStyle
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import dev.aiharness.core.theme.Sun
import dev.aiharness.core.theme.SunDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val OutlinePresets = listOf("White" to "#FFFFFF", "Cream" to "#FFF4E0", "Ink" to "#3B2F27")

/** Theme, polaroid frames, sticker outline and mood words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookSection(s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val theme = s.theme
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) = update { it.copy(theme = transform(it.theme)) }

    SectionTitle("Look")
    Text("Day and night", style = PaperType.label, color = paper.kasure2)
    val modes = listOf(DayNightMode.FIXED_HOURS to "Set hours", DayNightMode.SUN to "Sun", DayNightMode.STATIC to "Fixed theme")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(selected = theme.dayNight == mode, onClick = { updateTheme { it.copy(dayNight = mode) } }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) {
                Text(label)
            }
        }
    }
    when (theme.dayNight) {
        DayNightMode.FIXED_HOURS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Day starts (hour)", theme.dayStartMinute / 60, { v ->
                v.toIntOrNull()?.takeIf { it in 0..23 }?.let { h -> updateTheme { it.copy(dayStartMinute = h * 60) } }
            }, Modifier.weight(1f))
            NumberField("Night starts (hour)", theme.dayEndMinute / 60, { v ->
                v.toIntOrNull()?.takeIf { it in 0..23 }?.let { h -> updateTheme { it.copy(dayEndMinute = h * 60) } }
            }, Modifier.weight(1f))
        }
        DayNightMode.SUN -> SunLocation(theme, ::updateTheme)
        DayNightMode.STATIC -> {
            val seasons = listOf(Season.HARU to "春", Season.NATSU to "夏", Season.AKI to "秋", Season.FUYU to "冬")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                seasons.forEachIndexed { i, (season, label) ->
                    SegmentedButton(selected = theme.staticSeason == season, onClick = { updateTheme { it.copy(staticSeason = season) } }, shape = SegmentedButtonDefaults.itemShape(i, seasons.size)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            val times = listOf(TimeOfDay.HIRU to "昼 day", TimeOfDay.YORU to "夜 night")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                times.forEachIndexed { i, (time, label) ->
                    SegmentedButton(selected = theme.staticTime == time, onClick = { updateTheme { it.copy(staticTime = time) } }, shape = SegmentedButtonDefaults.itemShape(i, times.size)) {
                        Text(label)
                    }
                }
            }
        }
    }

    Text("Sticker outline", style = PaperType.label, color = paper.kasure2)
    Text("Stickers arrive in the next version; the outline colour also frames polaroids.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinePresets.forEach { (label, hex) ->
            val on = theme.outlineColor.equals(hex, ignoreCase = true)
            Column(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .selectable(on, role = Role.RadioButton) { updateTheme { it.copy(outlineColor = hex) } }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .border(if (on) 2.dp else 1.dp, if (on) paper.sumi else paper.nijimi1, CircleShape)
                        .clip(CircleShape)
                        .background(parseHex(hex) ?: paper.kami),
                )
                Text(label, style = PaperType.meta, color = paper.usuzumi1)
            }
        }
        var custom by remember(theme.outlineColor) { mutableStateOf(theme.outlineColor) }
        OutlinedTextField(
            custom,
            { v ->
                custom = v
                if (parseHex(v) != null) updateTheme { it.copy(outlineColor = v.trim().uppercase().let { h -> if (h.startsWith("#")) h else "#$h" }) }
            },
            label = { Text("Hex") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Text("Polaroid frames", style = PaperType.label, color = paper.kasure2)
    val frames = listOf(FrameStyle.MATCH_OUTLINE to "Match sticker outline", FrameStyle.PAPER to "Paper")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        frames.forEachIndexed { i, (style, label) ->
            SegmentedButton(selected = theme.polaroidFrame == style, onClick = { updateTheme { it.copy(polaroidFrame = style) } }, shape = SegmentedButtonDefaults.itemShape(i, frames.size)) {
                Text(label)
            }
        }
    }
    SwitchRow(
        "Mood words",
        s.moodTags,
        { v -> update { it.copy(moodTags = v) } },
        subtitle = "Personas choose a mood for each reply, shown beside their name.",
    )
}

@Composable
private fun SunLocation(theme: ThemeSettings, updateTheme: ((ThemeSettings) -> ThemeSettings) -> Unit) {
    val context = LocalContext.current
    var problem by remember { mutableStateOf<String?>(null) }
    fun save(location: Location) {
        // About 10 km is plenty for sunrise times, and keeps the saved location coarse.
        val lat = (location.latitude * 10).roundToInt() / 10.0
        val lon = (location.longitude * 10).roundToInt() / 10.0
        updateTheme { it.copy(latitude = lat, longitude = lon, locationLabel = "Near $lat, $lon") }
        problem = null
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            fetchCoarseLocation(context, ::save) { problem = it }
        } else {
            problem = "Location permission declined. You can type a latitude and longitude instead."
        }
    }

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
    OutlinedButton(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCoarseLocation(context, ::save) { problem = it }
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }) { Text("Use my approximate location") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var latText by remember(lat) { mutableStateOf(lat?.toString().orEmpty()) }
        var lonText by remember(lon) { mutableStateOf(lon?.toString().orEmpty()) }
        OutlinedTextField(latText, { v ->
            latText = v
            v.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { x -> updateTheme { it.copy(latitude = x, locationLabel = null) } }
        }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(lonText, { v ->
            lonText = v
            v.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { x -> updateTheme { it.copy(longitude = x, locationLabel = null) } }
        }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    problem?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1) }
}

/** The freshest last-known coarse location, or a one-off fix on Android 11+. No Play services needed. */
private fun fetchCoarseLocation(context: Context, onLocation: (Location) -> Unit, onProblem: (String) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return onProblem("No location service on this device.")
    try {
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null) return onLocation(known)
        if (Build.VERSION.SDK_INT >= 30 && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ContextCompat.getMainExecutor(context)) { location ->
                if (location != null) onLocation(location) else onProblem("Couldn't get a location fix. Try again, or type it in.")
            }
        } else {
            onProblem("Couldn't get a location fix. Try again, or type it in.")
        }
    } catch (e: SecurityException) {
        onProblem("Location permission is needed for sunrise and sunset.")
    }
}

private fun clock(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}
