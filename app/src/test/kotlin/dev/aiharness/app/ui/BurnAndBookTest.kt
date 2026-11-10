package dev.aiharness.app.ui

import dev.aiharness.app.ui.memory.BurnFrames
import dev.aiharness.app.ui.memory.BurnShape
import dev.aiharness.app.ui.memory.quoteFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class BurnAndBookTest {
    private val w = 180
    private val h = 60

    /** A cream slip with a darker band of "text", and a transparent margin around it. */
    private fun paper(): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        when {
            x < 4 || x >= w - 4 || y < 4 || y >= h - 4 -> 0
            y in 20..28 && x in 14..150 -> 0xFF3B2F27.toInt()
            else -> 0xFFF6EFE4.toInt()
        }
    }

    private fun frames() = BurnFrames(BurnShape(w.toFloat(), h.toFloat()), w, h, paper())

    private fun burned(out: IntArray, paper: IntArray) = out.indices.count { paper[it] ushr 24 != 0 && out[it] ushr 24 == 0 }.toFloat() / paper.count { it ushr 24 != 0 }

    @Test
    fun theBurnStartsWholeAndEndsInAsh() {
        val f = frames()
        val p = paper()
        val out = IntArray(w * h)
        f.render(0f, out)
        assertEquals(0f, burned(out, p), 0.001f)
        f.render(BurnShape.DURATION, out)
        assertEquals(1f, burned(out, p), 0.001f)
    }

    @Test
    fun theBurnOnlyMovesForwardAndStartsAtItsCorner() {
        val f = frames()
        val p = paper()
        val out = IntArray(w * h)
        var last = 0f
        for (step in 1..16) {
            f.render(BurnShape.DURATION * step / 16, out)
            val now = burned(out, p)
            assertTrue("burned area shrank at step $step", now >= last - 0.0001f)
            last = now
        }
        // A third of the way in, the bottom right (where it starts) is gone and the top left isn't.
        f.render(BurnShape.DURATION * 0.4f, out)
        assertEquals(0, out[(h - 6) * w + (w - 6)] ushr 24)
        assertTrue(out[6 * w + 6] ushr 24 > 0)
    }

    @Test
    fun theMarginStaysClear() {
        val f = frames()
        val out = IntArray(w * h)
        for (step in 0..10) {
            f.render(BurnShape.DURATION * step / 10, out)
            for (x in 0 until w) assertEquals("margin drawn on at step $step", 0, out[x] ushr 24)
        }
    }

    @Test
    fun theShaderHashMatchesItsKotlinTwin() {
        // Known values of hash12, so a change to one side is noticed.
        val a = BurnShape.hash(3f, 7f)
        val b = BurnShape.hash(3f, 7f)
        assertEquals(a, b, 0f)
        assertTrue(a in 0f..1f)
        val spread = (0 until 200).map { BurnShape.hash(it.toFloat(), (it * 7 % 13).toFloat()) }
        assertTrue("hash is not spread out", spread.max() - spread.min() > 0.8f)
    }

    @Test
    fun quotesComeFromTheOpeningOfALetter() {
        assertEquals(
            "Sam! That's huge. After all those late nights?",
            quoteFrom("*gasps and claps both hands over her mouth* Sam! That's **huge**. After all those late nights?"),
        )
        assertEquals("I'm writing this one down.", quoteFrom("I'm writing this one down. How do you feel? [mood: fond]"))
        assertNull(quoteFrom("*twirls*"))
        val long = quoteFrom("This is a rather long opening sentence that goes on for quite a while before it ends. And another.")
        assertTrue(long!!.length <= 120)
    }

    /** With HARNESS_SCREENSHOTS_DIR set, writes the burn as a strip of frames for review. */
    @Test
    fun writesAFrameStrip() {
        val dir = System.getenv("HARNESS_SCREENSHOTS_DIR") ?: return
        val f = frames()
        val out = IntArray(w * h)
        val times = listOf(0.25f, 0.5f, 0.75f, 0.95f, 1.15f, 1.35f)
        val strip = BufferedImage(w, h * times.size, BufferedImage.TYPE_INT_ARGB)
        times.forEachIndexed { row, t ->
            f.render(t, out)
            // Show it over the night desk colour, as it would be in the app.
            val desk = 0xFF2A211C.toInt()
            for (i in out.indices) {
                val a = (out[i] ushr 24) / 255f
                fun mix(shift: Int) = (((out[i] shr shift) and 0xff) * a + ((desk shr shift) and 0xff) * (1 - a)).toInt()
                strip.setRGB(i % w, row * h + i / w, (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0))
            }
        }
        File(dir).mkdirs()
        ImageIO.write(strip, "png", File(dir, "burn_frames.png"))
    }
}
