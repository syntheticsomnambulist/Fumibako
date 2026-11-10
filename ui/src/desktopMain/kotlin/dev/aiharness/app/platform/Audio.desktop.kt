package dev.aiharness.app.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** What a sound being played says back (on any thread). */
interface AudioEvents {
    /** Loaded: its length (0 when unknown). */
    fun ready(durationMs: Long) {}

    fun ended() {}

    /** It can't play here (a format the player doesn't know, a missing file, no sound device). */
    fun failed(why: String) {}
}

/** One sound at a time: songs from letters (the desktop's Playback) and previews from the web. */
interface AudioEngine {
    /** Loads [uri] (file: or https:) in place of what was loaded, and plays it once ready. */
    fun load(uri: String, events: AudioEvents)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(ms: Long)

    val positionMs: Long

    val playing: Boolean
}

/**
 * Sounds through JavaFX's media player: MP3, AAC (M4A), WAV and AIFF, from files or the web. A
 * format it doesn't know fails, and the caller opens the file in the computer's own player.
 */
class FxAudio : AudioEngine {
    @Volatile
    private var player: MediaPlayer? = null

    override fun load(uri: String, events: AudioEvents) {
        stop()
        val ran = Fx.later {
            val made = runCatching { MediaPlayer(Media(uri)) }.getOrElse {
                events.failed(it.message ?: "can't play this")
                return@later
            }
            made.setOnReady {
                events.ready(made.media.duration.toMillis().takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0L)
                made.play()
            }
            made.setOnEndOfMedia {
                made.pause()
                events.ended()
            }
            made.setOnError { events.failed(made.error?.message ?: "can't play this") }
            player = made
        }
        if (!ran) events.failed("no media player on this computer")
    }

    override fun play() {
        player?.let { p -> Fx.later { p.play() } }
    }

    override fun pause() {
        player?.let { p -> Fx.later { p.pause() } }
    }

    override fun stop() {
        val p = player ?: return
        player = null
        Fx.later { runCatching { p.stop(); p.dispose() } }
    }

    override fun seekTo(ms: Long) {
        player?.let { p -> Fx.later { p.seek(Duration.millis(ms.coerceAtLeast(0).toDouble())) } }
    }

    override val positionMs: Long get() = player?.currentTime?.toMillis()?.takeIf { it.isFinite() }?.toLong() ?: 0L

    override val playing: Boolean get() = player?.status == MediaPlayer.Status.PLAYING
}

/** JavaFX's toolkit, started the first time a sound plays (there are no JavaFX windows). */
object Fx {
    @Volatile
    private var started: Boolean? = null

    /** Runs [block] on JavaFX's thread; false if JavaFX can't start here. */
    fun later(block: () -> Unit): Boolean {
        if (!start()) return false
        Platform.runLater(block)
        return true
    }

    @Synchronized
    private fun start(): Boolean {
        started?.let { return it }
        val ok = runCatching {
            val up = CountDownLatch(1)
            try {
                Platform.startup { up.countDown() }
            } catch (already: IllegalStateException) {
                up.countDown()
            }
            up.await(15, TimeUnit.SECONDS).also { Platform.setImplicitExit(false) }
        }.getOrDefault(false)
        started = ok
        return ok
    }
}

actual fun newSongPreview(): SongPreview = FxSongPreview(FxAudio())

/** A preview from the web, in the app; one that can't play here opens in the browser instead. */
class FxSongPreview(private val audio: AudioEngine) : SongPreview {
    override var playing by mutableStateOf<String?>(null)
        private set

    override fun toggle(url: String?) {
        val was = playing
        stop()
        if (url == null || url == was) return
        playing = url
        audio.load(
            url,
            object : AudioEvents {
                override fun ended() {
                    if (playing == url) stop()
                }

                override fun failed(why: String) {
                    if (playing != url) return
                    stop()
                    runCatching { Desktop.getDesktop().browse(URI(url)) }
                }
            },
        )
    }

    override fun stop() {
        audio.stop()
        playing = null
    }
}
