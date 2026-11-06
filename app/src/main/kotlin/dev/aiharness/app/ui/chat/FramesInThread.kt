package dev.aiharness.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.media.LocalLift
import dev.aiharness.app.ui.media.reducedMotion
import dev.aiharness.app.ui.paper.PaperButton
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.media.AttachmentFrame
import dev.aiharness.app.ui.media.FrameActions
import dev.aiharness.app.ui.media.PhotoStack
import dev.aiharness.app.ui.media.PolaroidFrame
import dev.aiharness.app.ui.media.frameTilt
import dev.aiharness.app.ui.media.noteScale
import dev.aiharness.app.ui.media.playable
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.launch

/** What a frame does as it's tapped, before (or as) it opens (docs/design/media.md, "Tapping and motion"). */
sealed interface TapMotion {
    /** Opens straight away; the frame's own content moves, if anything does. */
    data object None : TapMotion

    /** Lifts [height], straightens and grows to [grow] over [ms], then opens after [openAfter]. */
    data class Lift(val height: Dp, val grow: Float, val ms: Int, val openAfter: Long) : TapMotion

    /** Swings on its string around [pivot] (a fraction of the frame), damped, as it opens. */
    data class Swing(val pivot: TransformOrigin) : TapMotion

    companion object {
        val Photo = Lift(10.dp, 1.05f, 280, 220)
        val Cutting = Lift(6.dp, 1.03f, 160, 160)
        val Tag = Swing(TransformOrigin(0.18f, 0.5f))
    }
}

/**
 * Frames that have just arrived (in a letter that came or a note just sent), by key, with their
 * place in it: they drop onto the page one after another. [done] keeps them from dropping again
 * when they scroll back into view.
 */
class Arrivals(val order: Map<String, Int>, val done: MutableSet<String> = mutableSetOf())

val LocalArrivals = staticCompositionLocalOf<Arrivals?> { null }

/**
 * A frame placed in a letter or note: tilted a little, stuck with its own stickers (stickers on a
 * frame belong to the attachment), opened by a tap with its [motion], held for Save, Share or Copy
 * link, and dropped onto the page when it has just arrived. With "Remove animations" on, every
 * effect is instant.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnPaper(
    key: String,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    onOpen: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    alignment: Alignment = Alignment.Center,
    motion: TapMotion = TapMotion.None,
    content: @Composable () -> Unit,
) {
    val target = StickerTarget.attachment(key)
    var open by remember { mutableStateOf(false) }
    if (handling != null) DisposableEffect(key) { onDispose { handling.forget(target) } }
    val instant = reducedMotion()
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }
    val swing = remember { Animatable(0f) }

    // Arriving: about 46dp above the page, settling with a little overshoot, 160ms after the one before.
    val arrivals = LocalArrivals.current
    val arriving = remember(key) { !instant && arrivals != null && key in arrivals.order && key !in arrivals.done }
    val drop = remember(key) { Animatable(if (arriving) 1f else 0f) }
    if (arriving) {
        LaunchedEffect(key) {
            arrivals!!.done += key
            delay(160L * (arrivals.order[key] ?: 0))
            drop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f))
        }
    }

    val tap: () -> Unit = {
        when {
            instant || motion == TapMotion.None -> onOpen()
            motion is TapMotion.Lift -> scope.launch {
                launch { lift.animateTo(1f, tween(motion.ms, easing = FastOutSlowInEasing)) }
                delay(motion.openAfter)
                onOpen()
                // Settles back under whatever opened.
                delay(600)
                lift.animateTo(0f, tween(200))
            }
            motion is TapMotion.Swing -> {
                onOpen()
                scope.launch {
                    swing.snapTo(0f)
                    swing.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 900
                            -10f at 150
                            7f at 340
                            -4f at 530
                            2f at 720
                        },
                    )
                }
            }
        }
    }

    val tilt = frameTilt(key)
    val pivot = (motion as? TapMotion.Swing)?.pivot ?: TransformOrigin.Center
    val height = (motion as? TapMotion.Lift)?.height ?: 0.dp
    val grow = (motion as? TapMotion.Lift)?.grow ?: 1f
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        StuckOn(
            stickers = handling?.on?.invoke(key).orEmpty(),
            inkOf = inkOf,
            modifier = Modifier
                .graphicsLayer {
                    val up = lift.value
                    transformOrigin = pivot
                    rotationZ = tilt * (1 - up) + swing.value
                    translationY = -height.toPx() * up - 46.dp.toPx() * drop.value
                    scaleX = 1f + (grow - 1f) * up
                    scaleY = scaleX
                    alpha = (1f - drop.value * 1.6f).coerceIn(0f, 1f)
                }
                .then(handling?.paper?.invoke(target) ?: Modifier),
            nameOf = nameOf,
            selectedId = handling?.selectedId,
            onTap = handling?.onTap,
            onChange = handling?.onChange,
        ) {
            Box(Modifier.combinedClickable(onClick = tap, onLongClick = { open = true }, onLongClickLabel = "Save or share")) {
                CompositionLocalProvider(LocalLift provides lift.value) { content() }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() }) }
                }
            }
        }
    }
}

/**
 * What tapping a frame does in a thread: play a song or playlist, watch a video, view photos.
 * Where it isn't provided (or for other kinds), a frame opens in the phone's own app.
 */
class FrameTaps(
    val play: (attachment: ContentPart.Attachment, ink: Color, fromYou: Boolean) -> Unit,
    val watch: ((attachment: ContentPart.Attachment, ink: Color, fromYou: Boolean) -> Unit)? = null,
)

val LocalFrameTaps = staticCompositionLocalOf<FrameTaps?> { null }

/** Attachments that carry a mark (a punched ticket), by kind. */
val LocalPunched = staticCompositionLocalOf<Set<String>> { emptySet() }

/** The menu a frame offers when held: Save to phone, Share, and Copy link for a cutting. */
@Composable
fun frameMenu(ref: String?, mime: String?, name: String?, url: String?): List<Pair<String, () -> Unit>> {
    val context = LocalContext.current
    val blobs = LocalAppContainer.current.blobStore
    val scope = rememberCoroutineScope()
    return buildList {
        if (ref != null && FrameActions.canSave) add("Save to phone" to {
            scope.launch {
                val saved = FrameActions.save(context, blobs, ref, mime, name)
                Toast.makeText(context, if (saved) "Saved to your phone." else "Couldn't save it.", Toast.LENGTH_SHORT).show()
            }
        })
        if (ref != null || url != null) add("Share" to { FrameActions.share(context, blobs, ref, mime, url) })
        if (url != null) add("Copy link" to { FrameActions.copyLink(context, url) })
    }
}

/** A letter's or note's pictures: one as a polaroid, several as a stack. */
@Composable
fun FramedPictures(
    images: List<ContentPart.Image>,
    ink: Color,
    pattern: TapePattern,
    scale: Float,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    onImage: (String) -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    if (images.isEmpty()) return
    val first = images.first()
    // Several photos fan out when tapped (a spring with a little overshoot); tapping one opens it.
    var fanned by remember { mutableStateOf(false) }
    val fan by animateFloatAsState(if (fanned) 1f else 0f, if (reducedMotion()) snap() else spring(dampingRatio = 0.6f, stiffness = 320f), label = "fan")
    OnPaper(
        first.ref, handling, inkOf, nameOf,
        onOpen = { if (images.size == 1) onImage(first.ref) else fanned = !fanned },
        menu = frameMenu(first.ref, first.mimeType, null, null),
        alignment = alignment,
        motion = if (images.size == 1) TapMotion.Photo else TapMotion.None,
    ) {
        if (images.size == 1) {
            PolaroidFrame(first, ink, pattern, scale)
        } else {
            PhotoStack(images, ink, pattern, scale * 0.9f, fan = fan, onPhoto = if (fanned) ({ i -> fanned = false; onImage(images[i].ref) }) else null)
        }
    }
}

/** One attachment in its frame. */
@Composable
fun FramedAttachment(
    attachment: ContentPart.Attachment,
    ink: Color,
    behind: Color,
    inNote: Boolean,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val blobs = LocalAppContainer.current.blobStore
    val taps = LocalFrameTaps.current
    val scope = rememberCoroutineScope()
    val instant = reducedMotion()
    val ref = attachment.ref ?: attachment.tracks.firstOrNull()?.ref
    val open = { FrameActions.open(context, blobs, attachment) }
    // A tag shows Open and Save beneath it; a film strip runs while its player opens.
    var actions by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val tag = attachment.kind == AttachmentKind.FILE && attachment.ref != null
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (alignment == Alignment.CenterStart) Alignment.Start else Alignment.CenterHorizontally) {
        OnPaper(
            attachment.id, handling, inkOf, nameOf,
            onOpen = when {
                taps != null && (attachment.kind == AttachmentKind.SONG || attachment.kind == AttachmentKind.PLAYLIST) && attachment.playable() ->
                    { { taps.play(attachment, ink, inNote) } }
                taps?.watch != null && attachment.kind == AttachmentKind.VIDEO && attachment.ref != null -> {
                    {
                        if (instant) {
                            taps.watch.invoke(attachment, ink, inNote)
                        } else {
                            scope.launch {
                                running = true
                                delay(250)
                                taps.watch.invoke(attachment, ink, inNote)
                                delay(850)
                                running = false
                            }
                        }
                        Unit
                    }
                }
                tag -> { { actions = !actions } }
                else -> open
            },
            menu = frameMenu(ref.takeIf { attachment.kind != AttachmentKind.LINK }, attachment.mimeType, attachment.name, attachment.url),
            alignment = alignment,
            motion = when {
                tag -> TapMotion.Tag
                attachment.kind == AttachmentKind.LINK -> TapMotion.Cutting
                else -> TapMotion.None
            },
        ) {
            AttachmentFrame(
                attachment, ink, behind, scale = if (inNote) noteScale(attachment.kind) else 1f,
                punched = attachment.id in LocalPunched.current, run = running,
            )
        }
        if (tag) {
            AnimatedVisibility(
                actions,
                enter = if (instant) EnterTransition.None else expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = if (instant) ExitTransition.None else shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
                Row(Modifier.padding(top = 6.dp, start = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperButton("Open", onClick = { actions = false; open() })
                    if (FrameActions.canSave) {
                        PaperButton("Save to phone", onClick = {
                            actions = false
                            scope.launch {
                                val saved = FrameActions.save(context, blobs, attachment.ref, attachment.mimeType, attachment.name)
                                Toast.makeText(context, if (saved) "Saved to your phone." else "Couldn't save it.", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
        }
    }
}
