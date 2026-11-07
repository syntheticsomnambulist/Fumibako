package dev.aiharness.app.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.PlannerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Locale
import javax.swing.JFileChooser

/*
 * The desktop's side of Expect.kt. First versions: pickers, links, files, the clipboard and pictures
 * work; videos and song previews open outside the app for now, and there's no camera, location or
 * calendar. Filled in during the desktop plan's steps 3 to 5 (docs/plan-desktop.md).
 */

/** Escape goes back, as the phone's back gesture does (see the desktop window's key handling). */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val back by rememberUpdatedState(onBack)
    val handlers = LocalBackHandlers.current
    androidx.compose.runtime.DisposableEffect(handlers, enabled) {
        val handler = { back() }
        if (enabled) handlers.add(handler)
        onDispose { handlers.remove(handler) }
    }
}

/** The back handlers in the window, innermost last: Escape runs the last one. */
class BackHandlers {
    private val stack = mutableListOf<() -> Unit>()
    fun add(handler: () -> Unit) {
        stack.add(handler)
    }

    fun remove(handler: () -> Unit) {
        stack.remove(handler)
    }

    /** Runs the innermost handler; false if there was none (the window goes back itself). */
    fun back(): Boolean {
        val handler = stack.lastOrNull() ?: return false
        handler()
        return true
    }

    /** For a window's onPreviewKeyEvent: Escape goes back. */
    fun onKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown && event.key == Key.Escape && back()
}

val LocalBackHandlers = androidx.compose.runtime.staticCompositionLocalOf { BackHandlers() }

@Composable
actual fun rememberPicker(kind: PickKind, onPicked: (List<Picked>) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    return remember(kind) {
        {
            val chooser = JFileChooser().apply {
                isMultiSelectionEnabled = kind == PickKind.PHOTOS || kind == PickKind.SONGS
                dialogTitle = when (kind) {
                    PickKind.PHOTOS, PickKind.PHOTO -> "Choose a photo"
                    PickKind.VIDEO -> "Choose a video"
                    PickKind.FILE -> "Choose a file"
                    PickKind.SONGS -> "Choose songs"
                }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val files = if (chooser.isMultiSelectionEnabled) chooser.selectedFiles.toList() else listOfNotNull(chooser.selectedFile)
                picked(files.take(if (kind == PickKind.PHOTOS) 12 else Int.MAX_VALUE).map { Picked(it.toURI().toString()) })
            } else {
                picked(emptyList())
            }
        }
    }
}

@Composable
actual fun rememberCamera(onTaken: (Picked) -> Unit): (() -> Unit)? = null

@Composable
actual fun rememberCalendarPermission(onResult: (granted: Boolean) -> Unit): (() -> Unit)? = null

@Composable
actual fun RequestNotificationPermission() = Unit

@Composable
actual fun rememberApproximateLocation(onLocation: (latitude: Double, longitude: Double) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? = null

@Composable
actual fun systemUses24HourClock(): Boolean = remember {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()
    pattern == null || 'H' in pattern || 'k' in pattern
}

@Composable
actual fun systemReducedMotion(): Boolean = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun rememberTimePicker(is24: Boolean, onPicked: (LocalTime) -> Unit): (LocalTime) -> Unit {
    val picked by rememberUpdatedState(onPicked)
    var asking by remember { mutableStateOf<LocalTime?>(null) }
    asking?.let { start ->
        val state = rememberTimePickerState(start.hour, start.minute, is24Hour = is24)
        AlertDialog(
            onDismissRequest = { asking = null },
            confirmButton = { TextButton(onClick = { asking = null; picked(LocalTime.of(state.hour, state.minute)) }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } },
            text = { TimeInput(state) },
        )
    }
    return { time -> asking = time }
}

@Composable
actual fun rememberOutside(): Outside {
    val blobs = LocalAppContainer.current.blobStore
    return remember(blobs) { DesktopOutside(blobs) }
}

/** Links in the default browser, files in the app that opens them, saving through a dialog. */
private class DesktopOutside(private val blobs: FileBlobStore) : Outside {
    override val device = "computer"

    private val desktop: Desktop? = runCatching { Desktop.getDesktop() }.getOrNull()

    override fun openLink(url: String, inApp: Boolean, toolbar: Int, night: Boolean) {
        runCatching { desktop!!.browse(URI(url)) }.onFailure { toast("Nothing on this computer opens that.") }
    }

    override fun open(attachment: ContentPart.Attachment) {
        val url = attachment.url
        if (attachment.kind == AttachmentKind.LINK && url != null) return openLink(url, inApp = false, toolbar = 0, night = false)
        val file = blobs.existingFile(attachment.ref ?: attachment.tracks.firstOrNull()?.ref)
        when {
            file != null -> runCatching { desktop!!.open(file) }.onFailure { toast("Nothing on this computer opens that.") }
            url != null -> openLink(url, inApp = false, toolbar = 0, night = false)
        }
    }

    override val canShare = false

    override val canSave = true

    override fun share(ref: String?, mime: String?, url: String?) = Unit

    override suspend fun save(ref: String?, mime: String?, name: String?): Boolean {
        val file = blobs.existingFile(ref) ?: return false
        val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply { this.file = name ?: file.name }
        dialog.isVisible = true
        val target = dialog.file?.let { File(dialog.directory, it) } ?: return false
        return withContext(Dispatchers.IO) { runCatching { file.copyTo(target, overwrite = true); true }.getOrDefault(false) }
    }

    override fun copyText(text: String, label: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    override fun toast(message: String) {
        Toasts.show(message)
    }

    override fun openPhoneEvent(entry: PlannerEntry) = Unit
}

/** Short notices, shown at the bottom of the window for a few seconds (see the desktop's window). */
object Toasts {
    var current by mutableStateOf<String?>(null)
        private set
    private var shownAt = 0L

    fun show(message: String) {
        current = message
        shownAt = System.currentTimeMillis()
    }

    /** Clears the notice once it has been up for [forMillis]. */
    fun expire(forMillis: Long = 2_500) {
        if (current != null && System.currentTimeMillis() - shownAt >= forMillis) current = null
    }
}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun decodeSampledImage(file: File, maxSize: Int): ImageBitmap? {
    val image = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull() ?: return null
    val longer = maxOf(image.width, image.height)
    if (longer <= maxSize * 2) return image.toComposeImageBitmap()
    // Like Android's inSampleSize: halve until the longer side is under twice maxSize.
    var side = longer
    while (side / 2 >= maxSize) side /= 2
    val scale = side.toFloat() / longer
    val w = (image.width * scale).toInt().coerceAtLeast(1)
    val h = (image.height * scale).toInt().coerceAtLeast(1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

@Composable
actual fun rememberVideoController(): VideoController = remember { NoVideo() }

/** For now a video plays in the computer's own player (see [VideoSurface]). */
private class NoVideo : VideoController {
    var file: File? = null
    override val isPlaying = false
    override val currentPosition = 0L
    override val duration = 0L
    override var volume = 1f
    override fun load(file: File) {
        this.file = file
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(ms: Long) = Unit
    override fun release() = Unit
}

@Composable
actual fun VideoSurface(controller: VideoController, modifier: Modifier) {
    val file = (controller as NoVideo).file
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Videos play in your computer's video player for now.", color = androidx.compose.ui.graphics.Color.White)
            if (file != null) PaperButton("Open the video") { runCatching { Desktop.getDesktop().open(file) } }
        }
    }
}

@Composable
actual fun rememberScreenTurner(): (landscape: Boolean) -> Unit = remember { { _: Boolean -> } }

/** For now the service's player opens in the browser. */
@Composable
actual fun EmbedView(embed: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        PaperButton("Play it in your browser") { runCatching { Desktop.getDesktop().browse(URI(embed)) } }
    }
}

actual fun newSongPreview(): SongPreview = BrowserPreview()

/** For now a preview opens in the browser. */
private class BrowserPreview : SongPreview {
    override val playing: String? = null
    override fun toggle(url: String?) {
        if (url != null) runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun stop() = Unit
}
