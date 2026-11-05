package dev.aiharness.app.ui.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.TapePattern
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/*
 * Washi tape, drawn rather than imaged so any ink and pattern works: translucent ink (fully opaque),
 * a pattern in white, zigzag torn ends and a slight hand-placed tilt. Measurements follow the
 * mockups in docs/design (13dp strips, 14dp pieces, 2.6dp teeth).
 */

/** How a piece of tape looks. */
@Stable
data class Tape(val ink: Color, val pattern: TapePattern, val alpha: Float = 1f, val shadow: Boolean = true)

/**
 * Draws one piece of tape along the line from [start] to [end] (its torn ends sit on those points).
 * Patterns are drawn in the tape's own coordinates, so they turn with it like real tape.
 */
fun DrawScope.drawTape(start: Offset, end: Offset, thickness: Float, tape: Tape, tooth: Float = 2.6.dp.toPx()) {
    val length = hypot(end.x - start.x, end.y - start.y)
    if (length <= 0f || thickness <= 0f) return
    val angle = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
    val outline = tapeOutline(length, thickness, tooth)
    translate(start.x, start.y) {
        rotate(angle, pivot = Offset.Zero) {
            if (tape.shadow) {
                translate(0f, 0.6.dp.toPx()) { drawPath(outline, Color.Black.copy(alpha = if (tape.alpha > 0.6f) 0.16f else 0.08f)) }
            }
            clipPath(outline) {
                drawRect(tape.ink.copy(alpha = tape.alpha), topLeft = Offset(0f, -thickness / 2), size = Size(length, thickness))
                drawPattern(tape.pattern, Rect(0f, -thickness / 2, length, thickness / 2))
            }
        }
    }
}

/** Tape outline in local coordinates: x from 0 to [length], y centred on 0, teeth on both ends. */
private fun tapeOutline(length: Float, thickness: Float, tooth: Float): Path = Path().apply {
    val half = thickness / 2
    // Left end: teeth pointing left (peaks at x = 0, valleys at x = tooth).
    var y = -half
    moveTo(tooth, y)
    var peak = true
    while (y < half) {
        val next = (y + tooth).coerceAtMost(half)
        val x = if (peak) 0f else tooth
        lineTo(x, next)
        y = next
        peak = !peak
    }
    // Right end, bottom to top: teeth pointing right.
    lineTo(length - tooth, half)
    y = half
    peak = true
    while (y > -half) {
        val next = (y - tooth).coerceAtLeast(-half)
        val x = if (peak) length else length - tooth
        lineTo(x, next)
        y = next
        peak = !peak
    }
    close()
}

private fun DrawScope.drawPattern(pattern: TapePattern, area: Rect) {
    val white = Color.White
    when (pattern) {
        TapePattern.PLAIN -> Unit
        TapePattern.DOTS -> {
            val step = 7.dp.toPx()
            val radius = 1.4.dp.toPx()
            var x = area.left + step / 2
            while (x < area.right) {
                var y = area.top + step / 2
                while (y < area.bottom + step) {
                    drawCircle(white.copy(alpha = 0.6f), radius, Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        TapePattern.STRIPES -> {
            // Diagonal bands 2.5dp wide every 6dp, across the tape.
            val period = 6.dp.toPx() * sqrt(2f)
            val band = 2.5.dp.toPx()
            val h = area.height
            var x = area.left - h
            while (x < area.right + h) {
                drawLine(white.copy(alpha = 0.38f), Offset(x, area.bottom), Offset(x + h, area.top), strokeWidth = band)
                x += period
            }
        }
        TapePattern.GINGHAM -> {
            val cell = 8.dp.toPx()
            val bandColor = white.copy(alpha = 0.3f)
            var x = area.left
            while (x < area.right) {
                drawRect(bandColor, Offset(x, area.top), Size(cell / 2, area.height))
                x += cell
            }
            var y = area.top
            while (y < area.bottom) {
                drawRect(bandColor, Offset(area.left, y), Size(area.width, cell / 2))
                y += cell
            }
        }
    }
}

enum class TapeSide { Start, End }

/**
 * A strip of tape down one edge, half on the paper, running past the top and bottom corners. Put it
 * first in the modifier chain so it draws over the card's background and content.
 */
fun Modifier.washiStrip(
    tape: Tape,
    side: TapeSide = TapeSide.Start,
    tiltDegrees: Float = -0.7f,
    width: Dp = 13.dp,
    inset: Dp = 6.dp,
    overhang: Dp = 5.dp,
): Modifier = drawWithCache {
    val w = width.toPx()
    val centreX = if (side == TapeSide.Start) -inset.toPx() + w / 2 else size.width + inset.toPx() - w / 2
    val top = Offset(centreX, -overhang.toPx())
    val bottom = Offset(centreX, size.height + overhang.toPx())
    onDrawWithContent {
        drawContent()
        rotate(tiltDegrees, pivot = Offset(centreX, size.height / 2)) {
            drawTape(top, bottom, w, tape)
        }
    }
}

/**
 * Two pieces of tape on opposing corners (upper left and lower right), the rule for corner tape.
 * [angleDegrees] is measured from horizontal; the pieces are centred [reach] in from each corner.
 */
fun Modifier.cornerTape(
    tape: Tape,
    length: Dp = 40.dp,
    thickness: Dp = 14.dp,
    angleDegrees: Float = -35f,
    reach: Dp = 13.dp,
    lift: Dp = 3.dp,
): Modifier = drawWithCache {
    val l = length.toPx()
    val dx = reach.toPx()
    val dy = lift.toPx()
    val rad = Math.toRadians(angleDegrees.toDouble())
    val half = Offset((kotlin.math.cos(rad) * l / 2).toFloat(), (kotlin.math.sin(rad) * l / 2).toFloat())
    val upperLeft = Offset(dx, dy)
    val lowerRight = Offset(size.width - dx, size.height - dy)
    onDrawWithContent {
        drawContent()
        drawTape(upperLeft - half, upperLeft + half, thickness.toPx(), tape)
        drawTape(lowerRight - half, lowerRight + half, thickness.toPx(), tape)
    }
}

/** One piece of tape across the top edge, centred (holds a polaroid to the page). */
fun Modifier.topTape(
    tape: Tape,
    length: Dp = 58.dp,
    thickness: Dp = 14.dp,
    tiltDegrees: Float = -4f,
    drop: Dp = -1.dp,
): Modifier = drawWithCache {
    val l = length.toPx()
    val centre = Offset(size.width / 2, drop.toPx())
    onDrawWithContent {
        drawContent()
        rotate(tiltDegrees, pivot = centre) {
            drawTape(Offset(centre.x - l / 2, centre.y), Offset(centre.x + l / 2, centre.y), thickness.toPx(), tape)
        }
    }
}

/** The persona-or-user tape in the current theme's version of [inkName]. */
@Composable
fun tapeOf(inkName: String?, pattern: TapePattern): Tape = Tape(stationery.ink(inkName), pattern)
