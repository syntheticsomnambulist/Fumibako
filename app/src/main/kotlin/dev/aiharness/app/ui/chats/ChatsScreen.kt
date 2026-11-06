package dev.aiharness.app.ui.chats

import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.paper.PaperCorner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.PersonaPortrait
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.UnreadSeal
import dev.aiharness.app.ui.paper.cornerTape
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.paper.washiStrip
import dev.aiharness.app.ui.seasonKanji
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Persona
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** The letters list: each conversation is a paper card held by its persona's washi strip. */
@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun ChatsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(null)
    val activity by remember { container.awaySince.flatMapLatest { harness.activity.observeSince(it) } }.collectAsStateWithLifecycle(emptyList())
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    val visible = conversations.filter { !it.archived }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenTitle("Letters", Modifier.statusBarsPadding()) }
            if (profiles?.isEmpty() == true) {
                item { SetupCard { nav.navigate(Routes.MODELS) } }
            }
            if (activity.any { it.kind != ActivityKind.RESTED }) {
                item { AwayCard(activity, personas, onDismiss = container::dismissAway) }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "No letters yet. Start one with a persona.",
                        style = PaperType.preview.copy(fontStyle = FontStyle.Italic),
                        color = paper.usuzumi0,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(visible, key = { it.id }) { conversation ->
                val persona = personas.firstOrNull { it.id == conversation.personaId }
                LetterCard(
                    conversation,
                    persona,
                    modifier = Modifier.combinedClickable(
                        onClick = { nav.navigate(Routes.chat(conversation.id)) },
                        onLongClick = { deleting = conversation },
                        onLongClickLabel = "Delete",
                    ),
                )
            }
        }
        NewLetterButton(Modifier.align(Alignment.BottomEnd).padding(18.dp)) { picking = true }
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Write to…", style = PaperType.entryTitle) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(personas, key = { it.id }) { persona ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickable(onClick = {
                                    picking = false
                                    scope.launch {
                                        val conversation = harness.createConversation(persona.id)
                                        onMain { nav.navigate(Routes.chat(conversation.id)) }
                                    }
                                })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PortraitDot(persona, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(persona.name, style = PaperType.letterName, color = paper.sumi)
                                if (persona.description.isNotBlank()) {
                                    Text(persona.description, style = PaperType.preview, color = paper.usuzumi1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false; nav.navigate(Routes.personaPage(Routes.NEW)) }) { Text("New persona") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete these letters?", style = PaperType.entryTitle) },
            text = { Text("The conversation is deleted. The persona keeps the memories it formed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { harness.conversations.delete(conversation.id) }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Big display title with the current season and time in brush kanji beside it. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = PaperType.screenTitle, color = paper.sumi)
        Text(seasonKanji(paper.key), style = PaperType.seasonKanji, color = paper.kasure2, modifier = Modifier.padding(bottom = 3.dp))
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun SetupCard(onClick: () -> Unit) {
    val you = stationery.you
    Column(
        Modifier
            .cornerTape(Tape(you, stationery.youPattern))
            .paperCard()
            .combinedClickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Set up a model", style = PaperType.entryTitle, color = paper.sumi)
        Text(
            "Add an API key and a model profile (Claude, Gemini, OpenAI-compatible or NovelAI) so your personas can write back. Tap here.",
            style = PaperType.prose,
            color = paper.usuzumi1,
        )
    }
}

/** What personas did in the background since the user last left the app. */
@Composable
private fun AwayCard(entries: List<ActivityEntry>, personas: List<Persona>, onDismiss: () -> Unit) {
    val s = stationery
    val rows = remember(entries) { summarise(entries) }
    Column(
        Modifier
            .cornerTape(Tape(s.you, s.youPattern))
            .paperCard()
            .fillMaxWidth()
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("While you were away", style = PaperType.entryTitle.copy(fontStyle = FontStyle.Italic, fontSize = 18.sp), color = paper.sumi)
        rows.take(5).forEach { row ->
            val persona = personas.firstOrNull { it.id == row.personaId } ?: return@forEach
            val ink = s.ink(persona.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortraitDot(persona, 20.dp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold)) { append(persona.name) }
                        append(" ")
                        append(row.describe())
                    },
                    style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = paper.usuzumi1,
                    modifier = Modifier.weight(1f),
                )
                Text(letterTime(row.at), style = PaperType.meta, color = paper.kasure2)
            }
        }
        Text(
            "Got it",
            style = PaperType.meta.copy(fontWeight = FontWeight.Bold),
            color = s.you,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

private data class AwayRow(val personaId: String, val kind: ActivityKind, val count: Int, val at: Long) {
    fun describe(): String = dev.aiharness.app.ui.activityPhrase(kind, count)
}

/** One line per persona and kind; wakeups that only rested aren't news. */
private fun summarise(entries: List<ActivityEntry>): List<AwayRow> =
    entries.filter { it.kind != ActivityKind.RESTED }.groupBy { it.personaId to it.kind }
        .map { (key, group) -> AwayRow(key.first, key.second, group.sumOf { it.count }, group.maxOf { it.createdAt }) }
        .sortedByDescending { it.at }

/** One conversation as a paper card: portrait, name, mood, time and the last line of the latest letter. */
@Composable
private fun LetterCard(conversation: Conversation, persona: Persona?, modifier: Modifier = Modifier) {
    val s = stationery
    val ink = s.ink(persona?.ink)
    val tilt = remember(conversation.id) { ((conversation.id.hashCode() % 9) / 10f).coerceIn(-0.9f, 0.9f) }
    val unread = conversation.hasUnread
    Box {
        Row(
            modifier
                .washiStrip(Tape(ink, persona?.tapePattern ?: dev.aiharness.core.model.TapePattern.DOTS), tiltDegrees = tilt)
                .paperCard()
                .fillMaxWidth()
                .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
                .semantics { if (unread) contentDescription = "Unread letter from ${persona?.name}" },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PersonaPortrait(
                persona,
                Modifier
                    .size(width = 52.dp, height = 62.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(paper.kami),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(persona?.name ?: conversation.title, style = PaperType.listName, color = paper.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    Box(Modifier.weight(1f)) { MoodWord(conversation.mood, ink, Modifier.padding(bottom = 2.dp)) }
                    Text(
                        letterTime(conversation.lastAgentMessageAt ?: conversation.updatedAt),
                        style = PaperType.meta,
                        color = paper.kasure2,
                        modifier = Modifier.padding(bottom = 3.dp, end = if (unread) 18.dp else 0.dp),
                    )
                }
                Text(
                    conversation.preview ?: " ",
                    style = PaperType.preview,
                    color = if (unread) paper.sumi else paper.usuzumi1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread && conversation.previewFromHeartbeat) {
                    Text("Left for you while you were away", style = PaperType.meta.copy(letterSpacing = 0.sp), color = ink)
                }
            }
        }
        if (unread) {
            UnreadSeal(persona?.ink, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).rotate(10f))
        }
    }
}

@Composable
private fun NewLetterButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = stationery
    Row(
        modifier
            .paperCard(color = s.you, shape = PaperCorner)
            .clip(PaperCorner)
            .combinedClickable(onClick = onClick, onClickLabel = "New letter")
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("文", style = PaperType.seasonKanji.copy(fontSize = 17.sp, fontFamily = HarnessFonts.Brush), color = s.colors.onInk(s.you))
        Text("New letter", style = PaperType.label.copy(fontSize = 14.sp, letterSpacing = 0.sp), color = s.colors.onInk(s.you))
        Spacer(Modifier.width(2.dp))
    }
}
