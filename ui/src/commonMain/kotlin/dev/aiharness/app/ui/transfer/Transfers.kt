package dev.aiharness.app.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.aiharness.app.platform.PickKind
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.rememberPicker
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.transfer.BackupPeek
import dev.aiharness.core.transfer.CardFile
import dev.aiharness.core.transfer.CardRead
import dev.aiharness.core.transfer.CardReader
import dev.aiharness.core.transfer.Lorebook
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A file picked to open: a card for Import a card, or a backup for Open a backup. */
class Opened(val file: File, val name: String, val card: CardFile? = null, val peek: BackupPeek? = null)

/**
 * Where files being imported, exported and opened wait while their pages are open: a folder in the
 * cache, emptied when the app starts.
 */
class Transfers {
    val dir: File = File(System.getProperty("java.io.tmpdir"), "fumibako-transfer")

    /** The file the Import a card or Open a backup page is showing. */
    var opened by mutableStateOf<Opened?>(null)

    /** A file opened or shared into the app from outside it (another app, a file manager), to open here. */
    val incoming = kotlinx.coroutines.flow.MutableStateFlow<dev.aiharness.app.platform.Picked?>(null)

    /** A new file here, named after [name]. */
    fun fresh(name: String): File = File(dir.apply { mkdirs() }, newId().take(8) + "-" + name.replace(Regex("[/\\\\]"), "-"))

    fun clear() {
        opened = null
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Cards are pictures and text; anything bigger is a backup or something else. */
        const val LARGEST_CARD = 64L shl 20
    }
}

/**
 * Opening a file from the device ("Import a card…", "Open a file…"): a card goes to Import a card,
 * a backup to Open a backup, a lorebook to [onLorebook] (a persona's memory book) or else a note on
 * where it goes. What isn't any of these says so.
 */
@Composable
fun rememberOpenFile(nav: NavController, onLorebook: ((Lorebook) -> Unit)? = null): () -> Unit {
    val container = LocalAppContainer.current
    val outside = rememberOutside()
    val scope = rememberCoroutineScope()
    return rememberPicker(PickKind.FILE) { picked ->
        val item = picked.firstOrNull() ?: return@rememberPicker
        scope.launch { openPicked(container, item, nav, outside::toast, onLorebook) }
    }
}

/**
 * What [item] is, and its page opened: a card's Import a card, a backup's Open a backup, a
 * lorebook to [onLorebook] (or a note on where it goes), else [tell] says what it isn't.
 */
internal suspend fun openPicked(
    container: dev.aiharness.app.AppContainer,
    item: dev.aiharness.app.platform.Picked,
    nav: NavController,
    tell: (String) -> Unit,
    onLorebook: ((Lorebook) -> Unit)? = null,
) {
    val name = container.media.nameOf(item) ?: "file"
    val file = container.transfers.fresh(name)
    val opened: Any? = withContext(Dispatchers.IO) {
        if (!container.media.copy(item, file)) return@withContext "That file couldn't be read."
        val peek = container.harness.backups.peek(file)
        if (peek !is BackupPeek.NotABackup) return@withContext Opened(file, name, peek = peek)
        if (file.length() > Transfers.LARGEST_CARD) return@withContext "That isn't a character card or a Fumibako backup."
        when (val read = CardReader.read(file.readBytes())) {
            // A card picked for its lore (a memory book's "Add lore from a lorebook") gives its book.
            is CardRead.OfCard -> if (onLorebook != null) read.file.card.book ?: "That card has no lorebook." else Opened(file, name, card = read.file)
            is CardRead.OfLorebook -> read.book
            is CardRead.Not -> read.why
        }
    }
    if (opened !is Opened) file.delete()
    dev.aiharness.app.ui.onMain {
        when (opened) {
            is Opened -> {
                container.transfers.opened = opened
                nav.navigate(if (opened.card != null) Routes.IMPORT_CARD else Routes.OPEN_BACKUP)
            }
            is Lorebook -> if (onLorebook != null) onLorebook(opened) else tell("That's a lorebook: add it from a persona's memory book (⋯, Add lore from a lorebook).")
            is String -> tell(opened)
        }
    }
}

/** Files opened or shared into the app from outside go to their pages here. */
@Composable
internal fun IncomingFiles(nav: NavController) {
    val container = LocalAppContainer.current
    val outside = rememberOutside()
    androidx.compose.runtime.LaunchedEffect(container) {
        container.transfers.incoming.collect { item ->
            if (item != null) {
                container.transfers.incoming.value = null
                openPicked(container, item, nav, outside::toast)
            }
        }
    }
}

/** A transfer page: back, its title, then [content] (taped cards). */
@Composable
internal fun TransferPage(nav: NavController, title: String, backLabel: String, content: LazyListScope.() -> Unit) {
    val colors = paper
    Column(Modifier.fillMaxSize().background(colors.hyoshi)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .drawBehind { drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel, tint = colors.usuzumi0) }
            Text(title, style = PaperType.letterName.copy(fontSize = 22.sp, lineHeight = 24.sp), color = colors.sumi)
        }
        val scroll = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize().scrollbar(scroll),
            state = scroll,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/**
 * One thing that goes (or comes) in, with a tick: [label], a line of [detail], and [size] at the
 * end. [onChange] null means it always goes (a grey tick that can't be taken off).
 */
@Composable
internal fun TickRow(label: String, detail: String?, checked: Boolean, onChange: ((Boolean) -> Unit)?, size: String? = null, ink: Color = stationery.you) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onChange != null) Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TickBox(checked, null, ink = if (onChange == null) colors.kasure2 else ink)
        Column(Modifier.weight(1f)) {
            Text(label, style = PaperType.noteBody.copy(fontSize = 15.sp), color = colors.sumi)
            detail?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.usuzumi0) }
        }
        if (size != null && checked) Text(size, style = PaperType.meta.copy(letterSpacing = 0.sp), color = colors.kasure2)
    }
}

/** [TickRow] for other screens (a lorebook going into a memory book). */
@Composable
fun TickRowPublic(label: String, detail: String?, checked: Boolean, onChange: ((Boolean) -> Unit)?, ink: Color = stationery.you) =
    TickRow(label, detail, checked, onChange, null, ink)

/** A small note under a card's rows. */
@Composable
internal fun Note(text: String, color: Color = paper.usuzumi0) {
    Text(text, style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = color)
}

/** "2,200". */
internal fun counted(n: Int): String = String.format(Locale.US, "%,d", n)

/** "32 MB", "1.6 MB", "420 KB". */
internal fun sizeOf(bytes: Long): String = when {
    bytes >= 10L shl 20 -> "${(bytes + (1L shl 19)) shr 20} MB"
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    else -> "${((bytes + 1023) / 1024).coerceAtLeast(1)} KB"
}
