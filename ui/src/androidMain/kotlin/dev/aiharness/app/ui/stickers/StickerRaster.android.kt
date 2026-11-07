package dev.aiharness.app.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Android's bitmaps: decoded with BitmapFactory, fitted with a filtered Canvas draw. */
internal actual object StickerRaster {
    actual fun emoji(emoji: String): StickerPixels? = fit(emojiBitmap(emoji))?.pixels()

    actual fun picture(bytes: ByteArray): StickerPixels? {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return fit(source)?.pixels()
    }

    actual fun image(argb: IntArray, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun Bitmap.pixels(): StickerPixels = StickerPixels(width, height, IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) })

    /** The emoji drawn as large as fits the shape's square. */
    private fun emojiBitmap(emoji: String): Bitmap {
        val bitmap = Bitmap.createBitmap(StickerBaker.CONTENT, StickerBaker.CONTENT, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = StickerBaker.CONTENT * 0.8f; textAlign = Paint.Align.CENTER }
        val bounds = Rect().also { paint.getTextBounds(emoji, 0, emoji.length, it) }
        Canvas(bitmap).drawText(emoji, StickerBaker.CONTENT / 2f, StickerBaker.CONTENT / 2f - bounds.exactCenterY(), paint)
        return bitmap
    }

    /** Trims clear margins and scales the shape into the square, centred, with padding around. */
    private fun fit(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) }
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
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val dw = (cw * scale).roundToInt()
        val dh = (ch * scale).roundToInt()
        val dx = (side - dw) / 2
        val dy = (side - dh) / 2
        Canvas(out).drawBitmap(source, Rect(left, top, right + 1, bottom + 1), Rect(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
