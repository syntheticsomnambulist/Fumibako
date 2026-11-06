package dev.aiharness.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.media.AttachmentFrame
import dev.aiharness.app.ui.media.FrameActions
import dev.aiharness.app.ui.media.PhotoStack
import dev.aiharness.app.ui.media.PolaroidFrame
import dev.aiharness.app.ui.media.frameTilt
import dev.aiharness.app.ui.media.noteScale
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.launch

/**
 * A frame placed in a letter or note: tilted a little, stuck with its own stickers (stickers on a
 * frame belong to the attachment), opened by a tap, and held for Save, Share or Copy link.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnPaper(
    key: String,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    onOpen: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val target = StickerTarget.attachment(key)
    var open by remember { mutableStateOf(false) }
    if (handling != null) DisposableEffect(key) { onDispose { handling.forget(target) } }
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        StuckOn(
            stickers = handling?.on?.invoke(key).orEmpty(),
            inkOf = inkOf,
            modifier = Modifier.graphicsLayer { rotationZ = frameTilt(key) }.then(handling?.paper?.invoke(target) ?: Modifier),
            nameOf = nameOf,
            selectedId = handling?.selectedId,
            onTap = handling?.onTap,
            onChange = handling?.onChange,
        ) {
            Box(Modifier.combinedClickable(onClick = onOpen, onLongClick = { open = true }, onLongClickLabel = "Save or share")) {
                content()
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() }) }
                }
            }
        }
    }
}

/** The menu a frame offers when held: Save to phone, Share, and Copy link for a cutting. */
@Composable
fun frameMenu(ref: String?, mime: String?, name: String?, url: String?): List<Pair<String, () -> Unit>> {
    val context = LocalContext.current
    val blobs = LocalAppContainer.current.blobStore
    val scope = rememberCoroutineScope()
    return buildList {
        if (ref != null && FrameActions.canSave) add("Save to phone" to {
            scope.launch {
                val saved = FrameActions.save(context, blobs, ref, mime, name)
                Toast.makeText(context, if (saved) "Saved to your phone." else "Couldn't save it.", Toast.LENGTH_SHORT).show()
            }
        })
        if (ref != null || url != null) add("Share" to { FrameActions.share(context, blobs, ref, mime, url) })
        if (url != null) add("Copy link" to { FrameActions.copyLink(context, url) })
    }
}

/** A letter's or note's pictures: one as a polaroid, several as a stack. */
@Composable
fun FramedPictures(
    images: List<ContentPart.Image>,
    ink: Color,
    pattern: TapePattern,
    scale: Float,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    onImage: (String) -> Unit,
    alignment: Alignment = Alignment.Center,
) {
    if (images.isEmpty()) return
    val first = images.first()
    OnPaper(first.ref, handling, inkOf, nameOf, onOpen = { onImage(first.ref) }, menu = frameMenu(first.ref, first.mimeType, null, null), alignment = alignment) {
        if (images.size == 1) PolaroidFrame(first, ink, pattern, scale) else PhotoStack(images, ink, pattern, scale * 0.9f)
    }
}

/** One attachment in its frame. */
@Composable
fun FramedAttachment(
    attachment: ContentPart.Attachment,
    ink: Color,
    behind: Color,
    inNote: Boolean,
    handling: StickerHandling?,
    inkOf: (Sticker) -> Color,
    nameOf: (Sticker) -> String?,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val blobs = LocalAppContainer.current.blobStore
    val ref = attachment.ref ?: attachment.tracks.firstOrNull()?.ref
    OnPaper(
        attachment.id, handling, inkOf, nameOf,
        onOpen = { FrameActions.open(context, blobs, attachment) },
        menu = frameMenu(ref.takeIf { attachment.kind != AttachmentKind.LINK }, attachment.mimeType, attachment.name, attachment.url),
        alignment = alignment,
    ) {
        AttachmentFrame(attachment, ink, behind, scale = if (inNote) noteScale(attachment.kind) else 1f)
    }
}
