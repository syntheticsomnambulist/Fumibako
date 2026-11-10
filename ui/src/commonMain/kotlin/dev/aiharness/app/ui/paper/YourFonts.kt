package dev.aiharness.app.ui.paper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.platform.PickKind
import dev.aiharness.app.platform.fontFromFile
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.rememberPicker
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.rememberHand
import dev.aiharness.core.fonts.FontFiles
import dev.aiharness.core.model.Hands
import dev.aiharness.core.model.YourFont
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The sample every hand writes, in the Hand board's words. */
internal const val HAND_SAMPLE = "P.S. the window cat 窓辺の猫"

/** A font file read and loaded, waiting on the sheet to be added. */
private class Added(val bytes: ByteArray, val ext: String, val fileName: String, val info: FontFiles.Info, val family: FontFamily)

/**
 * Your fonts in a hand picker: each a line of P.S. in its face, then "+ Add a font file…" (a .ttf
 * or .otf, maybe your own handwriting made into a font), for every persona and each of yours.
 */
@Composable
internal fun YourFontsInPicker(selected: String?, ink: Color, onSelect: (String?) -> Unit) {
    val container = LocalAppContainer.current
    val settings by remember { container.harness.settings.settings }.collectAsState(null)
    val fonts = settings?.fonts.orEmpty()
    var adding by remember { mutableStateOf<Added?>(null) }
    val outside = rememberOutside()
    val scope = rememberCoroutineScope()
    val pick = rememberPicker(PickKind.FILE) { picked ->
        val item = picked.firstOrNull() ?: return@rememberPicker
        scope.launch {
            val name = container.media.nameOf(item) ?: "font.ttf"
            val result = withContext(Dispatchers.IO) {
                val file = container.transfers.fresh(name)
                try {
                    if (!container.media.copy(item, file)) return@withContext "That file couldn't be read."
                    if (file.length() > FontFiles.MOST_BYTES) return@withContext "That font is larger than 20 MB."
                    val bytes = file.readBytes()
                    when (val read = FontFiles.read(bytes)) {
                        is FontFiles.Read.Not -> read.why
                        is FontFiles.Read.Font -> fontFromFile(file)?.let { Added(bytes, name.substringAfterLast('.', "ttf").lowercase(), name, read.info, it) }
                            ?: "This ${dev.aiharness.app.platform.deviceNoun} can't load that font."
                    }
                } finally {
                    file.delete()
                }
            }
            onMain { if (result is Added) adding = result else outside.toast(result as String) }
        }
    }
    if (fonts.isNotEmpty()) {
        Text("YOUR FONTS", style = PaperType.label, color = paper.kasure2, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        fonts.forEach { font ->
            val name = Hands.ofYours(font)
            HandRow(name == selected, ink, rememberHand(name).family, font.scale, font.name + " · " + listOfNotNull(font.fileName, if (font.japanese) null else "Latin only").joinToString(" · ")) { onSelect(name) }
        }
    }
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .border(1.5.dp, paper.nijimi1, PaperCorner)
            .selectable(selected = false, role = Role.Button) { pick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text("+ Add a font file…", style = PaperType.label.copy(fontSize = 12.5.sp, letterSpacing = 0.sp), color = ink) }
    Text(
        "A .ttf or .otf, maybe your own handwriting made into a font. Fonts you add are there for every persona, yours too.",
        style = PaperType.noteBody.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
        color = paper.usuzumi0,
        modifier = Modifier.padding(top = 4.dp),
    )
    adding?.let { a ->
        AddFontSheet(a, ink, onCancel = { adding = null }) { name, scale ->
            adding = null
            scope.launch {
                val ref = container.harness.blobs.put(a.bytes, a.ext)
                val font = YourFont(
                    id = newId(), name = name, ref = ref, scale = scale, latin = a.info.latin, japanese = a.info.japanese,
                    fileName = a.fileName, sizeBytes = a.bytes.size.toLong(), addedAt = System.currentTimeMillis(),
                )
                container.harness.settings.update { it.copy(fonts = it.fonts + font) }
                onMain { onSelect(Hands.ofYours(font)) }
            }
        }
    }
}

/** One hand to choose: its sample in its face, and under it its name. */
@Composable
internal fun HandRow(on: Boolean, ink: Color, family: FontFamily, scale: Float, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = on, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp).border(1.5.dp, ink, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(8.dp).background(ink, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(HAND_SAMPLE, style = TextStyle(fontFamily = family, fontSize = (16 * scale).sp, lineHeight = 22.sp), color = paper.sumi)
            Text(label, style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2)
        }
    }
}

/** Adding a font: its file, a name, a sample with a line of the letters under it, what it covers, and its size. */
@Composable
private fun AddFontSheet(a: Added, ink: Color, onCancel: () -> Unit, onAdd: (name: String, scale: Float) -> Unit) {
    var name by remember { mutableStateOf(a.info.name) }
    var scale by remember { mutableStateOf(a.info.scale) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add a handwriting font", style = PaperType.entryTitle, color = paper.sumi) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${a.fileName} · ${(a.bytes.size + 1023) / 1024} KB", style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2)
                IndexCardField("Its name", name, { name = it }, ink = ink, style = PaperType.noteBody, singleLine = true)
                Column(Modifier.fillMaxWidth().background(paper.kami, PaperCorner).border(1.dp, paper.nijimi0, PaperCorner).padding(10.dp)) {
                    Text(HAND_SAMPLE, style = TextStyle(fontFamily = a.family, fontSize = (16 * scale).sp, lineHeight = 22.sp), color = paper.sumi)
                    Text("I drew you the window cat while I waited.", style = PaperType.letterBody.copy(fontSize = 15.sp), color = paper.usuzumi1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(if (a.info.latin) "Latin ✓" else "No Latin")
                    Chip(if (a.info.japanese) "Japanese ✓" else "No Japanese: that comes in the ${dev.aiharness.app.platform.deviceNoun}'s own")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pct = Math.round((scale - 1f) * 100)
                    Text("Size · matched to the letters (${if (pct >= 0) "+" else ""}$pct%)", style = PaperType.noteBody.copy(fontSize = 12.5.sp), color = paper.sumi, modifier = Modifier.weight(1f))
                    PaperButton("−") { scale = (scale - 0.04f).coerceAtLeast(0.7f) }
                    PaperButton("+") { scale = (scale + 0.04f).coerceAtMost(1.5f) }
                }
                Text(
                    "Add only fonts you're allowed to use. It stays on this ${dev.aiharness.app.platform.deviceNoun} and goes in your backups; it isn't sent anywhere, and character cards only name it.",
                    style = PaperType.noteBody.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = paper.usuzumi0,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name.ifBlank { a.info.name }, scale) }) { Text("Add the font") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = PaperType.meta.copy(fontSize = 11.5.sp, letterSpacing = 0.sp),
        color = paper.sumi,
        modifier = Modifier.border(1.dp, paper.nijimi1, PaperCorner).background(paper.hyoshi, PaperCorner).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Settings, Look: your fonts, to rename or remove; anyone writing in a removed one goes back to Yomogi. */
@Composable
internal fun YourFontsCard() {
    val container = LocalAppContainer.current
    val harness = container.harness
    val settings by remember { harness.settings.settings }.collectAsState(null)
    val fonts = settings?.fonts.orEmpty()
    val scope = rememberCoroutineScope()
    CardTitle("Your fonts", note = if (fonts.size == 1) "1 font" else "${fonts.size} fonts")
    if (fonts.isEmpty()) {
        Text("Fonts you add as a hand (on a persona's page, under Look) are listed here.", style = PaperType.noteBody.copy(fontSize = 12.5.sp), color = paper.usuzumi0)
    }
    fonts.forEach { font ->
        IndexCardField(font.name, font.name, { v -> scope.launch { harness.settings.update { s -> s.copy(fonts = s.fonts.map { if (it.id == font.id) it.copy(name = v) else it }) } } }, style = PaperType.noteBody, singleLine = true)
        InkLink("Remove ${font.name}", paper.usuzumi0) {
            scope.launch {
                val name = Hands.ofYours(font)
                harness.personas.getAll().filter { it.hand == name }.forEach { harness.personas.upsert(it.copy(hand = null, updatedAt = System.currentTimeMillis())) }
                harness.userPersonas.observeAll().first().filter { it.hand == name }.forEach { harness.userPersonas.upsert(it.copy(hand = null, updatedAt = System.currentTimeMillis())) }
                harness.settings.update { s -> s.copy(fonts = s.fonts.filterNot { it.id == font.id }) }
                harness.blobs.delete(font.ref)
            }
        }
    }
}
