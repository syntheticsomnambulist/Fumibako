package dev.aiharness.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.HolidaySettings
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.planner.Holidays
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.seasons.Seasons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as DayText
import java.util.Locale

/** The month's days as the page lays them out: whole weeks, from the week's first day, with the days either side. */
object MonthDays {
    fun of(month: YearMonth, startsSunday: Boolean): List<LocalDate> {
        val first = PlannerDays.weekOf(month.atDay(1), startsSunday).first()
        val last = PlannerDays.weekOf(month.atEndOfMonth(), startsSunday).last()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
}

/**
 * The month page (月): the traditional month name and a grid of its days. Each day shows its date (Saturday blue, Sunday and public holidays red, today ringed),
 * a term's name on its first day, a holiday's name or its first plan, small ink marks for who has
 * written there, and 済 when everything on it is stamped. A tap opens the day; stickers stuck on
 * a day show here too.
 */
@Composable
fun MonthPage(
    month: YearMonth,
    today: LocalDate,
    startsSunday: Boolean,
    occurrences: List<Occurrence>,
    holidays: HolidaySettings,
    seasonBand: Boolean,
    zone: ZoneId,
    hemisphere: Hemisphere,
    personas: List<Persona>,
    plannerId: String?,
    stickers: PlannerStickers,
    onDay: (LocalDate) -> Unit,
    onHolidaysSetup: () -> Unit,
) {
    val colors = paper
    val ink = stationery.you
    val night = stationery.isNight
    val days = MonthDays.of(month, startsSunday)
    val weekdays = days.take(7).map { it.dayOfWeek }
    // A solar term's name on its first day, when the seasons are shown.
    val terms = if (seasonBand) Seasons.termsBeginning(days.first(), days.last(), zone, hemisphere).toMap() else emptyMap()
    val byDay = occurrences.groupBy { it.day }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 18.dp, end = 30.dp, top = 6.dp)) {
            val col: Dp = maxWidth / 7
            Column {
                // The days of the week: kanji, with the English under them.
                Row(Modifier.fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }) {
                    weekdays.forEach { d ->
                        val c = dayInk(d, colors.sumi, night)
                        Column(Modifier.width(col).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(PlannerDays.kanji(d), style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 13.sp, lineHeight = 14.sp), color = c)
                            Text(
                                d.getDisplayName(DayText.SHORT, Locale.US).uppercase(),
                                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 7.5.sp, letterSpacing = 0.08.em),
                                color = if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) c else colors.usuzumi1,
                            )
                        }
                    }
                }
                days.chunked(7).forEach { week ->
                    // A week holding the sticker picked or moved comes to the front (its square does within it).
                    val lifted = plannerId != null && week.any { stickers.lifts(StickerTarget.day(plannerId, it.toString())) }
                    Row(Modifier.zIndex(if (lifted) 1f else 0f)) {
                        week.forEach { day ->
                            DayCell(
                                day = day, inMonth = YearMonth.from(day) == month, today = day == today, col = col,
                                occurrences = byDay[day].orEmpty(), holidays = Holidays.on(holidays, day).map { it.name },
                                term = terms[day]?.kanji, personas = personas, plannerId = plannerId, stickers = stickers,
                                onClick = { onDay(day) },
                            )
                        }
                    }
                }
            }
        }
        MonthLegend(occurrences, personas, holidays, onHolidaysSetup)
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    today: Boolean,
    col: Dp,
    occurrences: List<Occurrence>,
    holidays: List<String>,
    term: String?,
    personas: List<Persona>,
    plannerId: String?,
    stickers: PlannerStickers,
    onClick: () -> Unit,
) {
    val colors = paper
    val sheet = stationery
    val ink = sheet.you
    val holidayRed = dayInk(DayOfWeek.SUNDAY, colors.sumi, sheet.isNight)
    val numberColor = when {
        !inMonth -> colors.kasure2
        today -> ink
        holidays.isNotEmpty() -> holidayRed
        else -> dayInk(day.dayOfWeek, colors.sumi, sheet.isNight)
    }
    val plans = occurrences.filter { it.entry.byPersonaId == null && it.entry.kind == EntryKind.PLAN }
    val first = plans.firstOrNull { it.entry.state != EntryState.CANCELLED }
    val writers = occurrences.mapNotNull { it.entry.byPersonaId }.distinct().mapNotNull { id -> personas.firstOrNull { it.id == id } }
    val allDone = plans.isNotEmpty() && plans.all { it.entry.state == EntryState.DONE || it.entry.state == EntryState.CANCELLED }
    val label = buildString {
        append(dayName(day))
        holidays.forEach { append(", $it") }
        term?.let { append(", ${Seasons.TERMS.first { t -> t.kanji == it }.english.lowercase()} ($it)") }
        if (plans.isNotEmpty()) append(", ${plans.size} ${if (plans.size == 1) "plan" else "plans"}")
        if (allDone) append(", all stamped done")
    }
    val cell: @Composable () -> Unit = {
        Box(
            Modifier
                .size(col, 64.dp)
                .background(
                    when {
                        !inMonth -> colors.chitsu.copy(alpha = 0.35f)
                        today -> ink.copy(alpha = 0.07f)
                        else -> Color.Transparent
                    },
                )
                .drawBehind {
                    val line = colors.nijimi0
                    drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                }
                .clickable(role = Role.Button, onClickLabel = "Open ${dayName(day)}", onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            Box(
                Modifier.padding(start = 3.dp, top = 2.dp).size(22.dp)
                    .then(if (today) Modifier.border(1.5.dp, ink, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text("${day.dayOfMonth}", style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 15.sp), color = numberColor)
            }
            term?.let {
                Text(it, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 8.5.sp, lineHeight = 9.sp), color = holidayRed, modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp))
            }
            // A holiday's name, else the day's first plan, under the date.
            val under = holidays.firstOrNull()
            if (under != null) {
                Text(
                    under, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 7.5.sp, lineHeight = 8.5.sp), color = holidayRed,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 3.dp, end = 2.dp, top = 26.dp),
                )
            } else if (first != null && inMonth) {
                Text(
                    first.entry.title, style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 8.5.sp, lineHeight = 9.sp),
                    color = lerp(ink, colors.sumi, 0.35f), maxLines = 1, overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 3.dp, end = 2.dp, top = 27.dp),
                )
            }
            // Who has written here: you, and each persona, in their inks.
            Row(Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (plans.isNotEmpty()) Box(Modifier.size(6.dp).background(ink, CircleShape))
                writers.take(3).forEach { p -> Box(Modifier.size(6.dp).background(sheet.ink(p.ink), CircleShape)) }
            }
            if (allDone) Stamp("済", ink, Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 3.dp), size = 16.dp)
        }
    }
    if (plannerId != null) {
        // A day's stickers, smaller on the month's small squares than on the week's tall columns.
        val look = dev.aiharness.app.ui.stickers.LocalStickerSettings.current
        androidx.compose.runtime.CompositionLocalProvider(dev.aiharness.app.ui.stickers.LocalStickerSettings provides look.copy(size = dev.aiharness.core.model.StickerSize.SMALL)) {
            stickers.On(StickerTarget.day(plannerId, day.toString())) { cell() }
        }
    } else {
        cell()
    }
}

/** Under the grid: what the marks mean, and (until they're set up) a way to show public holidays. */
@Composable
private fun MonthLegend(occurrences: List<Occurrence>, personas: List<Persona>, holidays: HolidaySettings, onHolidaysSetup: () -> Unit) {
    val colors = paper
    val sheet = stationery
    val ink = sheet.you
    val red = dayInk(DayOfWeek.SUNDAY, colors.sumi, sheet.isNight)
    val writers = occurrences.mapNotNull { it.entry.byPersonaId }.distinct().mapNotNull { id -> personas.firstOrNull { it.id == id } }
    val small = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 9.5.sp)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(start = 24.dp, end = 30.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(ink, CircleShape)); Text("  Yours", style = small, color = colors.sumi) }
        writers.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(sheet.ink(p.ink), CircleShape)); Text("  ${p.name}", style = small, color = colors.sumi) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("済", style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 10.sp), color = ink); Text("  All stamped done", style = small, color = colors.sumi) }
        if (holidays.show && holidays.country != null) {
            val where = java.util.Locale("", holidays.country).getDisplayCountry(Locale.US)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("11", style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 12.sp), color = red)
                Text("  Public holiday ($where)", style = small, color = colors.sumi)
            }
        }
    }
    if (!holidays.show) {
        Text(
            "Show public holidays",
            style = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 13.sp),
            color = ink,
            modifier = Modifier.padding(start = 18.dp, top = 10.dp).clickable(role = Role.Button, onClickLabel = "Choose whose public holidays to show", onClick = onHolidaysSetup).padding(6.dp),
        )
    }
}

/** "1st", "2nd", "22nd". */
fun ordinal(n: Int): String = "$n" + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}
