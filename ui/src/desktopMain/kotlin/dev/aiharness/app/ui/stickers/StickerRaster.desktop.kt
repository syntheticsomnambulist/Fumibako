package dev.aiharness.app.ui.stickers

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import kotlin.math.max
import kotlin.math.roundToInt

/** Skia: the same trimming and fitting as the phone, with Skia's bitmaps. */
internal actual object StickerRaster {
    actual fun emoji(emoji: String): StickerPixels? {
        val size = StickerBaker.CONTENT
        val surface = Surface.makeRasterN32Premul(size, size)
        val typeface = FontMgr.default.matchFamilyStyleCharacter(null, org.jetbrains.skia.FontStyle.NORMAL, null, emoji.codePointAt(0))
        val font = Font(typeface, size * 0.8f)
        val line = TextLine.make(emoji, font)
        surface.canvas.drawTextLine(line, (size - line.width) / 2f, size / 2f - (line.ascent + line.descent) / 2f, Paint().apply { isAntiAlias = true })
        return fit(pixelsOf(surface.makeImageSnapshot()))
    }

    actual fun picture(bytes: ByteArray): StickerPixels? {
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        return fit(pixelsOf(image))
    }

    actual fun image(argb: IntArray, width: Int, height: Int): ImageBitmap = imageOf(argb, width, height).toComposeImageBitmap()

    /** Trims clear margins and scales the shape into the square, centred, with padding around. */
    private fun fit(source: StickerPixels): StickerPixels? {
        val w = source.width
        val h = source.height
        val pixels = source.argb
        var left = w; var top = h; var right = -1; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 8) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        val cw = right - left + 1
        val ch = bottom - top + 1
        val scale = StickerBaker.CONTENT.toFloat() / max(cw, ch)
        val side = StickerBaker.CONTENT + 2 * StickerBaker.PAD
        val dw = (cw * scale).roundToInt()
        val dh = (ch * scale).roundToInt()
        val dx = (side - dw) / 2
        val dy = (side - dh) / 2
        val surface = Surface.makeRasterN32Premul(side, side)
        surface.canvas.drawImageRect(
            imageOf(pixels, w, h),
            Rect.makeLTRB(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat()),
            Rect.makeXYWH(dx.toFloat(), dy.toFloat(), dw.toFloat(), dh.toFloat()),
            SamplingMode.LINEAR,
            null,
            true,
        )
        return pixelsOf(surface.makeImageSnapshot())
    }

    private val unpremul = ImageInfo(ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB), 0, 0)

    /** ARGB ints (as Android's getPixels gives them) from an image. */
    fun pixelsOf(image: Image): StickerPixels {
        val w = image.width
        val h = image.height
        val bitmap = Bitmap().apply { allocPixels(unpremul.withWidthHeight(w, h)) }
        Canvas(bitmap).drawImage(image, 0f, 0f)
        val bytes = bitmap.readPixels(unpremul.withWidthHeight(w, h), w * 4, 0, 0) ?: ByteArray(w * h * 4)
        val argb = IntArray(w * h) { i ->
            val o = i * 4
            ((bytes[o + 3].toInt() and 0xFF) shl 24) or ((bytes[o + 2].toInt() and 0xFF) shl 16) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o].toInt() and 0xFF)
        }
        return StickerPixels(w, h, argb)
    }

    fun imageOf(argb: IntArray, w: Int, h: Int): Image {
        val bytes = ByteArray(w * h * 4)
        for (i in argb.indices) {
            val c = argb[i]
            val o = i * 4
            bytes[o] = (c and 0xFF).toByte()
            bytes[o + 1] = ((c ushr 8) and 0xFF).toByte()
            bytes[o + 2] = ((c ushr 16) and 0xFF).toByte()
            bytes[o + 3] = ((c ushr 24) and 0xFF).toByte()
        }
        return Image.makeRaster(unpremul.withWidthHeight(w, h), bytes, w * 4)
    }
}
