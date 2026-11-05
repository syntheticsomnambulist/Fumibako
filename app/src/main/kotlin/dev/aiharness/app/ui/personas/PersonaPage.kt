package dev.aiharness.app.ui.personas

import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.Polaroid
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.UnreadSeal
import dev.aiharness.app.ui.paper.cornerTape
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.paper.washiStrip
import dev.aiharness.app.ui.theme.InkKanji
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.Persona
import kotlinx.coroutines.launch

/** A persona's journal page: polaroid, mood, ink and tape, then taped entries. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonaPageScreen(nav: NavController, personaId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val memories by remember(personaId) { harness.memories.observeForPersona(personaId) }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val since = remember { System.currentTimeMillis() - 14 * 86_400_000L }
    val activity by remember(personaId) { harness.activity.observeSince(since) }.collectAsStateWithLifecycle(emptyList())

    val p = personas?.firstOrNull { it.id == personaId } ?: return
    val s = stationery
    val ink = s.ink(p.ink)
    val latest = conversations.filter { it.personaId == p.id && !it.archived }.maxByOrNull { it.updatedAt }
    val model = profiles.firstOrNull { it.id == (p.defaultModelProfileId ?: settings?.defaultModelProfileId) }

    fun openLetters() {
        scope.launch {
            val conversation = latest ?: harness.createConversation(p.id)
            onMain { nav.navigate(Routes.chat(conversation.id)) }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = paper.usuzumi0) }
                Box(Modifier.weight(1f))
                PaperButton("Edit") { nav.navigate(Routes.persona(p.id)) }
            }
        }
        item {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Polaroid(p, Modifier.width(132.dp), tiltDegrees = -2f, framePadding = 8.dp, bottomPadding = 26.dp, cornerTaped = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = PaperType.heroName, color = paper.sumi)
                    MoodWord(latest?.mood, ink, style = PaperType.mood.copy(fontSize = 15.sp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip {
                            Box(Modifier.size(12.dp).background(ink, PaperCorner))
                            Text(InkKanji[p.ink].orEmpty(), style = PaperType.seal.copy(fontSize = 13.sp), color = paper.sumi)
                            Text(p.ink, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1)
                        }
                        Chip {
                            androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 10.dp)) {
                                drawTape(androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), size.height, Tape(ink, p.tapePattern, shadow = false), tooth = 2.dp.toPx())
                            }
                            Text(p.tapePattern.name.lowercase(), style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1)
                        }
                        model?.let { Chip { Text(it.name, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = paper.usuzumi1) } }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Write a letter", Modifier.weight(1f), ink = ink) { openLetters() }
                PaperButton("Memories", Modifier.weight(1f)) { nav.navigate(Routes.memory(p.id)) }
            }
        }
        if (p.description.isNotBlank() || p.personality.isNotBlank()) {
            item {
                Entry(p, "About") {
                    listOf(p.description, p.personality).filter { it.isNotBlank() }.forEach {
                        Text(it.trim(), style = PaperType.prose, color = paper.sumi)
                    }
                }
            }
        }
        item {
            Entry(
                p,
                "Memory book",
                note = if (memories.size == 1) "1 memory" else "${memories.size} memories",
                more = "See all" to { nav.navigate(Routes.memory(p.id)) },
            ) { MemoryBookPreview(memories, ink) }
        }
        item {
            Entry(p, "Unread mark") {
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
            val last = activity.lastOrNull { it.personaId == p.id }
            val mode = settings?.heartbeat?.mode
            val status = when {
                mode == HeartbeatMode.OFF -> "Heartbeats are off for everyone (Settings)."
                mode == HeartbeatMode.TIME_AWARENESS -> "Time-aware only: ${p.name} knows the time, but doesn't wake up in the background (Settings)."
                !p.heartbeatEnabled -> "${p.name} won't wake up in the background."
                else -> "Wakes up now and then to reflect, tend memories, and maybe write to you."
            }
            val lastLine = last?.let {
                val what = when (it.kind) {
                    ActivityKind.WROTE_LETTER -> "wrote you a letter"
                    ActivityKind.STAYED_QUIET -> "stayed quiet"
                    ActivityKind.SAVED_MEMORIES -> "saved memories"
                    ActivityKind.RECORDED_EPISODE -> "wrote up your recent talks"
                }
                "Last heartbeat: $what, ${letterTime(it.createdAt)}."
            }
            Entry(p, "Heartbeat") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        listOfNotNull(status, lastLine).joinToString(" "),
                        style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = paper.usuzumi1,
                        modifier = Modifier.weight(1f),
                    )
                    TickBox(
                        checked = p.heartbeatEnabled,
                        onCheckedChange = { on -> scope.launch { harness.personas.upsert(p.copy(heartbeatEnabled = on, updatedAt = System.currentTimeMillis())) } },
                        ink = ink,
                    )
                }
            }
        }
    }
}

/** A taped entry on the persona's page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Entry(
    persona: Persona,
    title: String,
    note: String? = null,
    more: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    val s = stationery
    val ink = s.ink(persona.ink)
    val tilt = remember(title) { if (title.hashCode() % 2 == 0) -0.7f else 0.6f }
    Column(
        Modifier
            .washiStrip(Tape(ink, persona.tapePattern), tiltDegrees = tilt)
            .paperCard()
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = PaperType.entryTitle, color = paper.sumi)
            note?.let { Text(it, style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2, modifier = Modifier.padding(bottom = 2.dp)) }
            Box(Modifier.weight(1f))
            more?.let { (label, action) ->
                Text(
                    label,
                    style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    color = ink,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).combinedClickable(onClick = action).padding(4.dp),
                )
            }
        }
        content()
    }
}

/** Core memories taped down, then the latest episodes and reflections. */
@Composable
private fun MemoryBookPreview(memories: List<MemoryEntry>, ink: Color) {
    if (memories.isEmpty()) {
        Text("Nothing yet. Memories gather here as you talk.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0)
        return
    }
    val core = memories.filter { it.isCore }.sortedByDescending { it.importance }.take(3)
    val recent = memories.filter { !it.isCore && (it.kind == MemoryKind.EPISODE || it.kind == MemoryKind.REFLECTION) }
        .sortedByDescending { it.createdAt }.take(2)
    val facts = if (core.size + recent.size < 3) memories.filter { !it.isCore && it.kind == MemoryKind.FACT }.sortedByDescending { it.createdAt }.take(3 - core.size - recent.size) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        core.forEach { Slip(it, ink, label = "Always in mind", taped = true) }
        recent.forEach { Slip(it, ink, label = letterTime(it.createdAt).uppercase()) }
        facts.forEach { Slip(it, ink, label = null) }
    }
}

@Composable
private fun Slip(memory: MemoryEntry, ink: Color, label: String?, taped: Boolean = false) {
    val colors = paper
    Column(
        Modifier
            .then(if (taped) Modifier.cornerTape(Tape(ink, dev.aiharness.core.model.TapePattern.PLAIN), length = 30.dp, thickness = 11.dp, angleDegrees = -32f, reach = 7.dp, lift = 1.dp) else Modifier)
            .fillMaxWidth()
            .background(colors.hyoshi, RoundedCornerShape(2.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        label?.let { Text(it.uppercase(), style = PaperType.meta, color = colors.kasure2) }
        Text(
            memory.content,
            style = PaperType.prose.copy(
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                fontStyle = if (memory.kind == MemoryKind.REFLECTION) FontStyle.Italic else FontStyle.Normal,
            ),
            color = if (taped) colors.sumi else colors.usuzumi1,
        )
    }
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Row(
        Modifier
            .border(1.dp, paper.nijimi1, PaperCorner)
            .background(paper.kami, PaperCorner)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
