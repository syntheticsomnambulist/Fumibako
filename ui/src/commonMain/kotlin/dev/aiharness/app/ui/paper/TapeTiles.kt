package dev.aiharness.app.ui.paper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.aiharness.core.model.TapePattern

/**
 * The tape patterns as tiles, from the Tapes board in the v0.5 design (`docs/design/v0.5.md`, *The
 * stationery update*): each a width and height in its own units (a dp at the board's size, after
 * the board's own scale), drawn in the pattern's colour over the tape. A tile is drawn once per
 * repeat, clipped to its cell, inside a layer at [Tile.darkAlpha] (dark) or 0.95 (light).
 */
internal object TapeTiles {
    class Tile(
        val width: Float,
        val height: Float,
        /** The whole pattern's opacity on dark tape (white on the ink). */
        val darkAlpha: Float = 0.55f,
        /** Stripes are bands across the tape rather than tiles. */
        val diagonal: Boolean = false,
        val band: Float = 0f,
        /** A band's own opacity within the pattern. */
        val inner: Float = 1f,
        /** Draws one tile at the origin, in pattern units: the pattern's colour, the tape's own, and pixels to a unit. */
        val draw: DrawScope.(fg: Color, base: Color, unit: Float) -> Unit = { _, _, _ -> },
    )

    fun of(pattern: TapePattern): Tile? = TILES[pattern]

    /** A stroke at least [least] dp wide however small the tile is drawn. */
    private fun DrawScope.stroke(width: Float, unit: Float, least: Float = 0.75f) = Stroke(maxOf(width, least * density / unit))

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    private fun poly(vararg xy: Float) = path {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) {
            lineTo(xy[i], xy[i + 1])
            i += 2
        }
        close()
    }

    private val HEARTS = listOf(
        path {
            moveTo(4f, 7f); cubicTo(1.6f, 5.4f, 1f, 4f, 1.6f, 2.9f); cubicTo(2.3f, 1.8f, 3.6f, 2f, 4f, 3f)
            cubicTo(4.4f, 2f, 5.7f, 1.8f, 6.4f, 2.9f); cubicTo(7f, 4f, 6.4f, 5.4f, 4f, 7f); close()
        },
        path {
            moveTo(12f, 15f); cubicTo(9.6f, 13.4f, 9f, 12f, 9.6f, 10.9f); cubicTo(10.3f, 9.8f, 11.6f, 10f, 12f, 11f)
            cubicTo(12.4f, 10f, 13.7f, 9.8f, 14.4f, 10.9f); cubicTo(15f, 12f, 14.4f, 13.4f, 12f, 15f); close()
        },
    )

    private val ARROWS = listOf(
        poly(0f, 0f, 4f, 4f, 8f, 0f, 8f, 8f, 4f, 12f, 0f, 8f),
        poly(8f, 8f, 12f, 12f, 16f, 8f, 16f, 16f, 12f, 20f, 8f, 16f),
        poly(8f, -8f, 12f, -4f, 16f, -8f, 16f, 0f, 12f, 4f, 8f, 0f),
    )

    private val ASANOHA = path {
        fun l(x1: Float, y1: Float, x2: Float, y2: Float) { moveTo(x1, y1); lineTo(x2, y2) }
        l(0f, 0f, 20f, 0f); l(0f, 17.32f, 20f, 17.32f); l(0f, 34.64f, 20f, 34.64f)
        l(0f, 0f, 20f, 34.64f); l(20f, 0f, 0f, 34.64f)
        l(0f, 0f, 10f, 5.77f); l(10f, 5.77f, 20f, 0f); l(10f, 5.77f, 10f, 17.32f)
        l(-10f, 17.32f, 0f, 11.55f); l(0f, 11.55f, 10f, 17.32f); l(0f, 11.55f, 0f, 0f)
        l(10f, 17.32f, 20f, 11.55f); l(20f, 11.55f, 30f, 17.32f); l(20f, 11.55f, 20f, 0f)
        l(10f, 17.32f, 10f, 28.87f); l(0f, 34.64f, 10f, 28.87f); l(10f, 28.87f, 20f, 34.64f)
        l(-10f, 17.32f, 0f, 23.09f); l(0f, 23.09f, 10f, 17.32f); l(0f, 23.09f, 0f, 34.64f)
        l(10f, 17.32f, 20f, 23.09f); l(20f, 23.09f, 30f, 17.32f); l(20f, 23.09f, 20f, 34.64f)
    }

    private fun hexagon(cx: Float, top: Float, r: Float, rise: Float) = poly(
        cx, top, cx + r, top + rise, cx + r, top + 3 * rise, cx, top + 4 * rise, cx - r, top + 3 * rise, cx - r, top + rise,
    )

    private val KIKKO = listOf(
        hexagon(5.196f, 0f, 5.196f, 3f),
        hexagon(5.196f, 1.8f, 3.637f, 2.1f),
        hexagon(0f, 10.8f, 3.637f, 2.1f),
        hexagon(10.392f, 10.8f, 3.637f, 2.1f),
        hexagon(0f, -7.2f, 3.637f, 2.1f),
        hexagon(10.392f, -7.2f, 3.637f, 2.1f),
    )
    private val KIKKO_STEM = path { moveTo(5.196f, 12f); lineTo(5.196f, 18f) }

    private val KAGOME = path {
        fun l(x1: Float, y1: Float, x2: Float, y2: Float) { moveTo(x1, y1); lineTo(x2, y2) }
        l(0f, 0f, 16f, 0f); l(0f, 6.928f, 16f, 6.928f); l(0f, 13.856f, 16f, 13.856f)
        l(-8f, 0f, 0f, 13.856f); l(0f, 0f, 8f, 13.856f); l(8f, 0f, 16f, 13.856f)
        l(4f, 0f, -4f, 13.856f); l(12f, 0f, 4f, 13.856f); l(20f, 0f, 12f, 13.856f)
    }

    /** Tiles in the board's units, scaled by its patternTransform so they're the size they show at. */
    private fun scaled(s: Float, width: Float, height: Float, darkAlpha: Float = 0.55f, offsetY: Float = 0f, draw: DrawScope.(Color, Color, Float) -> Unit) =
        Tile(width * s, height * s, darkAlpha, draw = { fg, base, unit ->
            withTransformScale(s, offsetY) { draw(fg, base, unit * s) }
        })

    private inline fun DrawScope.withTransformScale(s: Float, offsetY: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(0f, offsetY)
        drawContext.transform.scale(s, s, Offset.Zero)
        block()
        drawContext.transform.scale(1 / s, 1 / s, Offset.Zero)
        drawContext.transform.translate(0f, -offsetY)
    }

    private val TILES: Map<TapePattern, Tile> = mapOf(
        TapePattern.DOTS to Tile(7f, 7f, darkAlpha = 0.6f, draw = { fg, _, _ -> drawCircle(fg, 1.4f, Offset(3.5f, 3.5f)) }),
        TapePattern.STRIPES to Tile(6f, 6f, darkAlpha = 0.38f, diagonal = true, band = 2.5f, inner = 1f),
        TapePattern.GINGHAM to Tile(7f, 7f, darkAlpha = 0.55f, draw = { fg, _, _ ->
            val band = fg.copy(alpha = 0.55f)
            drawRect(band, Offset(1.75f, 0f), Size(3.5f, 7f))
            drawRect(band, Offset(0f, 1.75f), Size(7f, 3.5f))
        }),
        TapePattern.ICHIMATSU to scaled(1.1667f, 12f, 12f) { fg, _, _ ->
            drawRect(fg, Offset.Zero, Size(6f, 6f))
            drawRect(fg, Offset(6f, 6f), Size(6f, 6f))
        },
        TapePattern.SHIPPO to scaled(1.1667f, 12f, 12f) { fg, _, unit ->
            val ring = stroke(1.1f, unit)
            listOf(0f to 0f, 12f to 0f, 0f to 12f, 12f to 12f, 6f to 6f, 6f to -6f, -6f to 6f, 18f to 6f, 6f to 18f).forEach { (x, y) ->
                drawCircle(fg, 8.49f, Offset(x, y), style = ring)
            }
        },
        TapePattern.SEIGAIHA to scaled(1.1667f, 24f, 12f) { fg, base, unit ->
            val line = stroke(1f, unit)
            listOf(0f to -6f, 24f to -6f, 12f to 0f, 0f to 6f, 24f to 6f, 12f to 12f, 0f to 18f, 24f to 18f).forEach { (x, y) ->
                val c = Offset(x, y)
                drawCircle(base, 12f, c)
                listOf(12f, 9f, 6f, 3f).forEach { r -> drawCircle(fg, r, c, style = line) }
            }
        },
        TapePattern.ASANOHA to scaled(0.8083f, 20f, 34.64f) { fg, _, unit -> drawPath(ASANOHA, fg, style = stroke(1.5f, unit)) },
        TapePattern.YAGASURI to scaled(0.875f, 16f, 16f) { fg, _, _ -> ARROWS.forEach { drawPath(it, fg) } },
        TapePattern.HEARTS to scaled(0.875f, 16f, 16f) { fg, _, _ -> HEARTS.forEach { drawPath(it, fg) } },
        TapePattern.UROKO to Tile(16f, 14f, draw = { fg, _, _ -> drawPath(poly(0f, 14f, 8f, 0f, 16f, 14f), fg) }),
        TapePattern.KIKKO to scaled(0.7778f, 10.392f, 18f) { fg, _, unit ->
            val line = stroke(0.9f, unit)
            KIKKO.forEach { drawPath(it, fg, style = line) }
            drawPath(KIKKO_STEM, fg, style = line)
        },
        TapePattern.KAGOME to scaled(1.0104f, 16f, 13.856f) { fg, _, unit -> drawPath(KAGOME, fg, style = stroke(1.1f, unit)) },
    )
}
