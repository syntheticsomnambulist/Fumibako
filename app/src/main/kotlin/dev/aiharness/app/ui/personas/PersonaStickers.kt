package dev.aiharness.app.ui.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.stickers.StickerArtDialog
import dev.aiharness.app.ui.stickers.StickerSheet
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PersonaStickers
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.stickers.StickerMaker
import dev.aiharness.core.stickers.StickerService
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A persona's own stickers: its sheet (tap one to rename it, say what it stands for, make it the
 * unread mark, or take it off), drawing a new one from how it looks, and its sticker switches.
 */
@Composable
fun PersonaStickersScreen(nav: NavController, personaId: String) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val persona by remember(personaId) { harness.personas.observeAll().map { all -> all.firstOrNull { it.id == personaId } } }
        .collectAsStateWithLifecycle(null)
    val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
    val settings by harness.settings.settings.collectAsStateWithLifecycle(null)
    val p = persona ?: return
    val ink = stationery.ink(p.ink)
    val dayInk = stationery.dayColors.ink(p.ink)
    val sheet = art.filter { it.ownerPersonaId == p.id }
    val mark = StickerService.unreadMark(p, sheet)
    var open by remember { mutableStateOf<StickerArt?>(null) }
    val update: (Persona) -> Unit = { changed -> scope.launch { harness.personas.upsert(changed.copy(updatedAt = System.currentTimeMillis())) } }
    val switches: (PersonaStickers) -> Unit = { update(p.copy(stickers = it)) }

    LazyColumn(
        Modifier.fillMaxSize().background(paper.hyoshi),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageBar(onBack = { nav.popBackStack() }) }
        item {
            Column {
                Text("${p.name}'s stickers", style = PaperType.screenTitle.copy(fontSize = 28.sp), color = paper.sumi)
                Text(
                    (if (sheet.size == 1) "1 sticker" else "${sheet.size} stickers").uppercase(),
                    style = PaperType.meta,
                    color = paper.kasure2,
                )
            }
        }
        item {
            if (sheet.isEmpty()) {
                Text(
                    "No stickers yet. Draw one below, or let ${p.name} draw some now and then.",
                    style = PaperType.postscript,
                    color = paper.usuzumi1,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerSheet(sheet, dayInk, p.name) { open = it }
                    Text(
                        "Tap one to rename it, say what feeling it stands for, or take it off the sheet. A sticker's feeling " +
                            "lets it stand in for ${p.name}'s stamps in the memory book." + (mark?.let { " Unread letters wear :${it.name}:." } ?: ""),
                        style = PaperType.postscript.copy(fontSize = 13.sp),
                        color = paper.kasure2,
                    )
                }
            }
        }
        item { DrawSticker(p, ready = settings?.imageGen?.ready == true, full = sheet.size >= StickerMaker.SHEET_SIZE, ink = ink) }
        item {
            Column(Modifier.paperCard().fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${p.name} and stickers", style = PaperType.entryTitle, color = paper.sumi)
                SwitchRow(
                    "${p.name} can draw new stickers",
                    p.stickers.draws,
                    { switches(p.stickers.copy(draws = it)) },
                    subtitle = "Now and then in a heartbeat. However many it draws, that's one of the heartbeat's activities.",
                    ink = ink,
                )
                SwitchRow("${p.name} can stick stickers", p.stickers.sticks, { switches(p.stickers.copy(sticks = it)) }, subtitle = "On your notes and ${p.name}'s own letters.", ink = ink)
                SwitchRow("I can peel off ${p.name}'s stickers", p.stickers.youCanPeel, { switches(p.stickers.copy(youCanPeel = it)) }, ink = ink)
                SwitchRow("${p.name} can peel off mine", p.stickers.canPeelYours, { switches(p.stickers.copy(canPeelYours = it)) }, ink = ink)
            }
        }
        item { Box(Modifier.navigationBarsPadding()) }
    }

    open?.let { a ->
        StickerArtDialog(
            art = a,
            ink = dayInk,
            isMark = mark?.id == a.id,
            markLabel = "Unread letters from ${p.name} wear this",
            onDismiss = { open = null },
            onSave = { name, feeling, wear ->
                open = null
                scope.launch {
                    val slug = StickerMaker.slug(name).ifEmpty { a.name }
                    val taken = (harness.stickers.art(p.id) + harness.stickers.art(null)).any { it.id != a.id && it.name == slug }
                    harness.stickers.upsertArt(a.copy(name = if (taken) a.name else slug, feeling = feeling?.trim()?.lowercase()))
                    when {
                        wear && p.unreadMark != a.id -> update(p.copy(unreadMark = a.id))
                        !wear && mark?.id == a.id -> update(p.copy(unreadMark = StickerService.SEAL))
                    }
                }
            },
            onRemove = {
                open = null
                scope.launch { harness.stickerMaker.remove(a) }
            },
        )
    }
}

/** Drawing a new sticker of the persona: what it shows, its name and feeling, then keep it or try again. */
@Composable
private fun DrawSticker(p: Persona, ready: Boolean, full: Boolean, ink: Color) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    var shows by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var feeling by remember { mutableStateOf("") }
    var drawing by remember { mutableStateOf(false) }
    var drawn by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val suggested = harness.stickerMaker.suggestName(p, shows)
    val draw = {
        drawn?.let { old -> scope.launch { harness.stickerMaker.discard(old) } }
        drawn = null
        problem = null
        drawing = true
        scope.launch {
            runCatching { harness.stickerMaker.draw(harness.settings.current().imageGen, harness.personas.get(p.id) ?: p, shows.trim()) }
                .onSuccess { drawn = it }
                .onFailure { problem = it.message ?: "Couldn't draw it." }
            drawing = false
        }
    }

    Column(Modifier.paperCard().fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Draw a new one", style = PaperType.entryTitle, color = paper.sumi)
        when {
            !ready -> Text("To draw stickers, add an image profile in Settings, Pictures.", style = PaperType.postscript, color = paper.usuzumi1)
            full -> Text("${p.name}'s sheet is full (${StickerMaker.SHEET_SIZE}). Take one off to draw another.", style = PaperType.postscript, color = paper.usuzumi1)
            else -> {
                IndexCardField("What it shows", shows, { shows = it }, placeholder = "sleepy, hugging a pillow", ink = ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IndexCardField("Name", name, { name = it }, Modifier.weight(1f), placeholder = ":$suggested:", singleLine = true, ink = ink)
                    IndexCardField("Stands for", feeling, { feeling = it }, Modifier.weight(1f), placeholder = "sleepy", singleLine = true, ink = ink)
                }
                Text(
                    "Drawn with how ${p.name} looks, what ${p.name} is wearing and ${p.name}'s drawing style, on a plain background, then cut out.",
                    style = PaperType.postscript.copy(fontSize = 13.sp),
                    color = paper.kasure2,
                )
                val result = drawn
                if (result == null) {
                    PaperButton(if (drawing) "Drawing…" else "Draw", Modifier.fillMaxWidth(), ink = ink, enabled = !drawing && shows.isNotBlank()) { draw() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .graphicsLayer { rotationZ = -2f }
                                .paperCard(color = lerp(paper.kami, Color.White, 0.6f))
                                .padding(8.dp),
                        ) {
                            BlobImage(result, Modifier.size(104.dp), contentScale = ContentScale.Fit, maxSize = 512)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperButton("Put it on the sheet", Modifier.fillMaxWidth(), ink = ink) {
                                val ref = result
                                drawn = null
                                scope.launch {
                                    harness.stickerMaker.keep(ref, p.id, name.ifBlank { suggested }, feeling.takeIf { it.isNotBlank() }, prompt = shows.trim())
                                    shows = ""; name = ""; feeling = ""
                                }
                            }
                            PaperButton(if (drawing) "Drawing…" else "Draw again", Modifier.fillMaxWidth(), enabled = !drawing) { draw() }
                        }
                    }
                }
                problem?.let { Text(it, style = PaperType.postscript, color = stationery.ink("beni")) }
            }
        }
    }
}
