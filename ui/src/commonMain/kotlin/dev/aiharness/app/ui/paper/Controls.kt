package dev.aiharness.app.ui.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery

/*
 * Controls in the stationery style: square corners, ink on paper. A choice is a row of boxed cells
 * like a form; a yes/no is a tick box; buttons are cut paper or a block of ink.
 */

val PaperCorner = RoundedCornerShape(2.dp)

/**
 * One choice from a few, as a row of boxed cells (or rows of [columns] each); the chosen one is
 * shaded and underlined in ink.
 */
@Composable
fun <T> PaperChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = stationery.you,
    /** Two lets longer labels ("Apple Music") wrap rather than be cut short. */
    maxLines: Int = 1,
    /** Cells to a row; fewer than the options puts them on several rows. */
    columns: Int = options.size,
) {
    val colors = paper
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.nijimi1, PaperCorner)
            .clip(PaperCorner),
    ) {
        options.chunked(columns.coerceAtLeast(1)).forEachIndexed { row, cells ->
            if (row > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.nijimi1))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                cells.forEachIndexed { index, (value, label) ->
                    val on = value == selected
                    if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.nijimi1))
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .background(if (on) lerp(colors.kami, ink, 0.16f) else colors.kami)
                            .drawBehind {
                                if (on) drawLine(ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                            }
                            .selectable(selected = on, role = Role.RadioButton) { onSelect(value) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                            color = if (on) colors.sumi else colors.usuzumi1,
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // A short last row keeps its cells the same width as the rows above.
                repeat(columns - cells.size) { Box(Modifier.width(1.dp).fillMaxHeight().background(colors.nijimi1)); Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tick box: a square on the paper with an ink tick when on. */
@Composable
fun TickBox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, ink: Color = stationery.you) {
    val colors = paper
    val box = Modifier
        .size(22.dp)
        .border(1.5.dp, if (checked) ink else colors.usuzumi0, PaperCorner)
        .background(colors.kami, PaperCorner)
    Canvas(
        modifier.then(
            if (onCheckedChange != null) {
                Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange).then(box)
            } else {
                box
            },
        ),
    ) {
        if (checked) {
            val tick = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.74f)
                lineTo(size.width * 0.84f, size.height * 0.24f)
            }
            drawPath(tick, ink, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A button: a block of [ink] (primary) or a slip of paper with a hairline edge (quiet). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaperButton(label: String, modifier: Modifier = Modifier, ink: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = paper
    val base = if (ink != null) Modifier.background(ink, PaperCorner) else Modifier.border(1.dp, colors.nijimi1, PaperCorner).background(colors.kami, PaperCorner)
    Box(
        modifier
            .then(base)
            .clip(PaperCorner)
            .combinedClickable(enabled = enabled, onClick = onClick)
            // A layer that is always there: Modifier.alpha(1f) drops its layer, and changing the chain that
            // way is what left the chat's send button undrawn once enabled.
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = PaperType.label.copy(fontSize = 14.sp, letterSpacing = 0.sp),
                color = if (ink != null) colors.onInk(ink) else colors.sumi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
