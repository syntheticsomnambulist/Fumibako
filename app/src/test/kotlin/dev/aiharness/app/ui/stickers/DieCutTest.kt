package dev.aiharness.app.ui.stickers

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The die-cut outline follows the shape with rounded corners, and plain backgrounds cut away cleanly. */
class DieCutTest {
    private val w = 60
    private val h = 60
    private val red = 0xFFD03030.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun alphaAt(pixels: IntArray, x: Int, y: Int) = ((pixels[y * w + x] ushr 24) and 0xFF) / 255f

    private fun square(): IntArray = IntArray(w * h) { i -> if (i % w in 20 until 40 && i / w in 20 until 40) red else 0 }

    @Test
    fun theOutlineGrowsTheShapeByAboutItsWidthWithRoundCorners() {
        val baked = DieCut.bake(square(), w, h, white, outlinePx = 4f, shadow = false)

        assertEquals(red, baked[30 * w + 30], "the picture itself is untouched")
        assertTrue(alphaAt(baked, 42, 30) > 0.95f, "two pixels out from an edge is outline")
        assertEquals(white and 0xFFFFFF, baked[30 * w + 42] and 0xFFFFFF, "in the outline colour")
        assertTrue(alphaAt(baked, 47, 30) < 0.05f, "seven pixels out is clear")
        // Three pixels out straight from an edge is covered; the same distance diagonally off a corner is less so.
        assertTrue(alphaAt(baked, 43, 30) > alphaAt(baked, 42, 42) + 0.2f, "corners are rounded, not square")
    }

    @Test
    fun aThinPointKeepsItsOutline() {
        val spike = IntArray(w * h) { i -> if (i % w == 30 && i / w in 10 until 50) red else 0 }
        val baked = DieCut.bake(spike, w, h, white, outlinePx = 4f, shadow = false)
        assertTrue(alphaAt(baked, 31, 30) > 0.9f, "beside a one-pixel line")
        assertTrue(alphaAt(baked, 30, 8) > 0.5f, "past the tip, so the point isn't clipped")
    }

    @Test
    fun aShadowFallsBelowNotAbove() {
        val baked = DieCut.bake(square(), w, h, white, outlinePx = 4f, shadow = true)
        assertTrue(alphaAt(baked, 30, 47) > alphaAt(baked, 30, 12) + 0.05f)
    }

    @Test
    fun aPlainBackgroundIsCutAwayButWhiteInsideTheShapeStays() {
        // A red disc on white, with a white highlight inside it.
        val drawn = IntArray(w * h) { i ->
            val x = i % w - 30
            val y = i / w - 30
            when {
                x * x + y * y <= 4 -> white
                x * x + y * y <= 400 -> red
                else -> white
            }
        }
        val cut = DieCut.cutOutPlainBackground(drawn, w, h)
        assertEquals(0f, alphaAt(cut, 0, 0))
        assertEquals(0f, alphaAt(cut, 55, 30))
        assertEquals(1f, alphaAt(cut, 30, 20), 0.01f)
        assertEquals(1f, alphaAt(cut, 30, 30), 0.01f, "the highlight isn't connected to the edge, so it stays")
    }
}
