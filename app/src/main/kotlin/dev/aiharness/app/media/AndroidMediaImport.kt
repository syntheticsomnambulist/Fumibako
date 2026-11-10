package dev.aiharness.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import dev.aiharness.core.media.FileFacts
import dev.aiharness.core.media.PdfInfo
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Track
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import dev.aiharness.app.platform.MediaImporter
import dev.aiharness.app.platform.Picked

/**
 * Brings what you pick into the app: copies it into the app's own storage (so it stays when the
 * original moves) and reads what its frame shows: a picture scaled down, a song's tags, length and
 * cover, a PDF's pages and first page, a video's length and three frames.
 */
class AndroidMediaImport(private val context: Context, private val blobs: FileBlobStore) : MediaImporter {
    private val shrinker = Shrinker(context)

    /** The smaller copy models are sent, stored beside the kept file: (ref, size), or null. */
    private suspend fun smallerCopy(ref: String, video: Boolean, onProgress: (Float) -> Unit = {}, endMs: Long? = null): Pair<String, Long>? {
        val source = blobs.existingFile(ref) ?: return null
        val out = File(context.cacheDir, "shrink-${newId()}.mp4")
        val made = (if (video) shrinker.video(source, out, endMs, onProgress) else shrinker.song(source, out, onProgress)) ?: return null
        return try {
            val size = made.length()
            blobs.move(made, if (video) "mp4" else "m4a") to size
        } finally {
            made.delete()
        }
    }

    /**
     * The copy kept in place of the original at [ref], to save space (see [Shrinker.keptVideo] and
     * [Shrinker.keptSong]): its ref and size, with the original deleted. Null, keeping the
     * original, when the copy can't be made or wouldn't save much.
     */
    private suspend fun keptCopy(ref: String, video: Boolean, shortSide: Int?, lengthMs: Long?, onProgress: (Float) -> Unit = {}): Pair<String, Long>? {
        val source = blobs.existingFile(ref) ?: return null
        val out = File(context.cacheDir, "keep-${newId()}.mp4")
        val made = (if (video) shrinker.keptVideo(source, out, shortSide, lengthMs, onProgress) else shrinker.keptSong(source, out, lengthMs, onProgress))
            ?: return null
        try {
            val size = made.length()
            if (size <= 0 || size > source.length() * Shrinker.KEPT_MOST) return null
            val kept = blobs.move(made, if (video) "mp4" else "m4a")
            blobs.delete(ref)
            return kept to size
        } finally {
            made.delete()
        }
    }

    /** Whether a video is big enough for its 720p copy to be worth making: larger than 720p, or well over its bitrate. */
    private fun worthKeepingSmaller(shortSide: Int?, bitrate: Int?): Boolean =
        shortSide == null || shortSide > Shrinker.KEPT_SHORT_SIDE || bitrate == null || bitrate > Shrinker.KEPT_VIDEO_BITRATE * 1.3

    /** A lossless song (FLAC, WAV, AIFF), which is kept as AAC; MP3s and the like stay as they are. */
    private fun isLossless(mime: String?, name: String?): Boolean =
        mime?.lowercase() in LOSSLESS_TYPES || name?.substringAfterLast('.', "")?.lowercase() in LOSSLESS_EXTENSIONS

    /** The picked name with its kept copy's extension: `walk.mov` kept as `walk.mp4`. */
    private fun keptName(name: String?, extension: String): String? = name?.let { it.substringBeforeLast('.', it) + ".$extension" }

    private val resolver get() = context.contentResolver

    /** What kind of thing is at [uri] ("video/mp4"), by what the phone says or else its name. */
    private fun typeOf(uri: Uri): String? = runCatching { resolver.getType(uri) }.getOrNull() ?: mimeOf(nameAndSize(uri).first)

    /** A photo, scaled so its longer side is at most [maxSide] (0 keeps it full size). */
    private suspend fun photo(uri: Uri, maxSide: Int): ContentPart.Image? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, maxSide) ?: return@withContext null
        ContentPart.Image(blobs.put(jpeg(bitmap), "jpg"), "image/jpeg").also {
            // The camera's full-size shot has been copied in: it needn't stay in the cache.
            cameraShotAt(uri)?.delete()
        }
    }

    /** Any file, as a luggage tag: its name, kind, size, and a PDF's pages and first page. */
    private suspend fun file(uri: Uri): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val (name, size) = nameAndSize(uri)
        val mime = resolver.getType(uri) ?: mimeOf(name)
        val ref = copy(uri, extensionOf(mime, name)) ?: return@withContext null
        var pages: Int? = null
        var thumb: String? = null
        var firstPage: String? = null
        if (mime == "application/pdf") {
            runCatching {
                PdfRenderer(ParcelFileDescriptor.open(blobs.fileFor(ref), ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    pages = pdf.pageCount
                    pdf.openPage(0).use { page ->
                        // Big enough to read: it's what a model that can't take PDFs sees.
                        val w = 1280
                        val h = (w * page.height / page.width.toFloat()).roundToInt().coerceIn(1, 2400)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        firstPage = blobs.put(jpeg(bitmap), "jpg")
                        thumb = blobs.put(jpeg(scale(bitmap, 320)), "jpg")
                    }
                }
            }
        }
        // A PDF's own title and author, from its start and end, where they're kept.
        val info = if (mime == "application/pdf") blobs.fileFor(ref)?.let { pdfInfo(it, name) } else null
        ContentPart.Attachment(
            newId(), AttachmentKind.FILE, ref = ref, mimeType = mime, name = name, sizeBytes = size, pages = pages,
            title = info?.title, artist = info?.author, pictureRef = firstPage, thumbRef = thumb,
        )
    }

    /**
     * Songs: one is a ticket stub; several are a playlist on a cassette, titled for [toName] and
     * labeled as from [fromName] until you change it in the playlist editor.
     */
    private suspend fun songUris(uris: List<Uri>, toName: String, fromName: String, onProgress: (Float) -> Unit): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val tracks = uris.mapNotNull { track(it) }
        when {
            tracks.isEmpty() -> null
            tracks.size == 1 -> tracks.single().let { (t, cover) ->
                val size = t.ref?.let { blobs.fileFor(it)?.length() }
                val known = dev.aiharness.core.media.MediaSupport.geminiAudioType(t.mimeType) != null
                // A big or unusual file gets a compact copy for models that hear songs.
                val small = if (t.ref != null && (!known || (size ?: 0L) > Shrinker.SMALL_SONG)) smallerCopy(t.ref!!, video = false, onProgress) else null
                ContentPart.Attachment(
                    newId(), AttachmentKind.SONG, ref = t.ref, mimeType = t.mimeType, name = t.name, title = t.title, artist = t.artist,
                    album = t.album, year = t.year, lengthMs = t.lengthMs, pictureRef = cover, sizeBytes = size,
                    modelRef = small?.first, modelMimeType = small?.let { "audio/mp4" }, modelSizeBytes = small?.second,
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

    /** More songs for a playlist being edited. */
    private suspend fun trackUris(uris: List<Uri>): List<Track> = withContext(Dispatchers.IO) { uris.mapNotNull { track(it)?.first } }

    private suspend fun track(uri: Uri): Pair<Track, String?>? {
        val (name, _) = nameAndSize(uri)
        val mime = resolver.getType(uri) ?: mimeOf(name)
        val ref = copy(uri, extensionOf(mime, name)) ?: return null
        val file = blobs.fileFor(ref) ?: return null
        val retriever = MediaMetadataRetriever()
        val read = try {
            retriever.setDataSource(file.path)
            val cover = retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { blobs.put(jpeg(scale(it, 512)), "jpg") }
            }
            Track(
                ref = ref,
                mimeType = mime,
                title = FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)) ?: name?.substringBeforeLast('.'),
                artist = FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    ?: FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)),
                album = FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)),
                year = FileFacts.year(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)),
                lengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                name = name,
            ) to cover
        } catch (e: Exception) {
            Track(ref = ref, mimeType = mime, title = name?.substringBeforeLast('.'), name = name) to null
        } finally {
            runCatching { retriever.release() }
        }
        // A lossless song is kept as AAC, which takes about a fifth of the space (the tags and cover were read above).
        if (!isLossless(mime, name)) return read
        val (track, cover) = read
        val kept = keptCopy(ref, video = false, shortSide = null, lengthMs = track.lengthMs) ?: return read
        return track.copy(ref = kept.first, mimeType = "audio/mp4", name = keptName(track.name, "m4a")) to cover
    }

    /**
     * When the phone's gallery says a picked photo or video was taken, for one whose file doesn't
     * say (the photo picker's own column, in milliseconds); null where there's no such column.
     */
    private fun takenAt(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf("datetaken"), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
        }
    }.getOrNull()

    /** A PDF's title and author; a big one is looked through at its start and end only. */
    private fun pdfInfo(file: File, name: String?): PdfInfo.Info? = runCatching {
        val bytes = if (file.length() <= 2L * PdfInfo.LOOK) file.readBytes() else java.io.RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(PdfInfo.LOOK).also { f.readFully(it) }
            val tail = ByteArray(PdfInfo.LOOK).also { f.seek(f.length() - PdfInfo.LOOK); f.readFully(it) }
            head + tail
        }
        PdfInfo.of(bytes, name)
    }.getOrNull()

    /**
     * A video, as a film strip: its length, three frames (what a model that can't watch it
     * receives), and what it says of itself: its title, who made it, when it was recorded. Where it
     * was recorded stays out.
     */
    private suspend fun video(uri: Uri, onProgress: (Float) -> Unit): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val (name, size) = nameAndSize(uri)
        val mime = resolver.getType(uri) ?: mimeOf(name) ?: "video/mp4"
        val ref = copy(uri, extensionOf(mime, name)) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        var bitrate: Int? = null
        // What it says of itself and its frames, read from the original before its copy replaces it.
        val read = try {
            retriever.setDataSource(blobs.fileFor(ref)!!.path)
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val shown = FileFacts.shownSize(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull(),
            )
            val frames = listOf(0.2, 0.5, 0.8).mapNotNull { at ->
                val us = ((length ?: 0L) * at * 1000).toLong()
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { blobs.put(jpeg(scale(it, 480)), "jpg") }
            }
            ContentPart.Attachment(
                newId(), AttachmentKind.VIDEO, ref = ref, mimeType = mime, name = name, sizeBytes = size,
                title = FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)) ?: FileFacts.titleFromName(name),
                artist = FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    ?: FileFacts.text(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)),
                recordedAt = FileFacts.recordedAt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)) ?: takenAt(uri),
                width = shown?.first,
                height = shown?.second,
                hasSound = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { it == "yes" },
                lengthMs = length, frameRefs = frames, thumbRef = frames.getOrNull(1) ?: frames.firstOrNull(),
            )
        } catch (e: Exception) {
            return@withContext ContentPart.Attachment(newId(), AttachmentKind.VIDEO, ref = ref, mimeType = mime, name = name, sizeBytes = size, title = FileFacts.titleFromName(name), recordedAt = takenAt(uri))
        } finally {
            runCatching { retriever.release() }
        }
        val length = read.lengthMs ?: return@withContext read
        // Kept as a 720p copy in place of the original (most of the wait is this), then the copy for models made from it.
        val (w, h) = read.width to read.height
        val shortSide = if (w != null && h != null) minOf(w, h) else null
        val keeping = worthKeepingSmaller(shortSide, bitrate)
        val kept = if (keeping) keptCopy(ref, video = true, shortSide, length) { onProgress(it * KEPT_SHARE) } else null
        val start = if (keeping) KEPT_SHARE else 0f
        // For models that watch video: all of it, or a longer one's first four minutes.
        val firstPart = length > Shrinker.FIRST_PART_MS
        val small = smallerCopy(kept?.first ?: ref, video = true, { onProgress(start + it * (1 - start)) }, endMs = if (firstPart) Shrinker.FIRST_PART_MS else null)
        read.copy(
            ref = kept?.first ?: ref,
            mimeType = if (kept != null) "video/mp4" else mime,
            name = if (kept != null) keptName(name, "mp4") else name,
            sizeBytes = kept?.second ?: size,
            modelRef = small?.first, modelMimeType = small?.let { "video/mp4" }, modelSizeBytes = small?.second,
            modelLengthMs = if (small != null && firstPart) Shrinker.FIRST_PART_MS else null,
        )
    }

    private val cameraDir get() = File(context.cacheDir, "camera")

    /**
     * A place for the camera to write a photo, and the address to give it. Shots left from before
     * (taken but never brought in) are cleared first.
     */
    private fun cameraFile(): Pair<File, Uri> {
        val dir = cameraDir.apply { mkdirs() }
        val stale = System.currentTimeMillis() - CAMERA_SHOT_KEPT_MS
        dir.listFiles()?.filter { it.isFile && it.lastModified() < stale }?.forEach { it.delete() }
        val file = File(dir, "${newId()}.jpg")
        return file to androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /**
     * The camera's shot that [uri] points to (this app's provider, under `camera/`), or null for
     * anything else: the same provider also hands out stored files, which must stay.
     */
    private fun cameraShotAt(uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != "${context.packageName}.files") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "camera") return null
        val name = segments[1].takeIf { it.isNotEmpty() && '/' !in it && it != "." && it != ".." } ?: return null
        return File(cameraDir, name).takeIf { it.isFile }
    }

    private fun nameAndSize(uri: Uri): Pair<String?, Long?> = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else {
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
                val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                name to size
            }
        }
    }.getOrNull() ?: (uri.lastPathSegment to null)

    private suspend fun copy(uri: Uri, extension: String): String? =
        runCatching { resolver.openInputStream(uri)?.let { blobs.putStream(it, extension) } }.getOrNull()

    private fun decode(uri: Uri, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder turns the picture the way the camera held it.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = max(info.size.width, info.size.height)
                if (maxSide > 0 && side > maxSide) {
                    val f = maxSide.toFloat() / side
                    decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
                }
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { if (maxSide > 0) scale(it, maxSide) else it }
        }
    }.getOrNull()

    private fun scale(bitmap: Bitmap, maxSide: Int): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val f = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1), true)
    }

    private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()

    private fun mimeOf(name: String?): String? =
        name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }

    private fun extensionOf(mime: String?, name: String?): String =
        name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
            ?: mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "bin"

    // What the shared screens call (see MediaImporter): a picked item is its content URI.
    override fun typeOf(item: Picked): String? = typeOf(Uri.parse(item.uri))

    override suspend fun photo(item: Picked, maxSide: Int): ContentPart.Image? = photo(Uri.parse(item.uri), maxSide)

    override suspend fun file(item: Picked): ContentPart.Attachment? = file(Uri.parse(item.uri))

    override suspend fun songs(items: List<Picked>, toName: String, fromName: String, onProgress: (Float) -> Unit): ContentPart.Attachment? =
        songUris(items.map { Uri.parse(it.uri) }, toName, fromName, onProgress)

    override suspend fun tracks(items: List<Picked>): List<Track> = trackUris(items.map { Uri.parse(it.uri) })

    override suspend fun video(item: Picked, onProgress: (Float) -> Unit): ContentPart.Attachment? = video(Uri.parse(item.uri), onProgress)

    override suspend fun read(item: Picked): ByteArray? =
        runCatching { context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { it.readBytes() } }.getOrNull()

    override fun cameraTarget(): Picked = Picked(cameraFile().second.toString())

    override val keepsSmallerCopies: Boolean get() = true

    private companion object {
        /** A camera shot never brought in is cleared this long after it was taken. */
        const val CAMERA_SHOT_KEPT_MS = 60 * 60 * 1000L

        /** How much of a video's progress bar its kept copy takes (the copy for models is shorter and smaller). */
        const val KEPT_SHARE = 0.75f

        val LOSSLESS_TYPES = setOf("audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "audio/aiff", "audio/x-aiff")
        val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "wave", "aif", "aiff")
    }
}
