package dev.aiharness.app.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.paper

/*
 * Mouse and keyboard on a computer (docs/plan-desktop.md, "Mouse, keyboard and the window"): a
 * right-click for a long press, drags without holding, the wheel for pinches, Ctrl+Enter to send,
 * and scrollbars.
 */

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onRightClick(action: () -> Unit): Modifier = onPointerEvent(PointerEventType.Press) { event ->
    // The innermost one under the pointer answers (a plan inside a day, say), not each around it.
    if (event.buttons.isSecondaryPressed && event.changes.none { it.isConsumed }) {
        event.changes.forEach { it.consume() }
        action()
    }
}

actual val dragsWithoutHolding: Boolean = true

actual suspend fun PointerInputScope.detectHoldDrag(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = detectDragGestures(onDragStart = onDragStart, onDragEnd = onDragEnd, onDragCancel = onDragCancel, onDrag = onDrag)

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.onWheel(onWheel: (steps: Float, shift: Boolean) -> Unit): Modifier = onPointerEvent(PointerEventType.Scroll) { event ->
    val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
    // With Shift, some systems send the wheel sideways.
    val steps = if (delta.y != 0f) delta.y else delta.x
    if (steps == 0f) return@onPointerEvent
    event.changes.forEach { it.consume() }
    onWheel(steps, event.keyboardModifiers.isShiftPressed)
}

actual fun Modifier.sendOnCtrlEnter(onSend: () -> Unit): Modifier = onPreviewKeyEvent { key ->
    val send = key.type == KeyEventType.KeyDown && (key.key == Key.Enter || key.key == Key.NumPadEnter) && (key.isCtrlPressed || key.isMetaPressed)
    if (send) onSend()
    send
}

/** Where a page is and how long it is, in pixels, for its scrollbar. */
private interface PageExtent {
    /** How far down (for a list that starts at the bottom, how far up). */
    val offset: Float
    val content: Float
    val viewport: Float
    val fromBottom: Boolean

    fun scrollBy(pixels: Float)
}

actual fun Modifier.scrollbar(state: ScrollState): Modifier = scrollbar(
    object : PageExtent {
        override val offset get() = state.value.toFloat()
        override val viewport get() = state.viewportSize.toFloat()
        override val content get() = state.maxValue.toFloat() + viewport
        override val fromBottom = false
        override fun scrollBy(pixels: Float) {
            state.dispatchRawDelta(pixels)
        }
    },
)

actual fun Modifier.scrollbar(state: LazyListState): Modifier = scrollbar(
    object : PageExtent {
        // A list knows only the items on screen: the rest are taken to be their average size.
        private val average: Float
            get() = state.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }
                ?.let { items -> items.sumOf { it.size }.toFloat() / items.size + state.layoutInfo.mainAxisItemSpacing } ?: 0f
        override val offset get() = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
        override val content get() = state.layoutInfo.let { it.totalItemsCount * average + it.beforeContentPadding + it.afterContentPadding }
        override val viewport get() = state.layoutInfo.let { (it.viewportEndOffset - it.viewportStartOffset).toFloat() }
        override val fromBottom get() = state.layoutInfo.reverseLayout
        override fun scrollBy(pixels: Float) {
            state.dispatchRawDelta(pixels)
        }
    },
)

actual fun Modifier.scrollbar(state: LazyGridState): Modifier = scrollbar(
    object : PageExtent {
        private val columns: Int
            get() = state.layoutInfo.visibleItemsInfo.let { items -> items.count { it.row == items.firstOrNull()?.row }.coerceAtLeast(1) }
        private val rowHeight: Float
            get() = state.layoutInfo.visibleItemsInfo.takeIf { it.isNotEmpty() }
                ?.let { items -> items.maxOf { it.size.height }.toFloat() + state.layoutInfo.mainAxisItemSpacing } ?: 0f
        override val offset get() = (state.firstVisibleItemIndex / columns) * rowHeight + state.firstVisibleItemScrollOffset
        override val content get() = state.layoutInfo.let { ((it.totalItemsCount + columns - 1) / columns) * rowHeight + it.beforeContentPadding + it.afterContentPadding }
        override val viewport get() = state.layoutInfo.let { (it.viewportEndOffset - it.viewportStartOffset).toFloat() }
        override val fromBottom get() = state.layoutInfo.reverseLayout
        override fun scrollBy(pixels: Float) {
            state.dispatchRawDelta(pixels)
        }
    },
)

/**
 * A thin bar along the right edge while the page is longer than its window, wider under the
 * pointer. Drag it to scroll, or click above or below it to move a page.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.scrollbar(extent: PageExtent): Modifier = composed {
    // A new extent comes with each recomposition; the gesture below keeps going with the latest.
    val page by rememberUpdatedState(extent)
    var near by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val ink = paper.sumi
    val reach = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    this
        .onPointerEvent(PointerEventType.Move) { near = it.changes.first().position.x >= size.width - reach }
        .onPointerEvent(PointerEventType.Exit) { near = false }
        .pointerInput(Unit) {
            awaitEachGesture {
                // Before what's under it gets the press (a card at the edge), so a drag on the bar is the bar's.
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val bar = Bar.of(page, size.height.toFloat(), 16.dp.toPx()) ?: return@awaitEachGesture
                if (down.position.x < size.width - reach) return@awaitEachGesture
                down.consume()
                val y = down.position.y
                if (y < bar.top || y > bar.top + bar.length) {
                    // Above or below the thumb: a page that way.
                    val towardEnd = (y > bar.top + bar.length) != page.fromBottom
                    page.scrollBy(if (towardEnd) page.viewport * 0.9f else -page.viewport * 0.9f)
                    return@awaitEachGesture
                }
                dragging = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val moved = change.position.y - change.previousPosition.y
                        change.consume()
                        val now = Bar.of(page, size.height.toFloat(), 16.dp.toPx()) ?: break
                        val perPixel = (page.content - page.viewport) / (size.height - now.length).coerceAtLeast(1f)
                        page.scrollBy(if (page.fromBottom) -moved * perPixel else moved * perPixel)
                    }
                } finally {
                    dragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()
            val bar = Bar.of(page, size.height, 16.dp.toPx()) ?: return@drawWithContent
            val wide = near || dragging
            val width = (if (wide) 7.dp else 4.dp).toPx()
            drawRoundRect(
                ink.copy(alpha = if (wide) 0.42f else 0.22f),
                topLeft = Offset(size.width - width - 2.dp.toPx(), bar.top),
                size = Size(width, bar.length),
                cornerRadius = CornerRadius(width / 2),
            )
        }
}

/** The thumb: where it starts and how long it is, or null when the page fits. */
private class Bar(val top: Float, val length: Float) {
    companion object {
        fun of(page: PageExtent, track: Float, shortest: Float): Bar? {
            val content = page.content
            val viewport = page.viewport
            if (track <= 0f || viewport <= 0f || content <= viewport + 1f) return null
            val length = (track * viewport / content).coerceIn(shortest.coerceAtMost(track), track)
            val through = (page.offset / (content - viewport)).coerceIn(0f, 1f)
            val top = (track - length) * (if (page.fromBottom) 1f - through else through)
            return Bar(top, length)
        }
    }
}
