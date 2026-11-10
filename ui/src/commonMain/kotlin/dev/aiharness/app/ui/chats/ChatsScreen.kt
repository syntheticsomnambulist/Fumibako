@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.aiharness.app.ui.chats

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import dev.aiharness.app.ui.LocalWide
import dev.aiharness.app.ui.LocalOpenLetter
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.TickBox
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.aiharness.app.ui.openFromList
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.UnreadSeal
import dev.aiharness.app.ui.paper.cornerTape
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.seasonKanji
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Persona
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.stickers.StickerService
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import dev.aiharness.app.platform.deviceNoun
import dev.aiharness.app.platform.onRightClick

/** The letters list: each conversation is a paper card held by its persona's washi strip. */
@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun ChatsScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val scope = rememberCoroutineScope()
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    // Letters in the post: the time now, ticking over as each one on its way arrives (counted off
    // the screen's own clock, in the background).
    val now by remember {
        harness.conversations.observeAll().transformLatest { list ->
            while (true) {
                val t = harness.clock.nowMillis()
                emit(t)
                val next = list.mapNotNull { it.lastAgentMessageAt }.filter { it > t }.minOrNull() ?: break
                delay(next - t + 50)
            }
        }.flowOn(Dispatchers.Default)
    }.collectAsStateWithLifecycle(harness.clock.nowMillis())
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(null)
    val activity by remember { container.awaySince.flatMapLatest { harness.activity.observeSince(it) } }.collectAsStateWithLifecycle(emptyList())
    var picking by remember { mutableStateOf(false) }
    val importCard = dev.aiharness.app.ui.transfer.rememberOpenFile(nav)
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    // Opening a letter opens its box first. On the phone the letter's page comes once the box is open,
    // and coming back the box shuts behind you; wide, the open letter's box stays open beside it.
    val wide = LocalWide.current
    val openBeside = LocalOpenLetter.current
    var opening by rememberSaveable { mutableStateOf<String?>(null) }
    var gone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(opening, gone) {
        val id = opening ?: return@LaunchedEffect
        if (gone) {
            delay(120)
            opening = null
            gone = false
        } else {
            delay(OPENING_MILLIS + 60L)
            gone = true
            nav.openFromList(Routes.chat(id), Routes.CHATS)
        }
    }
    val visible = conversations.filter { !it.archived }
    // Drawn once the profiles are in, so "Getting started" doesn't pop in above the first rows as the app starts.
    if (profiles == null) return

    Box(Modifier.fillMaxSize()) {
        val pageScroll = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize().scrollbar(pageScroll),
            state = pageScroll,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Every row has a key, so the list keeps its place as rows come and go.
            item(key = "title") {
                ScreenTitle("Letters", Modifier.statusBarsPadding()) {
                    IconButton(onClick = { nav.navigate(Routes.SEARCH) }) {
                        Icon(dev.aiharness.app.ui.paper.StationeryIcons.Loupe, "Search letters", tint = paper.usuzumi0)
                    }
                }
            }
            if (profiles?.isEmpty() == true) {
                item(key = "getting-started") { GettingStarted(nav) }
            }
            if (activity.any { it.kind != ActivityKind.RESTED }) {
                item(key = "away") { AwayCard(activity, personas, onDismiss = container::dismissAway) }
            }
            if (visible.isEmpty()) {
                item(key = "no-letters") {
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
                LetterBoxRow(
                    conversation,
                    persona,
                    now,
                    open = opening == conversation.id || (wide && openBeside == conversation.id),
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (wide) nav.openFromList(Routes.chat(conversation.id), Routes.CHATS)
                            else if (opening == null) opening = conversation.id
                        },
                        onLongClick = { deleting = conversation },
                        onLongClickLabel = "Delete",
                    ).onRightClick { deleting = conversation },
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
                                        onMain { nav.openFromList(Routes.chat(conversation.id), Routes.CHATS) }
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
            confirmButton = {
                Row {
                    TextButton(onClick = { picking = false; importCard() }) { Text("Import a card…") }
                    TextButton(onClick = { picking = false; nav.navigate(Routes.personaPage(Routes.NEW)) }) { Text("New persona") }
                }
            },
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
                    // Its unsent note goes too, as when it's deleted from the letter itself.
                    container.drafts.discard(conversation.id)
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
    // A 48 dp button beside the title stands 16 dp taller than its line: taken from the space above, so the title stays put.
    Row(modifier.fillMaxWidth().padding(top = if (trailing != null) 6.dp else 22.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = PaperType.screenTitle, color = paper.sumi)
        Text(seasonKanji(paper.key), style = PaperType.seasonKanji, color = paper.kasure2, modifier = Modifier.padding(bottom = 3.dp))
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * First steps on a new install, shown until there is a model: your name (so letters don't open
 * "Hi You!"), a key, and a model. Each is ticked off as it's done.
 */
@Composable
private fun GettingStarted(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val me = users.firstOrNull { it.id == settings?.activeUserPersonaId } ?: users.firstOrNull()
    val named = me != null && me.name != PLACEHOLDER_NAME
    var name by remember(me?.id) { mutableStateOf(me?.name?.takeIf { it != PLACEHOLDER_NAME }.orEmpty()) }
    val you = stationery.you
    Column(
        Modifier
            .cornerTape(Tape(you, stationery.youPattern))
            .paperCard()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Getting started", style = PaperType.entryTitle, color = paper.sumi)
            Text("Three small things, and your personas can write back.", style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp), color = paper.usuzumi0)
        }
        Step(1, "What should personas call you?", done = named) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndexCardField("Your name", name, { name = it }, Modifier.weight(1f), ink = you, style = PaperType.prose, singleLine = true)
                PaperButton("Keep", ink = you, enabled = me != null && name.isNotBlank() && name.trim() != me.name) {
                    val user = me ?: return@PaperButton
                    scope.launch { harness.userPersonas.upsert(user.copy(name = name.trim(), updatedAt = System.currentTimeMillis())) }
                }
            }
        }
        Step(2, "Add an API key", done = credentials.isNotEmpty(), detail = "From Anthropic, Google, OpenAI, OpenRouter or your own server. It stays encrypted on this $deviceNoun.") {
            InkLink("Add a key", you) { nav.navigate(Routes.KEYS) }
        }
        Step(3, "Choose a model", done = false, detail = "Start from a preset; the key you added is filled in.") {
            InkLink("Choose a model", you) { nav.navigate(Routes.model(Routes.NEW)) }
        }
    }
}

/** The name a new install's own persona starts with. */
private const val PLACEHOLDER_NAME = "You"

@Composable
private fun Step(number: Int, title: String, done: Boolean, detail: String? = null, action: @Composable () -> Unit) {
    val colors = paper
    val you = stationery.you
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        if (done) {
            TickBox(true, null, ink = you)
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, you, PaperCorner), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = PaperType.label.copy(fontSize = 12.sp), color = you)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = PaperType.noteBody.copy(fontSize = 15.sp, lineHeight = 20.sp), color = if (done) colors.kasure2 else colors.sumi)
            if (!done) {
                detail?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = colors.usuzumi0) }
                action()
            }
        }
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

/**
 * One conversation as the persona's little box (their own, see [LetterBoxPicture]), with a strip of
 * paper tucked beneath its side: their name, mood and the time, and the latest line.
 */
@Composable
private fun LetterBoxRow(conversation: Conversation, persona: Persona?, now: Long, open: Boolean, modifier: Modifier = Modifier) {
    val s = stationery
    val ink = s.ink(persona?.ink)
    val who = persona ?: Persona(id = conversation.personaId, name = conversation.title, createdAt = 0, updatedAt = 0)
    val tilt = remember(conversation.id) { ((conversation.id.hashCode() % 9) / 10f).coerceIn(-0.9f, 0.9f) }
    // A reply in the post: "on its way" (its words not shown) until it arrives, then unread.
    val onItsWay = conversation.onItsWay(now)
    val unread = conversation.unreadAt(now) && !open
    // Room above for the lid when the box is open (it tips back over the row's top edge).
    Box(modifier.fillMaxWidth().padding(top = 10.dp).semantics { if (unread) contentDescription = "Unread letter from ${who.name}" }) {
        // The strip, tucked under the box's side.
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = LetterBoxWidth - STRIP_TUCK)
                .fillMaxWidth()
                .graphicsLayer { rotationZ = tilt }
                .shadow(1.5.dp, PaperStrip)
                .background(paper.kami, PaperStrip)
                .padding(start = STRIP_TUCK + 10.dp, end = 18.dp, top = 6.dp, bottom = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(who.name, style = PaperType.listName.copy(fontSize = 17.sp, lineHeight = 20.sp), color = paper.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                Box(Modifier.weight(1f)) { MoodWord(conversation.mood, ink, Modifier.padding(bottom = 1.dp)) }
                Text(
                    letterTime(if (onItsWay) conversation.updatedAt else conversation.lastAgentMessageAt ?: conversation.updatedAt),
                    style = PaperType.meta,
                    color = paper.kasure2,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            if (onItsWay) {
                val line = dev.aiharness.core.letters.WayLines.pick(who, conversation.id, conversation.lastUserMessageAt)
                Text("〒 ${who.name}'s reply is on its way · $line", style = PaperType.postscript, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(
                    conversation.preview ?: " ",
                    style = PaperType.preview.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = if (unread) paper.sumi else paper.usuzumi1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.align(Alignment.CenterStart)) {
            LetterBoxPicture(who, open)
            if (unread) {
                val harness = LocalAppContainer.current.harness
                val art by remember { harness.stickers.observeArt() }.collectAsStateWithLifecycle(emptyList())
                val mark = persona?.let { p -> StickerService.unreadMark(p, art.filter { it.ownerPersonaId == p.id }) }
                if (mark != null) {
                    StickerPicture(
                        StickerRef.Art(mark.id),
                        s.dayColors.ink(persona.ink),
                        Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-20).dp).size((40 * StickerBaker.BOX).dp).rotate(10f),
                        name = "Unread",
                    )
                } else {
                    UnreadSeal(persona?.ink, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).rotate(10f))
                }
            }
        }
    }
}

/** How far the paper strip goes in under the box. */
private val STRIP_TUCK = 26.dp

/** A strip of paper with its right end torn in small teeth. */
private val PaperStrip = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tooth = with(density) { 4.dp.toPx() }
        val teeth = 6
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            for (i in 1..teeth) {
                val y = size.height * i / teeth
                lineTo(if (i % 2 == 1) size.width - tooth else size.width, y)
            }
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
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
