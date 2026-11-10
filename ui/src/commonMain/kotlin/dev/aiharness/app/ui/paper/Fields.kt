package dev.aiharness.app.ui.paper

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import kotlin.math.max
import kotlin.random.Random

/**
 * A field written on an index card: a box, a header rule in [ink] with the label, and ruled lines
 * the text sits on. The lines are drawn from the text's own line baselines, so they line up at any
 * size; blank ruled lines continue down to [minLines].
 */
@Composable
fun IndexCardField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    ink: Color = stationery.you,
    style: TextStyle = PaperType.letterBody,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = paper
    var focused by remember { mutableStateOf(false) }
    // Only the baselines, and only when they move: storing every new layout kept a dialog redrawing.
    var baselines by remember { mutableStateOf<List<Float>>(emptyList()) }
    val rule = colors.nijimi1
    Column(
        modifier
            .fillMaxWidth()
            .border(if (focused) 1.5.dp else 1.dp, if (focused) ink else colors.nijimi1, PaperCorner)
            .background(colors.kami, PaperCorner)
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 4.dp),
    ) {
        Text(
            label.uppercase(),
            style = PaperType.meta,
            color = colors.kasure2,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ink.copy(alpha = 0.75f), Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                .padding(bottom = 5.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style.copy(color = colors.sumi),
            cursorBrush = SolidColor(ink),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            onTextLayout = { l ->
                val lines = List(l.lineCount) { l.getLineBaseline(it) }
                if (lines != baselines) baselines = lines
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label }
                .padding(top = 4.dp, bottom = 6.dp)
                .drawBehind {
                    val known = baselines
                    if (known.isEmpty()) return@drawBehind
                    val lineHeight = if (known.size > 1) known[1] - known[0] else style.lineHeight.toPx().takeIf { it > 0 } ?: (style.fontSize.toPx() * 1.5f)
                    val lines = max(known.size, if (singleLine) 1 else minLines)
                    val gap = 4.dp.toPx()
                    for (i in 0 until lines) {
                        val baseline = if (i < known.size) known[i] else known.last() + (i - known.size + 1) * lineHeight
                        val y = baseline + gap
                        drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = style.copy(fontStyle = FontStyle.Italic), color = colors.kasure1, maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * − value +, in a box. Tapping the value lets you type it; the number is kept within [range].
 */
@Composable
fun PaperStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    width: Dp = 128.dp,
    describe: (Int) -> String = { it.toString() },
) {
    val colors = paper
    var typing by remember { mutableStateOf(false) }
    var text by remember(value, typing) { mutableStateOf(value.toString()) }
    val focus = remember { FocusRequester() }
    fun commit() {
        text.toIntOrNull()?.coerceIn(range)?.let { if (it != value) onValueChange(it) }
        typing = false
    }
    Row(
        modifier
            .widthIn(min = width)
            .height(IntrinsicSize.Min)
            .border(1.dp, colors.nijimi1, PaperCorner)
            .background(colors.kami, PaperCorner)
            .clip(PaperCorner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Less", enabled = value - step >= range.first) { onValueChange((value - step).coerceIn(range)) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.nijimi1))
        Box(
            Modifier
                .widthIn(min = width - 74.dp)
                .heightIn(min = 38.dp)
                .clickable(onClickLabel = "Type a number", role = Role.Button) { typing = true }
                .background(if (typing) androidx.compose.ui.graphics.lerp(colors.kami, stationery.you, 0.12f) else colors.kami),
            contentAlignment = Alignment.Center,
        ) {
            if (typing) {
                BasicTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    textStyle = PaperType.label.copy(fontSize = 14.sp, letterSpacing = 0.sp, color = colors.sumi, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(stationery.you),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .focusRequester(focus)
                        .onFocusChanged { if (!it.isFocused && typing) commit() },
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                val underline = colors.nijimi2
                Text(
                    describe(value),
                    style = PaperType.label.copy(fontSize = 14.sp, letterSpacing = 0.sp),
                    color = colors.sumi,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp).drawBehind {
                        val y = size.height + 1.dp.toPx()
                        drawLine(underline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                    },
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.nijimi1))
        StepButton("+", "More", enabled = value + step <= range.last) { onValueChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = paper
    Box(
        Modifier
            .width(36.dp)
            .heightIn(min = 38.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = PaperType.label.copy(fontSize = 16.sp), color = if (enabled) colors.usuzumi1 else colors.nijimi2)
    }
}

/** A labelled row with a stepper on the right. */
@Composable
fun StepperRow(label: String, value: Int, onValueChange: (Int) -> Unit, range: IntRange, step: Int = 1, describe: (Int) -> String = { it.toString() }) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = PaperType.noteBody.copy(fontSize = 14.sp, lineHeight = 19.sp), color = paper.sumi, modifier = Modifier.weight(1f))
        PaperStepper(value, onValueChange, range, step = step, describe = describe)
    }
}

/** A box showing the chosen option, opening a menu of [options]. */
@Composable
fun <T> PaperSelect(
    label: String?,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
) {
    val colors = paper
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it.uppercase(), style = PaperType.meta, color = colors.kasure2) }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.nijimi1, PaperCorner)
                    .background(colors.kami, PaperCorner)
                    .clip(PaperCorner)
                    .clickable(role = Role.DropdownList, onClickLabel = label?.let { "Choose $it" }) { open = true }
                    .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selected?.let(optionLabel) ?: noneLabel.orEmpty(),
                    style = PaperType.noteBody.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
                    color = colors.sumi,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.ArrowDropDown, null, tint = colors.usuzumi0)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (noneLabel != null) DropdownMenuItem(text = { Text(noneLabel) }, onClick = { open = false; onSelect(null) })
                options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { open = false; onSelect(option) }) }
            }
        }
    }
}

/** Where a card's tape goes. Corner tape is always a pair on opposing corners. */
/**
 * How a card on a screen is taped down: one of the placements, turned (see [TapePlacement]). A
 * strip runs down the left or right edge; the rest turn too, and four corners and photo corners
 * don't.
 */
enum class TapeLayout(val placement: TapePlacement, val turn: Int = 0) {
    LEFT(TapePlacement.STRIP),
    RIGHT(TapePlacement.STRIP, 2),
    CORNERS(TapePlacement.CORNERS),
    CORNERS_OTHER(TapePlacement.CORNERS, 1),
    TOP(TapePlacement.ACROSS),
    BOTTOM(TapePlacement.ACROSS, 2),
    TOP_PAIR(TapePlacement.PAIR),
    BOTTOM_PAIR(TapePlacement.PAIR, 2),
    FOUR_CORNERS(TapePlacement.FOUR_CORNERS),
    TOP_AND_BOTTOM(TapePlacement.TOP_AND_BOTTOM),
    PHOTO_CORNERS(TapePlacement.PHOTO_CORNERS),
}

/**
 * A mix of tape placements for the cards on one screen: stable for the same [seed] (screen and
 * persona), never the same placement on two cards in a row, and never tape at the same corner of
 * one card's bottom and the next one's top.
 */
fun tapeLayouts(seed: String, count: Int): List<TapeLayout> {
    val random = Random(seed.hashCode())
    var previous: TapeLayout? = null
    return List(count) {
        val options = TapeLayout.entries.filter { it != previous && (previous == null || (previous!!.bottomCorners intersect it.topCorners).isEmpty()) }
        options[random.nextInt(options.size)].also { previous = it }
    }
}

/** Where a layout's tape reaches the top and bottom edges ("L", "C", "R"), so neighbours don't collide. */
private val TapeLayout.topCorners: Set<String>
    get() = when (this) {
        TapeLayout.LEFT -> setOf("L")
        TapeLayout.RIGHT -> setOf("R")
        TapeLayout.CORNERS -> setOf("L")
        TapeLayout.CORNERS_OTHER -> setOf("R")
        TapeLayout.TOP, TapeLayout.TOP_AND_BOTTOM -> setOf("C")
        TapeLayout.BOTTOM, TapeLayout.BOTTOM_PAIR -> emptySet()
        TapeLayout.TOP_PAIR, TapeLayout.FOUR_CORNERS, TapeLayout.PHOTO_CORNERS -> setOf("L", "R")
    }

private val TapeLayout.bottomCorners: Set<String>
    get() = when (this) {
        TapeLayout.LEFT -> setOf("L")
        TapeLayout.RIGHT -> setOf("R")
        TapeLayout.CORNERS -> setOf("R")
        TapeLayout.CORNERS_OTHER -> setOf("L")
        TapeLayout.BOTTOM, TapeLayout.TOP_AND_BOTTOM -> setOf("C")
        TapeLayout.TOP, TapeLayout.TOP_PAIR -> emptySet()
        TapeLayout.BOTTOM_PAIR, TapeLayout.FOUR_CORNERS, TapeLayout.PHOTO_CORNERS -> setOf("L", "R")
    }

/** A paper card held down by [tape] in [layout], padded so the tape never covers its content. */
@Composable
fun TapedCard(
    layout: TapeLayout,
    tape: Tape,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    spacing: Dp = 9.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val padding = when (layout) {
        TapeLayout.LEFT -> PaddingValues(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        TapeLayout.RIGHT -> PaddingValues(start = 16.dp, end = 22.dp, top = 14.dp, bottom = 14.dp)
        TapeLayout.TOP, TapeLayout.TOP_PAIR -> PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)
        TapeLayout.BOTTOM, TapeLayout.BOTTOM_PAIR -> PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
        TapeLayout.TOP_AND_BOTTOM -> PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)
        TapeLayout.CORNERS, TapeLayout.CORNERS_OTHER, TapeLayout.FOUR_CORNERS, TapeLayout.PHOTO_CORNERS -> PaddingValues(16.dp, 14.dp)
    }
    val held = Modifier.taped(tape, layout.placement, layout.turn)
    Column(
        modifier
            .rotate(tilt)
            .then(held)
            .paperCard()
            .fillMaxWidth()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** A gentle per-card tilt for [TapedCard]s, stable for the same seed. */
fun cardTilt(seed: String): Float = ((seed.hashCode() % 11) / 10f).coerceIn(-0.6f, 0.6f)

/** Card title in the display face, with optional small note and trailing action. */
@Composable
fun CardTitle(title: String, note: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = PaperType.entryTitle, color = paper.sumi)
        note?.let { Text(it, style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2, modifier = Modifier.padding(bottom = 2.dp)) }
        Box(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A small text link in ink. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InkLink(text: String, ink: Color = stationery.you, onClick: () -> Unit) {
    Text(
        text,
        style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Bold),
        color = ink,
        modifier = Modifier.clip(PaperCorner).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
