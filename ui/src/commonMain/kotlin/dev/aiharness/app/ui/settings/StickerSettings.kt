package dev.aiharness.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.StepperRow
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.personas.PageBar
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.stickers.PhotoCutter
import dev.aiharness.app.ui.stickers.StickerArtDialog
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.app.ui.stickers.StickerSheet
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.parseHex
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.OutlineWidth
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerSettings
import dev.aiharness.core.model.StickerSize
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.stickers.StickerMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.aiharness.app.platform.PickKind
import dev.aiharness.app.platform.rememberPicker
import dev.aiharness.app.platform.deviceNoun

/** Outline colours to choose from: three colours, the paper, or whoever stuck it's ink. */
private val Outlines = listOf(
    "White" to "#FFFFFF",
    "Cream" to "#FFF4E0",
    "Dark" to "#3B2F27",
    "Paper" to ThemeSettings.OUTLINE_PAPER,
    "Their ink" to ThemeSettings.OUTLINE_INK,
)

// --- Stickers ------------------------------------------------------------------------------------

internal fun SettingsCards.stickersPage(nav: NavController, s: AppSettings, update: Update) {
    fun stickers(transform: (StickerSettings) -> StickerSettings) = update { it.copy(stickers = transform(it.stickers)) }
    card { StickerOutline(s, update) }
    card {
        CardTitle("New stickers")
        Text("LAND AT", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(StickerSize.entries.map { it to it.label }, s.stickers.size, { size -> stickers { it.copy(size = size) } })
        StepperRow("Most on one letter", s.stickers.maxPerLetter, { n -> stickers { it.copy(maxPerLetter = n) } }, 1..12)
        Hint("Yours and the persona's together. Any sticker can be resized after.")
    }
    card {
        CardTitle("Shared stickers")
        Text(
            "Stickers, stamps and decor every persona can use. Each persona's own stickers, and who can peel off whose, are on its page.",
            style = PaperType.prose,
            color = paper.usuzumi1,
        )
        PaperButton("Open the library", Modifier.fillMaxWidth()) { nav.navigate(Routes.SHARED_STICKERS) }
    }
}

/** The outline: its colour and width, with a strip of stickers to see it on. */
@Composable
private fun StickerOutline(s: AppSettings, update: Update) {
    val theme = s.theme
    Text("Outline", style = PaperType.entryTitle, color = paper.sumi)
    // A sample strip, using the settings being chosen.
    val sample = listOf(StickerRef.Emoji("🍵", "tea"), StickerRef.Emoji("🌸", "cherry_blossom"), StickerRef.Emoji("🎉", "tada"))
    androidx.compose.runtime.CompositionLocalProvider(LocalStickerSettings provides s.stickers) {
        Row(
            Modifier.fillMaxWidth().clip(PaperCorner).background(paper.hyoshi).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            sample.forEachIndexed { i, ref ->
                StickerPicture(ref, stationery.dayColors.ink(if (i == 1) "sakura" else "sora"), Modifier.size((44 * StickerBaker.BOX).dp).graphicsLayer { rotationZ = (i - 1) * 8f })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Outlines.forEach { (label, value) ->
            val on = theme.outlineColor.equals(value, ignoreCase = true)
            Column(
                Modifier
                    .weight(1f)
                    .clip(PaperCorner)
                    .selectable(on, role = Role.RadioButton) { update { it.copy(theme = it.theme.copy(outlineColor = value)) } }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fill: Brush = when (value) {
                    ThemeSettings.OUTLINE_PAPER -> Brush.linearGradient(listOf(stationery.dayColors.kami, stationery.dayColors.kami))
                    ThemeSettings.OUTLINE_INK -> Brush.linearGradient(listOf(stationery.ink("sakura"), stationery.ink("sakura"), stationery.ink("sora"), stationery.ink("sora")))
                    else -> Brush.linearGradient(listOf(parseHex(value) ?: Color.White, parseHex(value) ?: Color.White))
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .border(if (on) 2.dp else 1.dp, if (on) paper.sumi else paper.nijimi1, PaperCorner)
                        .clip(PaperCorner)
                        .background(fill),
                )
                Text(label, style = PaperType.meta.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = paper.usuzumi1, maxLines = 1)
            }
        }
    }
    PaperChoice(OutlineWidth.entries.map { it to it.label }, s.stickers.outlineWidth, { width -> update { it.copy(stickers = it.stickers.copy(outlineWidth = width)) } })
    Hint("The outline color also frames polaroids, unless they're set to paper (Look).")
}

// --- The shared library ----------------------------------------------------------------------------

/** Stickers, stamps and decor every persona can use: add from a picture or draw one; tap one to rename or remove it. */
@Composable
fun SharedStickersScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
    val shared = art.filter { it.ownerPersonaId == null }
    val ink = stationery.you
    val dayInk = stationery.dayColors.ink("sora")
    var open by remember { mutableStateOf<StickerArt?>(null) }

    val pageScroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().background(paper.hyoshi).scrollbar(pageScroll),
        state = pageScroll,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageBar(onBack = { nav.popBackStack() }) }
        item {
            Column {
                Text("Shared stickers", style = PaperType.screenTitle.copy(fontSize = 28.sp), color = paper.sumi)
                Text("EVERY PERSONA CAN USE THESE", style = PaperType.meta, color = paper.kasure2)
            }
        }
        if (shared.isEmpty()) {
            item { Text("Nothing here yet. Add a sticker from a picture, or draw one.", style = PaperType.quiet, color = paper.usuzumi1) }
        }
        listOf(StickerArtKind.STICKER to "Stickers", StickerArtKind.STAMP to "Stamps", StickerArtKind.DECOR to "Decor").forEach { (kind, label) ->
            val these = shared.filter { it.kind == kind }
            if (these.isNotEmpty()) item(key = kind.name) { StickerSheet(these, dayInk, label, columns = 4) { open = it } }
        }
        item { AddShared(ink) }
        item { Box(Modifier.navigationBarsPadding()) }
    }

    open?.let { a ->
        StickerArtDialog(
            art = a,
            ink = dayInk,
            isMark = false,
            markLabel = null,
            onDismiss = { open = null },
            onSave = { name, feeling, _ ->
                open = null
                scope.launch {
                    val slug = StickerMaker.slug(name).ifEmpty { a.name }
                    val taken = harness.stickers.art(null).any { it.id != a.id && it.name == slug }
                    harness.stickers.upsertArt(a.copy(name = if (taken) a.name else slug, feeling = feeling?.trim()?.lowercase()))
                }
            },
            onRemove = {
                open = null
                scope.launch { harness.stickerMaker.remove(a) }
            },
        )
    }
}

/** Adding to the library: a picture with its subject cut out, or a drawing; then name it and say what kind it is. */
@Composable
private fun AddShared(ink: Color) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val settings by harness.settings.settings.collectAsStateWithLifecycle(null)
    var shows by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StickerArtKind.STICKER) }
    var working by remember { mutableStateOf<String?>(null) }
    var made by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val discard = { made?.let { old -> scope.launch { harness.stickerMaker.discard(old) } }; made = null }
    val pick = rememberPicker(PickKind.PHOTO) { picked ->
        val uri = picked.firstOrNull() ?: return@rememberPicker
        discard()
        note = null
        working = "Cutting it out…"
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { container.media.read(uri) }
            val cut = bytes?.let { PhotoCutter.cutOut(it) }
            if (cut == null) {
                note = "Couldn't read that picture."
            } else {
                made = harness.blobs.put(cut.png, "png")
                if (!cut.bySegmentation) note = "Cut out by its plain background (the $deviceNoun's cutout wasn't available)."
            }
            working = null
        }
    }
    val draw = {
        discard()
        note = null
        working = "Drawing…"
        scope.launch {
            runCatching { harness.stickerMaker.draw(harness.settings.current().imageGen, null, shows.trim()) }
                .onSuccess { made = it }
                .onFailure { note = it.message ?: "Couldn't draw it." }
            working = null
        }
    }

    Column(Modifier.paperCard().fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add", style = PaperType.entryTitle, color = paper.sumi)
        val result = made
        if (result == null) {
            PaperButton(working ?: "From a picture", Modifier.fillMaxWidth(), ink = ink, enabled = working == null) {
                pick()
            }
            Text("Its subject is cut out and outlined.", style = PaperType.quiet.copy(fontSize = 13.sp), color = paper.kasure2)
            if (settings?.imageGen?.ready == true) {
                IndexCardField("Or draw one: what it shows", shows, { shows = it }, placeholder = "a pressed maple leaf", ink = ink)
                PaperButton("Draw one", Modifier.fillMaxWidth(), enabled = working == null && shows.isNotBlank()) { draw() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.graphicsLayer { rotationZ = -2f }.paperCard(color = lerp(paper.kami, Color.White, 0.6f)).padding(8.dp)) {
                    BlobImage(result, Modifier.size(104.dp), contentScale = ContentScale.Fit, maxSize = 512)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IndexCardField("Name", name, { name = it }, placeholder = ":${StickerMaker.slug(shows).ifEmpty { "sticker" }}:", singleLine = true, ink = ink)
                }
            }
            PaperChoice(listOf(StickerArtKind.STICKER to "Sticker", StickerArtKind.STAMP to "Stamp", StickerArtKind.DECOR to "Decor"), kind, { kind = it })
            Text(
                when (kind) {
                    StickerArtKind.STICKER -> "Die-cut, with an outline."
                    StickerArtKind.STAMP -> "Inked onto the paper, no outline."
                    StickerArtKind.DECOR -> "Tape, leaves and the like. Name tape \"tape-…\" and it goes on without an outline."
                },
                style = PaperType.quiet.copy(fontSize = 13.sp),
                color = paper.kasure2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Keep it", Modifier.weight(1f), ink = ink) {
                    val ref = result
                    made = null
                    scope.launch {
                        harness.stickerMaker.keep(ref, null, name.ifBlank { shows.ifBlank { "sticker" } }, prompt = shows.trim().ifEmpty { null }, kind = kind)
                        name = ""; shows = ""; kind = StickerArtKind.STICKER
                    }
                }
                PaperButton("Not this one", Modifier.weight(1f)) { discard() }
            }
        }
        note?.let { Text(it, style = PaperType.quiet, color = paper.usuzumi1) }
    }
}
