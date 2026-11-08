package dev.aiharness.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window's, the tray's and About's icons (drawn by tools/make_icon.py) load and draw: the
 * window's is a rounded square with room around it, and the tray's seal fills its square. Set
 * HARNESS_SCREENSHOTS_DIR to keep pictures of them.
 */
class IconsTest {
    @Test
    fun windowIconIsTheWholeIconInARoundedSquare() {
        val icon = draw("icon.xml")
        assertEquals(0, icon.alpha(1, 1), "the corners are clear")
        assertTrue(isPaper(icon.color(32, 7)), "the envelope's paper is at the top")
        assertTrue(isWax(icon.color(32, 50)), "the seal is in the middle")
    }

    @Test
    fun trayIconIsTheSealFillingItsSquare() {
        val tray = draw("tray.xml")
        // The wax reaches nearly to every edge (the tray is small: the seal is all that shows).
        assertTrue(isWax(tray.color(32, 7)) && isWax(tray.color(7, 32)) && isWax(tray.color(57, 32)) && isWax(tray.color(32, 57)))
        assertEquals(0, tray.alpha(1, 1), "the corners are clear")
    }

    @Test
    fun aboutShowsTheLaunchersSealLayer() {
        val seal = draw("seal.xml")
        assertEquals(0, seal.alpha(4, 32), "the layer has room around the seal")
        assertTrue(isWax(seal.color(32, 19)), "the seal is in the middle")
    }

    private fun draw(name: String, size: Int = 64): Bitmap {
        val image = ImageComposeScene(size, size) {
            @Suppress("DEPRECATION")
            Image(painterResource(name), null, Modifier.fillMaxSize())
        }.use { it.render() }
        System.getenv("HARNESS_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
            File(dir).mkdirs()
            File(dir, "desktop-icon-${name.removeSuffix(".xml")}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        return Bitmap.makeFromImage(image)
    }

    private fun Bitmap.alpha(x: Int, y: Int) = getColor(x, y) ushr 24

    private fun Bitmap.color(x: Int, y: Int) = getColor(x, y)

    private fun channels(color: Int) = Triple((color shr 16) and 255, (color shr 8) and 255, color and 255)

    // The wax is #a43b3b and the paper #f8f0e9 (tools/make_icon.py).
    private fun isWax(color: Int) = channels(color).let { (r, g, b) -> r in 140..190 && g < 90 && b < 90 }

    private fun isPaper(color: Int) = channels(color).let { (r, g, b) -> r > 220 && g > 210 && b > 200 }
}
