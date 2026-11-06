package dev.aiharness.app.ui.personas

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.theme.InkKanji
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Back on the left; [trailing] actions (Edit, Done) on the right. */
@Composable
fun PageBar(onBack: () -> Unit, backLabel: String = "Back", trailing: @Composable () -> Unit = {}) {
    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel, tint = paper.usuzumi0) }
        Box(Modifier.weight(1f))
        trailing()
    }
}

/**
 * Back while [editing] leaves edit mode instead of the page, asking first when anything [changed].
 * Returns the back action for the page bar's arrow.
 */
@Composable
fun discardGuard(editing: Boolean, changed: Boolean, onLeave: () -> Unit, onBack: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    val leave = { if (changed) asking = true else onLeave() }
    BackHandler(enabled = editing) { leave() }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Discard your changes?", style = PaperType.entryTitle) },
            confirmButton = { TextButton(onClick = { asking = false; onLeave() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Keep editing") } },
        )
    }
    return { if (editing) leave() else onBack() }
}

@Composable
fun Chip(content: @Composable () -> Unit) {
    Row(
        Modifier
            .border(1.dp, paper.nijimi1, PaperCorner)
            .background(paper.kami, PaperCorner)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
fun InkChip(inkName: String, ink: Color) = Chip {
    Box(Modifier.size(12.dp).background(ink, PaperCorner))
    Text(InkKanji[inkName].orEmpty(), style = PaperType.seal.copy(fontSize = 13.sp), color = paper.sumi)
    Text(inkName, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1)
}

@Composable
fun TapeChip(pattern: TapePattern, ink: Color) = Chip {
    androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
        drawTape(androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), size.height, Tape(ink, pattern, shadow = false), tooth = 2.dp.toPx())
    }
    Text(pattern.name.lowercase(), style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1)
}

/**
 * The picture menu on a polaroid while editing: choose a photo from the phone, optionally have
 * NovelAI draw one ([onDraw], when available), or remove it.
 */
@Composable
fun PictureMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
    onRemove: (() -> Unit)?,
    onDraw: (() -> Unit)? = null,
    drawBusy: Boolean = false,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
                if (bytes != null) onPicked(container.blobStore.put(bytes, "jpg"))
            }
        }
    }
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (onDraw != null) DropdownMenuItem(text = { Text(if (drawBusy) "Drawing…" else "Draw a portrait") }, enabled = !drawBusy, onClick = { onDismiss(); onDraw() })
        DropdownMenuItem(text = { Text("Choose a photo") }, onClick = { onDismiss(); pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        if (onRemove != null) DropdownMenuItem(text = { Text("Remove the picture") }, onClick = { onDismiss(); onRemove() })
    }
}
