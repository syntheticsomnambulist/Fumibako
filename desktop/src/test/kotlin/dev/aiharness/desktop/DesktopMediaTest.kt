package dev.aiharness.desktop

import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.platform.Picked
import dev.aiharness.core.model.AttachmentKind
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Photos and files from the computer: turned as the camera held them, and a PDF's pages. */
class DesktopMediaTest {
    private val dir: File = Files.createTempDirectory("fumibako-media").toFile()
    private val blobs = FileBlobStore(File(dir, "blobs"))
    private val media = DesktopMedia(blobs)

    private fun picked(file: File) = Picked(file.toURI().toString())

    private fun decode(ref: String?): Image = Image.makeFromEncoded(blobs.existingFile(ref)!!.readBytes())

    @Test
    fun aPhotoComesInTurnedTheWayTheCameraHeldIt() = runBlocking {
        // 40 by 20, red on its left half; the camera says to turn it a quarter clockwise (EXIF 6).
        val file = File(dir, "turned.jpg").apply { writeBytes(withOrientation(jpeg(40, 20), 6)) }
        val photo = assertNotNull(media.photo(picked(file), maxSide = 0))
        val image = decode(photo.ref)
        assertEquals(20 to 40, image.width to image.height)
        // Turned clockwise, the red left half is now the top.
        val pixels = Bitmap.makeFromImage(image)
        assertTrue(isRed(pixels.getColor(10, 5)), "red at the top")
        assertTrue(!isRed(pixels.getColor(10, 35)), "not red at the bottom")
    }

    @Test
    fun aPhotoWithoutOrientationStaysAsItIs() = runBlocking {
        val file = File(dir, "plain.jpg").apply { writeBytes(jpeg(40, 20)) }
        val image = decode(assertNotNull(media.photo(picked(file), maxSide = 0)).ref)
        assertEquals(40 to 20, image.width to image.height)
    }

    @Test
    fun aTurnedPhotoIsScaledByItsLongerSide() = runBlocking {
        val file = File(dir, "big.jpg").apply { writeBytes(withOrientation(jpeg(400, 200), 8)) }
        val image = decode(assertNotNull(media.photo(picked(file), maxSide = 100)).ref)
        assertEquals(50 to 100, image.width to image.height)
    }

    @Test
    fun aPdfComesWithItsPagesFirstPageAndTitle() = runBlocking {
        val file = File(dir, "zine.pdf")
        PDDocument().use { doc ->
            repeat(3) { i ->
                val page = PDPage(PDRectangle.A5)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { ink ->
                    ink.setNonStrokingColor(if (i == 0) java.awt.Color.RED else java.awt.Color.BLUE)
                    ink.addRect(0f, 0f, page.mediaBox.width, page.mediaBox.height / 2)
                    ink.fill()
                }
            }
            doc.documentInformation = PDDocumentInformation().apply { title = "A Small Zine"; author = "Mika" }
            doc.save(file)
        }
        val tag = assertNotNull(media.file(picked(file)))
        assertEquals("application/pdf", tag.mimeType)
        assertEquals("zine.pdf", tag.name)
        assertEquals(3, tag.pages)
        assertEquals("A Small Zine", tag.title)
        assertEquals("Mika", tag.artist)
        val first = decode(tag.pictureRef)
        assertEquals(1280, first.width)
        // A5 is taller than wide; the first page's lower half is red.
        assertTrue(first.height > first.width)
        val pixels = Bitmap.makeFromImage(first)
        assertTrue(isRed(pixels.getColor(640, first.height - 20)), "the first page")
        // The thumbnail's longer side is 320, as the phone's.
        assertEquals(320, decode(tag.thumbRef).height)
    }

    @Test
    fun anyOtherFileIsATagWithItsNameAndSize() = runBlocking {
        val file = File(dir, "notes.txt").apply { writeText("hello") }
        val tag = assertNotNull(media.file(picked(file)))
        assertEquals("notes.txt", tag.name)
        assertEquals(5L, tag.sizeBytes)
        assertEquals(null, tag.pages)
    }

    @Test
    fun aSongComesWithWhatItsTagsSay(): Unit = runBlocking {
        val file = wav(File(dir, "track01.wav"), seconds = 2)
        AudioFileIO.read(file).apply {
            tagOrCreateAndSetDefault.apply {
                setField(FieldKey.TITLE, "Paper Moon")
                setField(FieldKey.ARTIST, "The Inks")
                setField(FieldKey.ALBUM, "Letters")
                setField(FieldKey.YEAR, "1998")
            }
            commit()
        }
        val ticket = assertNotNull(media.songs(listOf(picked(file)), "Aria", "Mika") {})
        assertEquals(AttachmentKind.SONG, ticket.kind)
        assertEquals("Paper Moon", ticket.title)
        assertEquals("The Inks", ticket.artist)
        assertEquals("Letters", ticket.album)
        assertEquals(1998, ticket.year)
        assertEquals("track01.wav", ticket.name)
        assertTrue(ticket.lengthMs!! in 1_900..2_100, "about two seconds: ${ticket.lengthMs}")
        assertNotNull(blobs.existingFile(ticket.ref))
    }

    @Test
    fun songsWithoutTagsAreNamedByTheirFileAndSeveralMakeAMix() = runBlocking {
        val a = wav(File(dir, "Morning walk.wav"), seconds = 1)
        val b = wav(File(dir, "Evening.wav"), seconds = 1)
        val mix = assertNotNull(media.songs(listOf(picked(a), picked(b)), "Aria", "Mika") {})
        assertEquals(AttachmentKind.PLAYLIST, mix.kind)
        assertEquals("A mix for Aria", mix.title)
        assertEquals("for Aria, from Mika", mix.subtitle)
        assertEquals(listOf("Morning walk", "Evening"), mix.tracks.map { it.title })
    }

    @Test
    fun aVideoIsAFilmStripWithItsName(): Unit = runBlocking {
        val file = File(dir, "beach_day.mp4").apply { writeBytes(ByteArray(64)) }
        val strip = assertNotNull(media.video(picked(file)) {})
        assertEquals(AttachmentKind.VIDEO, strip.kind)
        assertEquals("beach_day.mp4", strip.name)
        assertEquals("video/mp4", strip.mimeType)
        assertNotNull(strip.title)
    }

    /** A silent 16-bit mono WAV of [seconds]. */
    private fun wav(file: File, seconds: Int): File {
        val rate = 8000
        val data = ByteArray(rate * 2 * seconds)
        fun le(v: Int, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }
        file.writeBytes(
            "RIFF".toByteArray() + le(36 + data.size, 4) + "WAVE".toByteArray() +
                "fmt ".toByteArray() + le(16, 4) + le(1, 2) + le(1, 2) + le(rate, 4) + le(rate * 2, 4) + le(2, 2) + le(16, 2) +
                "data".toByteArray() + le(data.size, 4) + data,
        )
        return file
    }

    private fun isRed(color: Int) = Color.getR(color) > 180 && Color.getG(color) < 80 && Color.getB(color) < 80

    /** A JPEG [w] by [h], red on the left half and white on the right. */
    private fun jpeg(w: Int, h: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas: Canvas = surface.canvas
        canvas.clear(Color.WHITE)
        canvas.drawRect(Rect.makeWH(w / 2f, h.toFloat()), Paint().apply { color = Color.RED })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 95)!!.bytes
    }

    /** [jpeg] with an EXIF block saying how the camera was held ([orientation], 1 to 8). */
    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, // big-endian, first IFD at 8
            0, 1, // one entry
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // Orientation, SHORT, 1
            0, 0, 0, 0, // no next IFD
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return ByteArrayOutputStream().apply {
            write(jpeg, 0, 2) // SOI
            write(app1)
            write(jpeg, 2, jpeg.size - 2)
        }.toByteArray()
    }
}
