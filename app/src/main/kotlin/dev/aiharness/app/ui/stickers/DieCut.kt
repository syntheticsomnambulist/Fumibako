package dev.aiharness.app.ui.stickers

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The die-cut outline around a sticker, baked once into its picture: blur the shape's alpha and
 * keep everything above a low threshold, so the outline follows the shape with rounded corners and
 * points aren't clipped (a square dilate would make them blocky). Then a soft shadow under it.
 *
 * Works on ARGB pixels (not premultiplied) so it can be tested without Android.
 */
object DieCut {
    /** How far below the shape the shadow falls, and how soft it is, as fractions of the outline width. */
    private const val SHADOW_DROP = 0.6f
    private const val SHADOW_ALPHA = 0.34f

    /**
     * Bakes [pixels] (width [w], height [h], with clear space around the shape of at least
     * [outlinePx] * 2) into a sticker: the shape, an outline of [outlineColor] about [outlinePx]
     * wide, and a shadow under it when [shadow].
     */
    fun bake(pixels: IntArray, w: Int, h: Int, outlineColor: Int, outlinePx: Float, shadow: Boolean = true): IntArray {
        val alpha = FloatArray(w * h) { ((pixels[it] ushr 24) and 0xFF) / 255f }
        val outline = if (outlinePx > 0f) outlineMask(alpha, w, h, outlinePx) else alpha.copyOf()
        val shade = if (shadow) shadowMask(outline, w, h, maxOf(outlinePx, 2f)) else null
        val out = IntArray(w * h)
        val oa = ((outlineColor ushr 24) and 0xFF) / 255f
        val or = (outlineColor shr 16) and 0xFF
        val og = (outlineColor shr 8) and 0xFF
        val ob = outlineColor and 0xFF
        for (i in out.indices) {
            // Back to front: shadow, outline, the picture itself ("over" each time).
            var a = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            shade?.let { s ->
                val sa = s[i] * SHADOW_ALPHA
                a = sa
                r = 30f * sa; g = 22f * sa; b = 14f * sa
            }
            val ola = outline[i] * oa
            if (ola > 0f) {
                r = or * ola + r * (1 - ola); g = og * ola + g * (1 - ola); b = ob * ola + b * (1 - ola); a = ola + a * (1 - ola)
            }
            val p = pixels[i]
            val pa = alpha[i]
            if (pa > 0f) {
                r = ((p shr 16) and 0xFF) * pa + r * (1 - pa)
                g = ((p shr 8) and 0xFF) * pa + g * (1 - pa)
                b = (p and 0xFF) * pa + b * (1 - pa)
                a = pa + a * (1 - pa)
            }
            out[i] = if (a <= 0f) 0 else {
                val ia = (a * 255f).roundToInt().coerceIn(0, 255)
                (ia shl 24) or ((r / a).roundToInt().coerceIn(0, 255) shl 16) or ((g / a).roundToInt().coerceIn(0, 255) shl 8) or (b / a).roundToInt().coerceIn(0, 255)
            }
        }
        return out
    }

    /**
     * The shape grown by about [widthPx]: a Gaussian blur of its alpha (sigma = width / 2), kept
     * where it's above 2%, with a soft edge one pixel or so wide.
     */
    fun outlineMask(alpha: FloatArray, w: Int, h: Int, widthPx: Float): FloatArray {
        val blurred = gaussian(alpha, w, h, widthPx / 2.05f)
        return FloatArray(w * h) { i -> smoothstep(0.008f, 0.035f, blurred[i]).coerceAtLeast(alpha[i]) }
    }

    private fun shadowMask(outline: FloatArray, w: Int, h: Int, widthPx: Float): FloatArray {
        val drop = (widthPx * SHADOW_DROP).roundToInt().coerceAtLeast(1)
        val shifted = FloatArray(w * h)
        for (y in drop until h) System.arraycopy(outline, (y - drop) * w, shifted, y * w, w)
        return gaussian(shifted, w, h, widthPx * 0.45f)
    }

    /** Three box blurs, which together come close to a Gaussian of [sigma]. */
    fun gaussian(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        if (sigma <= 0.3f) return src.copyOf()
        val boxWidth = sqrt(12f * sigma * sigma / 3f + 1f)
        val radius = ((boxWidth - 1f) / 2f).roundToInt().coerceAtLeast(1)
        var a = src.copyOf()
        var b = FloatArray(w * h)
        repeat(3) {
            boxHorizontal(a, b, w, h, radius)
            boxVertical(b, a, w, h, radius)
        }
        return a
    }

    private fun boxHorizontal(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val scale = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (x in -r..r) sum += src[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum * scale
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
    }

    private fun boxVertical(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        val scale = 1f / (2 * r + 1)
        for (x in 0 until w) {
            var sum = 0f
            for (y in -r..r) sum += src[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum * scale
                sum += src[(y + r + 1).coerceAtMost(h - 1) * w + x] - src[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /**
     * Cuts a drawn sticker out of its plain background: fills from the edges through every pixel
     * close to the background colour (sampled from the corners), making it clear, with a soft edge.
     * Pictures drawn for stickers are asked for on plain white, so this is enough for them; photos
     * use ML Kit instead.
     */
    fun cutOutPlainBackground(pixels: IntArray, w: Int, h: Int, tolerance: Float = 0.12f): IntArray {
        val corners = intArrayOf(pixels[0], pixels[w - 1], pixels[(h - 1) * w], pixels[h * w - 1])
        val bg = corners.map { floatArrayOf(((it shr 16) and 0xFF) / 255f, ((it shr 8) and 0xFF) / 255f, (it and 0xFF) / 255f) }
            .reduce { acc, c -> floatArrayOf(acc[0] + c[0], acc[1] + c[1], acc[2] + c[2]) }.map { it / 4f }
        fun distance(p: Int): Float {
            val dr = ((p shr 16) and 0xFF) / 255f - bg[0]
            val dg = ((p shr 8) and 0xFF) / 255f - bg[1]
            val db = (p and 0xFF) / 255f - bg[2]
            return sqrt(dr * dr + dg * dg + db * db)
        }
        val background = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        fun visit(i: Int) {
            if (!background[i] && distance(pixels[i]) <= tolerance) {
                background[i] = true
                queue.addLast(i)
            }
        }
        for (x in 0 until w) { visit(x); visit((h - 1) * w + x) }
        for (y in 0 until h) { visit(y * w); visit(y * w + w - 1) }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w
            val y = i / w
            if (x > 0) visit(i - 1)
            if (x < w - 1) visit(i + 1)
            if (y > 0) visit(i - w)
            if (y < h - 1) visit(i + w)
        }
        // A one-pixel feather where the shape meets what was cut away.
        val clear = FloatArray(w * h) { if (background[it]) 0f else 1f }
        val soft = gaussian(clear, w, h, 0.7f)
        return IntArray(w * h) { i ->
            val a = if (background[i]) 0f else minOf(1f, soft[i] * 1.6f) * (((pixels[i] ushr 24) and 0xFF) / 255f)
            ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (pixels[i] and 0xFFFFFF)
        }
    }
}
