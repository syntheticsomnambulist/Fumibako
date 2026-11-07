package dev.aiharness.desktop

import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.media.NowPlaying
import dev.aiharness.app.media.Playback
import dev.aiharness.app.platform.ClearableSecretStore
import dev.aiharness.app.platform.HeartbeatScheduling
import dev.aiharness.app.platform.MediaImporter
import dev.aiharness.app.platform.Picked
import dev.aiharness.app.platform.Wake
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatSettings
import dev.aiharness.core.model.Track
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Properties
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * First versions of the desktop's services (desktop plan, steps 3 and 4 fill them in): heartbeats
 * while the app runs, photos and files in letters, songs opening in the computer's own player.
 */

/**
 * Heartbeats while Fumibako runs: the first a minute after starting (which catches up on any missed
 * while it was closed; each persona keeps its own pace inside the engine), then every interval.
 * Reminders wake their persona at their time. [run] is set once the container exists.
 */
class DesktopHeartbeats : HeartbeatScheduling {
    var run: (suspend (force: Boolean, personaId: String?) -> List<HeartbeatOutcome>)? = null

    /** What a heartbeat wrote, for the tray's notifications. */
    var onOutcomes: (List<HeartbeatOutcome>) -> Unit = {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodic: Job? = null
    private val reminders = mutableMapOf<String, Job>()

    private suspend fun beat(force: Boolean, personaId: String?) {
        val outcomes = runCatching { run?.invoke(force, personaId) }.getOrNull() ?: return
        onOutcomes(outcomes)
    }

    @Synchronized
    override fun apply(settings: HeartbeatSettings, intervalMinutes: Int) {
        periodic?.cancel()
        if (settings.mode != HeartbeatMode.PROACTIVE) return
        periodic = scope.launch {
            delay(60_000L)
            while (isActive) {
                beat(force = false, personaId = null)
                delay(intervalMinutes.coerceAtLeast(15) * 60_000L)
            }
        }
    }

    @Synchronized
    override fun wakeFor(settings: HeartbeatSettings, wakes: Set<Wake>) {
        reminders.values.forEach { it.cancel() }
        reminders.clear()
        if (settings.mode != HeartbeatMode.PROACTIVE) return
        val now = System.currentTimeMillis()
        wakes.filter { it.at > now }.forEach { wake ->
            reminders[wake.entryId] = scope.launch {
                delay(wake.at - now + 30_000L)
                beat(force = false, personaId = wake.personaId)
            }
        }
    }

    override fun runNow(settings: HeartbeatSettings, personaId: String?) {
        scope.launch { beat(force = true, personaId = personaId) }
    }
}

/**
 * API keys in a file only you can read, in Fumibako's folder. Not encrypted yet: the desktop
 * plan's step 3 moves them into the system's keychain, before any tester build.
 */
class FileSecretStore(private val file: File) : ClearableSecretStore {
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }

    @Synchronized
    private fun save() {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
        }
        file.outputStream().use { props.store(it, null) }
    }

    override suspend fun get(key: String): String? = props.getProperty(key)
    override suspend fun put(key: String, value: String) {
        props.setProperty(key, value); save()
    }

    override suspend fun remove(key: String) {
        props.remove(key); save()
    }

    override fun clearAll() {
        props.clear(); save()
    }
}

/** Photos and files from the computer: a picked item is the file's URI. */
class DesktopMedia(private val blobs: FileBlobStore) : MediaImporter {
    private fun fileOf(item: Picked): File? = runCatching { File(URI(item.uri)) }.getOrNull()?.takeIf { it.isFile }

    override fun typeOf(item: Picked): String? = fileOf(item)?.let { f -> runCatching { Files.probeContentType(f.toPath()) }.getOrNull() ?: mimeOf(f.name) }

    /** A photo, scaled so its longer side is at most [maxSide] (0 keeps it full size), as a JPEG. */
    override suspend fun photo(item: Picked, maxSide: Int): ContentPart.Image? = withContext(Dispatchers.IO) {
        val bytes = fileOf(item)?.readBytes() ?: return@withContext null
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return@withContext null
        val side = max(image.width, image.height)
        val scaled = if (maxSide > 0 && side > maxSide) {
            val f = maxSide.toFloat() / side
            val w = (image.width * f).roundToInt().coerceAtLeast(1)
            val h = (image.height * f).roundToInt().coerceAtLeast(1)
            Surface.makeRasterN32Premul(w, h).apply { canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat())) }.makeImageSnapshot()
        } else {
            image
        }
        val jpeg = scaled.encodeToData(EncodedImageFormat.JPEG, 88)?.bytes ?: return@withContext null
        ContentPart.Image(blobs.put(jpeg, "jpg"), "image/jpeg")
    }

    /** Any file, as a luggage tag: its name, kind and size. (A PDF's pages come with step 4.) */
    override suspend fun file(item: Picked): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val file = fileOf(item) ?: return@withContext null
        val mime = typeOf(item)
        val ref = runCatching { file.inputStream().use { blobs.putStream(it, extensionOf(mime, file.name)) } }.getOrNull() ?: return@withContext null
        ContentPart.Attachment(newId(), AttachmentKind.FILE, ref = ref, mimeType = mime, name = file.name, sizeBytes = file.length())
    }

    /** Songs and videos go as files for now (the screens fall back to [file] on null). */
    override suspend fun songs(items: List<Picked>, toName: String, fromName: String, onProgress: (Float) -> Unit): ContentPart.Attachment? = null

    override suspend fun tracks(items: List<Picked>): List<Track> = emptyList()

    override suspend fun video(item: Picked, onProgress: (Float) -> Unit): ContentPart.Attachment? = null

    override suspend fun read(item: Picked): ByteArray? = fileOf(item)?.let { runCatching { it.readBytes() }.getOrNull() }

    override fun cameraTarget(): Picked? = null

    private fun mimeOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "md" -> "text/markdown"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        else -> null
    }

    private fun extensionOf(mime: String?, name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: mime?.substringAfter('/')?.takeIf { it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: "bin"
}

/** For now songs open in the computer's own player; the in-app player comes with step 4. */
class DesktopPlayback(private val blobs: FileBlobStore) : Playback {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    override val state: StateFlow<NowPlaying?> = _state

    override fun play(attachment: ContentPart.Attachment, conversationId: String?, ink: Int, fromYou: Boolean, startAt: Int) {
        val track = if (attachment.kind == AttachmentKind.PLAYLIST) attachment.tracks.getOrNull(startAt)?.ref else attachment.ref
        val file = blobs.existingFile(track)
        runCatching {
            when {
                file != null -> Desktop.getDesktop().open(file)
                attachment.url != null -> Desktop.getDesktop().browse(URI(attachment.url))
            }
        }
    }

    override fun pause() = Unit
    override fun preview(state: NowPlaying?) {
        _state.value = state
    }

    override fun toggle() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(ms: Long) = Unit
    override fun skip(deltaMs: Long) = Unit
    override fun playTrack(index: Int) = Unit
    override fun shuffle() = Unit
    override fun cycleRepeat() = Unit
    override fun stop() {
        _state.value = null
    }
}
