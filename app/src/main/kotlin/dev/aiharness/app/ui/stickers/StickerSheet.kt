package dev.aiharness.app.ui.stickers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef

/**
 * A sticker sheet: backing paper with stickers on it, each with its `:name:` and, when it has one,
 * the feeling it stands for. [label] is stamped on the corner.
 */
@Composable
fun StickerSheet(
    stickers: List<StickerArt>,
    ink: Color,
    label: String,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    onOpen: (StickerArt) -> Unit,
) {
    val colors = paper
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(PaperCorner)
                .background(lerp(colors.kami, Color.White, if (stationery.isNight) 0.06f else 0.45f))
                .border(1.dp, colors.nijimi1, PaperCorner)
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stickers.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { art ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(PaperCorner)
                                .clickable(onClickLabel = "Open :${art.name}:") { onOpen(art) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            StickerPicture(StickerRef.Art(art.id), ink, Modifier.size((60 * StickerBaker.BOX).dp), name = ":${art.name}: sticker")
                            Text(":${art.name}:", style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp), color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            art.feeling?.let { Text(it, style = PaperType.postscript.copy(fontSize = 12.sp), color = ink, maxLines = 1) }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // The sheet's label, stamped on its top edge.
        Text(
            label.uppercase(),
            style = PaperType.meta.copy(fontSize = 11.sp),
            color = ink,
            modifier = Modifier
                .offset(x = 14.dp)
                .background(colors.kami, PaperCorner)
                .border(1.5.dp, ink, PaperCorner)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * One sticker opened from a sheet: rename it, say what feeling it stands for, make it the unread
 * mark ([markLabel] when that applies), or take it off the sheet.
 */
@Composable
fun StickerArtDialog(
    art: StickerArt,
    ink: Color,
    isMark: Boolean,
    markLabel: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, feeling: String?, mark: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var name by remember(art.id) { mutableStateOf(art.name) }
    var feeling by remember(art.id) { mutableStateOf(art.feeling.orEmpty()) }
    var mark by remember(art.id) { mutableStateOf(isMark) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(":${art.name}:", style = PaperType.entryTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    StickerPicture(StickerRef.Art(art.id), ink, Modifier.size((84 * StickerBaker.BOX).dp))
                }
                IndexCardField("Name", name, { name = it }, singleLine = true, ink = ink)
                IndexCardField("Stands for", feeling, { feeling = it }, placeholder = "a feeling, like proud", singleLine = true, ink = ink)
                if (markLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(markLabel, style = PaperType.noteBody.copy(fontSize = 13.5.sp), color = paper.usuzumi1, modifier = Modifier.weight(1f))
                        TickBox(mark, { mark = it }, ink = ink)
                    }
                }
                if (confirmRemove) {
                    Text(
                        "Take it off the sheet for good? It comes off wherever it's stuck, too.",
                        style = PaperType.postscript.copy(fontStyle = FontStyle.Italic),
                        color = stationery.ink("beni"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, feeling.takeIf { it.isNotBlank() }, mark) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { if (confirmRemove) onRemove() else confirmRemove = true }) {
                    Text(if (confirmRemove) "Take it off" else "Remove", color = stationery.ink("beni"))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
