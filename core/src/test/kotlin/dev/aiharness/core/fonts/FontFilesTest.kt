package dev.aiharness.core.fonts

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reading a font file added as a hand (v0.5): its name, what it covers, and its size against the letters. */
class FontFilesTest {
    private fun bundled(name: String) = File("../ui/src/androidMain/res/font/$name.ttf").readBytes()

    @Test
    fun theBundledHandReadsAsYomogiWithLatinAndItsOwnSize() {
        val info = assertIs<FontFiles.Read.Font>(FontFiles.read(bundled("hand_regular"))).info
        assertEquals("Yomogi", info.name)
        assertTrue(info.latin)
        assertFalse(info.japanese, "the bundled subset is Latin only")
        assertEquals(1f, info.scale, 0.01f, "Yomogi is the size the others are matched to")
    }

    @Test
    fun aFontWithASmallerXHeightIsScaledUp() {
        val literata = assertIs<FontFiles.Read.Font>(FontFiles.read(bundled("read_regular"))).info
        assertEquals("Literata", literata.name)
        assertTrue(literata.scale < 1f, "Literata's x-height is taller than Yomogi's, so it's set a little smaller")
    }

    @Test
    fun whatIsntAFontIsRefusedInPlainWords() {
        assertIs<FontFiles.Read.Not>(FontFiles.read("hello, not a font".toByteArray()))
        assertIs<FontFiles.Read.Not>(FontFiles.read(ByteArray(4)))
        val big = FontFiles.read(ByteArray((FontFiles.MOST_BYTES + 1).toInt()))
        assertEquals("That font is larger than 20 MB.", assertIs<FontFiles.Read.Not>(big).why)
    }
}
