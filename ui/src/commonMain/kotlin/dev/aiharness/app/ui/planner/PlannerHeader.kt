package dev.aiharness.app.ui.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.platform.onRightClick
import dev.aiharness.app.ui.media.reducedMotion
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.paper.tape
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.TapeShade
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DayText
import java.util.Locale

/*
 * Header B (docs/design/calendar.md, Third pass, agreed 2026-11-08): one slim header on every
 * planner page. A label naming the month on a strip of masking tape in your ink's light shade, the
 * way to the pages either side in words under it, and a Week and Month switch with the view you're
 * on circled in ink. The ribbon is the way back; the edge tabs went.
 */

/** "霜月 • November"; a week across two months, "神無月 • Oct – Nov"; another year's adds it. */
internal fun weekLabel(days: List<LocalDate>, today: LocalDate): String {
    val first = days.first()
    val last = days.last()
    if (first.month == last.month) return monthLabel(YearMonth.from(first), today)
    val kanji = PlannerDays.months[first.monthValue - 1].first
    val short = { d: LocalDate -> d.month.getDisplayName(DayText.SHORT, Locale.US) }
    return "$kanji • ${short(first)} – ${short(last)}" + if (last.year != today.year) " ${last.year}" else ""
}

/** "霜月 • November" (and the year when it isn't this one). */
internal fun monthLabel(month: YearMonth, today: LocalDate): String =
    "${PlannerDays.months[month.monthValue - 1].first} • ${month.month.getDisplayName(DayText.FULL, Locale.US)}" +
        if (month.year != today.year) " ${month.year}" else ""

/** "金 • 6 November" (and the year when it isn't this one). */
internal fun dayLabel(day: LocalDate, today: LocalDate): String =
    "${PlannerDays.kanji(day.dayOfWeek)} • ${day.dayOfMonth} ${day.month.getDisplayName(DayText.FULL, Locale.US)}" +
        if (day.year != today.year) " ${day.year}" else ""

/** Room the ribbon takes at the page's right edge (the header and the grid keep clear of it). */
internal val RIBBON_ROOM = 52.dp

/**
 * The header: [label] on tape with the Week and Month switch beside it, then [before] and [after]
 * as words ("‹ Week before · Week after ›"), spoken as [spokenBefore] and [spokenAfter]. [view]
 * is circled in the switch; on the day page it's null, and both words are ways back up.
 */
@Composable
internal fun PlannerHeader(
    label: String,
    before: String,
    after: String,
    spokenBefore: String,
    spokenAfter: String,
    view: PlannerView?,
    onBefore: () -> Unit,
    onAfter: () -> Unit,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paper
    val ink = stationery.you
    val labelTape = tape(ink, TapePattern.PLAIN, TapeShade.LIGHT)
    val words = lerp(ink, colors.sumi, 0.3f)
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = RIBBON_ROOM, top = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                Text(
                    label,
                    style = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
                    // Light tape is mixed with the day's paper, at night too: the day's ink reads on it.
                    color = stationery.dayColors.sumi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .rotate(-1.5f)
                        .drawBehind {
                            val thick = maxOf(size.height, 28.dp.toPx())
                            drawTape(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), thick, labelTape, tooth = 3.dp.toPx())
                        }
                        .padding(horizontal = 15.dp, vertical = 3.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SwitchWord("Week", view == PlannerView.WEEK, ink, onWeek)
                SwitchWord("Month", view == PlannerView.MONTH, ink, onMonth)
            }
        }
        Row(Modifier.padding(start = 0.dp).offset(x = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
            NavWord("‹ $before", spokenBefore, words, onBefore)
            Text("·", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 12.sp), color = colors.kasure2)
            NavWord("$after ›", spokenAfter, words, onAfter)
        }
    }
}

@Composable
private fun NavWord(text: String, spoken: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .clickable(role = Role.Button, onClickLabel = spoken, onClick = onClick)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 12.sp, lineHeight = 16.sp), color = color, maxLines = 1)
    }
}

/** One side of the Week and Month switch: circled in [ink] when it's the view you're on, the loop drawn as you switch. */
@Composable
private fun SwitchWord(word: String, on: Boolean, ink: Color, onClick: () -> Unit) {
    val still = reducedMotion()
    val drawn = remember { Animatable(if (on) 1f else 0f) }
    LaunchedEffect(on) {
        if (!on) drawn.snapTo(0f)
        else if (still) drawn.snapTo(1f)
        else if (drawn.value < 1f) drawn.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val colors = paper
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 46.dp)
            .clickable(role = Role.Tab, onClickLabel = if (on) null else "Open the $word page", onClick = onClick)
            .semantics { contentDescription = if (on) "$word, the page you're on" else word }
            .drawBehind {
                val t = drawn.value
                if (t <= 0f) return@drawBehind
                // A quick loop round the word by hand, a little wider than it and not quite closed.
                val w = size.width + 8.dp.toPx()
                val h = 32.dp.toPx()
                val sx = w / 64f
                val sy = h / 32f
                val loop = Path().apply {
                    moveTo(50f * sx, 6.5f * sy)
                    cubicTo(41f * sx, 2.5f * sy, 14f * sx, 3f * sy, 7f * sx, 9.5f * sy)
                    cubicTo(1f * sx, 15f * sy, 5f * sx, 25f * sy, 22f * sx, 27f * sy)
                    cubicTo(38f * sx, 29f * sy, 58f * sx, 26f * sy, 60f * sx, 17f * sy)
                    cubicTo(61.5f * sx, 10f * sy, 52f * sx, 5f * sy, 38f * sx, 4.5f * sy)
                }
                val part = if (t >= 1f) loop else Path().also { out ->
                    val measure = PathMeasure()
                    measure.setPath(loop, false)
                    measure.getSegment(0f, measure.length * t, out, true)
                }
                translate(-4.dp.toPx(), (size.height - h) / 2) {
                    drawPath(part, ink, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            word,
            style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
            color = if (on) lerp(ink, colors.sumi, 0.35f) else colors.usuzumi1,
            maxLines = 1,
        )
    }
}

/**
 * The ribbon (栞) in your ink, from the page's top edge (behind the status bar) down past the
 * header: a bookmark. On the page it marks it hangs short; elsewhere it hangs longer, which shows
 * you've moved and where to tap to go back. A long press moves it to the page shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlannerRibbon(here: Boolean, words: String, modifier: Modifier = Modifier, onGo: () -> Unit, onMoveHere: () -> Unit) {
    val ink = stationery.you
    val length by animateDpAsState(if (here) 38.dp else 66.dp, tween(if (reducedMotion()) 0 else 220), label = "ribbon")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .padding(end = 14.dp)
            .size(30.dp, statusTop + length)
            .shadow(2.dp, RibbonShape)
            .clip(RibbonShape)
            .background(ink)
            .drawBehind {
                var x = 0f
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    x += 3.dp.toPx()
                }
            }
            .combinedClickable(
                onClickLabel = if (here) null else "Go to your bookmark",
                onLongClickLabel = "Put the bookmark here",
                onLongClick = onMoveHere,
            ) { if (!here) onGo() }
            .onRightClick(onMoveHere)
            .semantics { contentDescription = if (here) "Your bookmark, on this page" else "Your bookmark, on $words" },
    )
}

/**
 * The sticker sheet's corner, curling up at the bottom right of the page, three stickers on it:
 * a tap opens the sheet. It takes the place of the stickers' tab at the edge.
 */
@Composable
internal fun StickersCorner(modifier: Modifier = Modifier, spoken: String = "Stick stickers on the planner", onClick: () -> Unit) {
    val colors = paper
    val ink = stationery.you
    val sheet = lerp(colors.kami, Color.White, if (stationery.isNight) 0.06f else 0.5f)
    val back = colors.hyoshi
    Canvas(
        modifier
            .size(58.dp)
            .clickable(role = Role.Button, onClickLabel = spoken, onClick = onClick)
            .semantics { contentDescription = "Stickers" },
    ) {
        val u = size.width / 58f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val corner = Path().apply { moveTo(58 * u, 6 * u); lineTo(58 * u, 58 * u); lineTo(6 * u, 58 * u); close() }
        // A soft shadow up and to the left, where the sheet lifts off the page.
        for (k in 1..3) translate(-k * 0.6f * u, -k * 0.6f * u) { drawPath(corner, Color.Black.copy(alpha = 0.05f)) }
        drawPath(corner, sheet)
        // The curled edge along the fold.
        val edge = Path().apply { moveTo(58 * u, 6 * u); lineTo(6 * u, 58 * u); lineTo(18 * u, 46 * u); lineTo(46 * u, 18 * u); close() }
        drawPath(edge, back)
        val rim = sheet
        drawCircle(Color(0xFFE48AA9), 6 * u, p(44f, 44f))
        drawCircle(rim, 6 * u, p(44f, 44f), style = Stroke(2 * u))
        drawCircle(lerp(ink, Color.White, 0.35f), 4 * u, p(50f, 31f))
        drawCircle(rim, 4 * u, p(50f, 31f), style = Stroke(1.6f * u))
        drawCircle(Color(0xFFF0CB90), 4 * u, p(31f, 50f))
        drawCircle(rim, 4 * u, p(31f, 50f), style = Stroke(1.6f * u))
    }
}

/**
 * The coloured rule under the days, ruled by hand: a calligraphic stroke in [ink], heavier where
 * the pen goes down and thinning along the page, finished with a small loop past its end. [y] is
 * the line's centre from the top (or the bottom, [fromBottom]); the rule starts [start] in, and its
 * loop reaches [overhang] past the right edge.
 */
internal fun Modifier.inkRule(
    ink: Color,
    y: androidx.compose.ui.unit.Dp,
    start: androidx.compose.ui.unit.Dp = 0.dp,
    overhang: androidx.compose.ui.unit.Dp = 6.dp,
    fromBottom: Boolean = false,
): Modifier = drawBehind {
    val cy = if (fromBottom) size.height - y.toPx() else y.toPx()
    translate(start.toPx(), 0f) { drawInkRule(ink, cy, size.width - start.toPx() + overhang.toPx()) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkRule(ink: Color, cy: Float, reach: Float) {
    val d = 1.dp.toPx()
    val end = reach - 34.5f * d
    val stroke = Path().apply {
        moveTo(0f, cy - 1.75f * d)
        cubicTo(10 * d, cy - 2.15f * d, 30 * d, cy - 1.15f * d, 60 * d, cy - 0.85f * d)
        lineTo(end, cy - 0.65f * d)
        lineTo(end, cy + 0.65f * d)
        lineTo(60 * d, cy + 0.95f * d)
        cubicTo(30 * d, cy + 1.25f * d, 10 * d, cy + 2.15f * d, 0f, cy + 1.75f * d)
        cubicTo(-1.2f * d, cy + 1.35f * d, -1.2f * d, cy - 1.35f * d, 0f, cy - 1.75f * d)
        close()
    }
    drawPath(stroke, ink)
    val o = end - 0.5f * d
    val loop = Path().apply {
        moveTo(o, cy)
        cubicTo(o + 8.5f * d, cy, o + 16.5f * d, cy - 0.5f * d, o + 20.5f * d, cy - 4f * d)
        cubicTo(o + 24f * d, cy - 7.5f * d, o + 22.5f * d, cy - 11.5f * d, o + 19f * d, cy - 11f * d)
        cubicTo(o + 15.5f * d, cy - 10.5f * d, o + 15f * d, cy - 5f * d, o + 19f * d, cy - 2f * d)
        cubicTo(o + 23f * d, cy + 1f * d, o + 29.5f * d, cy + 1f * d, o + 34.5f * d, cy - 2.5f * d)
    }
    drawPath(loop, ink, style = Stroke(1.3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The planner's dot grid (方眼), lined up with the page's own wherever it's drawn: for a piece of
 * paper laid over the page (the days and the all-day bar, which the hours scroll under).
 */
@Composable
internal fun Modifier.pageDots(dot: Color): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this.onPlaced { origin = it.positionInRoot() }.drawBehind {
        val step = 10.dp.toPx()
        val r = 0.8.dp.toPx()
        val x0 = ((step / 2 - origin.x) % step + step) % step
        var y = ((step / 2 - origin.y) % step + step) % step
        while (y < size.height) {
            var x = x0
            while (x < size.width) {
                drawCircle(dot, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

/** The beni of the planner's small marks: the now line, a solar term's dot, a holiday. */
@Composable
internal fun beni(): Color = dayInk(java.time.DayOfWeek.SUNDAY, paper.sumi, stationery.isNight)

/** Room the sticker sheet's corner takes at the page's bottom right. */
internal val CORNER_ROOM = 58.dp

/**
 * Bringing something into view on a page whose top [covered] pixels lie under a strip laid over it
 * (the days and the all-day bar): it comes to rest just below the strip, not under it.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class UnderTheStrip : androidx.compose.foundation.gestures.BringIntoViewSpec {
    var covered = 0f

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val top = covered.coerceIn(0f, containerSize)
        val leading = offset - top
        val trailing = offset + size - containerSize
        return when {
            leading >= 0f && trailing <= 0f -> 0f
            leading < 0f && trailing > 0f -> 0f
            leading < 0f -> leading
            size <= containerSize - top -> trailing
            else -> leading
        }
    }
}
