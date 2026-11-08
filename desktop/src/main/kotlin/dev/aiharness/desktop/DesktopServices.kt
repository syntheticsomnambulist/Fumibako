package dev.aiharness.desktop

import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.platform.HeartbeatScheduling
import dev.aiharness.app.platform.MediaImporter
import dev.aiharness.app.platform.Picked
import dev.aiharness.app.platform.Wake
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.media.FileFacts
import dev.aiharness.core.media.PdfInfo
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatSettings
import dev.aiharness.core.model.Track
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The desktop's services (desktop plan, step 4): heartbeats while the app runs, and what you add to
 * letters (photos, files and PDFs, songs, videos). Songs play in DesktopPlayback.
 */

/**
 * Heartbeats while Fumibako runs, kept by the wall clock so they catch up after the computer
 * sleeps: the first a minute after starting (which catches up on any missed while it was closed;
 * each persona keeps its own pace inside the engine), then every interval, and a minute after
 * the computer wakes if one came due while it slept. Reminders wake their persona at their time,
 * or on waking if it passed in sleep. [run] is set once the container exists.
 */
class DesktopHeartbeats(
    private val clock: () -> Long = System::currentTimeMillis,
    /** Tests drive [check] themselves. */
    autoStart: Boolean = true,
) : HeartbeatScheduling {
    var run: (suspend (force: Boolean, personaId: String?) -> List<HeartbeatOutcome>)? = null

    /** What a heartbeat wrote, for the tray's notifications. */
    var onOutcomes: suspend (List<HeartbeatOutcome>) -> Unit = {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    // Guarded by [lock].
    private var intervalMs: Long? = null
    private var nextBeatAt: Long? = null
    private var reminders: List<Wake> = emptyList()
    private var lastCheck = clock()

    init {
        if (autoStart) {
            scope.launch {
                while (isActive) {
                    delay(TICK)
                    runCatching { check() }
                }
            }
        }
    }

    private suspend fun beat(force: Boolean, personaId: String?) {
        val outcomes = runCatching { run?.invoke(force, personaId) }.getOrNull() ?: return
        onOutcomes(outcomes)
    }

    override fun apply(settings: HeartbeatSettings, intervalMinutes: Int) = synchronized(lock) {
        if (settings.mode != HeartbeatMode.PROACTIVE) {
            intervalMs = null
            nextBeatAt = null
            return
        }
        val interval = intervalMinutes.coerceAtLeast(15) * 60_000L
        intervalMs = interval
        // The first a minute after starting (or turning on); a shorter interval brings the next closer.
        val now = clock()
        nextBeatAt = minOf(nextBeatAt ?: (now + FIRST), now + interval)
    }

    override fun wakeFor(settings: HeartbeatSettings, wakes: Set<Wake>) = synchronized(lock) {
        val now = clock()
        reminders = if (settings.mode == HeartbeatMode.PROACTIVE) wakes.filter { it.at > now }.sortedBy { it.at } else emptyList()
    }

    override fun runNow(settings: HeartbeatSettings, personaId: String?) {
        scope.launch { beat(force = true, personaId = personaId) }
    }

    /**
     * What's due now: reminders past their time, and the heartbeat. A long gap since the last
     * check means the computer slept: what came due then waits a minute, for the network.
     */
    internal suspend fun check() {
        val (wakes, beatNow) = synchronized(lock) {
            val now = clock()
            val slept = now - lastCheck > SLEPT
            lastCheck = now
            if (slept) {
                nextBeatAt = nextBeatAt?.let { maxOf(it, now + AFTER_WAKING) }
                reminders = reminders.map { if (it.at + LATE <= now) it.copy(at = now + AFTER_WAKING - LATE) else it }
            }
            val wakes = reminders.filter { it.at + LATE <= now }
            reminders = reminders - wakes.toSet()
            val beatNow = nextBeatAt?.let { it <= now } == true
            if (beatNow) nextBeatAt = now + intervalMs!!
            wakes.map { it.personaId }.distinct() to beatNow
        }
        // A heartbeat for everyone covers the reminders' personas too.
        if (beatNow) beat(force = false, personaId = null) else wakes.forEach { beat(force = false, personaId = it) }
    }

    /** When the next heartbeat is due (for tests). */
    internal fun nextBeat(): Long? = synchronized(lock) { nextBeatAt }

    companion object {
        internal const val TICK = 20_000L
        internal const val FIRST = 60_000L
        internal const val AFTER_WAKING = 60_000L
        /** A reminder wakes its persona this long after its time, as on the phone. */
        internal const val LATE = 30_000L
        /** No check for this long: the computer was asleep (checks come every [TICK]). */
        internal const val SLEPT = 3 * 60_000L
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
        // Skia turns it the way the camera held it (its EXIF orientation).
        val scaled = if (maxSide > 0) image.scaledTo(maxSide) else image
        val jpeg = runCatching { scaled.jpeg() }.getOrNull() ?: return@withContext null
        ContentPart.Image(blobs.put(jpeg, "jpg"), "image/jpeg")
    }

    /** Any file, as a luggage tag: its name, kind, size, and a PDF's pages, first page and title. */
    override suspend fun file(item: Picked): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val file = fileOf(item) ?: return@withContext null
        val mime = typeOf(item)
        val ref = copy(file, mime) ?: return@withContext null
        val pdf = if (mime == "application/pdf") blobs.existingFile(ref)?.let { pdf(it, file.name) } else null
        ContentPart.Attachment(
            newId(), AttachmentKind.FILE, ref = ref, mimeType = mime, name = file.name, sizeBytes = file.length(),
            pages = pdf?.pages, title = pdf?.info?.title, artist = pdf?.info?.author, pictureRef = pdf?.firstPage, thumbRef = pdf?.thumb,
        )
    }

    private class Pdf(val pages: Int?, val firstPage: String?, val thumb: String?, val info: PdfInfo.Info)

    /** As the phone reads one: its page count, its first page (what a model that can't take PDFs sees) and a thumbnail. */
    private suspend fun pdf(file: File, name: String): Pdf {
        var pages: Int? = null
        var firstPage: String? = null
        var thumb: String? = null
        var own = PdfInfo.Info(null, null)
        runCatching {
            Loader.loadPDF(file).use { doc ->
                pages = doc.numberOfPages
                doc.documentInformation?.let { own = PdfInfo.Info(FileFacts.text(it.title), FileFacts.text(it.author)) }
                if (doc.numberOfPages > 0) {
                    // Big enough to read, as on the phone: 1280 wide.
                    val scale = 1280f / doc.getPage(0).cropBox.width
                    val bitmap = PDFRenderer(doc).renderImage(0, scale, ImageType.RGB).toImage()
                    firstPage = blobs.put(bitmap.jpeg(), "jpg")
                    thumb = blobs.put(bitmap.scaledTo(320).jpeg(), "jpg")
                }
            }
        }
        // Its title and author as the phone reads them; else as PDFBox does (it also reads compressed
        // ones), leaving out a title that only repeats the file's name.
        val read = runCatching { PdfInfo.of(lookThrough(file), name) }.getOrNull()
        val stem = name.substringBeforeLast('.').lowercase()
        val title = read?.title ?: own.title?.takeUnless { it.lowercase() == stem || it.equals(name, ignoreCase = true) }
        return Pdf(pages, firstPage, thumb, PdfInfo.Info(title, read?.author ?: own.author))
    }

    /** A file's start and end, where a PDF keeps its title and author (all of a small one). */
    private fun lookThrough(file: File): ByteArray =
        if (file.length() <= 2L * PdfInfo.LOOK) file.readBytes() else RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(PdfInfo.LOOK).also { f.readFully(it) }
            val tail = ByteArray(PdfInfo.LOOK).also { f.seek(f.length() - PdfInfo.LOOK); f.readFully(it) }
            head + tail
        }

    private fun BufferedImage.toImage(): Image {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(this, "png", it) }.toByteArray()
        return Image.makeFromEncoded(bytes)
    }

    private fun Image.scaledTo(side: Int): Image {
        val longer = max(width, height)
        if (longer <= side) return this
        val f = side.toFloat() / longer
        val w = (width * f).roundToInt().coerceAtLeast(1)
        val h = (height * f).roundToInt().coerceAtLeast(1)
        return Surface.makeRasterN32Premul(w, h).apply { canvas.drawImageRect(this@scaledTo, Rect.makeWH(w.toFloat(), h.toFloat())) }.makeImageSnapshot()
    }

    private fun Image.jpeg(): ByteArray = encodeToData(EncodedImageFormat.JPEG, 88)!!.bytes

    /**
     * Songs: one is a ticket stub; several are a playlist on a cassette, titled for [toName] and
     * labeled as from [fromName] until you change it, as on the phone. (No smaller copy for models
     * that hear songs yet: a song goes as it is.)
     */
    override suspend fun songs(items: List<Picked>, toName: String, fromName: String, onProgress: (Float) -> Unit): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val tracks = items.mapNotNull { track(it) }
        when {
            tracks.isEmpty() -> null
            tracks.size == 1 -> tracks.single().let { (t, cover) ->
                ContentPart.Attachment(
                    newId(), AttachmentKind.SONG, ref = t.ref, mimeType = t.mimeType, name = t.name, title = t.title, artist = t.artist,
                    album = t.album, year = t.year, lengthMs = t.lengthMs, pictureRef = cover, sizeBytes = t.ref?.let { blobs.existingFile(it)?.length() },
                )
            }
            else -> ContentPart.Attachment(
                newId(),
                AttachmentKind.PLAYLIST,
                title = "A mix for $toName",
                subtitle = "for $toName, from $fromName",
                tracks = tracks.map { it.first },
                lengthMs = tracks.sumOf { it.first.lengthMs ?: 0L },
            )
        }
    }

    override suspend fun tracks(items: List<Picked>): List<Track> = withContext(Dispatchers.IO) { items.mapNotNull { track(it)?.first } }

    /** A song copied in, with what its tags say and its cover (scaled to 512). */
    private suspend fun track(item: Picked): Pair<Track, String?>? {
        val file = fileOf(item) ?: return null
        val mime = typeOf(item)
        val ref = copy(file, mime) ?: return null
        val tags = blobs.existingFile(ref)?.let(Tags::read)
        val cover = tags?.cover?.let { bytes -> runCatching { blobs.put(Image.makeFromEncoded(bytes).scaledTo(512).jpeg(), "jpg") }.getOrNull() }
        return Track(
            ref = ref,
            mimeType = mime,
            title = tags?.title ?: file.name.substringBeforeLast('.'),
            artist = tags?.artist,
            album = tags?.album,
            year = tags?.year,
            lengthMs = tags?.lengthMs,
            name = file.name,
        ) to cover
    }

    /**
     * A video, as a film strip: its name, size and (for an MP4 or a QuickTime file) its length and
     * title. No frames or smaller copy on the computer yet: a model that watches gets it whole, one
     * that can't reads its label (docs/plan-desktop.md).
     */
    override suspend fun video(item: Picked, onProgress: (Float) -> Unit): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val file = fileOf(item) ?: return@withContext null
        val mime = typeOf(item) ?: "video/mp4"
        val ref = copy(file, mime) ?: return@withContext null
        val tags = blobs.existingFile(ref)?.takeIf { it.extension.lowercase() in setOf("mp4", "m4v", "mov") }?.let(Tags::read)
        ContentPart.Attachment(
            newId(), AttachmentKind.VIDEO, ref = ref, mimeType = mime, name = file.name, sizeBytes = file.length(),
            title = tags?.title ?: FileFacts.titleFromName(file.name), artist = tags?.artist, lengthMs = tags?.lengthMs,
        )
    }

    private suspend fun copy(file: File, mime: String?): String? =
        runCatching { file.inputStream().use { blobs.putStream(it, extensionOf(mime, file.name)) } }.getOrNull()

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
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "aif", "aiff" -> "audio/aiff"
        "flac" -> "audio/flac"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> null
    }

    private fun extensionOf(mime: String?, name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: mime?.substringAfter('/')?.takeIf { it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: "bin"
}

/** What a song's (or an MP4's) tags say, by jaudiotagger; null where it can't read them. */
internal object Tags {
    class Read(val title: String?, val artist: String?, val album: String?, val year: Int?, val lengthMs: Long?, val cover: ByteArray?)

    init {
        // It reports every oddity it meets in a file.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun read(file: File): Read? = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        fun field(key: FieldKey) = tag?.let { t -> FileFacts.text(runCatching { t.getFirst(key) }.getOrNull()) }
        Read(
            title = field(FieldKey.TITLE),
            artist = field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST),
            album = field(FieldKey.ALBUM),
            year = FileFacts.year(field(FieldKey.YEAR)),
            lengthMs = audio.audioHeader?.preciseTrackLength?.takeIf { it > 0 }?.let { (it * 1000).toLong() },
            cover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull(),
        )
    }.getOrNull()
}
