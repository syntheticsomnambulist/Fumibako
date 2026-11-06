package dev.aiharness.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
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

/**
 * Brings what you pick into the app: copies it into the app's own storage (so it stays when the
 * original moves) and reads what its frame shows: a picture scaled down, a song's tags, length and
 * cover, a PDF's pages and first page, a video's length and three frames.
 */
class MediaImport(private val context: Context, private val blobs: FileBlobStore) {
    private val resolver get() = context.contentResolver

    /** A photo, scaled so its longer side is at most [maxSide] (0 keeps it full size). */
    suspend fun photo(uri: Uri, maxSide: Int = 1280): ContentPart.Image? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, maxSide) ?: return@withContext null
        ContentPart.Image(blobs.put(jpeg(bitmap), "jpg"), "image/jpeg")
    }

    /** Any file, as a luggage tag: its name, kind, size, and a PDF's pages and first page. */
    suspend fun file(uri: Uri): ContentPart.Attachment? = withContext(Dispatchers.IO) {
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
        ContentPart.Attachment(
            newId(), AttachmentKind.FILE, ref = ref, mimeType = mime, name = name, sizeBytes = size, pages = pages,
            pictureRef = firstPage, thumbRef = thumb,
        )
    }

    /**
     * Songs: one is a ticket stub; several are a playlist on a cassette, titled for [toName] and
     * labeled as from [fromName] until you change it in the playlist editor.
     */
    suspend fun songs(uris: List<Uri>, toName: String, fromName: String): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val tracks = uris.mapNotNull { track(it) }
        when {
            tracks.isEmpty() -> null
            tracks.size == 1 -> tracks.single().let { (t, cover) ->
                ContentPart.Attachment(newId(), AttachmentKind.SONG, ref = t.ref, mimeType = t.mimeType, name = t.name, title = t.title, artist = t.artist, lengthMs = t.lengthMs, pictureRef = cover)
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
    suspend fun tracks(uris: List<Uri>): List<Track> = withContext(Dispatchers.IO) { uris.mapNotNull { track(it)?.first } }

    private suspend fun track(uri: Uri): Pair<Track, String?>? {
        val (name, _) = nameAndSize(uri)
        val mime = resolver.getType(uri) ?: mimeOf(name)
        val ref = copy(uri, extensionOf(mime, name)) ?: return null
        val file = blobs.fileFor(ref) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val cover = retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { blobs.put(jpeg(scale(it, 512)), "jpg") }
            }
            Track(
                ref = ref,
                mimeType = mime,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: name?.substringBeforeLast('.'),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                lengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                name = name,
            ) to cover
        } catch (e: Exception) {
            Track(ref = ref, mimeType = mime, title = name?.substringBeforeLast('.'), name = name) to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A video, as a film strip: its length and three frames (what a model that can't watch it receives). */
    suspend fun video(uri: Uri): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        val (name, size) = nameAndSize(uri)
        val mime = resolver.getType(uri) ?: mimeOf(name) ?: "video/mp4"
        val ref = copy(uri, extensionOf(mime, name)) ?: return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(blobs.fileFor(ref)!!.path)
            val length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frames = listOf(0.2, 0.5, 0.8).mapNotNull { at ->
                val us = ((length ?: 0L) * at * 1000).toLong()
                retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { blobs.put(jpeg(scale(it, 480)), "jpg") }
            }
            ContentPart.Attachment(
                newId(), AttachmentKind.VIDEO, ref = ref, mimeType = mime, name = name, sizeBytes = size,
                title = name?.substringBeforeLast('.'), lengthMs = length, frameRefs = frames, thumbRef = frames.getOrNull(1) ?: frames.firstOrNull(),
            )
        } catch (e: Exception) {
            ContentPart.Attachment(newId(), AttachmentKind.VIDEO, ref = ref, mimeType = mime, name = name, sizeBytes = size, title = name?.substringBeforeLast('.'))
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A place for the camera to write a photo, and the address to give it. */
    fun cameraTarget(): Pair<File, Uri> {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${newId()}.jpg")
        return file to androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
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
}
