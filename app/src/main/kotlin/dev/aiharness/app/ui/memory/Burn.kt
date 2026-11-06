package dev.aiharness.app.ui.memory

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Burns [paper] (a snapshot of the card, at its size) away from the bottom right corner, with a
 * flickering glow, charring ahead of the front and a few embers that sway as they rise. Calls
 * [onDone] once the paper is gone and the last ember has faded. Embers drift above the card, so
 * leave a little room over it.
 */
@Composable
fun BurnAway(paper: ImageBitmap, seed: Int, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val density = LocalDensity.current.density
    val done by rememberUpdatedState(onDone)
    val source = remember(paper) { paper.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it } }
    val shape = remember(source) { BurnShape(source.width / density, source.height / density, seed = (seed % 97 + 97) % 97 + 3f) }
    val gpu = remember(source) { if (Build.VERSION.SDK_INT >= 33) runCatching { GpuBurn(source, shape, density) }.getOrNull() else null }
    var cpu by remember(source) { mutableStateOf<CpuBurn?>(null) }
    var t by remember(source) { mutableFloatStateOf(0f) }
    val embers = remember(source) { Embers(seed) }

    LaunchedEffect(source) {
        // The CPU path also gives the embers their "is this paper?" check, so it is always built.
        val frames = withContext(Dispatchers.Default) { CpuBurn.of(source, shape) }
        cpu = frames
        val start = withFrameNanos { it }
        var last = 0f
        while (true) {
            val now = withFrameNanos { (it - start) / 1_000_000_000f }
            embers.step(shape, frames.frames, now, (now - last).coerceIn(0f, 0.05f))
            last = now
            t = now
            if (now >= BurnShape.DURATION && embers.isEmpty()) break
        }
        done()
    }

    Canvas(modifier) {
        val time = t
        val frames = cpu
        if (frames == null) {
            // Still preparing (a few frames at most): keep showing the card as it was.
            drawImage(paper)
            return@Canvas
        }
        if (gpu != null) {
            gpu.update(time)
            drawRect(gpu.brush, size = size)
        } else {
            frames.render(time)
            drawImage(frames.image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.Low)
        }
        val px = density
        embers.list.forEach { e ->
            val fade = 1 - e.life / e.max
            val centre = Offset(e.x * px, e.y * px)
            val radius = e.r * px * 3.5f
            drawCircle(
                Brush.radialGradient(
                    0f to Color(1f, 0.9f, 0.63f, fade),
                    0.35f to Color(1f, 0.55f, 0.16f, 0.8f * fade),
                    1f to Color(1f, 0.35f, 0.08f, 0f),
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

@RequiresApi(33)
private class GpuBurn(source: Bitmap, private val shape: BurnShape, density: Float) {
    private val shader = RuntimeShader(BURN_SHADER).apply {
        setInputShader("paper", BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        setFloatUniform("dp", density)
        setFloatUniform("origin", shape.originXDp, shape.originYDp)
        setFloatUniform("diag", shape.diag)
        setFloatUniform("seed", shape.seed)
        setFloatUniform("front", shape.front(0f))
        setFloatUniform("flick", shape.flicker(0f))
    }
    val brush = ShaderBrush(shader)

    fun update(t: Float) {
        shader.setFloatUniform("front", shape.front(t))
        shader.setFloatUniform("flick", shape.flicker(t))
    }
}

private class CpuBurn(val frames: BurnFrames, private val bitmap: Bitmap) {
    private val out = IntArray(frames.width * frames.height)
    val image: ImageBitmap = bitmap.asImageBitmap()

    fun render(t: Float) {
        frames.render(t, out)
        bitmap.setPixels(out, 0, frames.width, 0, 0, frames.width, frames.height)
    }

    companion object {
        /** About one shaded point per 1.6 dp: smooth enough once filtered, cheap enough per frame. */
        fun of(source: Bitmap, shape: BurnShape): CpuBurn {
            val w = max(8, (shape.widthDp / 1.6f).toInt())
            val h = max(8, (shape.heightDp / 1.6f).toInt())
            val small = Bitmap.createScaledBitmap(source, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            return CpuBurn(BurnFrames(shape, w, h, pixels), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        }
    }
}

/** Embers born on the burning edge, rising with a sway, fading. Positions in dp. */
private class Embers(seed: Int) {
    class Ember(var x: Float, var y: Float, val vx: Float, var vy: Float, var life: Float, val max: Float, val r: Float, val phase: Float)

    private val random = Random(seed)
    val list = mutableListOf<Ember>()

    fun isEmpty() = list.isEmpty()

    fun step(shape: BurnShape, frames: BurnFrames, t: Float, dt: Float) {
        if (t < BurnShape.DURATION && random.nextFloat() < 0.45f) {
            val p = shape.front(t)
            for (attempt in 0 until 60) {
                val x = random.nextFloat() * shape.widthDp
                val y = random.nextFloat() * shape.heightDp
                val v = shape.value(x, y) - p
                if (v > -0.004f && v < 0.01f && frames.isPaper(x, y)) {
                    list += Ember(x, y, (random.nextFloat() - 0.5f) * 30f, -(35f + random.nextFloat() * 55f), 0f, 0.45f + random.nextFloat() * 0.6f, 0.6f + random.nextFloat() * 1.1f, random.nextFloat() * 6.28f)
                    break
                }
            }
        }
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.life += dt
            if (e.life >= e.max) {
                iterator.remove()
                continue
            }
            e.x += (e.vx + sin(e.life * 9 + e.phase) * 22f) * dt
            e.y += e.vy * dt
            e.vy *= 0.985f
        }
        // Keep the list small even on a very slow frame.
        while (list.size > 40) list.removeAt(0)
        list.forEach { it.x = min(max(it.x, -20f), shape.widthDp + 20f) }
    }
}
