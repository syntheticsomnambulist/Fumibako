package dev.aiharness.app.ui.stickers

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.stickers.StickerService
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * [content] (a letter, a note or your draft) with [stickers] stuck on it: each placed by its
 * centre as fractions of the paper, turned and sized, and free to hang over the edge.
 *
 * With [onTap], tapping a sticker reports it. The one whose id is [selectedId] shows a dashed box
 * and a corner handle, and with [onChange] it can be dragged, pinched and twisted, or turned and
 * sized by the handle; each step reports the moved sticker.
 */
@Composable
fun StuckOn(
    stickers: List<Sticker>,
    inkOf: (Sticker) -> Color,
    modifier: Modifier = Modifier,
    nameOf: (Sticker) -> String? = { null },
    selectedId: String? = null,
    onTap: ((Sticker) -> Unit)? = null,
    onChange: ((Sticker) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val size = LocalStickerSettings.current.size.dp
    var paperSize by remember { mutableStateOf(IntSize.Zero) }
    Layout(
        modifier = modifier,
        content = {
            content()
            stickers.forEach { sticker ->
                OnePiece(
                    sticker = sticker,
                    ink = inkOf(sticker),
                    name = nameOf(sticker),
                    selected = sticker.id == selectedId,
                    paperSize = { paperSize },
                    onTap = onTap,
                    // Only your own stickers move; a persona's can be selected to see whose it is.
                    onChange = onChange.takeIf { sticker.id == selectedId && sticker.byPersonaId == null },
                )
            }
        },
    ) { measurables, constraints ->
        val sheet = measurables.first().measure(constraints)
        paperSize = IntSize(sheet.width, sheet.height)
        val placed = measurables.drop(1).mapIndexed { i, m ->
            val side = (size * stickers[i].scale * StickerBaker.BOX).dp.roundToPx()
            m.measure(Constraints.fixed(side, side))
        }
        layout(sheet.width, sheet.height) {
            sheet.place(0, 0)
            placed.forEachIndexed { i, p ->
                val s = stickers[i]
                p.place((s.x * sheet.width - p.width / 2f).roundToInt(), (s.y * sheet.height - p.height / 2f).roundToInt())
            }
        }
    }
}

@Composable
private fun OnePiece(
    sticker: Sticker,
    ink: Color,
    name: String?,
    selected: Boolean,
    paperSize: () -> IntSize,
    onTap: ((Sticker) -> Unit)?,
    onChange: ((Sticker) -> Unit)?,
) {
    val current by rememberUpdatedState(sticker)
    val change by rememberUpdatedState(onChange)
    val tap by rememberUpdatedState(onTap)
    val frame = paper.sumi
    val density = LocalDensity.current
    // From the sticker's centre to the handle's, along each axis (the handle sits in the corner).
    val handleReach by rememberUpdatedState(
        with(density) { (LocalStickerSettings.current.size.dp * sticker.scale * StickerBaker.BOX / 2f).dp.toPx() - 14.dp.toPx() },
    )
    Box(
        Modifier
            .graphicsLayer { rotationZ = sticker.rotationDeg }
            .then(
                if (onTap == null) Modifier else Modifier
                    .pointerInput(sticker.id) { detectTapGestures { tap?.invoke(current) } }
                    .semantics {
                        role = Role.Button
                        onClick(label = if (selected) "Done with this sticker" else "Select this sticker") { tap?.invoke(current); true }
                    },
            )
            .then(
                if (onChange == null) Modifier else Modifier.pointerInput(sticker.id) {
                    // One finger moves it; two pinch and twist. Deltas arrive in the sticker's turned frame.
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val s = current
                        val paper = paperSize()
                        if (paper.width == 0 || paper.height == 0) return@detectTransformGestures
                        val moved = pan.rotate(s.rotationDeg)
                        change?.invoke(
                            s.copy(
                                x = (s.x + moved.x / paper.width).coerceIn(0f, 1f),
                                y = (s.y + moved.y / paper.height).coerceIn(0f, 1f),
                                rotationDeg = s.rotationDeg + rotation,
                                scale = (s.scale * zoom).coerceIn(StickerService.MIN_SCALE, StickerService.MAX_SCALE),
                            ),
                        )
                    }
                },
            )
            .then(
                if (!selected) Modifier else Modifier.drawBehind {
                    val inset = size.width * (1f - 1f / StickerBaker.BOX) / 2f - 4.dp.toPx()
                    drawRoundRect(
                        frame,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                },
            ),
    ) {
        StickerPicture(sticker.ref, ink, Modifier.matchParentSize(), name)
        if (selected && onChange != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset((-2).dp, (-2).dp)
                    .size(24.dp)
                    .background(paper.kami, CircleShape)
                    .border(1.5.dp, frame, CircleShape)
                    .pointerInput(sticker.id) {
                        // Dragging the corner turns the sticker about its centre and sizes it by distance.
                        detectDragGestures { pointer, drag ->
                            pointer.consume()
                            val s = current
                            val from = Offset(handleReach, handleReach)
                            val to = from + drag
                            val turn = Math.toDegrees((atan2(to.y, to.x) - atan2(from.y, from.x)).toDouble()).toFloat()
                            val grow = hypot(to.x, to.y) / hypot(from.x, from.y).coerceAtLeast(1f)
                            change?.invoke(
                                s.copy(
                                    rotationDeg = s.rotationDeg + turn,
                                    scale = (s.scale * grow).coerceIn(StickerService.MIN_SCALE, StickerService.MAX_SCALE),
                                ),
                            )
                        }
                    }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Turn and size", tint = frame, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** [this] turned by [degrees] (from the sticker's frame to the paper's). */
private fun Offset.rotate(degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Offset(x * c - y * s, x * s + y * c)
}
