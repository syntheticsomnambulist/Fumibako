package dev.aiharness.app.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.chat.StickerSlip
import dev.aiharness.app.ui.chat.StickerTray
import dev.aiharness.app.ui.chat.letterStamp
import dev.aiharness.app.ui.chat.stickerInk
import dev.aiharness.app.ui.chat.stickerLabel
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.stickers.StickResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.aiharness.app.platform.rememberOutside

/**
 * The stickers on a planner (its days and plans), and what you do with them: tap one to move,
 * resize or peel it; open the tray to tap one onto [PlannerStickers.trayTarget] or drag it onto
 * any day or plan. They're kept under the planner (not a letter), so a planner can be covered.
 */
class PlannerStickers internal constructor(
    private val stuck: Map<StickerTarget, List<Sticker>>,
    val selectedId: String?,
    /** The sticker picked or being moved, whose paper comes to the front so it (and its handle) isn't under its neighbors. */
    val liftedId: String?,
    val onTap: (Sticker) -> Unit,
    val onChange: (Sticker) -> Unit,
    val inkOf: (Sticker) -> Color,
    val nameOf: (Sticker) -> String?,
    private val papers: MutableMap<StickerTarget, Rect>,
    /** Opens the tray; a tap in it sticks onto [onto]. */
    val openTray: (onto: StickerTarget) -> Unit,
) {
    fun on(target: StickerTarget): List<Sticker> = stuck[target].orEmpty()

    /** Whether [target] holds the sticker picked or being moved. */
    fun lifts(target: StickerTarget): Boolean = liftedId != null && on(target).any { it.id == liftedId }

    /** Where [target] is on screen, so a sticker dragged from the tray can land on it. */
    fun paper(target: StickerTarget): Modifier = Modifier.onGloballyPositioned { papers[target] = it.boundsInRoot() }

    fun forget(target: StickerTarget) {
        papers.remove(target)
    }

    /** [content] with what's stuck on [target] over it. */
    @Composable
    fun On(target: StickerTarget, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        androidx.compose.runtime.DisposableEffect(target) { onDispose { forget(target) } }
        StuckOn(
            stickers = on(target),
            inkOf = inkOf,
            // Its paper comes to the front while one of its stickers is picked or moved.
            modifier = modifier.zIndex(if (lifts(target)) 1f else 0f).then(paper(target)),
            nameOf = nameOf,
            selectedId = selectedId,
            onTap = onTap,
            onChange = onChange,
            content = content,
        )
    }
}

/** Where a sticker tapped from the tray lands on a day: the corners and edges in turn, a little crooked. */
private val SPOTS = listOf(Triple(0.8f, 0.1f, 10f), Triple(0.25f, 0.3f, -9f), Triple(0.75f, 0.55f, 14f), Triple(0.3f, 0.8f, -6f), Triple(0.5f, 0.2f, 8f), Triple(0.55f, 0.92f, -12f))

/**
 * Sets up a planner's stickers for [content] (which gets the room the tray and the slip take at
 * the bottom), and shows the tray, the picked sticker's slip, and a sticker being dragged.
 */
@Composable
fun PlannerStickerHost(plannerId: String?, you: UserPersona?, content: @Composable (PlannerStickers, bottom: androidx.compose.ui.unit.Dp) -> Unit) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val outside = rememberOutside()
    val sheet = stationery
    val conversation = plannerId?.let(StickerTarget::plannerConversation)
    val stuckFlow = remember(conversation) { conversation?.let { harness.stickers.observeForConversation(it) } ?: flowOf(emptyList()) }
    val all by stuckFlow.collectAsStateWithLifecycle(emptyList())
    val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
    val artById = remember(art) { art.associateBy { it.id } }
    val recent by container.recentStickers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<Sticker?>(null) }
    var trayFor by remember { mutableStateOf<StickerTarget?>(null) }
    var dragging by remember { mutableStateOf<Pair<StickerRef, Offset>?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val papers = remember { mutableStateMapOf<StickerTarget, Rect>() }
    val yourInk = sheet.dayColors.ink(you?.ink ?: "sora")
    // A sticker being moved is saved once it's let go of for a moment.
    LaunchedEffect(moving) {
        val m = moving ?: return@LaunchedEffect
        delay(400)
        harness.stickerService.move(m, m.x, m.y, m.rotationDeg, m.scale)
    }
    val finish = {
        moving?.let { m -> scope.launch { harness.stickerService.move(m, m.x, m.y, m.rotationDeg, m.scale) } }
        moving = null
        selectedId = null
    }
    val shown = remember(all, moving) {
        val m = moving
        all.filter { !it.isPeeled }.map { if (m != null && it.id == m.id) m else it }.groupBy { it.target }
    }
    fun stick(target: StickerTarget, ref: StickerRef, x: Float? = null, y: Float? = null) {
        val c = conversation ?: return
        scope.launch {
            val n = harness.stickerService.on(c, target).size
            val (sx, sy, tilt) = x?.let { Triple(it, y ?: 0.5f, (Math.random() * 16 - 8).toFloat()) } ?: SPOTS[n % SPOTS.size]
            when (val r = harness.stickerService.stick(c, target, ref, null, sx, sy, rotationDeg = tilt, scale = 0.7f)) {
                is StickResult.Stuck -> container.usedSticker(ref)
                is StickResult.Refused -> outside.toast(r.reason)
            }
        }
    }
    val stickers = PlannerStickers(
        stuck = shown,
        selectedId = selectedId,
        liftedId = selectedId ?: moving?.id,
        onTap = { st ->
            if (selectedId == st.id) finish() else {
                moving?.let { m -> scope.launch { harness.stickerService.move(m, m.x, m.y, m.rotationDeg, m.scale) } }
                moving = null
                selectedId = st.id
            }
        },
        onChange = { moving = it },
        inkOf = { st -> stickerInk(st, sheet, null, you) },
        nameOf = { st -> stickerLabel(st, artById) },
        papers = papers,
        openTray = { onto -> finish(); trayFor = if (trayFor == onto) null else onto },
    )
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        content(stickers, if (trayFor != null) 330.dp else if (selectedId != null) 70.dp else 0.dp)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(paper.hyoshi)) {
            selectedId?.let { id -> shown.values.flatten().firstOrNull { it.id == id } }?.let { st ->
                StickerSlip(
                    text = "Your ${stickerLabel(st, artById).removeSuffix(" sticker")}, stuck ${letterStamp(st.createdAt).lowercase()}.",
                    peel = { scope.launch { harness.stickerService.peel(st, null) }; moving = null; selectedId = null },
                    done = { finish() },
                )
            }
            trayFor?.let { onto ->
                StickerTray(
                    onBack = { trayFor = null },
                    backLabel = "Close",
                    backHint = "Close the stickers",
                    hint = "Tap: onto ${if (onto.id.endsWith("/" + java.time.LocalDate.now())) "today" else "this day"}. Hold and drag: onto any day or plan.",
                    personaName = "",
                    personaId = null,
                    recent = recent,
                    art = artById,
                    youInk = yourInk,
                    onPick = { stick(onto, it) },
                    onDragStart = { ref, at -> dragging = ref to at },
                    onDrag = { delta -> dragging = dragging?.let { (ref, at) -> ref to (at + delta) } },
                    onDragEnd = {
                        dragging?.let { (ref, at) ->
                            // Onto whatever's under the finger, the smallest first (a plan before its day).
                            papers.entries.filter { it.value.contains(at) }.minByOrNull { it.value.width * it.value.height }?.let { (target, rect) ->
                                stick(target, ref, (at.x - rect.left) / rect.width, (at.y - rect.top) / rect.height)
                            }
                        }
                        dragging = null
                    },
                )
            }
        }
        dragging?.let { (ref, at) ->
            val side = (LocalStickerSettings.current.size.dp * StickerBaker.BOX).dp
            val half = with(LocalDensity.current) { side.toPx() / 2f }
            StickerPicture(ref, yourInk, Modifier.offset { IntOffset((at.x - origin.x - half).roundToInt(), (at.y - origin.y - half).roundToInt()) }.size(side))
        }
    }
}
