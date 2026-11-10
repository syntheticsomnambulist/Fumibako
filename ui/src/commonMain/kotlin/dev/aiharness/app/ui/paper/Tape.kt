package dev.aiharness.app.ui.paper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.TapeShade
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/*
 * Washi tape, drawn rather than imaged so any ink and pattern works: translucent ink (fully opaque),
 * a pattern in white, zigzag torn ends and a slight hand-placed tilt. Measurements follow the
 * mockups in docs/design (13dp strips, 14dp pieces, 2.6dp teeth).
 */

/**
 * How a piece of tape looks. With [paper] it's the light shade (the ink mixed into that paper, the
 * pattern in the paper's colour); without, the dark one (the ink itself, the pattern in white).
 * Make one with [tape], which picks the shade from a [TapeShade] and the time of day.
 */
@Stable
data class Tape(val ink: Color, val pattern: TapePattern, val alpha: Float = 1f, val shadow: Boolean = true, val paper: Color? = null) {
    /** The tape's own colour. */
    val base: Color get() = paper?.let { lerp(it, ink, LIGHT_MIX) } ?: ink

    companion object {
        /** How much ink a light tape has in it. */
        const val LIGHT_MIX = 0.4f
    }
}

/**
 * [ink] as tape in [pattern], light or dark as [shade] has it: with the time of day (light by day,
 * dark at night), or always one. A light tape at night is still mixed with the day's paper.
 */
@Composable
fun tape(ink: Color, pattern: TapePattern, shade: TapeShade = TapeShade.THEME, alpha: Float = 1f, shadow: Boolean = true): Tape {
    val s = stationery
    val light = when (shade) {
        TapeShade.LIGHT -> true
        TapeShade.DARK -> false
        TapeShade.THEME -> !s.isNight
    }
    return Tape(ink, pattern, alpha, shadow, paper = if (light) s.dayColors.kami else null)
}

/** A persona's tape: its ink, pattern and shade. */
@Composable
fun tapeOf(persona: dev.aiharness.core.model.Persona): Tape = tape(stationery.ink(persona.ink), persona.tapePattern, persona.tapeShade)

/** Your tape: your persona's ink, pattern and shade. */
@Composable
fun yourTape(pattern: TapePattern = stationery.youPattern): Tape = tape(stationery.you, pattern, stationery.youShade)

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
                val base = tape.base.copy(alpha = tape.alpha)
                drawRect(base, topLeft = Offset(0f, -thickness / 2), size = Size(length, thickness))
                drawTapePattern(tape, Rect(0f, -thickness / 2, length, thickness / 2))
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

/**
 * [tape]'s pattern over [area] (a piece of tape, in its own coordinates): laid out from its centre,
 * rows symmetric about the centreline and repeating outwards from the middle along its length, each
 * tile scaled so whole repeats fit across it (so no half-rows at the edges, and both torn ends cut
 * it alike).
 */
private fun DrawScope.drawTapePattern(tape: Tape, area: Rect) {
    val tile = TapeTiles.of(tape.pattern) ?: return
    val light = tape.paper != null
    val fg = tape.paper ?: Color.White
    val layer = (if (light) 0.95f else tile.darkAlpha) * tape.alpha
    val natural = tile.height * density
    val rows = maxOf(1, Math.round(area.height / natural))
    val unit = area.height / rows / tile.height
    drawTiles(tile, area, fg, tape.base, layer, unit, centered = true)
}

/**
 * A pattern laid over [area] in [fg] (with [base] for the parts that cover what's under them),
 * the whole at [alpha]: [unit] pixels to the pattern's unit (a dp at its own size); [centered] lays
 * it out from the middle, else from the area's top left (a box's face).
 */
fun DrawScope.drawPatternOver(pattern: TapePattern, area: Rect, fg: Color, base: Color, alpha: Float, unit: Float, centered: Boolean) {
    val tile = TapeTiles.of(pattern) ?: return
    drawTiles(tile, area, fg, base, alpha, unit, centered)
}

private fun DrawScope.drawTiles(tile: TapeTiles.Tile, area: Rect, fg: Color, base: Color, alpha: Float, unit: Float, centered: Boolean) {
    val tw = tile.width * unit
    val th = tile.height * unit
    if (tw <= 0f || th <= 0f) return
    val x0 = if (centered) area.center.x - tw / 2 - kotlin.math.ceil((area.width / 2 + tw) / tw).toFloat() * tw else area.left
    val y0 = if (centered) area.top else area.top
    // One layer for the whole pattern, so overlapping strokes don't darken where they cross.
    drawContext.canvas.saveLayer(area, Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) })
    if (tile.diagonal) {
        // Stripes: bands at 45 degrees, repeating outwards from the middle.
        val period = tile.width * unit * sqrt(2f)
        val h = area.height
        var x = (if (centered) area.center.x else area.left) - kotlin.math.ceil((area.width / 2 + h) / period).toFloat() * period
        while (x < area.right + h) {
            drawLine(fg.copy(alpha = tile.inner), Offset(x, area.bottom), Offset(x + h, area.top), strokeWidth = tile.band * unit)
            x += period
        }
    } else {
        var y = y0
        while (y < area.bottom - 0.01f) {
            var x = x0
            while (x < area.right) {
                if (x + tw > area.left) {
                    clipRect(x, y, x + tw, y + th) {
                        withTransform({ translate(x, y); scale(unit, unit, Offset.Zero) }) { tile.draw(this, fg, base, unit) }
                    }
                }
                x += tw
            }
            y += th
        }
    }
    drawContext.canvas.restore()
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

/** Two pieces of tape at the top corners, angled in towards each other. */
fun Modifier.topPairTape(tape: Tape, length: Dp = 44.dp, thickness: Dp = 14.dp, inset: Dp = 16.dp): Modifier = drawWithCache {
    val l = length.toPx()
    val left = Offset(inset.toPx() + l / 2, -1.dp.toPx())
    val right = Offset(size.width - inset.toPx() - l / 2, -1.dp.toPx())
    onDrawWithContent {
        drawContent()
        rotate(-8f, pivot = left) { drawTape(Offset(left.x - l / 2, left.y), Offset(left.x + l / 2, left.y), thickness.toPx(), tape) }
        rotate(7f, pivot = right) { drawTape(Offset(right.x - l / 2, right.y), Offset(right.x + l / 2, right.y), thickness.toPx(), tape) }
    }
}

/** The tape in the current theme's version of [inkName], with the time of day's shade (or [shade]). */
@Composable
fun tapeOf(inkName: String?, pattern: TapePattern, shade: TapeShade = TapeShade.THEME): Tape = tape(stationery.ink(inkName), pattern, shade)
