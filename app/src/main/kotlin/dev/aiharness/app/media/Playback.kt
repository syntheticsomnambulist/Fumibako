package dev.aiharness.app.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.ui.media.FrameActions
import dev.aiharness.app.ui.media.PlayerControls
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Track
import dev.aiharness.core.storage.MarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How a playlist or song repeats. */
enum class Repeat { OFF, ALL, ONE }

/**
 * What's playing: a song (ticket) or a playlist (cassette) from a letter, which track, and where.
 * [ink] is the sender's ink (day colours, as ARGB), for the progress line and the label.
 */
data class NowPlaying(
    val attachment: ContentPart.Attachment,
    val conversationId: String?,
    val ink: Int,
    val fromYou: Boolean,
    val index: Int = 0,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.OFF,
) {
    val tracks: List<Track>
        get() = if (attachment.kind == AttachmentKind.PLAYLIST) attachment.tracks
        else listOf(Track(attachment.ref, attachment.mimeType, attachment.title, attachment.artist, attachment.lengthMs, attachment.name))

    val track: Track? get() = tracks.getOrNull(index)
    val isPlaylist: Boolean get() = attachment.kind == AttachmentKind.PLAYLIST
    val title: String get() = track?.title ?: track?.name?.substringBeforeLast('.') ?: attachment.title ?: "A song"
    val artist: String? get() = track?.artist
    val length: Long get() = durationMs.takeIf { it > 0 } ?: track?.lengthMs ?: 0

    /** How far through the whole playlist: what the cassette's tape packs show. */
    val throughPlaylist: Float
        get() {
            val lengths = tracks.map { it.lengthMs ?: 0L }
            val total = lengths.sum().takeIf { it > 0 } ?: return 0f
            return ((lengths.take(index).sum() + positionMs).toFloat() / total).coerceIn(0f, 1f)
        }
}

/**
 * Plays songs and playlists through [PlaybackService], connecting to it when something first plays.
 * A song's ticket is punched the first time it plays (a mark, kept apart from the letter).
 */
class Playback(
    private val context: Context,
    private val blobs: FileBlobStore,
    private val marks: MarkRepository,
    private val scope: CoroutineScope,
) : PlayerControls {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    /** Plays [attachment] from track [startAt]. Songs without a file of their own can't play here. */
    fun play(attachment: ContentPart.Attachment, conversationId: String?, ink: Int, fromYou: Boolean, startAt: Int = 0) {
        val current = _state.value
        if (current?.attachment?.id == attachment.id && startAt == current.index) {
            controller?.play()
            return
        }
        val playing = NowPlaying(attachment, conversationId, ink, fromYou, index = startAt)
        val items = playing.tracks.mapIndexedNotNull { i, track -> item(attachment, i, track) }
        if (items.isEmpty()) return
        _state.value = playing.copy(playing = true)
        scope.launch {
            val c = runCatching { connect() }.getOrNull() ?: return@launch _state.update { null }
            c.setMediaItems(items, startAt.coerceIn(0, items.lastIndex), 0L)
            c.shuffleModeEnabled = false
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.prepare()
            c.play()
            if (attachment.kind == AttachmentKind.SONG) {
                marks.add(AttachmentMark(attachment.id, MarkKind.PUNCHED, conversationId = conversationId, at = System.currentTimeMillis()))
            }
        }
    }

    override fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun next() {
        controller?.seekToNextMediaItem()
    }

    /** Back a song, or to the start of this one when more than 3 seconds in. */
    override fun previous() {
        controller?.seekToPrevious()
    }

    override fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    override fun skip(deltaMs: Long) {
        controller?.let { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0)) }
    }

    override fun playTrack(index: Int) {
        controller?.let { it.seekTo(index, 0L); it.play() }
    }

    override fun shuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    override fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> if (_state.value?.isPlaylist == true) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** Pauses the music (a video is about to play). */
    fun pause() {
        controller?.pause()
    }

    override fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        _state.value = null
    }

    /** Shows [state] without playing anything, for the screenshot tour. */
    fun preview(state: NowPlaying?) {
        _state.value = state
    }

    private fun item(attachment: ContentPart.Attachment, index: Int, track: Track): MediaItem? {
        val file = FrameActions.fileOf(blobs, track.ref) ?: return null
        val cover = attachment.pictureRef?.let { FrameActions.fileOf(blobs, it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title ?: track.name?.substringBeforeLast('.'))
            .setArtist(track.artist)
            .setAlbumTitle(if (attachment.kind == AttachmentKind.PLAYLIST) attachment.title else attachment.album)
            .apply { cover?.let { setArtworkUri(Uri.fromFile(it)) } }
            .build()
        return MediaItem.Builder().setMediaId("${attachment.id}#$index").setUri(Uri.fromFile(file)).setMediaMetadata(metadata).build()
    }

    private suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val built = suspendCancellableCoroutine { cont ->
            future.addListener({ runCatching { future.get() }.onSuccess(cont::resume).onFailure(cont::resumeWithException) }, ContextCompat.getMainExecutor(context))
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
        built.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh(player)
            },
        )
        controller = built
        return built
    }

    private fun refresh(player: Player) {
        val now = _state.value ?: return
        if (player.mediaItemCount == 0 && player.playbackState == Player.STATE_IDLE) return
        _state.value = now.copy(
            index = player.currentMediaItemIndex,
            playing = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> Repeat.ALL
                Player.REPEAT_MODE_ONE -> Repeat.ONE
                else -> Repeat.OFF
            },
        )
        if (player.playbackState == Player.STATE_ENDED) _state.update { it?.copy(playing = false) }
        if (player.isPlaying) tick(player) else ticker?.cancel()
    }

    /** Keeps the position moving while playing. */
    private fun tick(player: Player) {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && player.isPlaying) {
                _state.update { it?.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                delay(250)
            }
        }
    }
}
