package dev.aiharness.core.transfer

import org.junit.Test
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Her ink suggested from a card's portrait (v0.5). */
class PortraitInkTest {
    private val inks = mapOf(
        "sakura" to 0xFFA26480.toInt(), "nadeshiko" to 0xFFC8336A.toInt(), "beni" to 0xFFA43B3B.toInt(),
        "yamabuki" to 0xFFE3940B.toInt(), "tokiwa" to 0xFF008B57.toInt(), "ruri" to 0xFF2973B0.toInt(), "ai" to 0xFF384DAD.toInt(),
    )

    private fun picture(background: Int, figure: Int, share: Double = 0.3): IntArray =
        IntArray(100 * 100) { i -> if ((i % 100) < 100 * share) figure else background }

    @Test
    fun aBluePortraitOnAPaleBackgroundIsRuri() {
        assertEquals("ruri", PortraitInk.nearest(picture(0xFFF4F1EC.toInt(), 0xFF2973B0.toInt()), 100, inks))
        assertEquals("ruri", PortraitInk.nearest(IntArray(64 * 64) { 0xFF2973B0.toInt() }, 64, inks), "a blue all over")
    }

    @Test
    fun otherColorsFindTheirOwnInks() {
        assertEquals("yamabuki", PortraitInk.nearest(picture(0xFFFFFFFF.toInt(), 0xFFE8A020.toInt()), 100, inks))
        assertEquals("tokiwa", PortraitInk.nearest(picture(0xFF222222.toInt(), 0xFF10995F.toInt()), 100, inks))
        assertEquals("nadeshiko", PortraitInk.nearest(picture(0xFFF4F1EC.toInt(), 0xFFD03872.toInt()), 100, inks))
    }

    @Test
    fun aGreyPictureSuggestsNothing() {
        assertNull(PortraitInk.nearest(picture(0xFFEEEEEE.toInt(), 0xFF555555.toInt()), 100, inks))
    }

    @Test
    fun huesAreComparedTheShortWayRound() {
        assertTrue(PortraitInk.hueApart(0.1, 2 * PI - 0.1) < 0.21)
        assertTrue(PortraitInk.hueApart(-2.67, 5.93) in 0.0..PI, "never negative, however far apart the numbers are")
        assertEquals(PI, PortraitInk.hueApart(0.0, PI), 1e-9)
    }
}
