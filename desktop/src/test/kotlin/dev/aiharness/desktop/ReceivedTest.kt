package dev.aiharness.desktop

import dev.aiharness.app.platform.Received
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a drop on the letter, or a paste, hands it: files, a copied picture, or nothing for text. */
class ReceivedTest {
    private class Holding(private val flavor: DataFlavor, private val value: Any) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(flavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == this.flavor
        override fun getTransferData(flavor: DataFlavor) = value
    }

    @Test
    fun filesComeAsTheyAre() {
        val dir = Files.createTempDirectory("fumibako-drop").toFile()
        val a = File(dir, "a.jpg").apply { writeText("a") }
        val b = File(dir, "b.pdf").apply { writeText("b") }
        val folder = File(dir, "folder").apply { mkdirs() }
        val data = Holding(DataFlavor.javaFileListFlavor, listOf(a, folder, b))
        assertTrue(Received.offers(data))
        // Folders are left out.
        assertEquals(listOf(a, b), Received.from(data)!!.map { File(URI(it.uri)) })
    }

    @Test
    fun aCopiedPictureIsSavedAsAPng() {
        val picture = BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB)
        val data = Holding(DataFlavor.imageFlavor, picture)
        assertTrue(Received.offers(data))
        val saved = File(URI(assertNotNull(Received.from(data)).single().uri))
        val back = ImageIO.read(saved)
        assertEquals(30 to 20, back.width to back.height)
    }

    @Test
    fun textIsLeftToTheNote() {
        val data = StringSelection("hello")
        assertFalse(Received.offers(data))
        assertNull(Received.from(data))
    }
}
