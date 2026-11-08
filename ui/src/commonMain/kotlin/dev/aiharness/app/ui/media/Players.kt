package dev.aiharness.app.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.staticCompositionLocalOf
import dev.aiharness.app.platform.scrollingPage
import dev.aiharness.core.media.MusicLinks
import dev.aiharness.core.media.SongSource
import dev.aiharness.core.model.SongsOpenIn
import dev.aiharness.app.ui.paper.PaperButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.MediaSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aiharness.app.media.NowPlaying
import dev.aiharness.app.media.Repeat
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.clock
import dev.aiharness.app.platform.EmbedView
import dev.aiharness.app.platform.PlatformBackHandler
import dev.aiharness.app.platform.rememberOutside

/** What the players can ask of the playback (the app's [dev.aiharness.app.media.Playback]). */
interface PlayerControls {
    fun toggle()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun skip(deltaMs: Long)
    fun playTrack(index: Int)
    fun shuffle()
    fun cycleRepeat()
    fun stop()
}

/**
 * Docked above the composer while something plays: a thin progress line in the sender's ink along
 * its top edge, a small cassette or ticket, title and artist, play/pause, next (playlists) and
 * stop. Tapping it opens the full player.
 */
@Composable
fun MiniPlayer(now: NowPlaying, controls: PlayerControls, onOpen: () -> Unit) {
    val colors = paper
    val ink = Color(now.ink)
    val progress = if (now.length > 0) (now.positionMs.toFloat() / now.length).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.kami)
            .clickable(onClickLabel = "Open the player", role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = "Now playing: " + listOfNotNull(now.title, now.artist).joinToString(" by ") },
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.nijimi2)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(ink))
        }
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (now.isPlaylist) LittleCassette(ink) else LittleTicket(ink)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(now.title, style = PaperType.noteBody, color = colors.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                now.artist?.let { Text(it, style = PaperType.meta, color = colors.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            ControlButton(if (now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (now.playing) "Pause" else "Play", colors.sumi) { controls.toggle() }
            if (now.isPlaylist) ControlButton(Icons.Rounded.SkipNext, "Next song", colors.sumi) { controls.next() }
            ControlButton(Icons.Rounded.Close, "Stop", colors.kasure2) { controls.stop() }
        }
    }
}

/**
 * The full player, a sheet over most of the screen (a tap above it, the chevron or back shrinks it
 * to the mini player): for a playlist, the cassette with its reels turning, the scrubber, shuffle,
 * previous, play/pause, next and repeat, and the track list on the cassette's J-card; for a song,
 * the punched ticket, back and forward 10 seconds, repeat, and [knows], a line on what the persona
 * knows of it.
 */
@Composable
fun FullPlayer(now: NowPlaying, controls: PlayerControls, knows: String?, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val colors = paper
    val ink = Color(now.ink)
    val instant = reducedMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Shrink the player", onClick = onClose),
        )
        AnimatedVisibility(
            shown,
            Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(if (instant) 0 else 340)) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .shadow(12.dp, shape)
                    .background(colors.hyoshi, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .scrollingPage()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(Icons.Rounded.KeyboardArrowDown, "Shrink the player", colors.kasure2, onClose)
                    Spacer(Modifier.weight(1f))
                    Text(if (now.isPlaylist) "PLAYLIST" else "SONG", style = PaperType.meta, color = colors.kasure2)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
                if (now.isPlaylist) {
                    Cassette(now.attachment, ink, scale = 1.2f, turn = now.playing, wound = now.throughPlaylist)
                } else {
                    TicketStub(now.attachment, ink, colors.hyoshi, scale = 1.1f, punched = true)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(now.title, style = PaperType.letterName, color = colors.sumi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    now.artist?.let { Text(it, style = PaperType.noteBody, color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (now.isPlaylist) {
                        Text(
                            "Track ${now.index + 1} of ${now.tracks.size} · ${now.attachment.title ?: "a mix"}",
                            style = PaperType.meta,
                            color = colors.kasure2,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Scrubber(now, ink, controls)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (now.isPlaylist) {
                        ControlButton(Icons.Rounded.Shuffle, if (now.shuffle) "Shuffle is on" else "Shuffle", if (now.shuffle) ink else colors.kasure2) { controls.shuffle() }
                        ControlButton(Icons.Rounded.SkipPrevious, "Previous song", colors.sumi) { controls.previous() }
                    } else {
                        // A blank opposite repeat keeps play in the middle.
                        Spacer(Modifier.size(48.dp))
                        ControlButton(Icons.Rounded.Replay10, "Back 10 seconds", colors.sumi) { controls.skip(-10_000) }
                    }
                    PlayButton(now.playing, ink) { controls.toggle() }
                    if (now.isPlaylist) {
                        ControlButton(Icons.Rounded.SkipNext, "Next song", colors.sumi) { controls.next() }
                    } else {
                        ControlButton(Icons.Rounded.Forward10, "Forward 10 seconds", colors.sumi) { controls.skip(10_000) }
                    }
                    RepeatButton(now.repeat, ink) { controls.cycleRepeat() }
                }
                if (now.isPlaylist) JCard(now, ink) { controls.playTrack(it) }
                if (!now.isPlaylist) WholeSongButton(now.attachment, whole = true)
                knows?.let { Text(it, style = PaperType.postscript, color = colors.usuzumi1, modifier = Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Scrubber(now: NowPlaying, ink: Color, controls: PlayerControls) {
    InkScrubber(now.positionMs, now.length, ink, onSeek = controls::seekTo)
}

/**
 * A scrubber for a song or a video: a thin line in the sender's [ink] with a small round thumb,
 * the time gone and the time left beneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkScrubber(positionMs: Long, lengthMs: Long, ink: Color, onSeek: (Long) -> Unit, rest: Color = paper.nijimi2, times: Color = paper.kasure2) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val length = lengthMs.coerceAtLeast(1)
    val at = dragging ?: (positionMs.toFloat() / length).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = at,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onSeek((it * length).toLong()) }; dragging = null },
            thumb = { Box(Modifier.size(14.dp).shadow(1.dp, CircleShape).background(ink, CircleShape)) },
            track = {
                Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                    val y = size.height / 2
                    val stroke = 3.dp.toPx()
                    drawLine(rest, Offset(0f, y), Offset(size.width, y), stroke, cap = StrokeCap.Round)
                    drawLine(ink, Offset(0f, y), Offset(size.width * at, y), stroke, cap = StrokeCap.Round)
                }
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(clock((at * length).toLong()), style = PaperType.meta, color = times)
            Spacer(Modifier.weight(1f))
            Text("−" + clock(((1 - at) * length).toLong()), style = PaperType.meta, color = times)
        }
    }
}

/** The cassette's J-card: the songs in the sender's handwriting, the one playing marked. */
@Composable
private fun JCard(now: NowPlaying, ink: Color, onTrack: (Int) -> Unit) {
    val card = Color(0xFFF6ECD9)
    Column(
        Modifier
            .fillMaxWidth()
            .lamplit()
            .background(card, RoundedCornerShape(4.dp))
            .border(1.dp, lerp(card, Color(0xFF5D4C3C), 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text((now.attachment.title ?: "a mix").uppercase(), style = ui(10f, bold = true, spacing = 0.16f), color = ink)
        now.attachment.subtitle?.let { Text(it, style = hand(13f), color = ink) }
        Spacer(Modifier.height(6.dp))
        now.tracks.forEachIndexed { i, track ->
            val current = i == now.index
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).clickable(onClickLabel = "Play this song", role = Role.Button) { onTrack(i) }
                    .background(if (current) ink.copy(alpha = 0.10f) else Color.Transparent).padding(vertical = 3.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (current) "▸" else "${i + 1}.", style = hand(15f, semibold = current), color = ink, modifier = Modifier.width(24.dp))
                Text(
                    listOfNotNull(track.title ?: track.name?.substringBeforeLast('.') ?: "a song", track.artist).joinToString(" · "),
                    style = hand(15f, semibold = current),
                    color = Color(0xFF3E342B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                track.lengthMs?.let { Text(clock(it), style = ui(10f), color = Color(0xFF8A7A6A)) }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
}

@Composable
private fun PlayButton(playing: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(ink).clickable(role = Role.Button, onClickLabel = if (playing) "Pause" else "Play", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(34.dp)) }
}

@Composable
private fun RepeatButton(repeat: Repeat, ink: Color, onClick: () -> Unit) {
    val colors = paper
    when (repeat) {
        Repeat.OFF -> ControlButton(Icons.Rounded.Repeat, "Repeat", colors.kasure2, onClick)
        Repeat.ALL -> ControlButton(Icons.Rounded.Repeat, "Repeating all", ink, onClick)
        Repeat.ONE -> ControlButton(Icons.Rounded.RepeatOne, "Repeating this song", ink, onClick)
    }
}

/** A small cassette for the mini player. */
@Composable
private fun LittleCassette(ink: Color) {
    Canvas(Modifier.size(38.dp, 25.dp).lamplit()) {
        val k = size.width / 38f
        drawRoundRect(Color(0xFF3B3530), Offset.Zero, size, CornerRadius(3 * k))
        drawRoundRect(Color(0xFFF6ECD9), Offset(3 * k, 3 * k), Size(32 * k, 14 * k), CornerRadius(1.5f * k))
        drawLine(ink, Offset(7 * k, 7 * k), Offset(24 * k, 7 * k), 1.6f * k)
        drawRoundRect(Color(0xFF2B2622), Offset(9 * k, 10.5f * k), Size(20 * k, 5 * k), CornerRadius(2.5f * k))
        drawCircle(Color(0xFFF3E4CC), 1.7f * k, Offset(12 * k, 13 * k))
        drawCircle(Color(0xFFF3E4CC), 1.7f * k, Offset(26 * k, 13 * k))
    }
}

/** A small ticket for the mini player. */
@Composable
private fun LittleTicket(ink: Color) {
    val behind = paper.kami
    Canvas(Modifier.size(38.dp, 25.dp).lamplit()) {
        val k = size.width / 38f
        val ticket = lerp(Color(0xFFF7F0E4), ink, 0.18f)
        drawRoundRect(ticket, Offset.Zero, size, CornerRadius(1.5f * k))
        drawLine(ink, Offset(4 * k, 8 * k), Offset(20 * k, 8 * k), 1.6f * k)
        drawLine(lerp(ticket, ink, 0.5f), Offset(4 * k, 13 * k), Offset(16 * k, 13 * k), 1.2f * k)
        drawLine(lerp(ticket, ink, 0.55f), Offset(28 * k, 2 * k), Offset(28 * k, 23 * k), 1f * k)
        drawCircle(behind, 2.4f * k, Offset(28 * k, 0f))
        drawCircle(behind, 2.4f * k, Offset(28 * k, size.height))
        drawCircle(behind, 2.2f * k, Offset(33 * k, 6 * k))
    }
}

/** Whether a song or playlist plays in the app's player: a file here, or a persona's link to an audio file. */
fun ContentPart.Attachment.playable(): Boolean =
    (ref ?: tracks.firstOrNull()?.ref) != null || (kind == AttachmentKind.SONG && MusicLinks.sourceOf(url) == SongSource.AUDIO)

/**
 * Opens [song] in your music app, by the "Open songs in" setting: the whole song, for a ticket
 * carrying only its preview ([whole]), or the song in the app for one already playing here. Not
 * shown for a song with no link, or one that's only an audio file.
 */
@Composable
fun WholeSongButton(song: ContentPart.Attachment, whole: Boolean) {
    val url = song.url ?: return
    val source = MusicLinks.sourceOf(url)
    if (source == SongSource.AUDIO) return
    val outside = rememberOutside()
    val music = dev.aiharness.app.ui.LocalAppContainer.current.harness.music
    val app = LocalMediaSettings.current.songsOpenIn
    val scope = rememberCoroutineScope()
    val where = if (app == SongsOpenIn.AS_LINKED) source?.label ?: "your browser" else app.label
    val label = if (whole && song.previewOnly) "Listen to the whole song in $where" else "Open in $where"
    PaperButton(label) {
        scope.launch {
            // Music services open in their own apps when they're installed, so not in the app's browser.
            music.whereToHear(song, app)?.let { outside.openLink(it, inApp = false, toolbar = 0, night = false) }
        }
    }
}

/** Android's "Remove animations", and the Media settings, as the frames see them. */
val LocalMediaSettings = staticCompositionLocalOf { MediaSettings() }

/**
 * A song a persona shared from a service with an official embeddable player (YouTube, SoundCloud,
 * Bandcamp), playing in a sheet over the thread: the ticket, the service's own player, and who it's
 * from. Embedded players only play while they're showing, so closing the sheet stops it.
 */
@Composable
fun EmbedPlayer(song: ContentPart.Attachment, embed: String, ink: Color, from: String, onClose: () -> Unit) {
    PlatformBackHandler(onBack = onClose)
    val colors = paper
    val instant = reducedMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val source = MusicLinks.sourceOf(song.url)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close the player", onClick = onClose),
        )
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(if (instant) 0 else 340)) { it }) {
            Column(
                Modifier.fillMaxWidth().shadow(12.dp, shape).background(colors.hyoshi, shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(Icons.Rounded.KeyboardArrowDown, "Close the player", colors.kasure2, onClose)
                    Spacer(Modifier.weight(1f))
                    Text("SONG", style = PaperType.meta, color = colors.kasure2)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
                TicketStub(song, ink, colors.hyoshi, scale = 1f, punched = true)
                val height = when (source) {
                    SongSource.YOUTUBE -> null
                    SongSource.SOUNDCLOUD -> 166.dp
                    else -> 150.dp
                }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.Black)
                        .then(if (height == null) Modifier.aspectRatio(16f / 9f) else Modifier.height(height)),
                ) { EmbedView(embed, Modifier.fillMaxSize()) }
                WholeSongButton(song, whole = false)
                Text(
                    "From $from, playing from ${source?.label ?: "the web"} while this is open.",
                    style = PaperType.postscript,
                    color = colors.usuzumi1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
