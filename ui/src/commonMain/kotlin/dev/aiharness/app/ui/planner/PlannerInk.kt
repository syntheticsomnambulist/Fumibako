package dev.aiharness.app.ui.planner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.theme.LocalHand

/*
 * How things are written in the planner (docs/design/calendar.md, Third pass, "The pages"): your
 * entries in your persona's hand, your plans in ink with a highlighter over them, what the phone's
 * calendars hold pencilled in, and nothing filled in behind them, so an entry in busy hours sits on
 * the wash as it would on paper.
 */

/** Text in the hand here (yours in your planner, a persona's on its slips), [size] matched to the letters. */
@Composable
internal fun handText(size: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified, crossed: Boolean = false): TextStyle {
    val hand = LocalHand.current
    return TextStyle(
        fontFamily = hand.family,
        fontSize = (size.value * hand.scale).sp,
        lineHeight = if (lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else (lineHeight.value * hand.scale).sp,
        textDecoration = if (crossed) TextDecoration.LineThrough else null,
    )
}

/**
 * A highlighter's stroke in [ink] behind what's here: see-through, with streaks along the stroke,
 * the ink a little darker where the pen comes down and lifts, and ends slanted by the chisel tip.
 * Stroked [down] the column (a plan's block on the week) or across (a name on one line). [faded]
 * for a cancelled plan.
 */
internal fun Modifier.highlighter(ink: Color, down: Boolean, faded: Boolean = false): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind
    val d = 1.dp.toPx()
    val k = if (faded) 0.5f else 1f
    val shape = Path().apply {
        if (down) {
            // The top and bottom cut at a slant; the sides a little uneven.
            moveTo(0f, 4 * d)
            lineTo(w * 0.35f, 1.5f * d)
            lineTo(w, 0f)
            lineTo(w, h * 0.45f)
            lineTo(w - d, h * 0.7f)
            lineTo(w, h - 4 * d)
            lineTo(w * 0.65f, h - 1.5f * d)
            lineTo(0f, h)
            lineTo(d, h * 0.6f)
            lineTo(0f, h * 0.3f)
        } else {
            moveTo(3 * d, 0f)
            lineTo(w, d)
            lineTo(w - 3 * d, h)
            lineTo(0f, h - d)
        }
        close()
    }
    clipPath(shape) {
        val end = (6 * d / if (down) h else w).coerceAtMost(0.3f)
        val stops = arrayOf(
            0f to ink.copy(alpha = 0.46f * k),
            end to ink.copy(alpha = 0.30f * k),
            0.5f to ink.copy(alpha = 0.27f * k),
            1f - end to ink.copy(alpha = 0.30f * k),
            1f to ink.copy(alpha = 0.46f * k),
        )
        drawRect(if (down) Brush.verticalGradient(*stops) else Brush.horizontalGradient(*stops))
        // Streaks along the stroke, where the felt tip ran drier.
        val streak = Color.White.copy(alpha = 0.22f * k)
        if (down) {
            var x = 6 * d
            while (x < w) {
                drawRect(streak, Offset(x, 0f), androidx.compose.ui.geometry.Size(d, h))
                x += 12 * d
            }
        } else {
            var y = 4 * d
            while (y < h) {
                drawRect(streak, Offset(0f, y), androidx.compose.ui.geometry.Size(w, d))
                y += 9 * d
            }
        }
    }
}

/**
 * Pencilled in: a box sketched round what's here with four quick strokes of graphite, each running
 * a little past its corner. Fainter for one you're not going to.
 */
internal fun Modifier.pencilled(line: Color, faded: Boolean = false): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind
    val d = 1.dp.toPx()
    val box = Path().apply {
        moveTo(-0.6f * d, 2 * d)
        cubicTo(w * 0.35f, 1.2f * d, w * 0.65f, 2.6f * d, w + 0.7f * d, 1.6f * d)
        moveTo(w - 1.8f * d, -0.7f * d)
        cubicTo(w - d, h * 0.35f, w - 2.2f * d, h * 0.65f, w - 1.4f * d, h + 0.7f * d)
        moveTo(w + 0.7f * d, h - 1.8f * d)
        cubicTo(w * 0.65f, h - 1.1f * d, w * 0.35f, h - 2.4f * d, -0.7f * d, h - 1.5f * d)
        moveTo(1.6f * d, h + 0.7f * d)
        cubicTo(0.9f * d, h * 0.65f, 2.1f * d, h * 0.35f, 1.3f * d, -0.7f * d)
    }
    drawPath(box, line.copy(alpha = if (faded) 0.45f else 0.9f), style = Stroke(d, cap = StrokeCap.Round))
}

/** Graphite, for the words of what's pencilled in (lighter on night paper). */
@Composable
internal fun graphite(): Color = if (dev.aiharness.app.ui.theme.stationery.isNight) Color(0xFFBBB4AC) else Color(0xFF625D58)

/** Graphite for a pencilled box's strokes. */
@Composable
internal fun graphiteLine(): Color = if (dev.aiharness.app.ui.theme.stationery.isNight) Color(0xFF9C958D) else Color(0xFF8A837C)

/** The busy hours' wash. */
val BusyWash = Color(0x2BB09C88)

/** The bracket down the edge of busy hours, a tick at each end. */
val BusyBracket = Color(0xFFA8927C)

/** Draws busy hours from [top] to [bottom] across [left] to [left] + [width]: the wash, and the bracket at its left edge. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.busyHours(left: Float, top: Float, width: Float, bottom: Float, bracket: Boolean) {
    drawRect(BusyWash, Offset(left, top), androidx.compose.ui.geometry.Size(width, bottom - top))
    if (!bracket) return
    val line = 1.2.dp.toPx()
    val x = left + 1.dp.toPx() + line / 2
    val tick = 5.dp.toPx()
    drawLine(BusyBracket, Offset(x, top + line / 2), Offset(x, bottom - line / 2), line)
    drawLine(BusyBracket, Offset(x - line / 2, top + line / 2), Offset(x + tick - line / 2, top + line / 2), line)
    drawLine(BusyBracket, Offset(x - line / 2, bottom - line / 2), Offset(x + tick - line / 2, bottom - line / 2), line)
}

/** A plan's box to tick, in [ink]: ticked by hand when it's [done], so it agrees with its 済. */
@Composable
internal fun PlanTick(done: Boolean, ink: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
        val d = 1.dp.toPx()
        drawRoundRect(ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2 * d), style = Stroke(1.3f * d))
        if (done) {
            val w = size.width
            val h = size.height
            val tick = Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                quadraticTo(w * 0.32f, h * 0.62f, w * 0.42f, h * 0.82f)
                quadraticTo(w * 0.62f, h * 0.36f, w * 1.05f, h * 0.02f)
            }
            drawPath(tick, ink, style = Stroke(1.6f * d, cap = StrokeCap.Round))
        }
    }
}
