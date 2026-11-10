package dev.aiharness.app.ui.planner

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aiharness.app.platform.onRightClick
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.planner.Planner
import java.time.LocalDate
import java.time.ZoneId

/*
 * A day of the planner as a list in time order (docs/design/calendar.md, Third pass, "Day"): your
 * sleep folded at both ends, your typical week's bands (work, with a bracket down the margin from
 * "9:00 · WORK" to "17:30 · WORK ENDS" and a wash behind what's in between), your entries as cards,
 * the now line in beni, and what your personas wrote between them at its time. An entry that has
 * ended without a stamp shows its stamps under it ("How did it go?").
 */

/** One thing in the day's list, at [minute] after midnight. */
private sealed interface DayItem {
    val minute: Int

    class Entry(val o: Occurrence, override val minute: Int) : DayItem
    class Slip(val o: Occurrence, val persona: Persona, override val minute: Int) : DayItem
    class Now(override val minute: Int) : DayItem
    class Band(val o: Occurrence, override val minute: Int, val ends: Int, val items: List<DayItem>) : DayItem
}

/** What the day's list needs from the page. */
internal class DayActions(
    val open: (Occurrence) -> Unit,
    val stamp: (PlannerEntry, EntryState) -> Unit,
    val slip: @Composable (Occurrence, Persona) -> Unit,
)

@Composable
internal fun DayList(
    day: LocalDate,
    today: LocalDate,
    now: Long,
    yours: List<Occurrence>,
    theirs: List<Occurrence>,
    personas: List<Persona>,
    clock: ClockFormat,
    zone: ZoneId,
    actions: DayActions,
) {
    val colors = paper
    val minuteOf = { millis: Long -> PlannerDays.minuteOf(millis, day, zone) }
    val timeWidth = if (clock.is24) 42.dp else 62.dp
    val sleeps = yours.filter { it.entry.kind == EntryKind.ROUTINE && it.entry.routine == RoutineKind.SLEEP && it.start != null }
    val morning = sleeps.filter { minuteOf(it.start!!) == 0 }
    val evening = sleeps - morning.toSet()
    val bands = yours.filter { it.entry.kind == EntryKind.ROUTINE && it.entry.routine != RoutineKind.SLEEP && it.start != null }.sortedBy { it.start }
    val plans = yours.filter { it.entry.kind != EntryKind.ROUTINE }
    val allDay = plans.filter { it.start == null && it.entry.allDay }
    val anyTime = plans.filter { it.start == null && !it.entry.allDay }
    val away = theirs.filter { it.entry.kind == EntryKind.AWAY }
    val slipsUntimed = theirs.filter { it.entry.kind != EntryKind.AWAY && it.start == null }
    val personaOf = { o: Occurrence -> personas.firstOrNull { it.id == o.entry.byPersonaId } }

    // What's at a time: your plans and the phone's, what your personas wrote, and now.
    val loose = buildList<DayItem> {
        plans.filter { it.start != null }.forEach { add(DayItem.Entry(it, minuteOf(it.start!!))) }
        theirs.filter { it.entry.kind != EntryKind.AWAY && it.start != null }.forEach { o -> personaOf(o)?.let { add(DayItem.Slip(o, it, minuteOf(o.start!!))) } }
        if (day == today) add(DayItem.Now(minuteOf(now)))
    }.sortedBy { it.minute }.toMutableList()
    // Your typical week's bands take in what falls inside them.
    val timed = mutableListOf<DayItem>()
    for (b in bands) {
        val from = minuteOf(b.start!!)
        val to = b.end?.let(minuteOf)?.takeIf { it > from } ?: 24 * 60
        val inside = loose.filter { it.minute in from until to }
        loose.removeAll(inside)
        timed += DayItem.Band(b, from, to, inside)
    }
    timed += loose
    timed.sortBy { it.minute }
    val eveningFrom = evening.minOfOrNull { minuteOf(it.start!!) } ?: 24 * 60
    val before = timed.filter { it.minute < eveningFrom }
    val after = timed.filter { it.minute >= eveningFrom }

    Column(Modifier.fillMaxWidth()) {
        morning.forEach { SleepBand(it, "${clock.time(0)}–${clock.time(it.end?.let(minuteOf) ?: 0)} asleep", actions) }
        away.forEach { o -> personaOf(o)?.let { p -> Box(Modifier.padding(top = 6.dp)) { actions.slip(o, p) } } }
        allDay.forEach { EntryRow(it, "all day", null, day, today, now, clock, zone, timeWidth, actions) }
        before.forEach { DayItemRow(it, day, today, now, clock, zone, timeWidth, actions) }
        if (plans.isEmpty()) {
            Text("Nothing planned yet.", style = handText(15.sp), color = colors.usuzumi1, modifier = Modifier.padding(start = timeWidth + 10.dp, top = 10.dp, bottom = 6.dp))
        }
        anyTime.forEach { AnyTimeRow(it, timeWidth, actions) }
        evening.forEach { SleepBand(it, "${clock.time(minuteOf(it.start!!))} asleep", actions) }
        after.forEach { DayItemRow(it, day, today, now, clock, zone, timeWidth, actions) }
        slipsUntimed.forEach { o -> personaOf(o)?.let { p -> Box(Modifier.padding(top = 6.dp)) { actions.slip(o, p) } } }
    }
}

@Composable
private fun DayItemRow(item: DayItem, day: LocalDate, today: LocalDate, now: Long, clock: ClockFormat, zone: ZoneId, timeWidth: Dp, actions: DayActions) {
    when (item) {
        is DayItem.Entry -> EntryRow(item.o, clock.time(item.minute), item.o.end?.takeIf { it != item.o.start }?.let { clock.time(PlannerDays.minuteOf(it, day, zone)) }, day, today, now, clock, zone, timeWidth, actions)
        is DayItem.Slip -> Box(Modifier.padding(start = timeWidth + 10.dp, top = 6.dp, bottom = 6.dp)) { actions.slip(item.o, item.persona) }
        is DayItem.Now -> NowLine(clock.time(item.minute), timeWidth)
        is DayItem.Band -> RoutineBand(item, day, today, now, clock, zone, timeWidth, actions)
    }
}

/** Sleep, folded to a hatched band ("0:00–7:00 asleep"); a tap opens it in your typical week. */
@Composable
private fun SleepBand(o: Occurrence, words: String, actions: DayActions) {
    val colors = paper
    Row(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(24.dp)
            .hatched(colors.nijimi1.copy(alpha = 0.7f))
            .clickable(role = Role.Button, onClickLabel = "Open your typical week's sleep") { actions.open(o) }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(words, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.sp), color = colors.usuzumi1)
    }
}

/** A row's time, in the margin. */
@Composable
private fun TimeCell(words: String, width: Dp, color: Color = paper.usuzumi1) {
    Text(words, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.sp, lineHeight = 18.sp), color = color, maxLines = 1, softWrap = false, modifier = Modifier.width(width))
}

private val SMALL_CAPS = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.sp, lineHeight = 18.sp, letterSpacing = 0.08.em)

/** One of your typical week's bands: its start and end as rows, what's inside between them on a wash, a bracket down the margin when it makes you busy. */
@Composable
private fun RoutineBand(band: DayItem.Band, day: LocalDate, today: LocalDate, now: Long, clock: ClockFormat, zone: ZoneId, timeWidth: Dp, actions: DayActions) {
    val colors = paper
    val e = band.o.entry
    val what = e.title.ifBlank { if (e.routine == RoutineKind.WORK) "Work" else "Routine" }
    val busy = Planner.isBusy(e)
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .drawBehind {
                val half = 16.dp.toPx()
                // Just clear of the times, so its ticks end before the band's words.
                val left = (timeWidth + 3.dp).toPx()
                busyHours(left, half, size.width - left, size.height - half, bracket = busy)
            },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open your typical week's $what") { actions.open(band.o) }.line(colors.nijimi0).padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimeCell(clock.time(band.minute), timeWidth)
            Text(listOfNotNull(what.uppercase(), "YOUR TYPICAL WEEK").joinToString(" · "), style = SMALL_CAPS, color = colors.usuzumi1)
        }
        band.items.forEach { DayItemRow(it, day, today, now, clock, zone, timeWidth, actions) }
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeCell(clock.time(band.ends), timeWidth)
            Text("${what.uppercase()} ENDS", style = SMALL_CAPS, color = colors.usuzumi1)
        }
    }
}

/** A faint line under a row. */
private fun Modifier.line(color: Color): Modifier = drawBehind { drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }

/** Now, on today's list: its time in beni and a line across with a dot at its start. */
@Composable
private fun NowLine(words: String, timeWidth: Dp) {
    val red = beni()
    Row(
        Modifier.fillMaxWidth().height(22.dp).semantics(mergeDescendants = true) { contentDescription = "Now, $words" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeCell(words, timeWidth, red)
        Box(
            Modifier.weight(1f).height(7.dp).drawBehind {
                val y = size.height / 2
                drawLine(red, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                drawCircle(red, 3.5.dp.toPx(), Offset(0f, y))
            },
        )
    }
}

/**
 * One of your entries as a card: in your hand, a plan of yours with the highlighter across its
 * name, one from the phone's calendar pencilled in. Its stamp lands on the card; ended without
 * one, its stamps wait under it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(o: Occurrence, start: String, end: String?, day: LocalDate, today: LocalDate, now: Long, clock: ClockFormat, zone: ZoneId, timeWidth: Dp, actions: DayActions) {
    val colors = paper
    val ink = stationery.you
    val e = o.entry
    val pencil = e.phone != null
    val stamp = stampOf(e)
    val ended = if (o.start == null) day < today else (o.end ?: o.start ?: Long.MAX_VALUE) < now
    val stamped = e.state == EntryState.DONE || e.state == EntryState.CANCELLED
    val title = e.title.ifBlank { if (pencil) "Busy" else "A plan" }
    val meta = listOfNotNull(
        end?.let { "UNTIL $it" },
        e.place?.uppercase(),
        e.phone?.let { "FROM YOUR ${it.uppercase()}" + if (it.contains("calendar", ignoreCase = true)) "" else " CALENDAR" },
        "BUSY".takeIf { Planner.isBusy(e) && !pencil },
        "MOVED".takeIf { e.movedFrom != null },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().line(colors.nijimi0).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeCell(start, timeWidth)
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (pencil) Modifier.pencilled(graphiteLine(), faded = e.state == EntryState.CANCELLED) else Modifier)
                    .combinedClickable(onClickLabel = "Open $title", onLongClickLabel = "Stamp it done", onLongClick = { actions.stamp(e, EntryState.DONE) }) { actions.open(o) }
                    .onRightClick { actions.stamp(e, EntryState.DONE) },
            ) {
                Column(Modifier.padding(start = if (pencil) 11.dp else 0.dp, end = 44.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = handText(17.sp, 21.sp, crossed = e.state == EntryState.CANCELLED),
                        color = if (pencil) graphite() else lerp(ink, colors.sumi, 0.45f),
                        modifier = if (pencil) Modifier else Modifier.highlighter(ink, down = false, faded = e.state == EntryState.CANCELLED).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    if (meta.isNotEmpty()) Text(meta, style = SMALL_CAPS, color = colors.usuzumi1)
                }
                // Its stamp lands on the card.
                if (stamp != null) {
                    Stamp(stamp, if (e.state == EntryState.CANCELLED) beni() else lerp(ink, colors.sumi, 0.2f), Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp), size = 30.dp, fresh = e.updatedAt > now - 1_500)
                }
            }
            when {
                stamped -> Text(
                    "Lift the stamp",
                    style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp, textDecoration = TextDecoration.Underline),
                    color = lerp(ink, colors.sumi, 0.3f),
                    modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClickLabel = "Lift the stamp from $title") { actions.stamp(e, e.state) }.padding(vertical = 8.dp),
                )
                ended -> FlowRow(
                    Modifier.padding(top = 6.dp).semantics { contentDescription = "Stamp $title" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("How did it go?", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp), color = colors.usuzumi1)
                    StampChoice("済", "Done", lerp(ink, colors.sumi, 0.2f)) { actions.stamp(e, EntryState.DONE) }
                    StampChoice("止", if (pencil) "Not going" else "Didn't happen", beni()) { actions.stamp(e, EntryState.CANCELLED) }
                }
            }
        }
    }
}

/** One stamp to choose under an ended entry: the stamp itself, and its word. */
@Composable
private fun StampChoice(glyph: String, word: String, ink: Color, onClick: () -> Unit) {
    val colors = paper
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClickLabel = "Stamp it ${word.lowercase()}", onClick = onClick)
            .padding(start = 5.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(20.dp).border(1.2.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
            Text(glyph, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 11.sp, lineHeight = 12.sp), color = ink)
        }
        Text(word, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp), color = colors.sumi)
    }
}

/** A plan with no time: a box to tick in your ink and its name in your hand; a tap stamps it done, a long press opens it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnyTimeRow(o: Occurrence, timeWidth: Dp, actions: DayActions) {
    val colors = paper
    val ink = stationery.you
    val e = o.entry
    val done = e.state == EntryState.DONE
    Row(
        Modifier
            .fillMaxWidth()
            .line(colors.nijimi0)
            .combinedClickable(onClickLabel = if (done) "Lift the done stamp" else "Stamp it done", onLongClickLabel = "Open ${e.title}", onLongClick = { actions.open(o) }) { actions.stamp(e, EntryState.DONE) }
            .onRightClick { actions.open(o) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeCell("any time", timeWidth)
        PlanTick(done, ink)
        Text(e.title.ifBlank { "A plan" }, style = handText(18.sp, 20.sp, crossed = e.state == EntryState.CANCELLED), color = lerp(ink, colors.sumi, 0.45f), modifier = Modifier.weight(1f, fill = false))
        stampOf(e)?.let { Stamp(it, ink, size = 22.dp) }
    }
}
