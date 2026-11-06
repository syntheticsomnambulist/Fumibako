package dev.aiharness.app.ui.memory

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The paper burn from the design prototype, as plain math so the CPU fallback, the AGSL shader
 * ([BURN_SHADER]) and the tests share one definition. Each point of the paper has a "time to
 * burn": its distance from the corner the fire starts at, warped by fractal noise so the front
 * curls and wanders. A second noise makes the glow flicker. Coordinates are in dp.
 */
class BurnShape(
    val widthDp: Float,
    val heightDp: Float,
    val seed: Float = 7f,
    originX: Float = 0.86f,
    originY: Float = 1.05f,
) {
    val originXDp = widthDp * originX
    val originYDp = heightDp * originY
    val diag = hypot(widthDp, heightDp)
    val minValue: Float
    val maxValue: Float

    init {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        val steps = 48
        for (j in 0..steps) {
            for (i in 0..steps) {
                val v = value(widthDp * i / steps, heightDp * j / steps)
                lo = min(lo, v)
                hi = max(hi, v)
            }
        }
        minValue = lo
        maxValue = hi
    }

    /** When the point burns: lower burns sooner. */
    fun value(x: Float, y: Float): Float =
        hypot(x - originXDp, y - originYDp) / diag * 0.82f + fbm(x / 34f, y / 34f) * 0.30f

    /** The flicker noise at a point, 0 to 1. */
    fun flickerNoise(x: Float, y: Float): Float = noise(x / 9f + 40f, y / 9f)

    /** The burn front [t] seconds in: points whose [value] is below it have burned away. */
    fun front(t: Float): Float {
        val k = (t / DURATION).coerceIn(0f, 1f)
        val eased = if (k < 0.5f) 2 * k * k else 1 - (-2 * k + 2).pow(2) / 2
        return minValue - 0.04f + eased * (maxValue - minValue + 0.2f)
    }

    /** How strongly the whole front flickers at [t]. */
    fun flicker(t: Float): Float = 0.75f + 0.25f * sin(t * 37f) * sin(t * 23f + 1.3f)

    fun noise(x: Float, y: Float): Float {
        val xi = floor(x)
        val yi = floor(y)
        val u = smooth(x - xi)
        val v = smooth(y - yi)
        val a = lattice(xi, yi)
        val b = lattice(xi + 1, yi)
        val c = lattice(xi, yi + 1)
        val d = lattice(xi + 1, yi + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    fun fbm(x: Float, y: Float): Float {
        var sum = 0f
        var amp = 0.5f
        var f = 1f
        repeat(5) {
            sum += amp * noise(x * f, y * f)
            f *= 2.03f
            amp *= 0.5f
        }
        return sum / 0.97f
    }

    private fun lattice(x: Float, y: Float): Float = hash(x + seed * 13.7f, y + seed * 7.3f)

    companion object {
        /** Seconds for the front to cross the paper; embers take a moment longer to fade. */
        const val DURATION = 1.6f

        /** How far behind the front the paper is gone, leaving only a fading glow. */
        const val BURNED = 0.022f
        const val CHAR = 0.040f
        const val SCORCH = 0.11f

        private fun smooth(t: Float) = t * t * (3 - 2 * t)
        private fun fract(x: Float) = x - floor(x)

        /** Dave Hoskins' hash12: float-only, so the shader computes the same values. */
        fun hash(x: Float, y: Float): Float {
            var a = fract(x * 0.1031f)
            var b = fract(y * 0.1031f)
            var c = fract(x * 0.1031f)
            val d = a * (b + 33.33f) + b * (c + 33.33f) + c * (a + 33.33f)
            a += d
            b += d
            c += d
            return fract((a + b) * c)
        }

        /**
         * One point's colour (non-premultiplied ARGB) given the paper's colour there, how far past
         * the front it is ([v], negative once burned) and its flicker [f]. Transparent paper (the
         * margin around a card) stays transparent.
         */
        fun shade(v: Float, f: Float, paper: Int): Int {
            val paperAlpha = paper ushr 24
            if (paperAlpha == 0 || v < -BURNED) return 0
            val mask = paperAlpha / 255f
            if (v < 0f) {
                val q = (1 + v / BURNED) * f
                return argb(min(255f, 200 * q * q) * mask, 255f, 120 + 60 * q, 30f)
            }
            val glow = 0.010f * f
            if (v < glow) {
                val q = v / glow
                return argb(255 * mask, 255f, 245 - 120 * q, 190 - 170 * q)
            }
            if (v < CHAR) {
                val q = (v - glow) / (CHAR - glow)
                val ember = max(0f, 1 - q * 2.2f) * f
                return argb(255 * mask, 34 + 190 * ember, 18 + 60 * ember, 10f)
            }
            if (v < SCORCH) {
                val q = (1 - (v - CHAR) / (SCORCH - CHAR)).pow(1.6f) * 0.9f
                val r = (paper shr 16 and 0xff).toFloat()
                val g = (paper shr 8 and 0xff).toFloat()
                val b = (paper and 0xff).toFloat()
                return argb(paperAlpha.toFloat(), r + (92 - r) * q, g + (48 - g) * q, b + (20 - b) * q)
            }
            return paper
        }

        private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
            (a.toInt().coerceIn(0, 255) shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }
}

/**
 * The CPU burn: the paper's pixels at a reduced resolution, each frame shaded in a loop. Used
 * below Android 13, where there are no runtime shaders, or if the shader fails.
 */
class BurnFrames(val shape: BurnShape, val width: Int, val height: Int, private val paper: IntArray) {
    private val values = FloatArray(width * height)
    private val flickers = FloatArray(width * height)

    init {
        require(paper.size == width * height)
        for (y in 0 until height) {
            val dy = (y + 0.5f) / height * shape.heightDp
            for (x in 0 until width) {
                val dx = (x + 0.5f) / width * shape.widthDp
                val i = y * width + x
                values[i] = shape.value(dx, dy)
                flickers[i] = shape.flickerNoise(dx, dy)
            }
        }
    }

    fun render(t: Float, out: IntArray) {
        val p = shape.front(t)
        val flick = shape.flicker(t)
        for (i in values.indices) out[i] = BurnShape.shade(values[i] - p, 0.55f + 0.9f * flickers[i] * flick, paper[i])
    }

    /** Whether a point (in dp) is paper rather than the margin around it. */
    fun isPaper(xDp: Float, yDp: Float): Boolean {
        val x = (xDp / shape.widthDp * width).toInt().coerceIn(0, width - 1)
        val y = (yDp / shape.heightDp * height).toInt().coerceIn(0, height - 1)
        return paper[y * width + x] ushr 24 > 128
    }
}

/**
 * The same burn as an AGSL runtime shader (Android 13 and later), drawn at full resolution. Kept
 * line for line with [BurnShape]; its inputs are in pixels with [dp] pixels per dp.
 */
const val BURN_SHADER = """
uniform shader paper;
uniform float dp;
uniform float2 origin;
uniform float diag;
uniform float seed;
uniform float front;
uniform float flick;

float hash(float2 q) {
    float3 p3 = fract(float3(q.x, q.y, q.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float lattice(float2 i) { return hash(i + float2(seed * 13.7, seed * 7.3)); }

float vnoise(float2 x) {
    float2 i = floor(x);
    float2 f = x - i;
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = lattice(i);
    float b = lattice(i + float2(1.0, 0.0));
    float c = lattice(i + float2(0.0, 1.0));
    float d = lattice(i + float2(1.0, 1.0));
    return a + (b - a) * u.x + (c - a) * u.y + (a - b - c + d) * u.x * u.y;
}

float fbm(float2 x) {
    float sum = 0.0;
    float amp = 0.5;
    float f = 1.0;
    for (int o = 0; o < 5; o++) {
        sum += amp * vnoise(x * f);
        f *= 2.03;
        amp *= 0.5;
    }
    return sum / 0.97;
}

half4 main(float2 xy) {
    float2 c = xy / dp;
    half4 col = paper.eval(xy);
    float mask = col.a;
    if (mask <= 0.0) return half4(0.0);
    float v = length(c - origin) / diag * 0.82 + fbm(c / 34.0) * 0.30 - front;
    float f = 0.55 + 0.9 * vnoise(c / 9.0 + float2(40.0, 0.0)) * flick;
    if (v < -0.022) return half4(0.0);
    if (v < 0.0) {
        float q = (1.0 + v / 0.022) * f;
        float a = min(1.0, 200.0 / 255.0 * q * q) * mask;
        return half4(half3(1.0, (120.0 + 60.0 * q) / 255.0, 30.0 / 255.0) * a, a);
    }
    float glow = 0.010 * f;
    if (v < glow) {
        float q = v / glow;
        return half4(half3(1.0, (245.0 - 120.0 * q) / 255.0, (190.0 - 170.0 * q) / 255.0) * mask, mask);
    }
    if (v < 0.040) {
        float q = (v - glow) / (0.040 - glow);
        float e = max(0.0, 1.0 - q * 2.2) * f;
        half3 charred = clamp(half3((34.0 + 190.0 * e) / 255.0, (18.0 + 60.0 * e) / 255.0, 10.0 / 255.0), 0.0, 1.0);
        return half4(charred * mask, mask);
    }
    if (v < 0.11) {
        float q = pow(1.0 - (v - 0.040) / (0.11 - 0.040), 1.6) * 0.9;
        col.rgb = mix(col.rgb, half3(92.0, 48.0, 20.0) / 255.0 * col.a, q);
    }
    return col;
}
"""
