package dev.aiharness.app.ui.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cuts the subject out of a photo for a sticker: ML Kit's subject segmentation, on the phone,
 * through Google Play services (its model downloads the first time). Where that isn't available it
 * falls back to cutting away a plain background, which suits pictures of things on a plain table.
 */
actual object PhotoCutter {
    private const val MAX_SIDE = 768

    actual suspend fun cutOut(bytes: ByteArray): PhotoCut? = withContext(Dispatchers.Default) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true) else decoded
        val subject = runCatching { segment(bitmap) }.getOrNull()
        if (subject != null) return@withContext PhotoCut(png(subject), bySegmentation = true)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        PhotoCut(png(Bitmap.createBitmap(DieCut.cutOutPlainBackground(pixels, w, h), w, h, Bitmap.Config.ARGB_8888)), bySegmentation = false)
    }

    private suspend fun segment(bitmap: Bitmap): Bitmap? {
        val segmenter = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        return try {
            suspendCancellableCoroutine { done ->
                segmenter.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result -> done.resume(result.foregroundBitmap) }
                    .addOnFailureListener { done.resume(null) }
            }
        } finally {
            segmenter.close()
        }
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}
