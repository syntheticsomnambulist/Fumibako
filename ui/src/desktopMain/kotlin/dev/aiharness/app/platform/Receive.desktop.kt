package dev.aiharness.app.platform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.paper
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Files dropped on the letter, or pasted with Ctrl+V (Command+V on a Mac): copied files, or a
 * copied picture (saved to a temporary file first). Text pastes into the note as usual. While
 * files are dragged over, a dashed line says they can go here.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.receiveFiles(onFiles: (List<Picked>) -> Unit): Modifier {
    val current by rememberUpdatedState(onFiles)
    var over by remember { mutableStateOf(false) }
    val line = paper.sumi.copy(alpha = 0.55f)
    val wash = paper.sumi.copy(alpha = 0.06f)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                over = true
            }

            override fun onExited(event: DragAndDropEvent) {
                over = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                over = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                over = false
                val picked = Received.from(event.awtTransferable) ?: return false
                current(picked)
                return true
            }
        }
    }
    return this
        .dragAndDropTarget(shouldStartDragAndDrop = { Received.offers(it.awtTransferable) }, target = target)
        .onPreviewKeyEvent { key ->
            val paste = key.type == KeyEventType.KeyDown && key.key == Key.V && (key.isCtrlPressed || key.isMetaPressed)
            if (!paste) return@onPreviewKeyEvent false
            val picked = runCatching { Received.from(Toolkit.getDefaultToolkit().systemClipboard.getContents(null)) }.getOrNull()
            if (picked != null) current(picked)
            picked != null
        }
        .drawWithContent {
            drawContent()
            if (over) {
                drawRect(wash)
                val inset = 10.dp.toPx()
                drawRoundRect(
                    line,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))),
                )
            }
        }
}

/** What a drop or the clipboard holds that a letter can take: files, or a picture. */
object Received {
    fun offers(data: Transferable?): Boolean =
        data != null && (data.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || data.isDataFlavorSupported(DataFlavor.imageFlavor))

    /** The files (or the picture, saved as a PNG), or null when there are none (text, say). */
    fun from(data: Transferable?): List<Picked>? {
        if (data == null) return null
        if (data.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files = runCatching { (data.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>() }.getOrNull()
            return files?.filter { it.isFile }?.map { Picked(it.toURI().toString()) }?.takeIf { it.isNotEmpty() }
        }
        if (data.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val image = runCatching { data.getTransferData(DataFlavor.imageFlavor) as? Image }.getOrNull() ?: return null
            return save(image)?.let { listOf(Picked(it.toURI().toString())) }
        }
        return null
    }

    private fun save(image: Image): File? = runCatching {
        val buffered = image as? BufferedImage ?: run {
            val w = image.getWidth(null)
            val h = image.getHeight(null)
            if (w <= 0 || h <= 0) return null
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() } }
        }
        Files.createTempFile("fumibako-pasted-", ".png").toFile().also { file ->
            file.deleteOnExit()
            ImageIO.write(buffered, "png", file)
        }
    }.getOrNull()
}
