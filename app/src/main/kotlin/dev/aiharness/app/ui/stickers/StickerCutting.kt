package dev.aiharness.app.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.aiharness.core.stickers.StickerCutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Cuts drawn stickers out of their plain white background on the phone (see [DieCut.cutOutPlainBackground]). */
object PlainBackgroundCutter : StickerCutter {
    /** Stickers are small; a drawing is scaled to this on its longer side before cutting. */
    private const val MAX_SIDE = 512

    override suspend fun cutOut(bytes: ByteArray): ByteArray? = withContext(Dispatchers.Default) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else {
            decoded
        }
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val cut = DieCut.cutOutPlainBackground(pixels, w, h)
        val out = Bitmap.createBitmap(cut, w, h, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
