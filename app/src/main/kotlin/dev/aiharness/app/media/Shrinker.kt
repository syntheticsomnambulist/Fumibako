package dev.aiharness.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Makes the smaller copies of videos and songs (docs/plan-v0.3.5.md, after step 8). Best effort:
 * null when the phone can't make one.
 *
 * - What models are sent: video at 360 px high and about 400 kbit/s with compact sound, a song as
 *   64 kbit/s AAC. Up to four minutes of video then fit in one request to Gemini, with nothing
 *   stored at Google; a longer video's copy is its first four minutes.
 * - What's kept in place of the original, to save space: a video at 720p and about 2.5 Mbit/s
 *   (a phone's 4K original takes over ten times as much), a lossless song as 256 kbit/s AAC.
 */
class Shrinker(private val context: Context) {
    /** How a copy is made: the video's short side (null for a song), and the bitrates. */
    private class Spec(val shortSide: Int?, val videoBitrate: Int, val audioBitrate: Int, val timeoutMs: Long)

    /**
     * A copy of the video at [source] for models, written to [out], of its first [endMs] when set;
     * null if it couldn't be made. [onProgress] hears how far along it is (0 to 1).
     */
    suspend fun video(source: File, out: File, endMs: Long? = null, onProgress: (Float) -> Unit = {}): File? =
        run(source, out, Spec(VIDEO_SHORT_SIDE, VIDEO_BITRATE, VIDEO_AUDIO_BITRATE, TIMEOUT_MS), endMs, onProgress)

    /** A compact copy of the song at [source] for models. */
    suspend fun song(source: File, out: File, onProgress: (Float) -> Unit = {}): File? =
        run(source, out, Spec(null, 0, SONG_BITRATE, TIMEOUT_MS), endMs = null, onProgress)

    /**
     * The copy a video is kept as: its short side at most [KEPT_SHORT_SIDE] ([shortSide] is the
     * video's own, so a smaller one isn't made bigger), about 2.5 Mbit/s. A long video takes a
     * while: it has its own length plus two minutes before giving up (the original is kept then).
     */
    suspend fun keptVideo(source: File, out: File, shortSide: Int?, lengthMs: Long?, onProgress: (Float) -> Unit = {}): File? {
        val side = minOf(KEPT_SHORT_SIDE, shortSide ?: KEPT_SHORT_SIDE)
        return run(source, out, Spec(side, KEPT_VIDEO_BITRATE, KEPT_VIDEO_AUDIO_BITRATE, TIMEOUT_MS + (lengthMs ?: 0L)), endMs = null, onProgress)
    }

    /** The copy a lossless song is kept as (256 kbit/s AAC). */
    suspend fun keptSong(source: File, out: File, lengthMs: Long?, onProgress: (Float) -> Unit = {}): File? =
        run(source, out, Spec(null, 0, KEPT_SONG_BITRATE, TIMEOUT_MS + (lengthMs ?: 0L)), endMs = null, onProgress)

    private suspend fun run(source: File, out: File, spec: Spec, endMs: Long?, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(spec.timeoutMs) {
            var started: Transformer? = null
            // The transformer says how far it's got when asked (on the thread it was made on, this one).
            val watch = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(PROGRESS_EVERY_MS)
                    val t = started ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                }
            }
            try {
                make(source, out, spec, endMs) { started = it }
            } finally {
                watch.cancel()
            }
        }
    }

    private suspend fun make(source: File, out: File, spec: Spec, endMs: Long?, onStart: (Transformer) -> Unit): File? =
            suspendCancellableCoroutine { cont ->
                val video = spec.shortSide != null
                val encoders = DefaultEncoderFactory.Builder(context)
                    .apply { if (video) setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(spec.videoBitrate).build()) }
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(spec.audioBitrate).build())
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoders)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(out.takeIf { it.exists() && it.length() > 0 })
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                out.delete()
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                    .build()
                val media = MediaItem.Builder().setUri(Uri.fromFile(source))
                    .apply { endMs?.let { setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(it).build()) } }
                    .build()
                val item = EditedMediaItem.Builder(media)
                    .apply {
                        if (spec.shortSide != null) setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(spec.shortSide))))
                        else setRemoveVideo(true)
                    }
                    .build()
                runCatching { transformer.start(item, out.path) }
                    .onSuccess { onStart(transformer) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation {
                    transformer.cancel()
                    out.delete()
                }
            }

    companion object {
        private const val VIDEO_SHORT_SIDE = 360
        private const val VIDEO_BITRATE = 400_000
        private const val VIDEO_AUDIO_BITRATE = 48_000
        private const val SONG_BITRATE = 64_000
        private const val TIMEOUT_MS = 120_000L
        private const val PROGRESS_EVERY_MS = 250L

        const val KEPT_SHORT_SIDE = 720
        const val KEPT_VIDEO_BITRATE = 2_500_000
        private const val KEPT_VIDEO_AUDIO_BITRATE = 128_000
        private const val KEPT_SONG_BITRATE = 256_000

        /** A kept copy that wouldn't save at least a fifth of the original isn't worth its loss: the original stays. */
        const val KEPT_MOST = 0.8

        /**
         * The most of a video one request carries at the copy's 450 kbit/s or so (under Gemini's
         * 14 MB): a longer video's copy is its first four minutes.
         */
        const val FIRST_PART_MS = 4 * 60_000L

        /** Songs smaller than this, in a type Gemini knows, go as they are. */
        const val SMALL_SONG = 6L * 1024 * 1024
    }
}
