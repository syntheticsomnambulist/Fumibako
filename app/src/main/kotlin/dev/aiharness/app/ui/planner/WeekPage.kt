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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.planner.Occurrence
import java.time.LocalDate
import java.time.format.TextStyle as DayText
import java.util.Locale
import dev.aiharness.core.model.Hemisphere
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.HolidaySettings
import dev.aiharness.core.planner.Holidays
import dev.aiharness.core.seasons.SolarTerm
import dev.aiharness.core.seasons.Seasons
import java.time.YearMonth

/** What's picked on the week page: an entry's stamps, or the days to move it to. */
private data class Picked(val entryId: String, val day: LocalDate, val moving: Boolean = false)

/**
 * The 帳 Planner tab's week (docs/design/calendar.md on the design branch, "Second pass"): a
 * バーチカル page of seven columns and every hour, your sleep folded to a thin band, your typical
 * week printed faintly, your plans in highlighter. Tap a plan for its stamps (済 done, 延 move,
 * 止 cancel, its card); tap an empty part of a day to open it. Below, the week's untimed plans.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    val you by vm.you.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val personas by vm.personas.collectAsStateWithLifecycle()
    val thisWeek = today in days
    val month = YearMonth.from(shown)
    val thisMonth = month == YearMonth.from(today)
    val monthDays = remember(month, settings.planner.weekStartsSunday) { MonthDays.of(month, settings.planner.weekStartsSunday) }
    val monthOccurrences = remember(entries, monthDays) { vm.occurrences(entries, monthDays.first(), monthDays.last()) }
    val hemisphere = vm.hemisphere(settings)
    val holidays = settings.planner.holidays
    PlannerStickerHost(you?.id, you) { stickers, bottom ->
    Box(Modifier.fillMaxSize().background(colors.kami).dotGrid(colors.sumi.copy(alpha = 0.12f))) {
        Column(Modifier.fillMaxSize().stitched(lerp(colors.nijimi2, colors.kami, 0.2f)).statusBarsPadding().padding(bottom = bottom)) {
          if (view == PlannerView.MONTH) {
            MonthHeader(month) { you?.let { u -> stickers.openTray(StickerTarget.day(u.id, (if (thisMonth) today else month.atDay(1)).toString())) } }
            MonthPage(
                month = month, today = today, startsSunday = settings.planner.weekStartsSunday, occurrences = monthOccurrences,
                holidays = holidays, seasonBand = settings.planner.seasonBand, zone = vm.zone, hemisphere = hemisphere,
                personas = personas, plannerId = you?.id, stickers = stickers,
                onDay = { day -> nav.navigate(Routes.plannerDay(day.toString())) },
                onHolidaysSetup = { nav.navigate(Routes.settings("planner")) },
            )
          } else {
            WeekHeader(days) { you?.let { u -> stickers.openTray(StickerTarget.day(u.id, (if (thisWeek) today else days.first()).toString())) } }
            if (settings.planner.seasonBand) SeasonBand(if (thisWeek) today else days.first(), vm.zone, hemisphere)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 18.dp, end = 30.dp)) {
                val col = ((maxWidth - GUTTER) / 7).coerceAtLeast(36.dp)
                Column(Modifier.fillMaxSize()) {
                    // The days and the all-day row stay put while the hours scroll.
                    DayHeads(days, today, col, holidays) { day -> nav.navigate(Routes.plannerDay(day.toString())) }
                    AwayRow(days, occurrences, personas, col)
                    AllDayRow(
                        days, occurrences, col, ink, holidays,
                        termOf = if (settings.planner.seasonBand) remember(days, hemisphere) { Seasons.termsBeginning(days.first(), days.last(), vm.zone, hemisphere).toMap() } else emptyMap(),
                    ) { o -> card = PlanDraft.of(o.entry, o.day) }
                    Column(Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 24.dp)) {
                        WeekGrid(
                            days = days, occurrences = occurrences, entries = entries, layout = layout, col = col, today = today,
                            now = vm.now(), zone = vm.zone, clock = clock, ink = ink, picked = picked,
                            plannerId = you?.id, stickers = stickers, personas = personas,
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
                            personas = personas, zone = vm.zone, clock = clock, hemisphereOf = { hemisphere },
                            onAddSuggestion = vm::addSuggestion, onDeclineSuggestion = vm::declineSuggestion,
                            onToggle = { e -> vm.stamp(e, EntryState.DONE) },
                            onOpen = { o -> card = PlanDraft.of(o.entry, o.day) },
                            onAdd = { card = PlanDraft.new(if (today in days) today else days.first()) },
                            onTypicalWeek = { nav.navigate(Routes.TYPICAL_WEEK) },
                        )
                    }
                }
            }
          }
        }
        // The ribbon (栞) in your ink, from the page's top edge below the phone's status bar: a
        // bookmark. It hangs on the page it marks, and only its end shows elsewhere; a tap goes back
        // to that page, a long press moves it to the one shown.
        val marked = vm.bookmark(settings)
        val here = if (view == PlannerView.MONTH) YearMonth.from(marked) == month else marked in days
        val markedWeek = PlannerDays.weekOf(marked, settings.planner.weekStartsSunday).first()
        val markedWords = if (view == PlannerView.MONTH) "${marked.month.getDisplayName(DayText.FULL, Locale.US)} ${marked.year}"
        else "the week of ${markedWeek.dayOfMonth} ${markedWeek.month.getDisplayName(DayText.FULL, Locale.US)}"
        val toast = LocalContext.current
        val ribbonLength by androidx.compose.animation.core.animateDpAsState(if (here) 60.dp else 22.dp, label = "ribbon")
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 58.dp)
                .size(44.dp, 64.dp)
                .combinedClickable(
                    onClickLabel = if (here) null else "Go to your bookmark",
                    onLongClickLabel = "Put the bookmark here",
                    onLongClick = {
                        val day = if (view == PlannerView.MONTH) (if (thisMonth) today else month.atDay(1)) else if (thisWeek) today else days.first()
                        vm.moveBookmark(day)
                        android.widget.Toast.makeText(toast, if (view == PlannerView.MONTH) "Bookmark moved to ${month.month.getDisplayName(DayText.FULL, Locale.US)}" else "Bookmark moved to this week", android.widget.Toast.LENGTH_SHORT).show()
                    },
                ) { if (!here) { vm.shownDay.value = marked; picked = null } }
                .semantics { contentDescription = if (here) "Your bookmark, on this page" else "Your bookmark, on $markedWords" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .size(30.dp, ribbonLength)
                    .shadow(2.dp, RibbonShape)
                    .clip(RibbonShape)
                    .background(ink)
                    .drawBehind {
                        var x = 0f
                        while (x < size.width) {
                            drawLine(Color.White.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            x += 3.dp.toPx()
                        }
                    },
            )
        }
        IndexTabs(
            view, ink, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 6.dp),
            onWeek = { vm.view.value = PlannerView.WEEK },
            onMonth = { vm.view.value = PlannerView.MONTH; picked = null },
        )
        // Going between weeks (or months): tabs at the right edge, under 週 and 月.
        if (view == PlannerView.MONTH) {
            NavTabs(
                "Month", thisMonth, ink, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 132.dp),
                onPrevious = { vm.shownDay.value = month.minusMonths(1).atDay(1) },
                onToday = { vm.shownDay.value = vm.today() },
                onNext = { vm.shownDay.value = month.plusMonths(1).atDay(1) },
            )
        } else {
            NavTabs(
                "Week", thisWeek, ink, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 132.dp),
                onPrevious = { vm.shownDay.value = shown.minusWeeks(1); picked = null },
                onToday = { vm.shownDay.value = vm.today(); picked = null },
                onNext = { vm.shownDay.value = shown.plusWeeks(1); picked = null },
            )
        }
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
}

/** Space at the side for the hours. */
private val GUTTER = 24.dp

/** How tall a plan's place to tap is at least, however short the plan. */
private val PLAN_TARGET = 40.dp

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

/** The week's title (the month's number and traditional name, and the days), with the stickers button before the ribbon. */
@Composable
private fun WeekHeader(days: List<LocalDate>, onStickers: () -> Unit) {
    val colors = paper
    val first = days.first()
    val last = days.last()
    val (kanji, meaning) = PlannerDays.months[first.monthValue - 1]
    val range = if (first.month == last.month) "${first.dayOfMonth}–${last.dayOfMonth}" else "${first.dayOfMonth} ${month(first)} – ${last.dayOfMonth} ${month(last)}"
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 108.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${first.monthValue}", style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 42.sp), color = colors.sumi)
        Column(Modifier.weight(1f).padding(bottom = 2.dp)) {
            Text(kanji, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 18.sp), color = colors.sumi)
            Text("$meaning · $range", style = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 12.5.sp), color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StickersButton(onStickers)
    }
}

/** The month's title: its traditional name large, the month and year and what the name means under it. */
@Composable
private fun MonthHeader(month: YearMonth, onStickers: () -> Unit) {
    val colors = paper
    val (kanji, meaning) = PlannerDays.months[month.monthValue - 1]
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 108.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(kanji, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 32.sp, lineHeight = 36.sp), color = colors.sumi)
            Text(
                "${month.month.getDisplayName(DayText.FULL, Locale.US)} ${month.year} · $meaning",
                style = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 12.5.sp),
                color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        StickersButton(onStickers)
    }
}

/**
 * The season band, one thin line: this microseason and the next solar term, each with its motif
 * ("楓蔦黄 Maples and ivy turn yellow | 立冬 winter begins, 7th"). A tap opens both in full.
 */
@Composable
private fun SeasonBand(day: LocalDate, zone: java.time.ZoneId, hemisphere: Hemisphere) {
    val colors = paper
    val red = dayInk(java.time.DayOfWeek.SUNDAY, colors.sumi, stationery.isNight)
    val season = remember(day, hemisphere) { Seasons.on(day, zone, hemisphere) }
    var open by rememberSaveable { mutableStateOf(false) }
    val micro = season.micro
    val next = season.next
    val nextOn = season.nextFirst.let { if (it.month == day.month) ordinal(it.dayOfMonth) else "${it.dayOfMonth} ${month(it)}" }
    val first = season.microFirst
    val last = season.microLast
    val days = if (first.month == last.month) "${first.dayOfMonth} to ${last.dayOfMonth} ${first.month.getDisplayName(DayText.FULL, Locale.US)}"
    else "${first.dayOfMonth} ${first.month.getDisplayName(DayText.FULL, Locale.US)} to ${last.dayOfMonth} ${last.month.getDisplayName(DayText.FULL, Locale.US)}"
    val small = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 9.5.sp, lineHeight = 11.sp)
    val brush = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 11.5.sp, lineHeight = 13.sp)
    Column(
        Modifier
            .padding(start = 24.dp, end = 36.dp, top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = 26.dp)
            .background(colors.hyoshi)
            .drawBehind { drawLine(red, Offset(0f, 0f), Offset(0f, size.height), 2.5.dp.toPx()) }
            .clickable(onClickLabel = if (open) "Fold the season away" else "The season in full") { open = !open }
            .semantics { contentDescription = "Season: ${micro.english.lowercase()} (${micro.kanji}); next, ${next.english.lowercase()} on the ${ordinal(season.nextFirst.dayOfMonth)}" }
            .padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SeasonMotif(micro.index / 3, Modifier.size(14.dp))
            Text(micro.kanji, style = brush, color = colors.sumi, maxLines = 1, softWrap = false)
            Text(micro.english, style = small, color = colors.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 13.dp).background(colors.nijimi2))
            SeasonMotif(next.index, Modifier.size(14.dp))
            Text(next.kanji, style = brush, color = colors.sumi, maxLines = 1, softWrap = false)
            Text("${next.english.lowercase()}, $nextOn", style = small, color = colors.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (open) {
            val term = season.term
            Text(
                "${term.kanji} ${micro.partKanji} ${micro.kanji}: ${term.english}, the ${micro.part} microseason: ${micro.english.replaceFirstChar { it.lowercase() }}. $days. " +
                    "Then ${next.kanji}, ${next.english.lowercase()}, from ${season.nextFirst.dayOfMonth} ${season.nextFirst.month.getDisplayName(DayText.FULL, Locale.US)}.",
                style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 12.5.sp, lineHeight = 17.sp),
                color = colors.sumi,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Going between weeks (or months), as tabs at the page's right edge under 週 and 月: the one
 * before, today (in your ink while you're on it), and the one after.
 */
@Composable
private fun NavTabs(unit: String, here: Boolean, ink: Color, modifier: Modifier, onPrevious: () -> Unit, onToday: () -> Unit, onNext: () -> Unit) {
    val word = unit.lowercase()
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EdgeTab("‹", "Before", false, ink, spoken = "The $word before", onClick = onPrevious)
        EdgeTab("今", "Today", here, ink, spoken = if (here) "This $word, the $word you're on" else "Back to this $word", onClick = onToday)
        EdgeTab("›", "After", false, ink, spoken = "The $word after", onClick = onNext)
    }
}

@Composable
private fun StickersButton(onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .border(1.dp, paper.nijimi2, dev.aiharness.app.ui.paper.PaperCorner)
            .background(paper.kami, dev.aiharness.app.ui.paper.PaperCorner)
            .clickable(role = Role.Button, onClickLabel = "Stick stickers on the planner", onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { StickersMark() }
}

private fun month(day: LocalDate) = day.month.getDisplayName(DayText.SHORT, Locale.US)

/** The index tabs at the page's right edge: 週 Week and 月 Month, the open one in your ink. */
@Composable
private fun IndexTabs(view: PlannerView, ink: Color, modifier: Modifier, onWeek: () -> Unit, onMonth: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        EdgeTab("週", "Week", view == PlannerView.WEEK, ink, onClick = onWeek)
        EdgeTab("月", "Month", view == PlannerView.MONTH, ink, onClick = onMonth)
    }
}

/** One tab at the page's edge: open, it's in [ink] and a little proud of the others. */
@Composable
private fun EdgeTab(kanji: String, word: String, open: Boolean, ink: Color, spoken: String? = null, onClick: () -> Unit) {
    val colors = paper
    val shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    val on = if (open) colors.onInk(ink) else colors.sumi
    // 48dp to tap, though the tab is narrower.
    Box(
        Modifier
            .size(44.dp, 56.dp)
            .clickable(role = if (spoken != null) Role.Button else Role.Tab, onClickLabel = spoken ?: if (open) null else "Open the $word page", onClick = onClick)
            .semantics { contentDescription = spoken ?: if (open) "$word, the page you're on" else word },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .size(if (open) 26.dp else 22.dp, 52.dp)
                .then(if (open) Modifier.shadow(1.dp, shape).background(ink, shape) else Modifier.background(colors.hyoshi, shape).border(1.dp, colors.nijimi1, shape)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(kanji, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 12.5.sp, lineHeight = 13.sp), color = on)
            // Sideways in a narrow tab, so it grows with large text only as far as it fits.
            Text(word, style = TextStyle(fontFamily = HarnessFonts.Ui, fontWeight = if (open) FontWeight.Bold else FontWeight.Normal, fontSize = grid(7.5f)), color = on, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 3.dp).sideways())
        }
    }
}

/** A band under the days for each persona away this week, across the days it's away, in its ink. */
@Composable
private fun AwayRow(days: List<LocalDate>, occurrences: List<Occurrence>, personas: List<Persona>, col: Dp) {
    val aways = occurrences.filter { it.entry.kind == EntryKind.AWAY }.groupBy { it.entry.id }
    if (aways.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        aways.values.forEach { span ->
            val e = span.first().entry
            val persona = personas.firstOrNull { it.id == e.byPersonaId } ?: return@forEach
            val first = days.indexOf(span.minOf { it.day }).coerceAtLeast(0)
            val last = days.indexOf(span.maxOf { it.day }).let { if (it < 0) days.size - 1 else it }
            val pInk = stationery.ink(persona.ink)
            Row(
                Modifier.offset(x = GUTTER + col * first).width(col * (last - first + 1)).height(16.dp)
                    .background(pInk.copy(alpha = 0.16f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp)
                    .semantics { contentDescription = "${persona.name} is away" + (e.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Moon(pInk, Modifier.size(8.dp))
                Text(
                    "${persona.name} away" + (e.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                    style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = grid(8f), lineHeight = grid(9f)),
                    color = lerp(pInk, paper.sumi, 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DayHeads(days: List<LocalDate>, today: LocalDate, col: Dp, holidays: HolidaySettings, onDay: (LocalDate) -> Unit) {
    val colors = paper
    val night = stationery.isNight
    val ink = stationery.you
    Box(Modifier.height(44.dp).fillMaxWidth()) {
        days.forEachIndexed { i, day ->
            // A public holiday's date is red, like a Sunday's.
            val holiday = Holidays.on(holidays, day).isNotEmpty()
            val dayColor = if (holiday) dayInk(java.time.DayOfWeek.SUNDAY, colors.sumi, night) else dayInk(day.dayOfWeek, colors.sumi, night)
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

internal fun dayName(day: LocalDate) = "${day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(DayText.FULL, Locale.US)}"

@Composable
private fun AllDayRow(
    days: List<LocalDate>,
    occurrences: List<Occurrence>,
    col: Dp,
    ink: Color,
    holidays: HolidaySettings,
    termOf: Map<LocalDate, SolarTerm>,
    onOpen: (Occurrence) -> Unit,
) {
    val colors = paper
    val red = dayInk(java.time.DayOfWeek.SUNDAY, colors.sumi, stationery.isNight)
    val allDay = occurrences.filter { it.entry.allDay && it.entry.kind == EntryKind.PLAN }
    Box(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(bottom = 4.dp).drawBehind { drawLine(colors.nijimi1, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        Text("all day", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(8.5f), lineHeight = grid(10f)), color = colors.kasure2, modifier = Modifier.width(GUTTER - 2.dp).padding(top = 4.dp), textAlign = TextAlign.End, maxLines = 2)
        days.forEachIndexed { i, day ->
            Column(Modifier.offset(x = GUTTER + col * i + 2.dp).width(col - 4.dp).padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // A solar term's first day, in small red kanji; a public holiday's name.
                termOf[day]?.let { t ->
                    Text(t.kanji, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = grid(9f), lineHeight = grid(10f)), color = red, maxLines = 1, modifier = Modifier.semantics { contentDescription = "${t.english} (${t.kanji})" })
                }
                Holidays.on(holidays, day).forEach { h ->
                    Text(h.name, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = grid(7.5f), lineHeight = grid(8.5f)), color = red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
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
    plannerId: String?,
    stickers: PlannerStickers,
    personas: List<Persona>,
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
                // Just under its line, so the line doesn't run through it.
                modifier = Modifier.offset(y = y(h * 60) + 1.dp).width(GUTTER - 4.dp),
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
                modifier = Modifier.offset(x = GUTTER + col * i + 4.dp, y = y(minute(o.start!!, o.day)) + 3.dp)
                    .background(colors.kami.copy(alpha = 0.85f)).padding(horizontal = 1.dp),
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

        // Now, on today's column (under the plans, so it doesn't run through their words).
        days.indexOf(today).takeIf { it >= 0 }?.let { i ->
            val at = y(minute(now, today))
            Box(Modifier.offset(x = GUTTER + col * i, y = at).size(col, 1.5.dp).background(ink))
            Box(Modifier.offset(x = GUTTER + col * i - 3.dp, y = at - 3.dp).size(7.dp).background(ink, CircleShape))
        }

        val plans = occurrences.filter { it.entry.kind == EntryKind.PLAN && it.entry.byPersonaId == null && it.start != null && !it.entry.allDay }
        // Your plans, in highlighter; two at once share their day.
        val lanes = lanesOf(plans, zone)
        // Each plan takes taps a little above and below it too (an hour is small to aim at), so a
        // near miss picks the plan rather than opening its day. Under the plans, so a plan right
        // beside another still gets its own taps.
        plans.forEach { o ->
            val i = days.indexOf(o.day).takeIf { it >= 0 } ?: return@forEach
            val (lane, of) = lanes[o] ?: (0 to 1)
            val start = o.start ?: return@forEach
            val top = y(minute(start, o.day))
            val bottom = maxOf(y(minute(o.end ?: start, o.day)), top + 16.dp)
            val reach = maxOf(bottom - top, PLAN_TARGET)
            val width = (col - 4.dp) / of
            Box(
                Modifier.offset(x = GUTTER + col * i + 2.dp + width * lane, y = (top + bottom) / 2 - reach / 2).size(width, reach)
                    .clickable(onClickLabel = "Stamps for ${o.entry.title}") { onPick(Picked(o.entry.id, o.day)) }
                    .clearAndSetSemantics { },
            )
        }
        plans.forEach { o ->
            val i = days.indexOf(o.day).takeIf { it >= 0 } ?: return@forEach
            val (lane, of) = lanes[o] ?: (0 to 1)
            val start = o.start ?: return@forEach
            val top = y(minute(start, o.day))
            val bottom = maxOf(y(minute(o.end ?: start, o.day)), top + 16.dp)
            val width = (col - 4.dp) / of
            val e = o.entry
            val stamp = stampOf(e)
            stickers.On(StickerTarget.entry(e.id), Modifier.offset(x = GUTTER + col * i + 2.dp + width * lane, y = top).size(width, bottom - top)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(Highlighter)
                    // Paper under the highlighter, so the page's lines don't run through the words.
                    .background(colors.kami)
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
            }
            if (stamp != null) {
                // Over the plan's bottom corner, half off it, so it doesn't cover the title.
                Stamp(stamp, ink, Modifier.offset(x = GUTTER + col * i + width * (lane + 1) - 16.dp, y = bottom - 10.dp), fresh = e.updatedAt > now - 1_500)
            }
        }


        // A persona's suggestion as a small sticky tab at its time, and where it means to wake, its mark.
        occurrences.filter { it.entry.byPersonaId != null && it.start != null && (it.entry.kind == EntryKind.SUGGESTION || it.entry.kind == EntryKind.REMINDER) }.forEach { o ->
            val persona = personas.firstOrNull { it.id == o.entry.byPersonaId } ?: return@forEach
            val i = days.indexOf(o.day).takeIf { it >= 0 } ?: return@forEach
            val top = y(minute(o.start!!, o.day))
            val pInk = stationery.ink(persona.ink)
            if (o.entry.kind == EntryKind.REMINDER) {
                WakeMark(
                    persona, kept = o.entry.state == EntryState.KEPT,
                    Modifier.offset(x = GUTTER + col * (i + 1) - 18.dp, y = top - 8.dp)
                        .semantics { contentDescription = "${persona.name} means to wake here: ${o.entry.title}" },
                )
            } else if (o.entry.state != EntryState.ADDED) {
                Text(
                    o.entry.title,
                    style = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = grid(8f), lineHeight = grid(9.5f), textDecoration = if (o.entry.state == EntryState.DECLINED) TextDecoration.LineThrough else null),
                    color = colors.onInk(pInk),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(x = GUTTER + col * i + col * 0.3f, y = top).width(col * 0.7f)
                        .background(pInk.copy(alpha = if (o.entry.state == EntryState.DECLINED) 0.45f else 0.9f), RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                        .semantics { contentDescription = "${persona.name} suggests: ${o.entry.title}" },
                )
            }
        }

        // What's stuck on each day, over its plans.
        if (plannerId != null) {
            days.forEachIndexed { i, day ->
                stickers.On(StickerTarget.day(plannerId, day.toString()), Modifier.offset(x = GUTTER + col * i).size(col, height)) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
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
    personas: List<Persona>,
    zone: java.time.ZoneId,
    clock: ClockFormat,
    hemisphereOf: (LocalDate) -> Hemisphere,
    onAddSuggestion: (PlannerEntry) -> Unit,
    onDeclineSuggestion: (PlannerEntry) -> Unit,
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
                stampOf(e)?.let { Stamp(it, ink, size = 22.dp, fresh = e.updatedAt > System.currentTimeMillis() - 1_500) }
            }
        }
        if (untimed.isEmpty()) {
            Text("Plans with no time go here, to stamp done when they are.", style = PaperType.postscript, color = colors.usuzumi1)
        }
        // What your personas wrote this week: their slips, in day and time order (time away once).
        val theirs = occurrences.filter { it.entry.byPersonaId != null }.distinctBy { if (it.entry.kind == EntryKind.AWAY) it.entry.id else it.entry.id + it.day }
            .sortedWith(compareBy<Occurrence> { it.entry.kind != EntryKind.AWAY }.thenBy { it.day }.thenBy { it.start ?: Long.MIN_VALUE })
        theirs.forEach { o ->
            val persona = personas.firstOrNull { it.id == o.entry.byPersonaId } ?: return@forEach
            PersonaSlip(o, persona, zone, hemisphereOf, clock, onAdd = onAddSuggestion, onDecline = onDeclineSuggestion)
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

/** The tray's sheet of stickers, with its name under it, so the button says what it's for. */
@Composable
internal fun StickersMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        dev.aiharness.app.ui.chat.TrayDrawing(dev.aiharness.app.ui.chat.TrayItem.STICKERS, Modifier.size(28.dp, 21.dp))
        Text(
            "Stickers",
            style = PaperType.meta.copy(fontSize = 10.sp, lineHeight = 11.sp, letterSpacing = 0.sp),
            color = paper.usuzumi1,
            maxLines = 1,
            softWrap = false,
        )
    }
}
