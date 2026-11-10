package dev.aiharness.app.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.MessageRole
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.letterTimeInSentence
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.paper.topPairTape
import dev.aiharness.app.ui.personas.PageBar
import dev.aiharness.app.ui.personas.discardGuard
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.memory.FeelingWords
import dev.aiharness.core.memory.MemoryAge
import dev.aiharness.core.memory.MemoryWeights
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Words to start from when choosing a feeling; any word works. */
private val FeelingSuggestions = listOf("happy", "fond", "proud", "calm", "grateful", "sad", "worried", "cross", "amazed", "wistful", "amused", "hopeful")

/**
 * One memory, as a card. Read-only unless the user may edit memories (Settings, Memory); then
 * **Edit** opens the same card for editing, **Done** keeps the changes, and the memory can be
 * forgotten: it burns away, and **Undo** stays until you leave the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryCardScreen(nav: NavController, personaId: String, memoryId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() }
    val density = LocalDensity.current
    val isNew = memoryId == Routes.NEW
    val memories by remember(personaId) { harness.memories.observeForPersona(personaId) }.collectAsStateWithLifecycle(null)
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())

    var editing by rememberSaveable { mutableStateOf(isNew) }
    var draft by remember { mutableStateOf<MemoryEntry?>(null) }
    var original by remember { mutableStateOf<MemoryEntry?>(null) }
    var pendingMood by remember { mutableStateOf<String?>(null) }
    var resetMood by remember { mutableStateOf(false) }
    var choosingFeeling by remember { mutableStateOf(false) }
    var forgotten by remember { mutableStateOf<MemoryEntry?>(null) }
    var burning by remember { mutableStateOf<ImageBitmap?>(null) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    // The forgotten card's height, kept so the page doesn't jump while it burns and after.
    var goneHeight by remember { mutableStateOf(IntSize.Zero) }
    val layer = rememberGraphicsLayer()

    val persona = personas.firstOrNull { it.id == personaId }
    val name = persona?.name ?: "…"
    val ink = stationery.ink(persona?.ink)
    val tape = dev.aiharness.app.ui.paper.tape(ink, persona?.tapePattern ?: TapePattern.DOTS, persona?.tapeShade ?: dev.aiharness.core.model.TapeShade.THEME)
    val s = settings ?: return
    val all = memories ?: return
    val canEdit = s.memory.userCanEdit

    if (isNew && draft == null) {
        val t = System.currentTimeMillis()
        val blank = MemoryEntry(newId(), personaId, "", createdAt = t, updatedAt = t, lastAccessedAt = t)
        draft = blank
        original = blank
    }
    val stored = all.firstOrNull { it.id == memoryId }
    if (editing && draft == null && stored != null) {
        val start = stored.forEditing()
        draft = start
        original = start
    }
    val back = discardGuard(
        editing && forgotten == null,
        changed = draft != original || pendingMood != null || resetMood,
        onLeave = {
            if (isNew) {
                nav.popBackStack()
            } else {
                editing = false
                draft = null
                pendingMood = null
                resetMood = false
            }
        },
        onBack = { nav.popBackStack() },
    )

    val gone = forgotten
    val m = (if (editing) draft else stored) ?: gone
    if (m == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            PageBar(onBack = { nav.popBackStack() })
            Text("This memory isn't here any more.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0)
        }
        return
    }
    val feeling = FeelingWords.lookup(m.currentMood, s.learnedFeelings)
    val age = if (s.memory.showAging && !m.isReplaced && !isNew) MemoryWeights.age(m, now, feeling) else null
    val about = users.firstOrNull { it.id == m.aboutUserPersonaId }
    val replacedBy = m.replacedById?.let { id -> all.firstOrNull { it.id == id } }
    val replaces = all.firstOrNull { it.replacedById == m.id }
    val source = m.sourceConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
    val sourceMessage by produceState<ChatMessage?>(null, m.sourceConversationId, m.sourceMessageId) {
        val conversation = m.sourceConversationId
        val message = m.sourceMessageId
        value = if (conversation != null && message != null) harness.conversations.getMessages(conversation).firstOrNull { it.id == message } else null
    }
    val origin = originOf(m, sourceMessage, source != null)
    // What could be kept with it: the pictures and attachments in its letter and the note that letter answered.
    val keepable by produceState(emptyList<Keepsake>(), m.sourceConversationId, m.sourceMessageId) {
        val conversation = m.sourceConversationId
        val message = m.sourceMessageId
        value = if (conversation == null || message == null) emptyList() else {
            val history = harness.conversations.getMessages(conversation)
            val at = history.indexOfFirst { it.id == message }
            if (at < 0) emptyList() else {
                val noteAt = (0 until at).lastOrNull { history[it].role == MessageRole.USER } ?: at
                val end = ((at + 1) until history.size).firstOrNull { history[it].role == MessageRole.USER } ?: history.size
                history.subList(noteAt, end).flatMap { msg ->
                    (msg.images + msg.attachments).map { Keepsake(msg.id, it, keptAt = System.currentTimeMillis(), fromYou = msg.role == MessageRole.USER) }
                }
            }
        }
    }

    fun save() {
        val d = draft ?: return
        if (d.content.isBlank()) return
        scope.launch {
            if (isNew) {
                val result = harness.memory.save(
                    personaId,
                    d.content,
                    kind = d.kind,
                    level = d.level,
                    pinned = d.pinned,
                    aboutUserPersonaId = d.aboutUserPersonaId,
                    feltMood = pendingMood,
                    feltMoodChosen = pendingMood != null,
                    userTouched = true,
                )
                onMain {
                    nav.popBackStack()
                    nav.navigate(Routes.memoryCard(personaId, result.entry.id))
                }
                return@launch
            }
            val before = original?.keepsakes.orEmpty()
            harness.memory.update(
                d.id, content = d.content, level = d.level, pinned = d.pinned, byUser = true,
                keep = d.keepsakes.filter { k -> before.none { it.key == k.key } },
                unkeep = before.map { it.key }.toSet() - d.keepsakes.map { it.key }.toSet(),
            )
            harness.memory.get(d.id)?.let { current ->
                if (current.kind != d.kind || current.aboutUserPersonaId != d.aboutUserPersonaId) {
                    harness.memories.upsert(current.copy(kind = d.kind, aboutUserPersonaId = d.aboutUserPersonaId))
                }
            }
            if (resetMood) {
                harness.memory.resetFeeling(d.id)
            } else {
                pendingMood?.let { harness.memory.reviseFeeling(d.id, it, reason = null, byUser = true) }
            }
            onMain {
                editing = false
                draft = null
                pendingMood = null
                resetMood = false
            }
        }
    }

    fun forget() {
        val target = stored ?: return
        scope.launch {
            val picture = if (s.memory.burnOnForget) runCatching { layer.toImageBitmap() }.getOrNull() else null
            harness.memory.delete(target.id)
            onMain {
                goneHeight = picture?.let { IntSize(it.width, it.height) } ?: cardSize
                forgotten = target
                burning = picture
            }
        }
    }

    fun undo() {
        val snapshot = forgotten ?: return
        scope.launch {
            harness.memories.upsert(snapshot)
            onMain {
                forgotten = null
                burning = null
                editing = false
                draft = null
                pendingMood = null
                resetMood = false
            }
        }
    }

    KeepsakeHost(personaId, persona, name) { below ->
    val pageScroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().scrollbar(pageScroll),
        state = pageScroll,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp + below),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item("bar") {
            PageBar(onBack = back) {
                when {
                    gone != null -> Unit
                    editing -> {
                        Text("EDITING", style = PaperType.meta, color = ink)
                        PaperButton("Done", ink = ink, enabled = m.content.isNotBlank()) { save() }
                    }
                    canEdit -> PaperButton("Edit") {
                        val start = (stored ?: return@PaperButton).forEditing()
                        draft = start
                        original = start
                        editing = true
                    }
                }
            }
        }
        item("title") {
            Text(if (isNew) "A new memory for $name" else "A memory", style = PaperType.entryTitle.copy(fontSize = 24.sp, lineHeight = 28.sp), color = paper.sumi)
        }

        item("card") {
            val picture = burning
            if (gone != null) {
                Box(Modifier.fillMaxWidth().height(with(density) { goneHeight.height.toDp() }.coerceAtLeast(60.dp))) {
                    if (picture != null) {
                        // Exactly the snapshot's size, so the burning card sits where the card was.
                        BurnAway(picture, seed = gone.id.hashCode(), modifier = Modifier.size(with(density) { picture.width.toDp() }, with(density) { picture.height.toDp() })) { burning = null }
                    } else {
                        Ash("You let this memory go.", Modifier.align(Alignment.BottomStart).fillMaxWidth()) { undo() }
                    }
                }
            } else {
                // Recorded so the card can burn exactly as it looks; the margin keeps the tape and the feeling box inside.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { cardSize = it }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ContentCard(m, about, ink, tape, age, editing) { v -> draft = draft?.copy(content = v) }
                        if (m.keepsakes.isNotEmpty()) {
                            val inkOf = LocalKeepsakeMarks.current.inkOfKeepsake
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(start = 6.dp, bottom = 18.dp)) {
                                m.keepsakes.forEach { KeepsakeObject(it, inkOf(it), age, scale = 0.8f, conversationId = m.sourceConversationId) }
                            }
                        }
                    }
                    val word = shownMood(m, pendingMood, resetMood)
                    if (!editing && word != null) {
                        FeelingWordBox(word, FeelingWords.lookup(word, s.learnedFeelings), ink, paper.kami, Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = 12.dp))
                    }
                }
            }
        }
        if (gone != null) return@LazyColumn

        if (editing) {
            item("felt") {
                Section("Felt") {
                    val word = shownMood(m, pendingMood, resetMood)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (word != null) {
                            FeelingWordBox(word, FeelingWords.lookup(word, s.learnedFeelings), ink, paper.kami, tilt = -3f, fontSize = 16f)
                        } else {
                            Text("No feeling", style = PaperType.mood, color = paper.kasure2)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            feelingHistory(m, name, pendingMood, resetMood).forEach { Text(it, style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = paper.usuzumi0) }
                        }
                        InkLink("Change", ink) { choosingFeeling = true }
                    }
                }
            }
            if (!isNew && (m.keepsakes.isNotEmpty() || keepable.isNotEmpty())) {
                item("kept") {
                    Section("Kept with it") {
                        m.keepsakes.forEach { k ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(k.label, style = PaperType.noteBody, color = paper.sumi, maxLines = 2, modifier = Modifier.weight(1f))
                                InkLink("Take out", ink) { draft = draft?.copy(keepsakes = m.keepsakes.filter { it.key != k.key }) }
                            }
                        }
                        val more = keepable.filter { c -> m.keepsakes.none { it.key == c.key } }
                        if (more.isNotEmpty()) {
                            Text("FROM ITS LETTER", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.padding(top = 4.dp))
                            more.forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.label, style = PaperType.noteBody, color = paper.usuzumi1, maxLines = 2, modifier = Modifier.weight(1f))
                                    InkLink("Keep", ink) { draft = draft?.copy(keepsakes = m.keepsakes + c) }
                                }
                            }
                        }
                    }
                }
            }
            item("kind") {
                Section("Kind") {
                    PaperChoice(listOf(MemoryKind.FACT to "Fact", MemoryKind.EPISODE to "Happened", MemoryKind.REFLECTION to "Thought"), m.kind, { k -> draft = draft?.copy(kind = k) }, ink = ink)
                    Row(Modifier.fillMaxWidth().clickable { draft = draft?.copy(pinned = !m.pinned) }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always in mind", style = PaperType.noteBody, color = paper.sumi)
                            Text("Kept in every letter, not only when it comes up.", style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = paper.usuzumi0)
                        }
                        TickBox(m.pinned, null, ink = ink)
                    }
                }
            }
            item("level") {
                Section("How much it matters") {
                    PaperChoice(MemoryLevel.entries.map { it to it.label }, m.level, { l -> draft = draft?.copy(level = l) }, ink = ink)
                }
            }
            item("about") {
                Section("Who it's about") {
                    if (users.size <= 2) {
                        PaperChoice(listOf<UserPersona?>(null).plus(users).map { it to (it?.name ?: "Anyone") }, about, { u -> draft = draft?.copy(aboutUserPersonaId = u?.id) }, ink = ink)
                    } else {
                        PaperSelect(null, users, about, { it.name }, { u -> draft = draft?.copy(aboutUserPersonaId = u?.id) }, noneLabel = "Anyone")
                    }
                }
            }
            if (!isNew) {
                item("meta") { Slip { keptLines(m, name, zone, origin).forEach { Text(it.uppercase(), style = PaperType.meta, color = paper.kasure2) } } }
                item("forget") { ForgetButton { forget() } }
            }
        } else {
            item("feelings") {
                Slip {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FELT WHEN KEPT", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.weight(1f))
                        Text(m.feltMood ?: "not noted", style = PaperType.mood.copy(fontSize = 13.sp), color = if (m.feltMood != null) ink else paper.kasure2)
                    }
                    if (m.mood != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FEELS NOW", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.weight(1f))
                            Text(listOfNotNull(m.mood, m.moodRevisedAt?.let { "since ${shortDate(dayOf(it, zone))}" }).joinToString(" · "), style = PaperType.mood.copy(fontSize = 13.sp), color = ink)
                        }
                        Text(
                            m.moodReason?.let { "“$it”" } ?: "You changed how it feels.",
                            style = PaperType.prose.copy(fontSize = 13.sp, lineHeight = 19.sp, fontStyle = FontStyle.Italic),
                            color = paper.usuzumi1,
                        )
                    }
                }
            }
            item("facts") {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Fact("Matters", m.level.label)
                    Fact("Always in mind", if (m.isCore) "Yes" else "No")
                    Fact("About", about?.name ?: "Anyone")
                    Fact("Kept", keptWhen(m, zone, origin))
                    Fact("Recalled", if (m.accessCount == 0) "Not yet" else "${if (m.accessCount == 1) "Once" else "${m.accessCount} times"} · last ${letterTimeInSentence(m.lastAccessedAt).removePrefix("at ").removePrefix("on ")}")
                    when (age) {
                        MemoryAge.FADING -> Fact("Aging", "Fading: it hasn't come up for a while")
                        MemoryAge.WORN -> Fact("Aging", "Worn: it doesn't come up on its own any more, but $name can still find it")
                        else -> Unit
                    }
                }
            }
            if (replacedBy != null || replaces != null) {
                item("history") {
                    Slip {
                        if (replacedBy != null) {
                            Text("NO LONGER SO${m.replacedAt?.let { " · SINCE ${shortDate(dayOf(it, zone)).uppercase()}" }.orEmpty()}", style = PaperType.meta, color = paper.kasure2)
                            Text(replacedBy.content, style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 20.sp), color = paper.sumi)
                            InkLink("Open what replaced it", ink) { nav.navigate(Routes.memoryCard(personaId, replacedBy.id)) }
                        }
                        if (replaces != null) {
                            Text("UPDATES AN EARLIER MEMORY", style = PaperType.meta, color = paper.kasure2)
                            Text(replaces.content, style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 20.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = paper.kasure2)
                            InkLink("Open the earlier one", ink) { nav.navigate(Routes.memoryCard(personaId, replaces.id)) }
                        }
                    }
                }
            }
            if (source != null) {
                item("letter") { InkLink("Open the letter it came from", ink) { nav.navigate(Routes.chat(source.id)) } }
            }
            if (!canEdit) {
                item("hint") {
                    Slip {
                        Text(
                            "These are $name's memories. To change or remove them yourself, turn on \"Let me edit memories\" in Settings, Memory.",
                            style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                            color = paper.usuzumi0,
                        )
                        InkLink("Memory settings") { nav.navigate(Routes.settings("memory")) }
                    }
                }
            }
        }
    }
    }

    if (choosingFeeling) {
        FeelingPicker(
            current = shownMood(m, pendingMood, resetMood).orEmpty(),
            original = m.feltMood,
            revised = m.mood != null || pendingMood != null,
            ink = ink,
            onDismiss = { choosingFeeling = false },
            onPick = { word ->
                choosingFeeling = false
                val clean = FeelingWords.normalize(word)
                if (clean.isEmpty()) return@FeelingPicker
                if (isNew) {
                    pendingMood = clean
                } else if (clean == m.feltMood) {
                    resetMood = true
                    pendingMood = null
                } else {
                    resetMood = false
                    pendingMood = clean
                }
            },
            onReset = {
                choosingFeeling = false
                pendingMood = null
                resetMood = true
            },
        )
    }
}

/** Old "core" memories are edited as facts kept always in mind. */
private fun MemoryEntry.forEditing(): MemoryEntry = if (kind == MemoryKind.CORE) copy(kind = MemoryKind.FACT, pinned = true) else this

/** The feeling to show, counting changes that aren't saved yet. */
private fun shownMood(m: MemoryEntry, pending: String?, reset: Boolean): String? = when {
    pending != null -> pending
    reset -> m.feltMood
    else -> m.currentMood
}

private fun feelingHistory(m: MemoryEntry, name: String, pending: String?, reset: Boolean): List<String> = buildList {
    when {
        pending != null || reset -> add("Changes when you tap Done.")
        m.mood != null && m.moodReason != null -> add("$name changed this${m.moodRevisedAt?.let { " on ${shortDate(dayOf(it, ZoneId.systemDefault()))}" }.orEmpty()}, while reflecting.")
        m.mood != null -> add("You changed this${m.moodRevisedAt?.let { " on ${shortDate(dayOf(it, ZoneId.systemDefault()))}" }.orEmpty()}.")
    }
    m.feltMood?.let { add("When it was kept: $it") }
}

/** Where a memory came from, for "Kept". */
private enum class Origin { LETTER, HEARTBEAT, YOU, UNKNOWN }

private fun originOf(m: MemoryEntry, source: ChatMessage?, hasLetter: Boolean): Origin = when {
    source?.origin == MessageOrigin.HEARTBEAT -> Origin.HEARTBEAT
    hasLetter -> Origin.LETTER
    m.sourceMessageId == null && m.userTouched -> Origin.YOU
    else -> Origin.UNKNOWN
}

private fun keptWhen(m: MemoryEntry, zone: ZoneId, origin: Origin): String {
    val date = shortDate(dayOf(m.createdAt, zone))
    return when (origin) {
        Origin.YOU -> "$date, by you"
        Origin.LETTER -> "$date, from a letter"
        Origin.HEARTBEAT -> "$date, while you were away"
        Origin.UNKNOWN -> date
    }
}

private fun keptLines(m: MemoryEntry, name: String, zone: ZoneId, origin: Origin): List<String> = listOfNotNull(
    "Kept by ${if (origin == Origin.YOU) "you" else name} on ${longDate(dayOf(m.createdAt, zone))}" + when (origin) {
        Origin.LETTER -> ", from a letter"
        Origin.HEARTBEAT -> ", while you were away"
        else -> ""
    },
    if (m.accessCount > 0) "Recalled ${if (m.accessCount == 1) "once" else "${m.accessCount} times"}" else null,
)

/** The memory itself: an index card held by two pieces of tape. */
@Composable
private fun ContentCard(m: MemoryEntry, about: UserPersona?, ink: Color, tape: Tape, age: MemoryAge?, editing: Boolean, onChange: (String) -> Unit) {
    val colors = paper
    val label = listOfNotNull(about?.let { "About ${it.name}" }, kindLabel(m.kind)).joinToString(" · ")
    if (editing) {
        Box(Modifier.rotate(-0.6f).topPairTape(tape, thickness = 13.dp)) {
            IndexCardField(label, m.content, onChange, ink = ink, style = PaperType.prose.copy(fontSize = 16.sp, lineHeight = 28.sp), minLines = 2, placeholder = "What to remember")
        }
        return
    }
    Column(
        Modifier
            .rotate(-0.6f)
            .topPairTape(tape.copy(alpha = agedAlpha(age)), thickness = 13.dp)
            .fillMaxWidth()
            .background(colors.kami, PaperCorner)
            .border(1.dp, colors.nijimi1, PaperCorner)
            .worn(age, m.id)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
    ) {
        Text(
            label.uppercase(),
            style = PaperType.meta,
            color = colors.kasure2,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ink.copy(alpha = 0.75f), Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                .padding(bottom = 5.dp),
        )
        RuledText(
            m.content,
            PaperType.prose.copy(fontSize = 16.sp, lineHeight = 28.sp, textDecoration = if (m.isReplaced) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
            if (m.isReplaced) colors.kasure2 else agedInk(age),
            Modifier.padding(top = 4.dp),
        )
    }
}

private fun kindLabel(kind: MemoryKind) = when (kind) {
    MemoryKind.CORE -> "always in mind"
    MemoryKind.FACT -> "fact"
    MemoryKind.EPISODE -> "what happened"
    MemoryKind.REFLECTION -> "thought"
    MemoryKind.LORE -> "lore"
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = PaperType.label, color = paper.kasure2)
        content()
    }
}

@Composable
private fun Slip(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(paper.hyoshi, PaperCorner).border(1.dp, paper.nijimi0, PaperCorner).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp), color = paper.usuzumi0, modifier = Modifier.weight(0.42f))
        Text(value, style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp), color = paper.sumi, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun ForgetButton(onClick: () -> Unit) {
    val danger = stationery.ink("beni")
    Text(
        "Forget this memory",
        style = PaperType.noteBody.copy(fontSize = 14.sp),
        color = danger,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, danger, PaperCorner)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** What's left once a memory has burned: a line of ash, and Undo. */
@Composable
private fun Ash(text: String, modifier: Modifier = Modifier, onUndo: () -> Unit) {
    val colors = paper
    val ash = lerp(colors.kasure1, Color(0xFF5A4A40), 0.35f)
    Row(
        modifier
            .drawBehind {
                val y = size.height - 2.dp.toPx()
                drawLine(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.15f to ash.copy(alpha = 0.55f), 0.7f to ash.copy(alpha = 0.35f), 1f to Color.Transparent),
                    Offset(0f, y),
                    Offset(size.width, y),
                    2.5.dp.toPx(),
                )
            }
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = PaperType.prose.copy(fontSize = 14.sp, fontStyle = FontStyle.Italic), color = colors.kasure2, modifier = Modifier.weight(1f))
        PaperButton("Undo", onClick = onUndo)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelingPicker(current: String, original: String?, revised: Boolean, ink: Color, onDismiss: () -> Unit, onPick: (String) -> Unit, onReset: () -> Unit) {
    var word by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How it feels now", style = PaperType.entryTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IndexCardField("A feeling word", word, { word = it }, ink = ink, style = PaperType.prose, singleLine = true, placeholder = "fond, wistful, proud…")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FeelingSuggestions.forEach { w ->
                        Text(
                            w,
                            style = PaperType.mood.copy(fontSize = 13.sp),
                            color = if (w == word) paper.sumi else paper.usuzumi1,
                            modifier = Modifier
                                .border(1.dp, if (w == word) ink else paper.nijimi1, PaperCorner)
                                .clickable { word = w }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                if (original != null && revised) {
                    InkLink("Back to how it felt when kept ($original)", ink) { onReset() }
                }
            }
        },
        confirmButton = { TextButton(enabled = word.isNotBlank(), onClick = { onPick(word) }) { Text("Keep") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
