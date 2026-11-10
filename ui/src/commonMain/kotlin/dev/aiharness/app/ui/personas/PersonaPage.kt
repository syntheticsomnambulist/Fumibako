package dev.aiharness.app.ui.personas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.LocalBesideList
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.activityPhrase
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.letterTimeInSentence
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.InkPicker
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.paper.Polaroid
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapeLayout
import dev.aiharness.app.ui.paper.TapePicker
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.paper.UnreadSeal
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.cornerTape
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.settings.interval
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.util.newId
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.stickers.StickerService
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.words
import dev.aiharness.core.model.pronounsLabel
import dev.aiharness.core.model.pronounSets
import dev.aiharness.core.model.PronounMix
import kotlinx.coroutines.launch

/**
 * A persona's journal page. **Edit** turns each card into index-card fields in place; **Done**
 * keeps the changes. A new persona ([Routes.NEW]) opens straight into editing.
 */
@Composable
fun PersonaPageScreen(nav: NavController, personaId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val isNew = personaId == Routes.NEW
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var editing by rememberSaveable { mutableStateOf(isNew) }
    var draft by remember { mutableStateOf<Persona?>(null) }
    var original by remember { mutableStateOf<Persona?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(isNew) {
        if (isNew && draft == null) {
            val now = System.currentTimeMillis()
            draft = Persona(id = newId(), name = "", createdAt = now, updatedAt = now)
            original = draft
        }
    }
    val stored = personas?.firstOrNull { it.id == personaId }
    if (editing && draft == null && stored != null) {
        draft = stored
        original = stored
    }
    val p = (if (editing) draft else stored) ?: return
    val ink = stationery.ink(p.ink)
    val latest = conversations.filter { it.personaId == p.id && !it.archived }.maxByOrNull { it.updatedAt }
    val back = discardGuard(
        editing,
        changed = draft != original,
        onLeave = {
            if (isNew) {
                nav.popBackStack()
            } else {
                editing = false
                draft = null
            }
        },
        onBack = { nav.popBackStack() },
    )

    fun save() {
        val d = draft ?: return
        if (d.name.isBlank()) return
        val now = System.currentTimeMillis()
        // Its model changed here: its model row says since when, and what it was.
        val modelOf = { id: String? -> profiles.firstOrNull { it.id == (id ?: settings?.defaultModelProfileId) } }
        val before = original?.takeIf { !isNew }?.let { modelOf(it.defaultModelProfileId) }
        val modelChanged = before != null && before.id != modelOf(d.defaultModelProfileId)?.id
        scope.launch {
            harness.personas.upsert(
                d.copy(
                    name = d.name.trim(),
                    outfit = d.outfit?.trim()?.takeIf { it.isNotEmpty() },
                    stylePrompt = d.stylePrompt?.trim()?.takeIf { it.isNotEmpty() },
                    modelSince = if (modelChanged) now else d.modelSince,
                    previousModelName = if (modelChanged) before?.name else d.previousModelName,
                    updatedAt = now,
                ),
            )
            onMain {
                editing = false
                draft = null
                if (isNew) {
                    nav.popBackStack()
                    nav.navigate(Routes.personaPage(d.id))
                }
            }
        }
    }

    fun openLetters() {
        scope.launch {
            val conversation = latest ?: harness.createConversation(p.id)
            onMain { nav.navigate(Routes.chat(conversation.id)) }
        }
    }

    val pageScroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().scrollbar(pageScroll),
        state = pageScroll,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageBar(onBack = back, showBack = editing || !LocalBesideList.current) {
                if (editing) {
                    Text("EDITING", style = PaperType.meta, color = ink)
                    PaperButton("Done", ink = ink, enabled = p.name.isNotBlank()) { save() }
                } else {
                    if (!isNew) {
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            androidx.compose.material3.IconButton(onClick = { menu = true }) { androidx.compose.material3.Icon(dev.aiharness.app.ui.paper.StationeryIcons.InkDots, "More", tint = paper.usuzumi0) }
                            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text("Export ${p.name}…") }, onClick = { menu = false; nav.navigate(Routes.export(p.id)) })
                            }
                        }
                    }
                    PaperButton("Edit") {
                        draft = stored
                        original = stored
                        editing = true
                    }
                }
            }
        }
        item {
            Hero(
                p,
                latest?.mood,
                editing,
                model = profiles.firstOrNull { it.id == (p.defaultModelProfileId ?: settings?.defaultModelProfileId) }?.name,
                onChange = { draft = it },
            )
        }
        if (editing) {
            editCards(p, onChange = { draft = it }, profiles = profiles, nav = nav, onDelete = { confirmDelete = true }, isNew = isNew)
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton("Write a letter", Modifier.weight(1f), ink = ink) { openLetters() }
                    PaperButton("Memories", Modifier.weight(1f)) { nav.navigate(Routes.memory(p.id)) }
                }
            }
            viewCards(p, nav, settings?.heartbeat?.mode)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${p.name}?", style = PaperType.entryTitle) },
            text = { Text("This also deletes ${p.words(0).their} letters and memories.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        harness.conversations.getAll().filter { it.personaId == p.id }.forEach { harness.conversations.delete(it.id) }
                        harness.memory.all(p.id).forEach { harness.memory.delete(it.id) }
                        // Its sticker sheet, pictures and all (ones already stuck keep a placeholder).
                        harness.stickers.art(p.id).forEach { harness.stickerMaker.remove(it) }
                        harness.personas.delete(p.id)
                        harness.plannerEntries.deleteWrittenBy(p.id)
                        onMain { nav.popBackStack() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(p: Persona, mood: String?, editing: Boolean, model: String?, onChange: (Persona) -> Unit) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val ink = stationery.ink(p.ink)
    var menu by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Polaroid(
                p,
                Modifier
                    .width(128.dp)
                    .then(if (editing) Modifier.clickable(role = Role.Button, onClickLabel = "Change the picture") { menu = true } else Modifier),
                tiltDegrees = -2f,
                framePadding = 8.dp,
                bottomPadding = if (editing) 6.dp else 26.dp,
                cornerTaped = true,
                caption = if (editing) {
                    { Text(if (drawing) "DRAWING…" else "CHANGE", style = PaperType.meta, color = stationery.frameInk, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
                } else {
                    null
                },
            )
            val canDraw = settings?.imageGen?.ready == true && p.selfPrompt.isNotBlank()
            PictureMenu(
                open = menu,
                onDismiss = { menu = false },
                onPicked = { ref -> onChange(p.copy(avatarRef = ref)) },
                onRemove = p.avatarRef?.let { { onChange(p.copy(avatarRef = null)) } },
                onDraw = if (!canDraw) {
                    null
                } else {
                    {
                        drawing = true
                        problem = null
                        scope.launch {
                            try {
                                val image = harness.images.generate(
                                    settings = harness.settings.current().imageGen,
                                    prompt = "portrait, upper body, looking at viewer, simple background",
                                    persona = p,
                                    orientation = "square",
                                )
                                onChange(p.copy(avatarRef = image.ref))
                            } catch (e: Exception) {
                                problem = e.message
                            } finally {
                                drawing = false
                            }
                        }
                    }
                },
                drawBusy = drawing,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) {
                IndexCardField("Name", p.name, { v -> onChange(p.copy(name = v)) }, ink = ink, style = PaperType.heroName.copy(fontSize = 28.sp, lineHeight = 36.sp), singleLine = true, placeholder = "Their name")
                problem?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.sp), color = stationery.ink("beni")) }
            } else {
                Text(p.name, style = PaperType.heroName, color = paper.sumi)
                MoodWord(mood, ink, style = PaperType.mood.copy(fontSize = 15.sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InkChip(p.ink, ink)
                    TapeChip(p.tapePattern, ink)
                    p.pronounsLabel?.let { Chip { Text(it, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1) } }
                    model?.let { Chip { Text(it, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1) } }
                }
            }
        }
    }
}

private fun LazyListScope.viewCards(p: Persona, nav: NavController, mode: HeartbeatMode?) {
    val layouts = tapeLayouts("page-${p.id}", 4)
    if (p.description.isNotBlank() || p.personality.isNotBlank() || p.currentOutfit.isNotBlank() || p.fromCard != null || p.moodPortraits.isNotEmpty()) {
        item {
            PageCard(p, layouts[0], "about") {
                CardTitle("About")
                listOf(p.description, p.personality).filter { it.isNotBlank() }.forEach { Text(it.trim(), style = PaperType.prose, color = paper.sumi) }
                p.currentOutfit.takeIf { it.isNotBlank() }?.let { outfit ->
                    Text("Wearing: $outfit", style = PaperType.prose.copy(fontStyle = FontStyle.Italic, fontSize = 13.5.sp), color = paper.usuzumi1)
                }
                if (p.moodPortraits.isNotEmpty()) MoodPortraits(p)
                p.fromCard?.let { card ->
                    val by = listOfNotNull(card.creator.takeIf { it.isNotBlank() }?.let { "by $it" }, card.version.takeIf { it.isNotBlank() }?.let { "version $it" })
                    Text(
                        (listOf("From a character card") + by).joinToString(" · "),
                        style = PaperType.meta.copy(letterSpacing = 0.sp),
                        color = paper.kasure2,
                    )
                    if (card.creatorNotes.isNotBlank()) {
                        Text("From the card's creator: " + card.creatorNotes.trim(), style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp), color = paper.usuzumi1)
                        Text("Shown to you only; never sent to ${p.words.them}.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
                    }
                }
            }
        }
    }
    item {
        val harness = LocalAppContainer.current.harness
        val memories by remember(p.id) { harness.memories.observeForPersona(p.id) }.collectAsStateWithLifecycle(emptyList())
        val kept = memories.filter { !it.isReplaced }
        PageCard(p, layouts[1], "memory") {
            CardTitle("Memory book", note = if (kept.size == 1) "1 memory" else "${kept.size} memories", trailing = { InkLink("See all", stationery.ink(p.ink)) { nav.navigate(Routes.memory(p.id)) } })
            MemoryBookPreview(kept, stationery.ink(p.ink)) { id -> nav.navigate(Routes.memoryCard(p.id, id)) }
        }
    }
    item {
        val harness = LocalAppContainer.current.harness
        val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
        val sheet = art.filter { it.ownerPersonaId == p.id }
        val mark = StickerService.unreadMark(p, sheet)
        val dayInk = stationery.dayColors.ink(p.ink)
        PageCard(p, layouts[2], "stickers") {
            CardTitle(
                "Stickers",
                note = if (sheet.size == 1) "1 sticker" else "${sheet.size} stickers",
                trailing = { InkLink(if (sheet.isEmpty()) "Draw some" else "Open sheet", stationery.ink(p.ink)) { nav.navigate(Routes.personaStickers(p.id)) } },
            )
            if (sheet.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    sheet.take(6).forEachIndexed { i, a ->
                        StickerPicture(
                            StickerRef.Art(a.id),
                            dayInk,
                            Modifier.size((44 * StickerBaker.BOX).dp).graphicsLayer { rotationZ = if (i % 2 == 0) -6f else 5f },
                            name = ":${a.name}: sticker",
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mark == null) UnreadSeal(p.ink, size = 34.dp)
                Text(
                    mark?.let { "Unread letters from ${p.name} wear :${it.name}:." } ?: "Unread letters wear the 新 seal in ${p.name}'s ink" +
                        (if (sheet.isEmpty()) ", until ${p.name} has a sticker for hello." else "."),
                    style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = paper.usuzumi1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    item(key = "letter-box") { LetterBoxCard(p) }
    item(key = "on-its-way") { OnItsWayCard(p) }
    item {
        val harness = LocalAppContainer.current.harness
        val scope = rememberCoroutineScope()
        val since = remember { System.currentTimeMillis() - 14 * 86_400_000L }
        val activity by remember(p.id) { harness.activity.observeSince(since) }.collectAsStateWithLifecycle(emptyList())
        val last = activity.lastOrNull { it.personaId == p.id && it.kind.isBeat }
        val own = p.ownHeartbeat
        val status = when {
            mode == HeartbeatMode.OFF -> "Heartbeats are off for everyone (Settings)."
            mode == HeartbeatMode.TIME_AWARENESS -> "Time only: ${p.name} knows the time, but doesn't wake between letters (Settings)."
            !p.heartbeatEnabled -> "${p.name} doesn't wake between letters."
            own != null -> "Wakes every ${interval(own.intervalMinutes)}, on a rhythm of ${p.words(3).their} own."
            else -> "Wakes now and then to reflect, tend memories, and maybe write to you."
        }
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[3], "heartbeat") {
            CardTitle("Heartbeat", trailing = { InkLink("Settings", ink) { nav.navigate(Routes.personaHeartbeat(p.id)) } })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOfNotNull(status, last?.let { "Last heartbeat: ${activityPhrase(it.kind, it.count, it.note)} ${letterTimeInSentence(it.createdAt)}." }).joinToString(" "),
                    style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = paper.usuzumi1,
                    modifier = Modifier.weight(1f),
                )
                TickBox(p.heartbeatEnabled, { on -> scope.launch { harness.personas.upsert(p.copy(heartbeatEnabled = on)) } }, ink = ink)
            }
        }
    }
}

private fun LazyListScope.editCards(
    p: Persona,
    onChange: (Persona) -> Unit,
    profiles: List<ModelProfile>,
    nav: NavController,
    onDelete: () -> Unit,
    isNew: Boolean,
) {
    val layouts = tapeLayouts("edit-${p.id}", 5)
    val named = p.name.isNotBlank()
    item {
        PageCard(p, layouts[0], "look") {
            CardTitle("Look")
            Text("INK", style = PaperType.meta, color = paper.kasure2)
            InkPicker(p.ink, { v -> onChange(p.copy(ink = v)) })
            Text("TAPE", style = PaperType.meta, color = paper.kasure2)
            TapePicker(p.ink, p.tapePattern, { v -> onChange(p.copy(tapePattern = v)) })
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[1], "about") {
            CardTitle(if (named) "Who ${p.name} is" else "Who they are")
            PronounPicker(p, ink, onChange)
            val w = p.words(1)
            IndexCardField(
                "Description",
                p.description,
                { v -> onChange(p.copy(description = v)) },
                ink = ink,
                style = PaperType.prose,
                minLines = 3,
                placeholder = if (named && p.pronouns != null) "Who ${w.they} ${w.be}, in a few lines" else "Who they are, in a few lines",
            )
            IndexCardField("Personality", p.personality, { v -> onChange(p.copy(personality = v)) }, ink = ink, style = PaperType.prose, minLines = 2)
            IndexCardField("Scenario", p.scenario, { v -> onChange(p.copy(scenario = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "Where and when your letters take place (optional)")
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[2], "writes") {
            CardTitle(if (named) "How ${p.name} writes" else "How they write")
            IndexCardField("First letter", p.greeting, { v -> onChange(p.copy(greeting = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "Hi {{user}}! … (optional)")
            p.alternateGreetings.forEachIndexed { i, other ->
                IndexCardField("Another first letter", other, { v -> onChange(p.copy(alternateGreetings = p.alternateGreetings.toMutableList().also { it[i] = v })) }, ink = ink, style = PaperType.prose, minLines = 2)
                InkLink("Take this one out", ink) { onChange(p.copy(alternateGreetings = p.alternateGreetings.filterIndexed { j, _ -> j != i })) }
            }
            if (p.greeting.isNotBlank()) {
                InkLink("Add another first letter", ink) { onChange(p.copy(alternateGreetings = p.alternateGreetings + "")) }
                if (p.alternateGreetings.isNotEmpty()) {
                    Text("A new thread starts with the first; you can read another instead before you write.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
                }
            }
            IndexCardField("Example letters", p.exampleDialogue, { v -> onChange(p.copy(exampleDialogue = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "A few lines in ${if (named && p.pronouns != null) p.words(2).their else "their"} voice (optional)")
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[3], "pictures") {
            CardTitle("In pictures")
            val named = p.name.ifBlank { "this persona" }
            IndexCardField("How $named looks", p.selfPrompt, { v -> onChange(p.copy(selfPrompt = v)) }, ink = ink, style = PaperType.noteBody, minLines = 2, placeholder = "1girl, short black hair, glasses")
            Text("The body and the traits that never change.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
            IndexCardField("Usually wears", p.defaultOutfit, { v -> onChange(p.copy(defaultOutfit = v)) }, ink = ink, style = PaperType.noteBody, placeholder = "hoodie, jeans")
            val wearing = p.outfit
            if (wearing != null) {
                IndexCardField("Wearing now", wearing, { v -> onChange(p.copy(outfit = v)) }, ink = ink, style = PaperType.noteBody, placeholder = "yellow raincoat, rain boots")
                InkLink("Back to the usual clothes", ink) { onChange(p.copy(outfit = null)) }
            } else {
                InkLink("Change clothes for now", ink) { onChange(p.copy(outfit = "")) }
            }
            Text("$named can change clothes too; the change lasts until the next one.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
            IndexCardField("Drawing style", p.stylePrompt.orEmpty(), { v -> onChange(p.copy(stylePrompt = v)) }, ink = ink, style = PaperType.noteBody, placeholder = "Blank: the style in Settings, Pictures")
            Text("Artist and style tags for $named's pictures.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
            val harness = LocalAppContainer.current.harness
            val appSettings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
            val imageProfiles = appSettings?.imageGen?.profiles.orEmpty()
            if (imageProfiles.size > 1) {
                PaperSelect("Draws with", imageProfiles, imageProfiles.firstOrNull { it.id == p.imageProfileId }, { it.name }, { v -> onChange(p.copy(imageProfileId = v?.id)) }, noneLabel = "The default image profile")
            }
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[4], "behavior") {
            CardTitle("Behavior")
            PaperSelect("Model", profiles, profiles.firstOrNull { it.id == p.defaultModelProfileId }, { it.name }, { m -> onChange(p.copy(defaultModelProfileId = m?.id)) }, noneLabel = "App default")
            p.modelSince?.let { since ->
                Text(modelSinceLine(since, p.previousModelName), style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
            }
            val w = p.words
            SwitchRow(
                "Tell ${w.them} when ${w.their} model changes",
                p.tellModelChanges,
                { v -> onChange(p.copy(tellModelChanges = v)) },
                subtitle = if (p.tellModelChanges) "A line in ${w.their} next letter, and a notice in your thread where it changed."
                else "${w.theyCapital} won't be told; the notice still shows in your thread.",
                ink = ink,
            )
            SwitchRow("Heartbeat", p.heartbeatEnabled, { v -> onChange(p.copy(heartbeatEnabled = v)) }, subtitle = "Wakes now and then to reflect, and may write to you.")
            if (!isNew) InkLink("Heartbeat settings", ink) { nav.navigate(Routes.personaHeartbeat(p.id)) }
            IndexCardField(
                "Custom system prompt (advanced)",
                p.systemPromptOverride.orEmpty(),
                { v -> onChange(p.copy(systemPromptOverride = v.ifBlank { null })) },
                ink = ink,
                style = PaperType.noteBody,
                minLines = 2,
                placeholder = "Replaces the generated character section; memory and context notes are still added",
            )
            IndexCardField(
                "After each letter's context (advanced)",
                p.postHistoryInstructions.orEmpty(),
                { v -> onChange(p.copy(postHistoryInstructions = v.ifBlank { null })) },
                ink = ink,
                style = PaperType.noteBody,
                minLines = 2,
                placeholder = "Goes last in each turn's context, like a card's post-history instructions",
            )
        }
    }
    if (!isNew) {
        item { PaperButton("Delete ${p.name}, with letters and memories", Modifier.fillMaxWidth(), onClick = onDelete) }
    }
}

/**
 * On its way: their lines for the 〒 postmark while a reply is in the post. You can take one out,
 * change one or add your own (yours stay as you wrote them), or let them write their own in a
 * short turn in their voice.
 */
@Composable
private fun OnItsWayCard(p: Persona) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val ink = stationery.ink(p.ink)
    val w = p.words
    val lines = p.wayLines.ifEmpty { dev.aiharness.core.letters.WayLines.plain(p).map { dev.aiharness.core.model.WayLine(it) } }
    var editing by remember(p.id) { mutableStateOf<Int?>(null) }
    var draft by remember(p.id) { mutableStateOf("") }
    var asking by remember(p.id) { mutableStateOf(false) }
    var problem by remember(p.id) { mutableStateOf<String?>(null) }
    fun save(next: List<dev.aiharness.core.model.WayLine>) {
        scope.launch {
            val current = harness.personas.get(p.id) ?: p
            harness.personas.upsert(current.copy(wayLines = next.filter { it.text.isNotBlank() }, updatedAt = System.currentTimeMillis()))
        }
    }
    val layout = remember(p.id) { tapeLayouts("way-${p.id}", 1)[0] }
    PageCard(p, layout, "on-its-way") {
        CardTitle("On its way", note = p.wayLinesWrittenAt?.let { "${w.their} own, ${dev.aiharness.app.ui.letterTime(it)}" })
        Text(
            "While ${p.name}'s reply is in the post, its postmark carries one of these.",
            style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = paper.usuzumi1,
        )
        lines.forEachIndexed { i, line ->
            if (editing == i) {
                IndexCardField("${p.name}'s reply is on its way ·", draft, { draft = it }, ink = ink, style = PaperType.noteBody, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkLink("Keep it", ink) {
                        val text = draft.trim()
                        val next = lines.toMutableList()
                        if (i < next.size) next[i] = dev.aiharness.core.model.WayLine(text, yours = true) else next += dev.aiharness.core.model.WayLine(text, yours = true)
                        save(next)
                        editing = null
                    }
                    InkLink("Cancel", paper.usuzumi0) { editing = null }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("${p.name}'s reply is on its way · ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = ink))
                            append(line.text)
                            pop()
                        },
                        style = PaperType.postscript,
                        color = paper.usuzumi1,
                        modifier = Modifier.weight(1f).clickable(onClickLabel = "Change this line") { draft = line.text; editing = i },
                    )
                    if (line.yours) Text("yours", style = PaperType.meta.copy(letterSpacing = 0.sp), color = ink)
                    if (lines.size > 1) InkLink("Take out", paper.usuzumi0) { save(lines.filterIndexed { j, _ -> j != i }) }
                }
            }
        }
        if (editing == lines.size) {
            IndexCardField("${p.name}'s reply is on its way ·", draft, { draft = it }, ink = ink, style = PaperType.noteBody, singleLine = true, placeholder = "${w.they}${w.verb("'s", "'re")} …")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkLink("Add it", ink) {
                    if (draft.isNotBlank()) save(lines + dev.aiharness.core.model.WayLine(draft.trim(), yours = true))
                    editing = null
                }
                InkLink("Cancel", paper.usuzumi0) { editing = null }
            }
        } else if (lines.size < dev.aiharness.core.letters.WayLines.MOST) {
            InkLink("+ Add a line", ink) { draft = ""; editing = lines.size }
        }
        PaperButton(
            when {
                asking -> "${p.name} is writing…"
                p.wayLinesWrittenAt == null -> "Let ${p.name} write ${w.their} own"
                else -> "Ask ${w.them} for new ones"
            },
            Modifier.fillMaxWidth(),
            ink = ink,
            enabled = !asking,
        ) {
            asking = true
            problem = null
            scope.launch {
                val written = runCatching { harness.writeOwnWayLines(p.id) }.getOrNull()
                onMain {
                    asking = false
                    if (written == null) problem = "${p.name} couldn't write them just now. Is there a model with a key?"
                }
            }
        }
        problem?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.sp), color = stationery.ink("beni")) }
        Text(
            "Lines marked yours stay as they are. ${w.theyCapital} may rewrite ${w.their} own, at most once a week, when ${w.they} reflect${w.verb("s", "")}.",
            style = PaperType.noteBody.copy(fontSize = 12.sp),
            color = paper.usuzumi0,
        )
    }
}

/** A taped card on a persona's page, in the persona's ink and pattern. */
/**
 * The box their letters are kept in, in the letters list: theirs to choose (now and then, in a
 * heartbeat), picked at random until they do; choosing here marks it as chosen by you.
 */
@Composable
private fun LetterBoxCard(p: Persona) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val ink = stationery.ink(p.ink)
    val box = dev.aiharness.core.model.LetterBoxes.of(p)
    var open by remember { mutableStateOf(false) }
    fun choose(next: dev.aiharness.core.model.LetterBox) {
        val now = System.currentTimeMillis()
        scope.launch { harness.personas.upsert(p.copy(letterBox = next.copy(chosenByYou = true, chosenAt = now), updatedAt = now)) }
    }
    val layout = remember(p.id) { tapeLayouts("box-${p.id}", 1)[0] }
    PageCard(p, layout, "letter-box") {
        CardTitle(
            "Letter box",
            note = when (p.letterBox?.chosenByYou) {
                null -> "picked at random"
                true -> "chosen by you"
                false -> "chosen by ${p.name}"
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            dev.aiharness.app.ui.chats.LetterBoxPicture(
                p,
                open,
                Modifier.padding(top = 16.dp, bottom = 6.dp).clickable(onClickLabel = if (open) "Shut the box" else "Open the box") { open = !open },
                box = box,
            )
            Text(
                "Where ${p.name}'s letters are kept, in your letters list. ${p.name} chooses a new one now and then.",
                style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = paper.usuzumi1,
                modifier = Modifier.weight(1f),
            )
        }
        PaperChoice(
            listOf(dev.aiharness.core.model.BoxFinish.LACQUER to "Lacquer", dev.aiharness.core.model.BoxFinish.WASHI to "Washi", dev.aiharness.core.model.BoxFinish.PAULOWNIA to "Paulownia"),
            box.finish, { choose(box.copy(finish = it)) }, ink = ink,
        )
        PaperChoice(
            listOf(
                dev.aiharness.core.model.BoxLid.NAME to "Name", dev.aiharness.core.model.BoxLid.SEAL to "Seal", dev.aiharness.core.model.BoxLid.ENGRAVED to "Engraved",
                dev.aiharness.core.model.BoxLid.GILDED to "Gilded", dev.aiharness.core.model.BoxLid.KANJI to "Kanji",
            ),
            box.lid, { choose(box.copy(lid = it)) }, ink = ink, columns = 3,
        )
        PaperChoice(
            listOf(
                dev.aiharness.core.model.BoxTie.BOW to "Bow", dev.aiharness.core.model.BoxTie.CROSSWISE to "Crosswise",
                dev.aiharness.core.model.BoxTie.MIZUHIKI to "Mizuhiki", dev.aiharness.core.model.BoxTie.TASSEL to "Tassel",
            ),
            box.tie, { choose(box.copy(tie = it)) }, ink = ink, columns = 2,
        )
    }
}

@Composable
private fun PageCard(p: Persona, layout: TapeLayout, key: String, content: @Composable () -> Unit) {
    TapedCard(layout, Tape(stationery.ink(p.ink), p.tapePattern), tilt = cardTilt("${p.id}-$key")) { content() }
}

/** Always-in-mind memories taped down, then the latest episodes and reflections. */
@Composable
private fun MemoryBookPreview(memories: List<MemoryEntry>, ink: Color, onOpen: (String) -> Unit) {
    if (memories.isEmpty()) {
        Text("Nothing yet. Memories gather here as you talk.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0)
        return
    }
    val core = memories.filter { it.isCore }.sortedByDescending { it.level.ordinal }.take(3)
    val recent = memories.filter { !it.isCore && (it.kind == MemoryKind.EPISODE || it.kind == MemoryKind.REFLECTION) }.sortedByDescending { it.createdAt }.take(2)
    val facts = if (core.size + recent.size < 3) {
        memories.filter { !it.isCore && it.kind == MemoryKind.FACT }.sortedByDescending { it.createdAt }.take(3 - core.size - recent.size)
    } else {
        emptyList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        core.forEach { Slip(it, ink, label = "Always in mind", taped = true) { onOpen(it.id) } }
        recent.forEach { Slip(it, ink, label = letterTime(it.createdAt)) { onOpen(it.id) } }
        facts.forEach { Slip(it, ink, label = null) { onOpen(it.id) } }
    }
}

@Composable
private fun Slip(memory: MemoryEntry, ink: Color, label: String?, taped: Boolean = false, onClick: () -> Unit) {
    val colors = paper
    Column(
        Modifier
            .then(if (taped) Modifier.cornerTape(Tape(ink, TapePattern.PLAIN), length = 30.dp, thickness = 11.dp, angleDegrees = -32f, reach = 7.dp, lift = 1.dp) else Modifier)
            .fillMaxWidth()
            .background(colors.hyoshi, PaperCorner)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Open this memory", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        label?.let { Text(it.uppercase(), style = PaperType.meta, color = colors.kasure2) }
        Text(
            memory.content,
            style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 20.sp, fontStyle = if (memory.kind == MemoryKind.REFLECTION) FontStyle.Italic else FontStyle.Normal),
            color = if (taped) colors.sumi else colors.usuzumi1,
        )
    }
}

/**
 * A persona's pronouns: she, he, they, it, its name only, or its own (five forms, and whether verbs
 * go as with "they"). Told to the persona, and used when the app writes about it.
 */
@Composable
private fun PronounPicker(p: Persona, ink: Color, onChange: (Persona) -> Unit) {
    Text("PRONOUNS", style = PaperType.meta, color = paper.kasure2)
    PronounSetChoice(p.pronouns, ink) { set -> onChange(p.copy(pronouns = set, alsoPronouns = if (set == null) emptyList() else p.alsoPronouns)) }
    // More sets beside the first (she/they): the persona is told all of them.
    p.alsoPronouns.forEachIndexed { i, set ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ALSO", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.weight(1f))
            InkLink("Take out", ink) { onChange(p.copy(alsoPronouns = p.alsoPronouns.filterIndexed { n, _ -> n != i })) }
        }
        PronounSetChoice(set, ink) { changed ->
            onChange(p.copy(alsoPronouns = if (changed == null) p.alsoPronouns.filterIndexed { n, _ -> n != i } else p.alsoPronouns.toMutableList().also { it[i] = changed }))
        }
    }
    val first = p.pronouns
    if (first != null && p.alsoPronouns.size < 3) {
        val next = if (first.kind == PronounKind.THEY) Pronouns(PronounKind.SHE) else Pronouns(PronounKind.THEY)
        InkLink(if (p.alsoPronouns.isEmpty()) "+ Another set, like she/they" else "+ Another set", ink) { onChange(p.copy(alsoPronouns = p.alsoPronouns + next)) }
    }
    if (p.pronounSets.size > 1) {
        Text("WHEN THE APP WRITES ABOUT ${p.name.ifBlank { "THEM" }.uppercase()}", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(PronounMix.entries.map { it to it.label }, p.pronounsMix, { mix -> onChange(p.copy(pronounsMix = mix)) }, ink = ink)
    }
    val them = p.words(0).them
    Text(
        when {
            first == null -> "Not chosen yet: the app uses ${p.name.ifBlank { "the name" }}, and nothing is said."
            p.pronounSets.size > 1 -> "${p.name.ifBlank { "The persona" }} is told all of them, and the app's sentences about $them " +
                if (p.pronounsMix == PronounMix.TAKE_TURNS) "take turns with them." else "use any of them, changing from day to day."
            first.kind == PronounKind.NAME_ONLY -> "${p.name.ifBlank { "They" }} is told to go by name, and the app uses the name too."
            else -> "${p.name.ifBlank { "The persona" }} is told, and the app uses them when it writes about $them."
        },
        style = PaperType.noteBody.copy(fontSize = 12.sp),
        color = paper.usuzumi0,
    )
}

/** One set of pronouns: she, he, they, it, the name only, or its own (five forms, and how verbs go). */
@Composable
private fun PronounSetChoice(current: Pronouns?, ink: Color, onChange: (Pronouns?) -> Unit) {
    val pick: (PronounKind?) -> Unit = { kind ->
        onChange(kind?.let { k -> if (k == PronounKind.CUSTOM) Pronouns(k, current?.custom.orEmpty(), current?.customPlural ?: false) else Pronouns(k) })
    }
    PaperChoice(listOf(PronounKind.SHE, PronounKind.HE, PronounKind.THEY).map { it to it.label }, current?.kind, pick, ink = ink)
    PaperChoice(listOf(PronounKind.IT to "it/its", PronounKind.NAME_ONLY to "Name only", PronounKind.CUSTOM to "Their own"), current?.kind, pick, ink = ink)
    if (current?.kind == PronounKind.CUSTOM) {
        val forms = (current.custom + List(5) { "" }).take(5)
        val set: (Int, String) -> Unit = { i, v -> onChange(current.copy(custom = forms.toMutableList().also { it[i] = v.trim() })) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IndexCardField("They", forms[0], { set(0, it) }, Modifier.weight(1f), placeholder = "xe", singleLine = true, ink = ink, style = PaperType.noteBody)
            IndexCardField("Them", forms[1], { set(1, it) }, Modifier.weight(1f), placeholder = "xem", singleLine = true, ink = ink, style = PaperType.noteBody)
            IndexCardField("Their", forms[2], { set(2, it) }, Modifier.weight(1f), placeholder = "xyr", singleLine = true, ink = ink, style = PaperType.noteBody)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IndexCardField("Theirs", forms[3], { set(3, it) }, Modifier.weight(1f), placeholder = "xyrs", singleLine = true, ink = ink, style = PaperType.noteBody)
            IndexCardField("Themself", forms[4], { set(4, it) }, Modifier.weight(1f), placeholder = "xemself", singleLine = true, ink = ink, style = PaperType.noteBody)
        }
        SwitchRow("Verbs as with \"they\"", current.customPlural, { onChange(current.copy(customPlural = it)) }, subtitle = "\"${forms[0].ifBlank { "xe" }} are\" rather than \"${forms[0].ifBlank { "xe" }} is\".", ink = ink)
    }
}

/** "Since today, 15:40 · was Claude Sonnet 5.5": when its model was changed on this page, and from what. */
private fun modelSinceLine(since: Long, previous: String?): String {
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(since).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val day = when (at.toLocalDate()) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> dev.aiharness.app.ui.chat.shortDay(since, zone, today)
    }
    val time = dev.aiharness.app.ui.planner.AppClock.format.time(at.hour * 60 + at.minute)
    return "Since $day, $time" + (previous?.let { " · was $it" } ?: "")
}

/** Her mood portraits, beside her portrait: small polaroids named for their moods. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodPortraits(p: Persona) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        p.moodPortraits.forEach { mood ->
            Column(
                Modifier.background(paper.kami, dev.aiharness.app.ui.paper.PaperCorner).padding(start = 4.dp, end = 4.dp, top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dev.aiharness.app.ui.BlobImage(mood.ref, Modifier.size(56.dp, 68.dp), maxSize = 256, placeholder = paper.chitsu, description = "${p.name}, ${mood.name}")
                Text(mood.name.replace('_', ' '), style = PaperType.meta.copy(letterSpacing = 0.sp, fontSize = 10.sp), color = paper.usuzumi0, maxLines = 1, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
