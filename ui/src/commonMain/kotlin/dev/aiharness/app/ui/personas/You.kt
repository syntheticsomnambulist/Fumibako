package dev.aiharness.app.ui.personas

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.onMain
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkPicker
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PolaroidFrame
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapePicker
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.paper.UserPortrait
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.settings.Stamp
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Your own personas, as polaroids like the persona gallery. */
@Composable
fun YouGalleryScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val pageScroll = rememberLazyListState()
    LazyColumn(
        Modifier.fillMaxSize().scrollbar(pageScroll),
        state = pageScroll,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                PageBar(onBack = { nav.popBackStack() }, backLabel = "Back to personas")
                Text("Your personas", style = PaperType.screenTitle, color = paper.sumi)
                Text("WHO YOU ARE IN YOUR LETTERS", style = PaperType.meta, color = paper.kasure2)
            }
        }
        val cells: List<UserPersona?> = users + null
        items(cells.chunked(2), key = { row -> row.joinToString { it?.id ?: "new" } }) { pair ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEachIndexed { column, user ->
                    Box(Modifier.weight(1f)) {
                        if (user == null) {
                            NewPolaroid("New you") { nav.navigate(Routes.youPage(Routes.NEW)) }
                        } else {
                            val letters = conversations.count { it.userPersonaId == user.id }
                            YouPolaroid(user, active = user.id == settings?.activeUserPersonaId, letters = letters, column = column) { nav.navigate(Routes.youPage(user.id)) }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Personas see the name and \"About you\" of whoever is writing. Each conversation remembers who it was.",
                style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = paper.usuzumi0,
            )
        }
    }
}

@Composable
private fun YouPolaroid(user: UserPersona, active: Boolean, letters: Int, column: Int, onClick: () -> Unit) {
    val s = stationery
    val seed = user.id.hashCode()
    PolaroidFrame(
        tape = dev.aiharness.app.ui.paper.tape(s.ink(user.ink), user.tapePattern, user.tapeShade),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${user.name}") { onClick() },
        tiltDegrees = if (column == 0) -1.8f - (seed % 3) * 0.3f else 1.6f + (seed % 3) * 0.3f,
        tapeTiltDegrees = if (column == 0) 4f else -5f,
        picture = { UserPortrait(user, it) },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(user.name, style = PaperType.polaroidName, color = s.frameInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (active) Stamp("Writing as", s.frameInks.ink(user.ink))
            Text(if (letters == 1) "1 letter" else "$letters letters", style = PaperType.meta.copy(letterSpacing = 0.sp), color = lerp(s.frameInk, s.frame, 0.38f), textAlign = TextAlign.Center)
        }
    }
}

/** One of your personas: about you, what each persona remembers about you, letters written as them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouPageScreen(nav: NavController, userId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val isNew = userId == Routes.NEW
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(null)
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var editing by rememberSaveable { mutableStateOf(isNew) }
    var draft by remember { mutableStateOf<UserPersona?>(null) }
    var original by remember { mutableStateOf<UserPersona?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isNew) {
        if (isNew && draft == null) {
            val now = System.currentTimeMillis()
            draft = UserPersona(id = newId(), name = "", createdAt = now, updatedAt = now)
            original = draft
        }
    }
    val stored = users?.firstOrNull { it.id == userId }
    if (editing && draft == null && stored != null) {
        draft = stored
        original = stored
    }
    val u = (if (editing) draft else stored) ?: return
    val ink = stationery.ink(u.ink)
    val tape = dev.aiharness.app.ui.paper.tape(ink, u.tapePattern, u.tapeShade)
    val active = settings?.activeUserPersonaId == u.id
    val layouts = tapeLayouts("you-${u.id}", 4)
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
            harness.userPersonas.upsert(d.copy(name = d.name.trim(), updatedAt = System.currentTimeMillis()))
            onMain {
                editing = false
                draft = null
                if (isNew) {
                    nav.popBackStack()
                    nav.navigate(Routes.youPage(d.id))
                }
            }
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
            PageBar(onBack = back) {
                if (editing) {
                    Text("EDITING", style = PaperType.meta, color = ink)
                    PaperButton("Done", ink = ink, enabled = u.name.isNotBlank()) { save() }
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    PolaroidFrame(
                        tape = tape,
                        modifier = Modifier.width(120.dp).then(if (editing) Modifier.clickable(role = Role.Button, onClickLabel = "Change the picture") { menu = true } else Modifier),
                        tiltDegrees = -2f,
                        framePadding = 8.dp,
                        bottomPadding = if (editing) 6.dp else 24.dp,
                        cornerTaped = true,
                        picture = { UserPortrait(u, it) },
                        caption = if (editing) {
                            { Text("CHANGE", style = PaperType.meta, color = stationery.frameInk, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
                        } else {
                            null
                        },
                    )
                    val canDraw = settings?.imageGen?.ready == true && (u.selfPrompt.isNotBlank() || u.outfit.isNotBlank())
                    PictureMenu(
                        open = menu,
                        onDismiss = { menu = false },
                        onPicked = { ref -> draft = u.copy(avatarRef = ref) },
                        onRemove = u.avatarRef?.let { { draft = u.copy(avatarRef = null) } },
                        onDraw = if (!canDraw) null else {
                            {
                                drawing = true
                                problem = null
                                scope.launch {
                                    try {
                                        val image = harness.images.generate(
                                            settings = harness.settings.current().imageGen,
                                            prompt = "portrait, upper body, looking at viewer, simple background",
                                            orientation = "square",
                                            parts = dev.aiharness.core.image.PicturePrompt.Parts.STYLE_ONLY.copy(you = true),
                                            you = u,
                                        )
                                        draft = (draft ?: u).copy(avatarRef = image.ref)
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing) {
                        problem?.let { Text(it, style = PaperType.noteBody.copy(fontSize = 12.sp), color = stationery.ink("beni")) }
                        IndexCardField("Name", u.name, { v -> draft = u.copy(name = v) }, ink = ink, style = PaperType.heroName.copy(fontSize = 28.sp, lineHeight = 36.sp), singleLine = true, placeholder = "Your name here")
                    } else {
                        Text(u.name, style = PaperType.heroName, color = paper.sumi)
                        if (active) Stamp("Writing as", ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InkChip(u.ink, ink)
                            TapeChip(u.tapePattern, ink)
                        }
                    }
                }
            }
        }
        if (editing) {
            item {
                TapedCard(layouts[0], tape, tilt = cardTilt("${u.id}-look")) {
                    CardTitle("Your look")
                    Text("INK", style = PaperType.meta, color = paper.kasure2)
                    InkPicker(u.ink, { v -> draft = u.copy(ink = v) })
                    Text("TAPE", style = PaperType.meta, color = paper.kasure2)
                    TapePicker(u.ink, u.tapePattern, { v -> draft = u.copy(tapePattern = v) }, shade = u.tapeShade, onShade = { v -> draft = u.copy(tapeShade = v) })
                    Text("YOUR HAND", style = PaperType.meta, color = paper.kasure2)
                    dev.aiharness.app.ui.paper.HandPicker(u.hand, stationery.ink(u.ink), "Captions on what you send, and labels on your keepsakes.") { v -> draft = u.copy(hand = v) }
                }
            }
            item {
                TapedCard(layouts[1], tape, tilt = cardTilt("${u.id}-about")) {
                    CardTitle("About you")
                    IndexCardField("Shared with the personas you write to", u.description, { v -> draft = u.copy(description = v) }, ink = ink, style = PaperType.prose, minLines = 3)
                }
            }
            item {
                TapedCard(layouts[2], tape, tilt = cardTilt("${u.id}-pictures")) {
                    CardTitle("In pictures")
                    IndexCardField("How you look", u.selfPrompt, { v -> draft = u.copy(selfPrompt = v) }, ink = ink, style = PaperType.noteBody, minLines = 2, placeholder = "1boy, messy brown hair, glasses")
                    IndexCardField("What you wear", u.outfit, { v -> draft = u.copy(outfit = v) }, ink = ink, style = PaperType.noteBody, placeholder = "green raincoat, scarf")
                    Text(
                        "As image tags. With these, a picture in a letter can have you in it with the persona, and you can draw your own portrait (tap the picture).",
                        style = PaperType.noteBody.copy(fontSize = 12.sp),
                        color = paper.usuzumi0,
                    )
                }
            }
            if (!isNew && (users?.size ?: 0) > 1) {
                item { PaperButton("Delete ${u.name}", Modifier.fillMaxWidth()) { confirmDelete = true } }
            }
        } else {
            item {
                PaperButton("New letter as ${u.name}", Modifier.fillMaxWidth(), ink = ink) {
                    scope.launch {
                        harness.settings.update { it.copy(activeUserPersonaId = u.id) }
                        onMain { nav.navigate(Routes.CHATS) }
                    }
                }
            }
            item {
                TapedCard(layouts[0], tape, tilt = cardTilt("${u.id}-about")) {
                    CardTitle("About you")
                    Text(u.description.ifBlank { "Nothing yet. Edit to tell your personas who you are." }, style = PaperType.prose, color = if (u.description.isBlank()) paper.usuzumi0 else paper.sumi)
                    Text("Shared with the personas you write to as ${u.name}.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
                }
            }
            item {
                val counts by produceState(emptyMap<String, Int>(), personas, u.id) {
                    value = personas.associate { p -> p.id to harness.memory.all(p.id).count { it.aboutUserPersonaId == u.id && !it.isReplaced } }
                }
                TapedCard(layouts[1], tape, tilt = cardTilt("${u.id}-remember")) {
                    CardTitle("What they remember")
                    val remembering = personas.filter { (counts[it.id] ?: 0) > 0 }
                    if (remembering.isEmpty()) {
                        Text("No memories kept about ${u.name} in particular yet. Memories about you in general sit in each persona's book.", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi0)
                    }
                    remembering.forEach { p ->
                        val n = counts[p.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.navigate(Routes.memory(p.id)) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PortraitDot(p, 20.dp)
                            Text("${p.name} keeps ${if (n == 1) "1 memory" else "$n memories"} about ${u.name}", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1, modifier = Modifier.weight(1f))
                            Text("OPEN", style = PaperType.meta, color = stationery.ink(p.ink))
                        }
                    }
                }
            }
            item {
                val mine = conversations.filter { it.userPersonaId == u.id }.sortedByDescending { it.updatedAt }
                TapedCard(layouts[2], tape, tilt = cardTilt("${u.id}-letters")) {
                    CardTitle("Letters as ${u.name}")
                    if (mine.isEmpty()) Text("None yet.", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi0)
                    mine.take(8).forEach { c ->
                        val p = personas.firstOrNull { it.id == c.personaId }
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.navigate(Routes.chat(c.id)) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PortraitDot(p, 20.dp)
                            Text(p?.name ?: c.title, style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi1, modifier = Modifier.weight(1f))
                            Text(letterTime(c.updatedAt), style = PaperType.meta, color = paper.kasure2)
                        }
                    }
                }
            }
            item {
                TapedCard(layouts[3], tape, tilt = cardTilt("${u.id}-default")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Default for new letters", style = PaperType.entryTitle, color = paper.sumi)
                            Text("You can switch inside any letter.", style = PaperType.noteBody.copy(fontSize = 12.sp), color = paper.usuzumi0)
                        }
                        TickBox(active, { on -> if (on) scope.launch { harness.settings.update { it.copy(activeUserPersonaId = u.id) } } }, ink = ink)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${u.name}?", style = PaperType.entryTitle) },
            text = { Text("Letters written as ${u.name} stay; they'll show your default persona instead.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        harness.userPersonas.delete(u.id)
                        harness.plannerEntries.deletePlanner(u.id)
                        if (active) {
                            val next = harness.userPersonas.observeAll().first().firstOrNull { it.id != u.id }?.id
                            harness.settings.update { s -> s.copy(activeUserPersonaId = next) }
                        }
                        onMain { nav.popBackStack() }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
