package dev.aiharness.app.ui.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.chat.stickerInk
import dev.aiharness.app.ui.media.FrameActions
import dev.aiharness.app.ui.media.FullPlayer
import dev.aiharness.app.ui.media.LocalMediaSettings
import dev.aiharness.app.ui.media.MiniPlayer
import dev.aiharness.app.ui.media.PhotoViewer
import dev.aiharness.app.ui.media.VideoPlayer
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.StickerTargetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import dev.aiharness.app.ui.chat.StickerSlip
import dev.aiharness.app.ui.chat.StickerTray
import dev.aiharness.app.ui.chat.letterStamp
import dev.aiharness.app.ui.chat.stickerLabel
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.stickers.StickResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.media.Cutting
import dev.aiharness.app.ui.media.FilmSnip
import dev.aiharness.app.ui.media.JCardInsert
import dev.aiharness.app.ui.media.LuggageTag
import dev.aiharness.app.ui.media.TicketStub
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.memory.MemoryAge
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.Sticker

/** What tapping a keepsake does in the book: plays or opens it in place. */
class KeepsakeTaps(
    val play: (ContentPart.Attachment, Color, Boolean) -> Unit,
    val view: (String) -> Unit,
    val watch: (ContentPart.Attachment, Color, Boolean) -> Unit,
    val open: (ContentPart.Attachment) -> Unit,
)

/** The book's view of stickers and marks: the stickers on each kept attachment, punched tickets, read stamps. */
class KeepsakeMarks(
    val stickers: (String) -> List<Sticker> = { emptyList() },
    val inkOf: (Sticker) -> Color = { Color.Unspecified },
    val punched: Set<String> = emptySet(),
    val readBy: Map<String, Color> = emptyMap(),
    /** Whose ink a keepsake is in: yours for something from your note, the persona's for its own. */
    val inkOfKeepsake: (Keepsake) -> Color = { Color.Unspecified },
    /** Stickers you can move and peel on keepsakes, and holding one to stick a sticker on it. */
    val selectedId: String? = null,
    val onTap: ((Sticker) -> Unit)? = null,
    val onChange: ((Sticker) -> Unit)? = null,
    val paper: (StickerTarget, String?) -> Modifier = { _, _ -> Modifier },
    val onHold: ((StickerTarget, String?) -> Unit)? = null,
    /** A keepsake that scrolled away can't be dropped on. */
    val forget: (StickerTarget) -> Unit = {},
    /** The conversation a keepsake's letter is in, found from the letter. */
    val conversationOf: (Keepsake) -> String? = { null },
    /** A sticker's name, ":heart: sticker", for TalkBack. */
    val nameOf: (Sticker) -> String? = { null },
)

/**
 * Sets up keepsakes for a page of the memory book: their stickers (stuck on the attachment,
 * wherever it shows), punches and read stamps, and what tapping one does. A song plays with the
 * mini player docked at the bottom, so you can keep scrolling; photos and videos open their
 * viewers; a cutting opens its page. [content] gets the room the mini player takes.
 */
@Composable
fun KeepsakeHost(personaId: String, persona: Persona?, name: String, content: @Composable (below: Dp) -> Unit) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val context = LocalContext.current
    val sheet = stationery
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val mine = remember(conversations, personaId) { conversations.filter { it.personaId == personaId } }
    val you = users.firstOrNull { it.id == settings?.activeUserPersonaId } ?: users.firstOrNull()
    val personaInk = sheet.dayColors.ink(persona?.ink)
    val yourInk = sheet.dayColors.ink(you?.ink ?: "sora")
    val stuckFlow = remember(mine) {
        if (mine.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else combine(mine.map { harness.stickers.observeForConversation(it.id) }) { lists -> lists.flatMap { it } }
    }
    val allStuck by stuckFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    // Stickers on keepsakes: one selected to move, resize or peel; holding a keepsake opens the tray for it.
    var selectedId by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<Sticker?>(null) }
    var trayFor by remember { mutableStateOf<Pair<StickerTarget, String>?>(null) }
    var dragging by remember { mutableStateOf<Pair<StickerRef, Offset>?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val papers = remember { mutableStateMapOf<StickerTarget, Pair<Rect, String>>() }
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
    val shown = remember(allStuck, moving) {
        val m = moving
        allStuck.filter { it.target.kind == StickerTargetKind.ATTACHMENT && !it.isPeeled }
            .map { if (m != null && it.id == m.id) m else it }
            .groupBy { it.target.id }
    }
    fun stick(target: StickerTarget, conversationId: String, ref: StickerRef, x: Float? = null, y: Float? = null) {
        scope.launch {
            val n = harness.stickerService.on(conversationId, target).size
            val (sx, sy, tilt) = x?.let { Triple(it, y ?: 0.5f, (Math.random() * 16 - 8).toFloat()) } ?: SPOTS[n % SPOTS.size]
            when (val r = harness.stickerService.stick(conversationId, target, ref, null, sx, sy, rotationDeg = tilt, scale = 0.8f)) {
                is StickResult.Stuck -> container.usedSticker(ref)
                is StickResult.Refused -> android.widget.Toast.makeText(context, r.reason, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
    val artById = remember(art) { art.associateBy { it.id } }
    // Which conversation each letter is in, for keepsakes whose memory doesn't say.
    val letterConversation by produceState(emptyMap<String, String>(), mine) {
        value = withContext(Dispatchers.IO) { mine.flatMap { c -> harness.conversations.getMessages(c.id).map { it.id to c.id } }.toMap() }
    }
    val allMarks by remember { harness.marks.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val marks = KeepsakeMarks(
        stickers = { shown[it].orEmpty() },
        inkOf = { st -> stickerInk(st, sheet, persona, you) },
        punched = remember(allMarks) { allMarks.filter { it.kind == MarkKind.PUNCHED }.map { it.attachmentId }.toSet() },
        readBy = remember(allMarks, personaInk) { allMarks.filter { it.kind == MarkKind.READ && it.byPersonaId == personaId }.associate { it.attachmentId to personaInk } },
        inkOfKeepsake = { if (it.fromYou) yourInk else personaInk },
        selectedId = selectedId,
        onTap = { st ->
            if (selectedId == st.id) finish() else {
                moving?.let { m -> scope.launch { harness.stickerService.move(m, m.x, m.y, m.rotationDeg, m.scale) } }
                moving = null
                selectedId = st.id
            }
        },
        onChange = { moving = it },
        paper = { target, conversationId ->
            if (conversationId == null) Modifier else Modifier.onGloballyPositioned { papers[target] = it.boundsInRoot() to conversationId }
        },
        onHold = { target, conversationId -> if (conversationId != null) { finish(); trayFor = target to conversationId } },
        forget = { papers.remove(it) },
        conversationOf = { k -> k.messageId?.let { letterConversation[it] } },
        nameOf = { st -> stickerLabel(st, artById) },
    )
    val recent by container.recentStickers.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<String?>(null) }
    var watching by remember { mutableStateOf<Pair<ContentPart.Attachment, Color>?>(null) }
    var playerOpen by remember { mutableStateOf(false) }
    val nowPlaying by container.playback.state.collectAsStateWithLifecycle()
    val media = LocalMediaSettings.current
    val toolbar = paper.kami.toArgb()
    val night = sheet.isNight
    val taps = remember(personaId, media, toolbar, night) {
        KeepsakeTaps(
            play = { a, ink, fromYou -> container.playback.play(a, null, ink.toArgb(), fromYou) },
            view = { viewing = it },
            watch = { a, ink, _ -> watching = a to ink },
            open = { a ->
                val url = a.url
                if (url != null && a.ref == null) FrameActions.openLink(context, url, media.linksInApp, toolbar, night)
                else FrameActions.open(context, container.blobStore, a)
            },
        )
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalKeepsakeTaps provides taps, LocalKeepsakeMarks provides marks) {
            content(if (nowPlaying != null || trayFor != null || selectedId != null) 70.dp else 0.dp)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(paper.hyoshi)) {
            val selected = selectedId?.let { id -> shown.values.flatten().firstOrNull { it.id == id } }
            selected?.let { st ->
                val mine = st.byPersonaId == null
                val canPeel = harness.stickerService.youMayPeel(st, persona?.stickers?.youCanPeel == true)
                StickerSlip(
                    text = if (mine) "Your ${stickerLabel(st, artById).removeSuffix(" sticker")}, stuck ${letterStamp(st.createdAt).lowercase()}."
                    else "$name stuck this ${letterStamp(st.createdAt).lowercase()}." + if (canPeel) "" else " Only $name can peel it off.",
                    peel = if (canPeel) ({ scope.launch { harness.stickerService.peel(st, null) }; moving = null; selectedId = null }) else null,
                    done = { finish() },
                )
            }
            trayFor?.let { (target, conversationId) ->
                StickerTray(
                    onBack = { trayFor = null },
                    backLabel = "Close",
                    backHint = "Close the stickers",
                    hint = "Tap: onto the one you held. Hold and drag: onto any keepsake.",
                    personaName = name,
                    personaId = personaId,
                    recent = recent,
                    art = artById,
                    youInk = yourInk,
                    onPick = { stick(target, conversationId, it) },
                    onDragStart = { ref, at -> dragging = ref to at },
                    onDrag = { delta -> dragging = dragging?.let { (ref, at) -> ref to (at + delta) } },
                    onDragEnd = {
                        dragging?.let { (ref, at) ->
                            // Onto whichever keepsake is under the finger.
                            papers.entries.filter { it.value.first.contains(at) }.minByOrNull { it.value.first.width * it.value.first.height }?.let { (t, value) ->
                                val (rect, c) = value
                                stick(t, c, ref, (at.x - rect.left) / rect.width, (at.y - rect.top) / rect.height)
                            }
                        }
                        dragging = null
                    },
                )
            }
            if (trayFor == null) nowPlaying?.let { MiniPlayer(it, container.playback, onOpen = { playerOpen = true }) }
        }
        dragging?.let { (ref, at) ->
            val side = (LocalStickerSettings.current.size.dp * StickerBaker.BOX).dp
            val half = with(LocalDensity.current) { side.toPx() / 2f }
            StickerPicture(
                ref,
                yourInk,
                Modifier.offset { IntOffset((at.x - origin.x - half).roundToInt(), (at.y - origin.y - half).roundToInt()) }.size(side),
            )
        }
        val playing = nowPlaying
        if (playerOpen && playing != null) FullPlayer(playing, container.playback, knows = null, onClose = { playerOpen = false })
        LaunchedEffect(playing == null) { if (playing == null) playerOpen = false }
        viewing?.let { ref -> PhotoViewer(listOf(ContentPart.Image(ref)), 0, onClose = { viewing = null }) }
        watching?.let { (video, ink) ->
            VideoPlayer(listOf(video), 0, from = name, ink = ink, line = { null }, onOpen = { container.playback.pause() }, onClose = { watching = null })
        }
    }
}

val LocalKeepsakeTaps = staticCompositionLocalOf<KeepsakeTaps?> { null }
val LocalKeepsakeMarks = staticCompositionLocalOf { KeepsakeMarks() }

/** Keepsakes in the book are about two-thirds the size of their frames in a letter. */
const val KEEPSAKE_SCALE = 0.66f

/**
 * A keepsake as it's glued into the book (docs/design/keepsakes.md, "How each object looks in the
 * book"): a smaller polaroid, a tag tied through a punched hole, the stub (punched if played), a
 * cassette's J-card, a smaller cutting, two frames snipped from the strip. Still while you scroll;
 * aged with its memory; its stickers stay stuck.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepsakeObject(
    keepsake: Keepsake,
    ink: Color,
    age: MemoryAge?,
    modifier: Modifier = Modifier,
    scale: Float = KEEPSAKE_SCALE,
    /** The conversation its letter is in, where its stickers belong. */
    conversationId: String? = null,
) {
    val taps = LocalKeepsakeTaps.current
    val marks = LocalKeepsakeMarks.current
    val part = keepsake.part
    val tap: (() -> Unit)? = taps?.let { t ->
        when (part) {
            is ContentPart.Image -> ({ t.view(part.ref) })
            is ContentPart.Attachment -> when (part.kind) {
                AttachmentKind.SONG, AttachmentKind.PLAYLIST -> ({ t.play(part, ink, keepsake.fromYou) })
                AttachmentKind.VIDEO -> ({ t.watch(part, ink, keepsake.fromYou) })
                else -> ({ t.open(part) })
            }
            else -> null
        }
    }
    val target = StickerTarget.attachment(keepsake.key)
    val convo = conversationId ?: marks.conversationOf(keepsake)
    val hold = marks.onHold?.let { h -> { h(target, convo) } }
    androidx.compose.runtime.DisposableEffect(target) { onDispose { marks.forget(target) } }
    StuckOn(
        stickers = marks.stickers(keepsake.key),
        inkOf = marks.inkOf,
        modifier = modifier.graphicsLayer { rotationZ = seededTilt(keepsake.key, 3f) }.then(marks.paper(target, convo)),
        nameOf = marks.nameOf,
        selectedId = marks.selectedId,
        onTap = marks.onTap,
        onChange = marks.onChange,
    ) {
        Box(
            Modifier.aged(age, part, keepsake.key)
                .then(
                    if (tap != null || hold != null) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClickLabel = "Open it",
                            onClick = { tap?.invoke() },
                            onLongClickLabel = "Stick a sticker",
                            onLongClick = hold,
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            when (part) {
                is ContentPart.Image -> BookPolaroid(part.ref, polaroidCaptionOf(part), ink, keepsake.key) { tap?.invoke() }
                is ContentPart.Attachment -> when (part.kind) {
                    AttachmentKind.FILE -> TiedTag(part, ink, scale)
                    AttachmentKind.SONG -> TicketStub(part, ink, paper.hyoshi, scale, punched = part.id in marks.punched)
                    AttachmentKind.PLAYLIST -> JCardInsert(part, ink, scale)
                    AttachmentKind.LINK -> Cutting(part, ink, scale, readBy = marks.readBy[part.id])
                    AttachmentKind.VIDEO -> FilmSnip(part, scale)
                }
                else -> Unit
            }
        }
    }
}

private fun polaroidCaptionOf(image: ContentPart.Image) = dev.aiharness.app.ui.media.polaroidCaption(image.caption)

/** A luggage tag, its string tied through a hole punched in the page. */
@Composable
private fun TiedTag(file: ContentPart.Attachment, ink: Color, scale: Float) {
    val hole = paper.sumi.copy(alpha = 0.55f)
    val rim = paper.kami
    Box {
        LuggageTag(file, ink, scale, Modifier.padding(start = (14 * scale).dp))
        Canvas(Modifier.size((16 * scale).dp).offset(y = (28 * scale).dp)) {
            val r = size.minDimension / 2
            drawCircle(rim, r)
            drawCircle(hole, r * 0.62f)
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.62f, style = Stroke(r * 0.12f))
        }
    }
}

/**
 * Keepsakes age with their memory: fading lightens and yellows them (newsprint fastest, then
 * polaroids); a worn one creases and loses a corner.
 */
private fun Modifier.aged(age: MemoryAge?, part: ContentPart, seed: String): Modifier {
    if (age == null || age == MemoryAge.FRESH) return this
    val yellowing = when {
        part is ContentPart.Attachment && part.kind == AttachmentKind.LINK -> 0.34f
        part is ContentPart.Image -> 0.26f
        else -> 0.18f
    } * (if (age == MemoryAge.WORN) 1.3f else 1f)
    val corner = if (age == MemoryAge.WORN) LostCorner(seed) else null
    return this
        .then(if (corner != null) Modifier.clip(corner) else Modifier)
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen, alpha = if (age == MemoryAge.WORN) 0.86f else 0.92f)
        .drawWithContent {
            drawContent()
            drawRect(Color(0xFFF3E2B0).copy(alpha = yellowing.coerceAtMost(0.5f)), blendMode = BlendMode.SrcAtop)
            if (age == MemoryAge.WORN) {
                // A crease across it.
                val y = size.height * (0.35f + (seed.hashCode() and 0xFF) / 255f * 0.3f)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y + size.height * 0.08f), 1.5f)
                drawLine(Color.Black.copy(alpha = 0.10f), Offset(0f, y + 1.5f), Offset(size.width, y + size.height * 0.08f + 1.5f), 1f)
            }
        }
}

/** A shape with one corner (chosen by [seed]) torn off. */
private fun LostCorner(seed: String) = GenericShape { size, _ ->
    val cut = minOf(size.width, size.height) * 0.14f
    val which = (seed.hashCode() and 0x3)
    val w = size.width
    val h = size.height
    when (which) {
        0 -> { moveTo(cut, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); lineTo(0f, cut * 0.8f) }
        1 -> { moveTo(0f, 0f); lineTo(w - cut, 0f); lineTo(w, cut * 0.8f); lineTo(w, h); lineTo(0f, h) }
        2 -> { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h - cut * 0.8f); lineTo(w - cut, h); lineTo(0f, h) }
        else -> { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(cut, h); lineTo(0f, h - cut * 0.8f) }
    }
    close()
}

/**
 * A day's keepsakes on its page: at most two, with a small "+2" leading to the rest on the
 * memory cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayKeepsakes(kept: List<Pair<Keepsake, BookPage>>, onMore: (String) -> Unit) {
    val inkOf = LocalKeepsakeMarks.current.inkOfKeepsake
    if (kept.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 4.dp, end = 10.dp),
    ) {
        kept.take(2).forEach { (keepsake, page) -> KeepsakeObject(keepsake, inkOf(keepsake), page.age, conversationId = page.memory.sourceConversationId) }
        if (kept.size > 2) {
            val (_, page) = kept[2]
            Box(Modifier.align(Alignment.CenterVertically).clickable(role = Role.Button, onClickLabel = "See the rest") { onMore(page.memory.id) }.padding(8.dp)) {
                Row { Text("+${kept.size - 2}", style = PaperType.mood, color = paper.usuzumi1) }
            }
        }
    }
}

/** Where stickers tapped onto a keepsake land, in turn: around its top edge, tilted. */
private val SPOTS = listOf(Triple(0.88f, 0.12f, 10f), Triple(0.14f, 0.14f, -9f), Triple(0.86f, 0.82f, 14f), Triple(0.16f, 0.84f, -6f), Triple(0.5f, 0.08f, 8f), Triple(0.5f, 0.9f, -12f))
