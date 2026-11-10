package dev.aiharness.app.ui.stickers

import dev.aiharness.core.stickers.StickerCutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt

/** Cuts a picture out of its plain background on the computer (see [DieCut.cutOutPlainBackground]). */
private fun cutPlain(bytes: ByteArray, maxSide: Int): ByteArray? {
    val decoded = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
    val image = if (scale < 1f) {
        val w = (decoded.width * scale).roundToInt()
        val h = (decoded.height * scale).roundToInt()
        Surface.makeRasterN32Premul(w, h).apply { canvas.drawImageRect(decoded, Rect.makeWH(w.toFloat(), h.toFloat())) }.makeImageSnapshot()
    } else {
        decoded
    }
    val pixels = StickerRaster.pixelsOf(image)
    val cut = DieCut.cutOutPlainBackground(pixels.argb, pixels.width, pixels.height)
    return StickerRaster.imageOf(cut, pixels.width, pixels.height).encodeToData(EncodedImageFormat.PNG)?.bytes
}

actual object PlainBackgroundCutter : StickerCutter {
    actual override suspend fun cutOut(bytes: ByteArray): ByteArray? = withContext(Dispatchers.Default) { cutPlain(bytes, 512) }
}

/** No subject segmentation on the computer yet: a plain background is cut away. */
actual object PhotoCutter {
    actual suspend fun cutOut(bytes: ByteArray): PhotoCut? = withContext(Dispatchers.Default) {
        cutPlain(bytes, 768)?.let { PhotoCut(it, bySegmentation = false) }
    }
}
