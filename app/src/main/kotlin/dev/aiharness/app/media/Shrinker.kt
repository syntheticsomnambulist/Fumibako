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
 * Makes the smaller copy of a video or song that models are sent (docs/plan-v0.3.5.md, after
 * step 8): video at 360 px high and about 400 kbit/s with compact sound, a song as 64 kbit/s AAC.
 * A clip of two or three minutes then fits in one request to Gemini, with nothing stored at Google.
 * The original stays for playing and saving. Best effort: null when the phone can't do it.
 */
class Shrinker(private val context: Context) {
    /** A copy of the video at [source], written to [out]; null if it couldn't be made. [onProgress] hears how far along it is (0 to 1). */
    suspend fun video(source: File, out: File, onProgress: (Float) -> Unit = {}): File? = run(source, out, video = true, onProgress)

    /** A compact copy of the song at [source]. */
    suspend fun song(source: File, out: File, onProgress: (Float) -> Unit = {}): File? = run(source, out, video = false, onProgress)

    private suspend fun run(source: File, out: File, video: Boolean, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(TIMEOUT_MS) {
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
                make(source, out, video) { started = it }
            } finally {
                watch.cancel()
            }
        }
    }

    private suspend fun make(source: File, out: File, video: Boolean, onStart: (Transformer) -> Unit): File? =
            suspendCancellableCoroutine { cont ->
                val encoders = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(if (video) VIDEO_AUDIO_BITRATE else SONG_BITRATE).build())
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
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                    .apply {
                        if (video) setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(VIDEO_SHORT_SIDE))))
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

        /** Videos longer than this won't fit a model's budget even small, so they aren't shrunk. */
        const val LONGEST_VIDEO_MS = 5 * 60_000L

        /** Songs smaller than this, in a type Gemini knows, go as they are. */
        const val SMALL_SONG = 6L * 1024 * 1024
    }
}
