package dev.aiharness.app.ui.stickers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.aiharness.core.model.Sticker
import kotlin.math.roundToInt

/**
 * [content] (a letter or a note) with [stickers] stuck on it: each placed by its centre as
 * fractions of the paper, turned and sized, and free to hang over the edge.
 */
@Composable
fun StuckOn(
    stickers: List<Sticker>,
    inkOf: (Sticker) -> Color,
    modifier: Modifier = Modifier,
    nameOf: (Sticker) -> String? = { null },
    content: @Composable () -> Unit,
) {
    val size = LocalStickerSettings.current.size.dp
    Layout(
        modifier = modifier,
        content = {
            content()
            stickers.forEach { sticker ->
                StickerPicture(
                    sticker.ref,
                    inkOf(sticker),
                    Modifier.graphicsLayer { rotationZ = sticker.rotationDeg },
                    name = nameOf(sticker),
                )
            }
        },
    ) { measurables, constraints ->
        val paper = measurables.first().measure(constraints)
        val placed = measurables.drop(1).mapIndexed { i, m ->
            val side = (size * stickers[i].scale * StickerBaker.BOX).dp.roundToPx()
            m.measure(Constraints.fixed(side, side))
        }
        layout(paper.width, paper.height) {
            paper.place(0, 0)
            placed.forEachIndexed { i, p ->
                val s = stickers[i]
                p.place((s.x * paper.width - p.width / 2f).roundToInt(), (s.y * paper.height - p.height / 2f).roundToInt())
            }
        }
    }
}
