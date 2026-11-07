package dev.aiharness.app.ui.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.core.model.ContentPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.aiharness.app.platform.PlatformBackHandler
import dev.aiharness.app.platform.VideoSurface
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.rememberScreenTurner
import dev.aiharness.app.platform.rememberVideoController

private val Dark = Color(0xFF14110F)
private val Light = Color(0xFFF3EBDF)
private val Dim = Color(0xFFB8AC9C)

/**
 * The photo viewer: a letter's or note's photos over a dark ground, swiped through, pinched or
 * double-tapped to look closer, with Save and Share.
 */
@Composable
fun PhotoViewer(images: List<ContentPart.Image>, start: Int, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
    val outside = rememberOutside()
    val scope = rememberCoroutineScope()
    val shown = fadeIn()
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(Dark).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Zoomable {
                BlobImage(
                    images[page].ref, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, maxSize = 2048, placeholder = Color.Transparent,
                    description = if (images.size > 1) "Photo ${page + 1} of ${images.size}" else "Photo",
                )
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ViewerButton(Icons.Rounded.Close, "Close") { onClose() }
            Spacer(Modifier.weight(1f))
            if (images.size > 1) Text("${pager.currentPage + 1} of ${images.size}", style = PaperType.meta, color = Dim)
            Spacer(Modifier.weight(1f))
            val image = images.getOrNull(pager.currentPage)
            if (outside.canShare) ViewerButton(Icons.Rounded.Share, "Share") { image?.let { outside.share(it.ref, it.mimeType) } }
            if (outside.canSave) {
                ViewerButton(SaveIcon, "Save to ${outside.device}") {
                    image?.let {
                        scope.launch {
                            val saved = outside.save(it.ref, it.mimeType, null)
                            outside.toast(if (saved) "Saved to your ${outside.device}." else "Couldn't save it.")
                        }
                    }
                }
            }
        }
        images.getOrNull(pager.currentPage)?.caption?.let(::polaroidCaption)?.let {
            Text(it, style = hand(18f), color = Light, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp))
        }
    }
}

/** Pinch or double-tap to look closer; panning only once zoomed, so swipes still turn the page. */
@Composable
private fun Zoomable(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    ) { content() }
}

/**
 * A video, full screen. Tapping the picture shows or hides back 10 seconds, play/pause and forward
 * 10 seconds over it. Below: mute, a scrubber with times and full screen; the title, who it's from
 * and [line] (what the persona sees of it); previous and next clip when the letter has several.
 */
@Composable
fun VideoPlayer(
    videos: List<ContentPart.Attachment>,
    start: Int,
    from: String,
    ink: Color,
    line: (ContentPart.Attachment) -> String?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val outside = rememberOutside()
    val blobs = LocalAppContainer.current.blobStore
    var index by remember { mutableStateOf(start.coerceIn(0, videos.lastIndex)) }
    val video = videos[index]
    val file = blobs.existingFile(video.ref)
    val player = rememberVideoController()
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(video.lengthMs ?: 0L) }
    val startMuted = LocalMediaSettings.current.videosMuted
    var muted by remember { mutableStateOf(startMuted) }
    var controls by remember { mutableStateOf(true) }
    var full by remember { mutableStateOf(false) }
    val turnScreen = rememberScreenTurner()

    LaunchedEffect(Unit) { onOpen() }
    LaunchedEffect(index) {
        length = video.lengthMs ?: 0L
        position = 0
        if (file != null) {
            player.volume = if (muted) 0f else 1f
            player.load(file)
            player.play()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0)
            player.duration.takeIf { it > 0 }?.let { length = it }
            delay(250)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            turnScreen(false)
        }
    }
    val close = {
        turnScreen(false)
        onClose()
    }
    PlatformBackHandler(onBack = close)

    val shown = fadeIn()
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(Dark).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ViewerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close") { close() }
            Spacer(Modifier.weight(1f))
            if (videos.size > 1) Text("${index + 1} of ${videos.size}", style = PaperType.meta, color = Dim)
            Spacer(Modifier.weight(1f))
            if (outside.canShare) ViewerButton(Icons.Rounded.Share, "Share") { outside.share(video.ref, video.mimeType) }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) },
            contentAlignment = Alignment.Center,
        ) {
            if (file != null) {
                VideoSurface(player, Modifier.fillMaxSize())
            } else {
                // No file to play (or not yet): its middle frame stands in.
                (video.frameRefs.getOrNull(1) ?: video.frameRefs.firstOrNull())?.let {
                    BlobImage(it, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholder = Color.Transparent, description = "A frame from the video")
                }
            }
            if (controls) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    ViewerButton(Icons.Rounded.Replay10, "Back 10 seconds") { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }
                    Box(
                        Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).clickable(onClickLabel = if (playing) "Pause" else "Play") {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Light, modifier = Modifier.size(36.dp)) }
                    ViewerButton(Icons.Rounded.Forward10, "Forward 10 seconds") { player.seekTo(player.currentPosition + 10_000) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ViewerButton(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Sound on" else "Mute") {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                }
                Box(Modifier.weight(1f)) { InkScrubber(position, length, ink, onSeek = { player.seekTo(it) }, rest = Color(0xFF4A423B), times = Dim) }
                ViewerButton(if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (full) "Leave full screen" else "Full screen") {
                    full = !full
                    turnScreen(full)
                }
            }
            if (!full) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(video.title ?: video.name ?: "A video", style = PaperType.noteBody, color = Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("From $from", style = PaperType.meta, color = Dim)
                        line(video)?.let { Text(it, style = PaperType.postscript, color = Dim) }
                    }
                    if (videos.size > 1) {
                        ViewerButton(Icons.Rounded.ChevronLeft, "Previous clip", enabled = index > 0) { index-- }
                        ViewerButton(Icons.Rounded.ChevronRight, "Next clip", enabled = index < videos.lastIndex) { index++ }
                    }
                }
            }
        }
    }
}

/** The viewers fade in (220ms), at once with "Remove animations". */
@Composable
private fun fadeIn(): Animatable<Float, AnimationVector1D> {
    val instant = reducedMotion()
    val shown = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(220)) }
    return shown
}

@Composable
private fun ViewerButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (enabled) Light else Dim.copy(alpha = 0.4f)) }
}

private val SaveIcon: ImageVector get() = Icons.Rounded.Download
