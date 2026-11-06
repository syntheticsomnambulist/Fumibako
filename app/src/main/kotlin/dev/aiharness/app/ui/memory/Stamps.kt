package dev.aiharness.app.ui.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.core.memory.Feeling
import dev.aiharness.core.memory.FeelingFamily
import dev.aiharness.core.memory.Intensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The icon for each feeling family, drawn as strokes on a 24 by 24 grid. */
private val FamilyIcons: Map<FeelingFamily, List<String>> = mapOf(
    FeelingFamily.BRIGHT to listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"),
    FeelingFamily.FOND to listOf("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
    FeelingFamily.PROUD to listOf("M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8 6.8 19.5l1-5.8-4.2-4.1 5.8-.8z"),
    FeelingFamily.CALM to listOf("M5 19c0-8 5-13 14-14 0 9-5 14-13 14z", "M5 19L13 11"),
    FeelingFamily.SAD to listOf("M12 3.5s6 6.6 6 10.5a6 6 0 0 1-12 0c0-3.9 6-10.5 6-10.5z"),
    FeelingFamily.UNEASY to listOf("M7 18h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.3 3.3 0 0 0 7 18z"),
    FeelingFamily.CROSS to listOf("M12 21c-3.6 0-6-2.5-6-5.8 0-3.6 3-5.4 3.6-9.2 2.6 1.6 4 3.8 4 6 1-.6 1.7-1.7 1.9-3 1.6 1.6 2.5 3.8 2.5 6.2 0 3.3-2.4 5.8-6 5.8z"),
    FeelingFamily.WONDER to listOf("M12 3c.6 4.2 2.8 6.4 7 7-4.2.6-6.4 2.8-7 7-.6-4.2-2.8-6.4-7-7 4.2-.6 6.4-2.8 7-7z"),
    FeelingFamily.WISTFUL to listOf("M19 15.5A8 8 0 0 1 8.5 5a7 7 0 1 0 10.5 10.5z"),
)

/** Plain names for the families, for filters and descriptions. */
val FeelingFamily.label: String
    get() = when (this) {
        FeelingFamily.BRIGHT -> "bright"
        FeelingFamily.FOND -> "fond"
        FeelingFamily.PROUD -> "proud"
        FeelingFamily.CALM -> "calm"
        FeelingFamily.SAD -> "sad"
        FeelingFamily.UNEASY -> "uneasy"
        FeelingFamily.CROSS -> "cross"
        FeelingFamily.WONDER -> "wonder"
        FeelingFamily.WISTFUL -> "wistful"
    }

/** Whether a feeling earns a stamp in the memory book: only strong ones do. */
val Feeling?.stamped: Boolean get() = this != null && intensity == Intensity.STRONG

/**
 * A rubber-stamp impression of a feeling: a double ring and the family's icon in [ink], pressed a
 * little unevenly, with flecks where the ink didn't take. The same [seed] always stamps the same.
 */
@Composable
fun FeelingStamp(family: FeelingFamily, ink: Color, seed: String, modifier: Modifier = Modifier, size: Dp = 34.dp, alpha: Float = 1f, word: String? = null) {
    val paths = remember(family) { FamilyIcons.getValue(family).map { PathParser().parsePathString(it).toPath() } }
    val random = remember(seed) { Random(seed.hashCode()) }
    val tilt = remember(seed) { (seed.hashCode() % 25) - 12f }
    val flecks = remember(seed) { List(28) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) } }
    val pressure = remember(seed) { random.nextFloat() * 6.283f }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = word ?: family.label }
            .graphicsLayer {
                rotationZ = tilt
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val r = this.size.minDimension / 2
        val ring = 1.7.dp.toPx()
        drawCircle(ink.copy(alpha = 0.14f), r - ring)
        drawCircle(ink, r - ring / 2, style = Stroke(ring))
        drawCircle(ink, r * 0.80f, style = Stroke(0.75.dp.toPx()))
        val box = r * 1.02f
        withTransform({
            translate(center.x - box / 2, center.y - box / 2)
            scale(box / 24f, box / 24f, pivot = Offset.Zero)
        }) {
            paths.forEach { drawPath(it, ink, style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
        // Flecks where the ink didn't take, and one side pressed more lightly than the other.
        flecks.forEach { (fx, fy, fr) ->
            drawCircle(Color.Black, radius = (0.25f + fr * 0.55f).dp.toPx(), center = Offset(fx * this.size.width, fy * this.size.height), alpha = 0.55f + fr * 0.45f, blendMode = BlendMode.DstOut)
        }
        val dir = Offset(cos(pressure), sin(pressure))
        drawRect(
            Brush.linearGradient(0f to Color.Black.copy(alpha = 0.38f), 0.55f to Color.Transparent, start = center + dir * r, end = center - dir * r),
            blendMode = BlendMode.DstOut,
        )
    }
}

/** A feeling word in a stamped box (the memory card's "amused"), with its stamp when it has one. */
@Composable
fun FeelingWordBox(word: String, feeling: Feeling?, ink: Color, background: Color, modifier: Modifier = Modifier, tilt: Float = 4f, fontSize: Float = 15f) {
    Row(
        modifier
            .rotate(tilt)
            .background(background, PaperCorner)
            .background(ink.copy(alpha = 0.08f), PaperCorner)
            .border(1.5.dp, ink, PaperCorner)
            .padding(start = if (feeling != null) 4.dp else 8.dp, end = 8.dp, top = 2.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (feeling != null) FeelingStamp(feeling.family, ink, "box-$word", size = (fontSize + 7).dp)
        Text(word, style = PaperType.mood.copy(fontSize = fontSize.sp, lineHeight = (fontSize + 4).sp), color = ink)
    }
}
