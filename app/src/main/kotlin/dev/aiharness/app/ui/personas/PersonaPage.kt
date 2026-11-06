package dev.aiharness.app.ui.personas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
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
        scope.launch {
            harness.personas.upsert(
                d.copy(
                    name = d.name.trim(),
                    outfit = d.outfit?.trim()?.takeIf { it.isNotEmpty() },
                    stylePrompt = d.stylePrompt?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAt = System.currentTimeMillis(),
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageBar(onBack = back) {
                if (editing) {
                    Text("EDITING", style = PaperType.meta, color = ink)
                    PaperButton("Done", ink = ink, enabled = p.name.isNotBlank()) { save() }
                } else {
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
            text = { Text("This also deletes their letters and memories.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        harness.conversations.getAll().filter { it.personaId == p.id }.forEach { harness.conversations.delete(it.id) }
                        harness.memory.all(p.id).forEach { harness.memory.delete(it.id) }
                        harness.personas.delete(p.id)
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
            val canDraw = settings?.imageGen?.credentialId != null && p.selfPrompt.isNotBlank()
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
                                    settings = harness.settings.current().imageGen.copy(width = 1024, height = 1024),
                                    prompt = "portrait, upper body, looking at viewer, simple background",
                                    persona = p,
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
                    model?.let { Chip { Text(it, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1) } }
                }
            }
        }
    }
}

private fun LazyListScope.viewCards(p: Persona, nav: NavController, mode: HeartbeatMode?) {
    val layouts = tapeLayouts("page-${p.id}", 4)
    if (p.description.isNotBlank() || p.personality.isNotBlank() || p.currentOutfit.isNotBlank()) {
        item {
            PageCard(p, layouts[0], "about") {
                CardTitle("About")
                listOf(p.description, p.personality).filter { it.isNotBlank() }.forEach { Text(it.trim(), style = PaperType.prose, color = paper.sumi) }
                p.currentOutfit.takeIf { it.isNotBlank() }?.let { outfit ->
                    Text("Wearing: $outfit", style = PaperType.prose.copy(fontStyle = FontStyle.Italic, fontSize = 13.5.sp), color = paper.usuzumi1)
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
        PageCard(p, layouts[2], "unread") {
            CardTitle("Unread mark")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UnreadSeal(p.ink, size = 40.dp)
                Text(
                    "The 新 seal, in ${p.name}'s ink. Custom marks arrive with stickers.",
                    style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = paper.usuzumi1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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
            own != null -> "Wakes every ${interval(own.intervalMinutes)}, on a rhythm of its own."
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
            IndexCardField("Description", p.description, { v -> onChange(p.copy(description = v)) }, ink = ink, style = PaperType.prose, minLines = 3, placeholder = "Who they are, in a few lines")
            IndexCardField("Personality", p.personality, { v -> onChange(p.copy(personality = v)) }, ink = ink, style = PaperType.prose, minLines = 2)
            IndexCardField("Scenario", p.scenario, { v -> onChange(p.copy(scenario = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "Where and when your letters take place (optional)")
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[2], "writes") {
            CardTitle(if (named) "How ${p.name} writes" else "How they write")
            IndexCardField("First letter", p.greeting, { v -> onChange(p.copy(greeting = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "Hi {{user}}! … (optional)")
            IndexCardField("Example letters", p.exampleDialogue, { v -> onChange(p.copy(exampleDialogue = v)) }, ink = ink, style = PaperType.prose, minLines = 2, placeholder = "A few lines in their voice (optional)")
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
        }
    }
    item {
        val ink = stationery.ink(p.ink)
        PageCard(p, layouts[4], "behavior") {
            CardTitle("Behavior")
            PaperSelect("Model", profiles, profiles.firstOrNull { it.id == p.defaultModelProfileId }, { it.name }, { m -> onChange(p.copy(defaultModelProfileId = m?.id)) }, noneLabel = "App default")
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
        }
    }
    if (!isNew) {
        item { PaperButton("Delete ${p.name}, with letters and memories", Modifier.fillMaxWidth(), onClick = onDelete) }
    }
}

/** A taped card on a persona's page, in the persona's ink and pattern. */
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
