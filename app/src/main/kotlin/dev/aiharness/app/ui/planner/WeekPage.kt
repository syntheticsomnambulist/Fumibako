package dev.aiharness.app.ui.planner

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.planner.Occurrence
import java.time.LocalDate
import java.time.format.TextStyle as DayText
import java.util.Locale

/** What's picked on the week page: an entry's stamps, or the days to move it to. */
private data class Picked(val entryId: String, val day: LocalDate, val moving: Boolean = false)

/**
 * The 帳 Planner tab's week (docs/design/calendar.md on the design branch, "Second pass"): a
 * バーチカル page of seven columns and every hour, your sleep folded to a thin band, your typical
 * week printed faintly, your plans in highlighter. Tap a plan for its stamps (済 done, 延 move,
 * 止 cancel, its card); tap an empty part of a day to open it. Below, the week's untimed plans.
 */
@Composable
fun PlannerScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val shown by vm.shownDay.collectAsStateWithLifecycle()
    val seenBy by vm.seenBy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clock = remember(settings.theme.clock) { ClockFormat(settings.theme.clock, DateFormat.is24HourFormat(context)) }
    val days = remember(shown, settings.planner.weekStartsSunday) { PlannerDays.weekOf(shown, settings.planner.weekStartsSunday) }
    val occurrences = remember(entries, days) { vm.occurrences(entries, days.first(), days.last()) }
    var unfolded by rememberSaveable { mutableStateOf(false) }
    val folded = if (settings.planner.foldSleep && !unfolded) HourLayout.asleepEveryDay(occurrences, days, vm.zone) else emptyList()
    val layout = remember(folded) { HourLayout(folded) }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var card by remember { mutableStateOf<PlanDraft?>(null) }
    val colors = paper
    val ink = stationery.you
    val today = vm.today()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Open at the current hour, a little above it.
    LaunchedEffect(layout) {
        val now = PlannerDays.minuteOf(vm.now(), today, vm.zone)
        scroll.scrollTo(with(density) { (layout.y(now).dp - 90.dp).coerceAtLeast(0.dp).roundToPx() })
    }

    Box(Modifier.fillMaxSize().background(colors.kami).dotGrid(colors.sumi.copy(alpha = 0.12f))) {
        Column(Modifier.fillMaxSize().stitched(lerp(colors.nijimi2, colors.kami, 0.2f)).statusBarsPadding()) {
            WeekHeader(days, onPrevious = { vm.shownDay.value = shown.minusWeeks(1); picked = null }, onNext = { vm.shownDay.value = shown.plusWeeks(1); picked = null })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 18.dp, end = 6.dp)) {
                val col = ((maxWidth - GUTTER) / 7).coerceAtLeast(36.dp)
                Column(Modifier.fillMaxSize()) {
                    // The days and the all-day row stay put while the hours scroll.
                    DayHeads(days, today, col) { day -> nav.navigate(Routes.plannerDay(day.toString())) }
                    AllDayRow(days, occurrences, col, ink) { o -> card = PlanDraft.of(o.entry, o.day) }
                    Column(Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 24.dp)) {
                        WeekGrid(
                            days = days, occurrences = occurrences, entries = entries, layout = layout, col = col, today = today,
                            now = vm.now(), zone = vm.zone, clock = clock, ink = ink, picked = picked,
                            onPick = { picked = if (picked?.entryId == it.entryId && !it.moving) null else it },
                            onClose = { picked = null },
                            onStamp = { e, state -> vm.stamp(e, state); picked = null },
                            onMove = { e, from, to -> vm.move(e, from, to); picked = null },
                            onOpen = { o -> picked = null; card = PlanDraft.of(o.entry, o.day) },
                            onDay = { day -> nav.navigate(Routes.plannerDay(day.toString())) },
                            onFold = { unfolded = !unfolded },
                        )
                        Memo(
                            days = days, occurrences = occurrences, ink = ink, seenBy = seenBy.map { it.name },
                            onToggle = { e -> vm.stamp(e, EntryState.DONE) },
                            onOpen = { o -> card = PlanDraft.of(o.entry, o.day) },
                            onAdd = { card = PlanDraft.new(if (today in days) today else days.first()) },
                            onTypicalWeek = { nav.navigate(Routes.TYPICAL_WEEK) },
                        )
                    }
                }
            }
        }
        // The ribbon (栞) in your ink goes back to today.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 64.dp)
                .offset(y = (-8).dp)
                .size(30.dp, 72.dp)
                .shadow(2.dp, RibbonShape)
                .clip(RibbonShape)
                .background(ink)
                .drawBehind {
                    var x = 0f
                    while (x < size.width) {
                        drawLine(Color.White.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        x += 3.dp.toPx()
                    }
                }
                .clickable(role = Role.Button, onClickLabel = "Back to today") { vm.shownDay.value = vm.today(); picked = null }
                .semantics { contentDescription = "Back to today" },
        )
        IndexTab(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 6.dp), ink)
        card?.let { draft ->
            PlanCard(
                draft = draft, clock = clock, zone = vm.zone, ink = ink, seenBy = seenBy.map { it.name },
                onSave = { e -> vm.save(e); card = null },
                onDelete = { e -> vm.delete(e); card = null },
                onClose = { card = null },
                blank = { kind -> vm.blank(kind) },
            )
        }
    }
}

/** Space at the side for the hours. */
private val GUTTER = 24.dp

/**
 * The grid's small type, which grows with the phone's text size only so far (to 1.15 times):
 * further, a word no longer fits a day's column. Every entry says itself in full to TalkBack, and
 * on its day's page.
 */
@Composable
private fun grid(size: Float): androidx.compose.ui.unit.TextUnit {
    val scale = LocalDensity.current.fontScale
    return (size * minOf(scale, 1.15f) / scale).sp
}

@Composable
private fun WeekHeader(days: List<LocalDate>, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = paper
    val first = days.first()
    val last = days.last()
    val (kanji, meaning) = PlannerDays.months[first.monthValue - 1]
    val range = if (first.month == last.month) "${first.dayOfMonth}–${last.dayOfMonth}" else "${first.dayOfMonth} ${month(first)} – ${last.dayOfMonth} ${month(last)}"
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 100.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${first.monthValue}", style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 42.sp), color = colors.sumi)
        Column(Modifier.padding(bottom = 2.dp)) {
            Text(kanji, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 18.sp), color = colors.sumi)
            Text("$meaning · $range", style = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 12.5.sp), color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        WeekArrow("‹", "The week before", onPrevious)
        WeekArrow("›", "The week after", onNext)
    }
}

@Composable
private fun WeekArrow(glyph: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp, 44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(glyph, style = TextStyle(fontFamily = HarnessFonts.Display, fontSize = 26.sp), color = paper.usuzumi1)
    }
}

private fun month(day: LocalDate) = day.month.getDisplayName(DayText.SHORT, Locale.US)

/** The week's index tab at the page's right edge (週 Week; 月 Month joins it with the month page). */
@Composable
private fun IndexTab(modifier: Modifier, ink: Color) {
    Column(
        modifier
            .size(26.dp, 56.dp)
            .shadow(1.dp, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
            .background(ink, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
            .semantics { contentDescription = "Week, the page you're on" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val on = paper.onInk(ink)
        Text("週", style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 12.5.sp, lineHeight = 13.sp), color = on)
        Text("Week", style = TextStyle(fontFamily = HarnessFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 7.5.sp), color = on, modifier = Modifier.padding(top = 3.dp).sideways())
    }
}

@Composable
private fun DayHeads(days: List<LocalDate>, today: LocalDate, col: Dp, onDay: (LocalDate) -> Unit) {
    val colors = paper
    val night = stationery.isNight
    val ink = stationery.you
    Box(Modifier.height(44.dp).fillMaxWidth()) {
        days.forEachIndexed { i, day ->
            val dayColor = dayInk(day.dayOfWeek, colors.sumi, night)
            Column(
                Modifier
                    .offset(x = GUTTER + col * i)
                    .size(col, 44.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${dayName(day)}") { onDay(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(24.dp).then(if (day == today) Modifier.border(1.5.dp, ink, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${day.dayOfMonth}",
                        style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                        color = if (day == today) ink else dayColor,
                    )
                }
                Text(PlannerDays.kanji(day.dayOfWeek), style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 12.sp, lineHeight = 12.sp), color = dayColor)
            }
        }
    }
}

private fun dayName(day: LocalDate) = "${day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(DayText.FULL, Locale.US)}"

@Composable
private fun AllDayRow(days: List<LocalDate>, occurrences: List<Occurrence>, col: Dp, ink: Color, onOpen: (Occurrence) -> Unit) {
    val colors = paper
    val allDay = occurrences.filter { it.entry.allDay && it.entry.kind == EntryKind.PLAN }
    Box(Modifier.fillMaxWidth().heightIn(min = 22.dp).drawBehind { drawLine(colors.nijimi1, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        Text("all day", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(8.5f)), color = colors.kasure2, modifier = Modifier.width(GUTTER - 2.dp).padding(top = 6.dp), textAlign = TextAlign.End)
        days.forEachIndexed { i, day ->
            Column(Modifier.offset(x = GUTTER + col * i + 2.dp).width(col - 4.dp).padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                allDay.filter { it.day == day }.forEach { o ->
                    Text(
                        o.entry.title.ifBlank { "Plan" },
                        style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = grid(9f), lineHeight = grid(13f)),
                        color = lerp(ink, colors.sumi, 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(Highlighter).background(ink.copy(alpha = 0.3f))
                            .clickable(onClickLabel = "Open ${o.entry.title}") { onOpen(o) }.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekGrid(
    days: List<LocalDate>,
    occurrences: List<Occurrence>,
    entries: List<PlannerEntry>,
    layout: HourLayout,
    col: Dp,
    today: LocalDate,
    now: Long,
    zone: java.time.ZoneId,
    clock: ClockFormat,
    ink: Color,
    picked: Picked?,
    onPick: (Picked) -> Unit,
    onClose: () -> Unit,
    onStamp: (PlannerEntry, EntryState) -> Unit,
    onMove: (PlannerEntry, LocalDate, LocalDate) -> Unit,
    onOpen: (Occurrence) -> Unit,
    onDay: (LocalDate) -> Unit,
    onFold: () -> Unit,
) {
    val colors = paper
    val night = stationery.isNight
    val sleepInk = if (night) Color(0xFFB9A6E8) else Color(0xFF5C4A86)
    val height = layout.height.dp
    fun y(minutes: Int) = layout.y(minutes).dp
    val minute = { millis: Long, day: LocalDate -> PlannerDays.minuteOf(millis, day, zone) }
    Box(Modifier.fillMaxWidth().height(height).padding(top = 0.dp)) {
        // The page: today's column, the columns and hours, your sleep when it's open, your typical week.
        val routines = occurrences.filter { it.entry.kind == EntryKind.ROUTINE && it.start != null && it.end != null }
        Box(
            Modifier.matchParentSizeCompat().drawBehind {
                val c = col.toPx()
                val gutter = GUTTER.toPx()
                days.indexOf(today).takeIf { it >= 0 }?.let { i -> drawRect(ink.copy(alpha = 0.06f), Offset(gutter + c * i, 0f), androidx.compose.ui.geometry.Size(c, size.height)) }
                for (o in routines) {
                    val i = days.indexOf(o.day).takeIf { it >= 0 } ?: continue
                    val top = y(minute(o.start!!, o.day)).toPx()
                    val bottom = y(minute(o.end!!, o.day)).toPx()
                    val color = if (o.entry.routine == RoutineKind.SLEEP) sleepInk.copy(alpha = 0.06f) else Color(0x2BB49A84)
                    if (o.entry.routine == RoutineKind.SLEEP && layout.folded.isNotEmpty()) continue
                    drawRect(color, Offset(gutter + c * i + 2.dp.toPx(), top), androidx.compose.ui.geometry.Size(c - 4.dp.toPx(), bottom - top))
                }
                for (k in 0..7) drawLine(colors.nijimi1.copy(alpha = 0.7f), Offset(gutter + c * k, 0f), Offset(gutter + c * k, size.height), 1.dp.toPx())
                for (h in 0..23) {
                    if (layout.isFolded(h)) continue
                    val top = y(h * 60).toPx()
                    drawLine(if (h % 3 == 0) colors.nijimi2.copy(alpha = 0.6f) else colors.nijimi1.copy(alpha = 0.6f), Offset(gutter, top), Offset(size.width, top), 1.dp.toPx())
                }
            },
        )
        // An empty part of a day opens it.
        days.forEachIndexed { i, day ->
            Box(Modifier.offset(x = GUTTER + col * i).size(col, height).clickable(onClickLabel = "Open ${dayName(day)}") { onDay(day) })
        }
        // The hours down the side.
        for (h in 0..23) {
            if (layout.isFolded(h)) continue
            Text(
                clock.hour(h),
                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(8.5f)),
                color = colors.kasure2,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = y(h * 60) - 6.dp).width(GUTTER - 4.dp),
            )
        }
        // The labels of your typical week's bands, on their first day.
        routines.filter { it.entry.routine != RoutineKind.SLEEP }.distinctBy { it.entry.id }.forEach { o ->
            val i = days.indexOf(o.day)
            Text(
                o.entry.title.ifBlank { if (o.entry.routine == RoutineKind.WORK) "Work" else "Routine" }.uppercase(),
                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(7.5f), letterSpacing = 0.1.em),
                color = colors.usuzumi1,
                maxLines = 1,
                modifier = Modifier.offset(x = GUTTER + col * i + 5.dp, y = y(minute(o.start!!, o.day)) + 3.dp),
            )
        }
        // Folded sleep: a thin band for each stretch; tap it to open the hours.
        layout.folded.forEach { band ->
            val label = "${clock.time(band.first * 60)}–${clock.time((band.last + 1) * 60)} asleep"
            Row(
                Modifier
                    .offset(y = y(band.first * 60))
                    .fillMaxWidth()
                    .height(HourLayout.FOLD.dp)
                    .hatched(sleepInk.copy(alpha = 0.12f))
                    .clickable(role = Role.Button, onClickLabel = "Unfold your sleep hours") { onFold() }
                    .padding(start = GUTTER + 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("☾ $label", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(8.5f)), color = sleepInk, modifier = Modifier.weight(1f), maxLines = 1)
                Text("⌄", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 9.sp), color = sleepInk)
            }
        }
        if (layout.folded.isEmpty() && routines.any { it.entry.routine == RoutineKind.SLEEP }) {
            Text(
                "☾ FOLD MY SLEEP",
                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 8.sp, letterSpacing = 0.08.em),
                color = sleepInk,
                modifier = Modifier.align(Alignment.TopEnd).clickable(role = Role.Button, onClickLabel = "Fold your sleep hours") { onFold() }.padding(6.dp),
            )
        }

        // Ghosts where plans were moved from.
        val plans = occurrences.filter { it.entry.kind == EntryKind.PLAN && it.entry.byPersonaId == null && it.start != null && !it.entry.allDay }
        plans.filter { it.entry.movedFrom != null }.forEach { o ->
            val from = LocalDate.parse(o.entry.movedFrom)
            val i = days.indexOf(from).takeIf { it >= 0 } ?: return@forEach
            val start = o.start ?: return@forEach
            val top = y(minute(start, o.day))
            val bottom = maxOf(y(minute(o.end ?: start, o.day)), top + 16.dp)
            Box(
                Modifier.offset(x = GUTTER + col * i + 2.dp, y = top).size(col - 4.dp, bottom - top)
                    .border(1.dp, ink.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                    .semantics { contentDescription = "${o.entry.title}, moved to ${o.day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)}" },
            ) {
                Text("${o.entry.title}\n→ ${PlannerDays.kanji(o.day.dayOfWeek)}", style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = grid(9f), lineHeight = grid(10.5f)), color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(2.dp), maxLines = 3)
                Stamp("延", ink, Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
            }
        }

        // Your plans, in highlighter; two at once share their day.
        val lanes = lanesOf(plans, zone)
        plans.forEach { o ->
            val i = days.indexOf(o.day).takeIf { it >= 0 } ?: return@forEach
            val (lane, of) = lanes[o] ?: (0 to 1)
            val start = o.start ?: return@forEach
            val top = y(minute(start, o.day))
            val bottom = maxOf(y(minute(o.end ?: start, o.day)), top + 16.dp)
            val width = (col - 4.dp) / of
            val e = o.entry
            val stamp = stampOf(e.state)
            Box(
                Modifier
                    .offset(x = GUTTER + col * i + 2.dp + width * lane, y = top)
                    .size(width, bottom - top)
                    .clip(Highlighter)
                    .background(ink.copy(alpha = if (e.state == EntryState.CANCELLED) 0.14f else 0.3f))
                    .combinedClickable(onClickLabel = "Stamps for ${e.title}", onLongClick = { onOpen(o) }) { onPick(Picked(e.id, o.day)) }
                    .semantics { contentDescription = planLabel(e, o, clock, zone) },
            ) {
                Text(
                    e.title.ifBlank { "Plan" },
                    style = TextStyle(
                        fontFamily = HarnessFonts.Read, fontSize = grid(9.5f), lineHeight = grid(10.5f),
                        textDecoration = if (e.state == EntryState.CANCELLED) TextDecoration.LineThrough else null,
                    ),
                    color = lerp(ink, colors.sumi, 0.45f).copy(alpha = if (e.state == EntryState.CANCELLED) 0.55f else 1f),
                    modifier = Modifier.padding(start = 3.dp, top = 2.dp, end = 2.dp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stamp != null) {
                Stamp(stamp, ink, Modifier.offset(x = GUTTER + col * i + width * (lane + 1) - 20.dp, y = maxOf(bottom - 21.dp, top)), fresh = e.updatedAt > now - 1_500)
            }
        }

        // Now, on today's column.
        days.indexOf(today).takeIf { it >= 0 }?.let { i ->
            val at = y(minute(now, today))
            Box(Modifier.offset(x = GUTTER + col * i, y = at).size(col, 1.5.dp).background(ink))
            Box(Modifier.offset(x = GUTTER + col * i - 3.dp, y = at - 3.dp).size(7.dp).background(ink, CircleShape))
        }

        // A plan's stamps, or the days to move it to.
        picked?.let { p ->
            val o = plans.firstOrNull { it.entry.id == p.entryId && it.day == p.day } ?: return@let
            val i = days.indexOf(o.day)
            val start = o.start ?: return@let
            val bottom = maxOf(y(minute(o.end ?: start, o.day)), y(minute(start, o.day)) + 16.dp)
            val below = bottom + 4.dp + 52.dp < height
            val top = if (below) bottom + 4.dp else y(minute(start, o.day)) - 56.dp
            // A tap anywhere else puts it away.
            Box(Modifier.matchParentSizeCompat().clickable(onClickLabel = "Put the stamps away", onClick = onClose))
            if (p.moving) {
                MoveTo(days, o.day, col, Modifier.offset(x = GUTTER, y = top.coerceAtLeast(0.dp))) { to -> onMove(o.entry, o.day, to) }
            } else {
                val e = o.entry
                val left = (GUTTER + col * i + col / 2 - 100.dp).coerceIn(GUTTER, GUTTER + col * 7 - 200.dp)
                Row(
                    Modifier
                        .offset(x = left, y = top.coerceAtLeast(0.dp))
                        .shadow(3.dp, RoundedCornerShape(2.dp))
                        .background(colors.hyoshi, RoundedCornerShape(2.dp))
                        .border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                ) {
                    StampButton("済", if (e.state == EntryState.DONE) "Lift" else "Done", if (e.state == EntryState.DONE) "Lift the done stamp" else "Stamp it done", ink) { onStamp(e, EntryState.DONE) }
                    StampButton("延", "Move", "Move it to another day", ink) { onPick(p.copy(moving = true)) }
                    StampButton("止", if (e.state == EntryState.CANCELLED) "Restore" else "Cancel", if (e.state == EntryState.CANCELLED) "Lift the cancelled stamp" else "Stamp it cancelled", ink) { onStamp(e, EntryState.CANCELLED) }
                    StampButton("›", "Open", "Open the plan's card", ink) { onOpen(o) }
                }
            }
        }
    }
}

/** Which of a day's plans overlap, and so how its column is shared: each one's place and how many share it. */
private fun lanesOf(plans: List<Occurrence>, zone: java.time.ZoneId): Map<Occurrence, Pair<Int, Int>> {
    val out = mutableMapOf<Occurrence, Pair<Int, Int>>()
    plans.groupBy { it.day }.forEach { (_, day) ->
        val sorted = day.sortedBy { it.start }
        val ends = mutableListOf<Long>()
        val lane = mutableMapOf<Occurrence, Int>()
        for (o in sorted) {
            val start = o.start ?: continue
            val end = maxOf(o.end ?: start, start + 45 * 60_000L)
            val free = ends.indexOfFirst { it <= start }
            val l = if (free >= 0) free else ends.size.also { ends += 0L }
            ends[l] = end
            lane[o] = l
        }
        val of = ends.size.coerceAtLeast(1)
        lane.forEach { (o, l) -> out[o] = l to of }
    }
    return out
}

private fun planLabel(e: PlannerEntry, o: Occurrence, clock: ClockFormat, zone: java.time.ZoneId): String {
    val start = o.start?.let { clock.time(PlannerDays.minuteOf(it, o.day, zone)) }
    val state = when {
        e.state == EntryState.DONE -> ", done"
        e.state == EntryState.CANCELLED -> ", cancelled"
        e.movedFrom != null -> ", moved"
        else -> ""
    }
    return "${e.title.ifBlank { "A plan" }}, ${o.day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)}${start?.let { " $it" } ?: ""}$state. Tap for stamps, hold to open"
}

@Composable
private fun StampButton(glyph: String, label: String, spoken: String, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier.size(48.dp, 44.dp).clickable(role = Role.Button, onClickLabel = spoken, onClick = onClick).semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(glyph, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 14.sp, lineHeight = 14.sp), color = ink)
        Text(label, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 8.5.sp), color = paper.usuzumi0)
    }
}

/** The seven days, lined up with the columns, to move a plan to; its own day is marked. */
@Composable
private fun MoveTo(days: List<LocalDate>, from: LocalDate, col: Dp, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    val colors = paper
    val night = stationery.isNight
    val ink = stationery.you
    Column(
        modifier
            .width(col * 7)
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(colors.hyoshi, RoundedCornerShape(2.dp))
            .border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp)),
    ) {
        Text("MOVE TO", style = PaperType.meta.copy(fontSize = 8.sp), color = colors.kasure2, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
        Row {
            days.forEach { day ->
                val c = dayInk(day.dayOfWeek, colors.sumi, night)
                Column(
                    Modifier
                        .size(col, 44.dp)
                        .then(if (day == from) Modifier.background(colors.chitsu).drawBehind { drawLine(ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) } else Modifier)
                        .clickable(role = Role.Button, onClickLabel = if (day == from) "Keep it on ${dayName(day)}" else "Move to ${dayName(day)}") { onPick(day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(PlannerDays.kanji(day.dayOfWeek), style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 13.sp, lineHeight = 13.sp), color = c)
                    Text("${day.dayOfMonth}", style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 13.sp), color = c)
                }
            }
        }
    }
}

/** メモ: the week's plans with no time, which stamp done with a tap; your typical week; adding a plan. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Memo(
    days: List<LocalDate>,
    occurrences: List<Occurrence>,
    ink: Color,
    seenBy: List<String>,
    onToggle: (PlannerEntry) -> Unit,
    onOpen: (Occurrence) -> Unit,
    onAdd: () -> Unit,
    onTypicalWeek: () -> Unit,
) {
    val colors = paper
    val untimed = occurrences.filter { it.entry.kind == EntryKind.PLAN && it.entry.byPersonaId == null && it.start == null && !it.entry.allDay }
    Column(Modifier.padding(top = 20.dp, start = 4.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }.padding(bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("メモ", style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 16.sp), color = colors.sumi)
            Text("Notes this week", style = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 13.5.sp), color = colors.usuzumi1)
        }
        untimed.forEach { o ->
            val e = o.entry
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .combinedClickable(onClickLabel = if (e.state == EntryState.DONE) "Lift the done stamp" else "Stamp it done", onLongClick = { onOpen(o) }) { onToggle(e) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "• ${e.title.ifBlank { "A plan" }}",
                    style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 14.5.sp, textDecoration = if (e.state == EntryState.CANCELLED) TextDecoration.LineThrough else null),
                    color = ink,
                )
                Text(o.day.dayOfWeek.getDisplayName(DayText.SHORT, Locale.US).uppercase(), style = PaperType.meta.copy(fontSize = 10.sp), color = colors.kasure2)
                stampOf(e.state)?.let { Stamp(it, ink, size = 22.dp, fresh = e.updatedAt > System.currentTimeMillis() - 1_500) }
            }
        }
        if (untimed.isEmpty()) {
            Text("Plans with no time go here, to stamp done when they are.", style = PaperType.postscript, color = colors.usuzumi1)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            dev.aiharness.app.ui.paper.PaperButton("+ Add a plan", onClick = onAdd)
            dev.aiharness.app.ui.paper.PaperButton("Your typical week", onClick = onTypicalWeek)
        }
        Text(
            if (seenBy.isEmpty()) "Only you see this planner." else "${names(seenBy)} can see this planner.",
            style = PaperType.postscript,
            color = colors.usuzumi1,
        )
    }
}

/** Text set on its side, read top to bottom, as an index tab's word is. */
private fun Modifier.sideways(): Modifier = layout { measurable, _ ->
    val p = measurable.measure(androidx.compose.ui.unit.Constraints())
    layout(p.height, p.width) {
        p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}

/** "Aria", "Aria and Juno", "Aria, Juno and Kestrel". */
fun names(list: List<String>): String = when (list.size) {
    0 -> ""
    1 -> list[0]
    else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
}

/** Box.matchParentSize, for the grid's layers. */
@Composable
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()
