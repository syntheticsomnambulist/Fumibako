package dev.aiharness.desktop

import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.media.NowPlaying
import dev.aiharness.app.media.Playback
import dev.aiharness.app.media.Repeat
import dev.aiharness.app.platform.AudioEngine
import dev.aiharness.app.platform.AudioEvents
import dev.aiharness.app.platform.FxAudio
import dev.aiharness.app.platform.Toasts
import dev.aiharness.core.media.MusicLinks
import dev.aiharness.core.media.SongSource
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Track
import dev.aiharness.core.storage.MarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Songs and playlists from letters, played in the app through [audio] (JavaFX's player), with the
 * phone's controls: next and back, shuffle and repeat. A song's ticket is punched the first time
 * it plays. One the player can't play (FLAC, say) opens in the computer's own player instead.
 */
class DesktopPlayback(
    private val blobs: FileBlobStore,
    private val marks: MarkRepository,
    private val audio: AudioEngine = FxAudio(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val openOutside: (file: File?, url: String?) -> Unit = ::openInTheComputersPlayer,
) : Playback {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    override val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val lock = Any()

    // Guarded by [lock]: each track's address (null when it can't play here), the order tracks
    // play in (shuffled or not), and which load the player's callbacks belong to.
    private var sources: List<String?> = emptyList()
    private var order: List<Int> = emptyList()
    private var loading: Any? = null
    private var ticker: Job? = null

    override fun play(attachment: ContentPart.Attachment, conversationId: String?, ink: Int, fromYou: Boolean, startAt: Int) {
        val current = _state.value
        if (current?.attachment?.id == attachment.id && startAt == current.index) return resume()
        val now = NowPlaying(attachment, conversationId, ink, fromYou, index = startAt)
        val found = now.tracks.map { source(attachment, it) }
        if (found.all { it == null }) return
        synchronized(lock) {
            sources = found
            order = now.tracks.indices.toList()
            _state.value = now
        }
        val first = startAt.coerceIn(0, now.tracks.lastIndex).let { if (found[it] != null) it else after(it, wrap = true) ?: it }
        load(first)
        if (attachment.kind == AttachmentKind.SONG) {
            scope.launch { runCatching { marks.add(AttachmentMark(attachment.id, MarkKind.PUNCHED, conversationId = conversationId, at = System.currentTimeMillis())) } }
        }
    }

    /** A file here, or a persona's link to an audio file, streamed. */
    private fun source(attachment: ContentPart.Attachment, track: Track): String? =
        blobs.existingFile(track.ref)?.toURI()?.toString()
            ?: attachment.url?.takeIf { attachment.kind == AttachmentKind.SONG && MusicLinks.sourceOf(it) == SongSource.AUDIO }

    private fun load(index: Int) {
        val (uri, key) = synchronized(lock) {
            val uri = sources.getOrNull(index) ?: return
            val key = Any()
            loading = key
            _state.update { it?.copy(index = index, positionMs = 0, durationMs = 0, playing = true) }
            uri to key
        }
        audio.load(
            uri,
            object : AudioEvents {
                override fun ready(durationMs: Long) {
                    if (current(key)) {
                        _state.update { it?.copy(durationMs = durationMs, playing = true) }
                        tick()
                    }
                }

                override fun ended() {
                    if (current(key)) onEnded()
                }

                override fun failed(why: String) {
                    if (!current(key)) return
                    val now = _state.value
                    stop()
                    val track = now?.track
                    openOutside(blobs.existingFile(track?.ref), now?.attachment?.url)
                    Toasts.show("Playing in your computer's player.")
                }
            },
        )
    }

    private fun current(key: Any) = synchronized(lock) { loading === key }

    /**
     * Where the order goes after [index]: the next track that can play; with [wrap], around to the
     * start again (and to [index] itself, when it's the only one).
     */
    private fun after(index: Int, wrap: Boolean): Int? = synchronized(lock) {
        val at = order.indexOf(index)
        val ahead = order.drop(at + 1) + if (wrap) order.take(at + 1) else emptyList()
        ahead.firstOrNull { sources.getOrNull(it) != null }
    }

    private fun before(index: Int): Int? = synchronized(lock) {
        val at = order.indexOf(index)
        order.take(at.coerceAtLeast(0)).lastOrNull { sources.getOrNull(it) != null }
    }

    private fun onEnded() {
        val now = _state.value ?: return
        when {
            now.repeat == Repeat.ONE -> {
                audio.seekTo(0)
                audio.play()
            }
            else -> after(now.index, wrap = now.repeat == Repeat.ALL)?.let(::load) ?: run {
                // The end: back to the start of this track, ready to play again.
                audio.seekTo(0)
                _state.update { it?.copy(playing = false, positionMs = 0) }
            }
        }
    }

    private fun resume() {
        if (_state.value == null) return
        audio.play()
        _state.update { it?.copy(playing = true) }
        tick()
    }

    /** Keeps the position moving while playing. */
    private fun tick() {
        synchronized(lock) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                while (isActive && _state.value?.playing == true) {
                    _state.update { it?.copy(positionMs = audio.positionMs) }
                    delay(250)
                }
            }
        }
    }

    override fun toggle() {
        if (_state.value?.playing == true) pause() else resume()
    }

    override fun next() {
        val now = _state.value ?: return
        after(now.index, wrap = now.repeat == Repeat.ALL)?.let(::load)
    }

    /** Back a song, or to the start of this one when more than 3 seconds in. */
    override fun previous() {
        val now = _state.value ?: return
        val back = if (audio.positionMs > 3_000) null else before(now.index)
        if (back != null) load(back) else seekTo(0)
    }

    override fun seekTo(ms: Long) {
        val length = _state.value?.length?.takeIf { it > 0 } ?: Long.MAX_VALUE
        val to = ms.coerceIn(0, length)
        audio.seekTo(to)
        _state.update { it?.copy(positionMs = to) }
    }

    override fun skip(deltaMs: Long) = seekTo(audio.positionMs + deltaMs)

    override fun playTrack(index: Int) {
        if (synchronized(lock) { sources.getOrNull(index) } != null) load(index)
    }

    override fun shuffle() {
        val now = _state.value ?: return
        val on = !now.shuffle
        synchronized(lock) {
            // Shuffled, the one playing stays first and the rest follow in a new order.
            order = if (on) listOf(now.index) + now.tracks.indices.filter { it != now.index }.shuffled() else now.tracks.indices.toList()
        }
        _state.update { it?.copy(shuffle = on) }
    }

    override fun cycleRepeat() {
        _state.update {
            it?.copy(
                repeat = when (it.repeat) {
                    Repeat.OFF -> if (it.isPlaylist) Repeat.ALL else Repeat.ONE
                    Repeat.ALL -> Repeat.ONE
                    Repeat.ONE -> Repeat.OFF
                },
            )
        }
    }

    /** Pauses the music (a video is about to play). */
    override fun pause() {
        audio.pause()
        _state.update { it?.copy(playing = false) }
    }

    override fun stop() {
        synchronized(lock) {
            loading = null
            ticker?.cancel()
        }
        audio.stop()
        _state.value = null
    }

    /** Shows [state] without playing anything, for the screenshot tour. */
    override fun preview(state: NowPlaying?) {
        _state.value = state
    }
}

/** A song the in-app player can't play: in the computer's own player (or the browser, for a link). */
private fun openInTheComputersPlayer(file: File?, url: String?) {
    runCatching {
        when {
            file != null -> Desktop.getDesktop().open(file)
            url != null -> Desktop.getDesktop().browse(URI(url))
        }
    }
}
