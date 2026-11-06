package dev.aiharness.app.ui.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.media.LocalMediaSettings
import dev.aiharness.app.ui.media.reducedMotion
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.core.model.EntryState
import java.time.DayOfWeek

/** Highlighter: a band of ink with slightly rough ends, as your plans are marked on the week page. */
val Highlighter = GenericShape { size, _ ->
    moveTo(size.width * 0.02f, size.height * 0.04f)
    lineTo(size.width * 0.98f, 0f)
    lineTo(size.width, size.height * 0.96f)
    lineTo(0f, size.height)
    close()
}

/** The ribbon (栞)'s notched end. */
val RibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2f, size.height * 0.88f)
    lineTo(0f, size.height)
    close()
}

/** The day's color in a Japanese diary: Saturday blue, Sunday (and, later, a public holiday) red. */
fun dayInk(day: DayOfWeek, plain: Color, night: Boolean): Color = when (day) {
    DayOfWeek.SATURDAY -> if (night) Color(0xFF8CB8E6) else Color(0xFF2973B0)
    DayOfWeek.SUNDAY -> if (night) Color(0xFFE59A9A) else Color(0xFFA43B3B)
    else -> plain
}

/** The stamp a state leaves: 済 done, 止 cancelled (or not going), 延 moved. */
fun stampOf(state: EntryState): String? = when (state) {
    EntryState.DONE, EntryState.KEPT -> "済"
    EntryState.CANCELLED -> "止"
    else -> null
}

/**
 * A round stamp in [ink], inked at a slant. When it's just been stamped ([fresh]) it comes down
 * from larger with a little bounce, and ticks; still with "Remove animations" on.
 */
@Composable
fun Stamp(glyph: String, ink: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, fresh: Boolean = false) {
    val still = reducedMotion()
    val landing = remember { Animatable(if (fresh && !still) 0f else 1f) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tick = LocalMediaSettings.current.haptics
    LaunchedEffect(fresh) {
        if (fresh && !still) {
            landing.snapTo(0f)
            landing.animateTo(1f, tween(380))
            if (tick) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
        }
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val t = landing.value
                val grow = 1f + 0.8f * (1f - t)
                scaleX = grow
                scaleY = grow
                rotationZ = -12f - 12f * (1f - t)
                alpha = (0.2f + t).coerceAtMost(1f) * 0.92f
            }
            .border(1.5.dp, ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = (size.value * 0.55f).sp), color = ink)
    }
}

/** A faint dot grid (方眼), the planner's paper. */
fun Modifier.dotGrid(dot: Color): Modifier = drawBehind {
    val step = 10.dp.toPx()
    val r = 0.8.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(dot, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/** A dashed line down the page's left edge, and the shadow of the binding beside it. */
fun Modifier.stitched(thread: Color): Modifier = drawBehind {
    drawRect(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0x24000000), Color.Transparent), endX = 14.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(14.dp.toPx(), size.height),
    )
    drawLine(
        thread, Offset(6.dp.toPx(), 8.dp.toPx()), Offset(6.dp.toPx(), size.height),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

/** Hatching: the short mark in the margin of anything that makes you busy. */
fun Modifier.hatched(ink: Color): Modifier = drawBehind {
    val step = 4.dp.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(ink, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
            x += step
        }
    }
}
