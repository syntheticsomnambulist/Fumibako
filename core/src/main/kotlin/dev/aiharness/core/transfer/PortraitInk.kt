package dev.aiharness.core.transfer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ink nearest a portrait, for a card coming in: its vivid pixels' hue, saturation and value
 * against each ink's. Grey and near-white pixels (a portrait's background) count for little.
 */
object PortraitInk {
    /**
     * [pixels] are ARGB, a row of [width] at a time; [inks] are names and their ARGB colors.
     * Null when the picture is grey all over (or there are no inks).
     */
    fun nearest(pixels: IntArray, width: Int, inks: Map<String, Int>): String? {
        if (width <= 0 || pixels.isEmpty()) return null
        val height = pixels.size / width
        val step = maxOf(1, maxOf(width, height) / 64)
        var x = 0.0
        var y = 0.0
        var sat = 0.0
        var value = 0.0
        var weight = 0.0
        var sampled = 0
        for (j in 0 until height step step) for (i in 0 until width step step) {
            val argb = pixels[j * width + i]
            sampled++
            if ((argb ushr 24) < 128) continue
            val (hue, s, v) = hsv(argb)
            val wt = s * v
            x += cos(hue) * wt
            y += sin(hue) * wt
            sat += s * wt
            value += v * wt
            weight += wt
        }
        if (sampled == 0 || weight < sampled * 0.02) return null
        val hue = atan2(y, x)
        val s = sat / weight
        val v = value / weight
        return inks.minByOrNull { (_, color) ->
            val (ih, isat, iv) = hsv(color)
            hueApart(ih, hue) / PI * 2 + abs(isat - s) * 0.5 + abs(iv - v) * 0.5
        }?.key
    }

    /** How far apart two hues are, the short way round (0 to π). */
    internal fun hueApart(a: Double, b: Double): Double {
        val d = abs(a - b).mod(2 * PI)
        return if (d > PI) 2 * PI - d else d
    }

    /** Hue (radians), saturation and value of an ARGB color. */
    private fun hsv(argb: Int): Triple<Double, Double, Double> {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0.0 -> 0.0
            max == r -> ((g - b) / d).mod(6.0)
            max == g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * PI / 3
        return Triple(hue, if (max == 0.0) 0.0 else d / max, max)
    }
}
