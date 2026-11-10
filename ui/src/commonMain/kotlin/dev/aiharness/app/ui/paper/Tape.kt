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

/** The ways to tape something down (the Placements board in `docs/design/v0.5.md`). */
enum class TapePlacement {
    /** Down an edge, past both corners: letters and notes. */
    STRIP,

    /** Two corners, opposite each other: cards and the cutting. */
    CORNERS,

    /** One piece across an edge, centred: polaroids. */
    ACROSS,

    /** A pair at the two corners of an edge, angled in. */
    PAIR,

    /** One small piece at a corner: memory slips. */
    CORNER,

    /** Held down all round (doesn't turn). */
    FOUR_CORNERS,

    /** A piece across each end. */
    TOP_AND_BOTTOM,

    /** Paper album corners in the ink, without a pattern (doesn't turn). */
    PHOTO_CORNERS,
}

/** One piece of tape: centred at ([cx], [cy]) dp, [w] by [h] before turning by [r] degrees; [photo] marks a paper corner (0 to 3, clockwise from top left). */
internal data class TapePiece(val cx: Float, val cy: Float, val w: Float, val h: Float, val r: Float, val photo: Int? = null)

/**
 * Where each piece goes on a thing [w] by [h] dp, for [placement] and [turn] (quarter turns
 * clockwise). Each placement is drawn upright and the turn rotates the layout round the thing.
 * Corner pieces sit about 9dp in along the corner's diagonal, across it, and are long enough that
 * both ends run onto the page (its white tip still peeks out past the tape).
 */
internal fun tapePieces(placement: TapePlacement, w: Float, h: Float, turn: Int = 0, length: Float? = null, thickness: Float? = null): List<TapePiece> {
    val k = if (placement == TapePlacement.FOUR_CORNERS || placement == TapePlacement.PHOTO_CORNERS) 0 else Math.floorMod(turn, 4)
    val wide = if (k % 2 == 1) h else w
    val tall = if (k % 2 == 1) w else h
    val t = thickness ?: 13f
    val upright = when (placement) {
        TapePlacement.STRIP -> listOf(TapePiece(-0.5f, tall / 2, t, length ?: (tall + 16f), -0.7f))
        TapePlacement.CORNERS -> listOf(TapePiece(9f, 9f, length ?: 50f, t, -45f), TapePiece(wide - 9f, tall - 9f, length ?: 50f, t, -44f))
        TapePlacement.ACROSS -> listOf(TapePiece(wide / 2, 0f, length ?: 58f, thickness ?: 14f, -4f))
        TapePlacement.PAIR -> listOf(TapePiece(9f, 9f, length ?: 50f, t, -42f), TapePiece(wide - 9f, 9f, length ?: 50f, t, 42f))
        TapePlacement.CORNER -> listOf(TapePiece(8f, 8f, length ?: 44f, thickness ?: 11f, -45f))
        TapePlacement.FOUR_CORNERS -> (length ?: 46f).let { l ->
            val th = thickness ?: 12f
            listOf(TapePiece(8f, 8f, l, th, -45f), TapePiece(wide - 8f, 8f, l, th, 44f), TapePiece(8f, tall - 8f, l, th, 46f), TapePiece(wide - 8f, tall - 8f, l, th, -45f))
        }
        TapePlacement.TOP_AND_BOTTOM -> listOf(TapePiece(wide / 2, -0.5f, length ?: 50f, t, -3f), TapePiece(wide / 2, tall + 0.5f, length ?: 50f, t, 2f))
        TapePlacement.PHOTO_CORNERS -> listOf(
            TapePiece(7f, 7f, 20f, 20f, 0f, 0), TapePiece(wide - 7f, 7f, 20f, 20f, 0f, 1),
            TapePiece(wide - 7f, tall - 7f, 20f, 20f, 0f, 2), TapePiece(7f, tall - 7f, 20f, 20f, 0f, 3),
        )
    }
    return upright.map { p ->
        val (x, y) = when (k) {
            1 -> (w - p.cy) to p.cx
            2 -> (w - p.cx) to (h - p.cy)
            3 -> p.cy to (h - p.cx)
            else -> p.cx to p.cy
        }
        p.copy(cx = x, cy = y, r = p.r + 90f * k)
    }
}

/**
 * [tape] holding this down in [placement], turned [turn] quarter turns. Put it first in the
 * modifier chain so the tape draws over the thing's background and content.
 */
fun Modifier.taped(tape: Tape, placement: TapePlacement, turn: Int = 0, length: Dp? = null, thickness: Dp? = null): Modifier = drawWithCache {
    val d = density
    val pieces = tapePieces(placement, size.width / d, size.height / d, turn, length?.value, thickness?.value)
    onDrawWithContent {
        drawContent()
        pieces.forEach { drawPiece(it, tape) }
    }
}

/** One piece: a strip of tape along its longer side, or a paper photo corner. */
internal fun DrawScope.drawPiece(p: TapePiece, tape: Tape) {
    val d = density
    val centre = Offset(p.cx * d, p.cy * d)
    if (p.photo != null) {
        val half = p.w * d / 2
        // A right triangle over the corner, its long side facing in.
        val corner = Path().apply {
            when (p.photo) {
                0 -> { moveTo(-half, -half); lineTo(half, -half); lineTo(-half, half) }
                1 -> { moveTo(-half, -half); lineTo(half, -half); lineTo(half, half) }
                2 -> { moveTo(half, -half); lineTo(half, half); lineTo(-half, half) }
                else -> { moveTo(-half, -half); lineTo(-half, half); lineTo(half, half) }
            }
            close()
        }
        translate(centre.x, centre.y) {
            if (tape.shadow) translate(0f, 0.6.dp.toPx()) { drawPath(corner, Color.Black.copy(alpha = 0.16f)) }
            drawPath(corner, tape.base.copy(alpha = tape.alpha))
        }
        return
    }
    val along = p.w >= p.h
    val length = (if (along) p.w else p.h) * d
    val thick = (if (along) p.h else p.w) * d
    val angle = Math.toRadians((p.r + if (along) 0f else 90f).toDouble())
    val half = Offset((kotlin.math.cos(angle) * length / 2).toFloat(), (kotlin.math.sin(angle) * length / 2).toFloat())
    drawTape(centre - half, centre + half, thick, tape)
}

/**
 * Two pieces of tape on opposing corners (upper left and lower right), each about [reach] in along
 * the corner's diagonal and across it, long enough to reach the page at both ends.
 */
fun Modifier.cornerTape(tape: Tape, length: Dp = 50.dp, thickness: Dp = 13.dp, reach: Dp = 9.dp): Modifier = drawWithCache {
    val d = density
    val r = reach.value
    val pieces = listOf(
        TapePiece(r, r, length.value, thickness.value, -45f),
        TapePiece(size.width / d - r, size.height / d - r, length.value, thickness.value, -44f),
    )
    onDrawWithContent {
        drawContent()
        pieces.forEach { drawPiece(it, tape) }
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
fun Modifier.topPairTape(tape: Tape, length: Dp = 50.dp, thickness: Dp = 13.dp): Modifier =
    taped(tape, TapePlacement.PAIR, length = length, thickness = thickness)

/** The tape in the current theme's version of [inkName], with the time of day's shade (or [shade]). */
@Composable
fun tapeOf(inkName: String?, pattern: TapePattern, shade: TapeShade = TapeShade.THEME): Tape = tape(stationery.ink(inkName), pattern, shade)
