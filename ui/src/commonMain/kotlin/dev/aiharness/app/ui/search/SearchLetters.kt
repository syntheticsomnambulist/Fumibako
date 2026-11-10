package dev.aiharness.app.ui.search

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.chat.LetterFocus
import dev.aiharness.app.ui.chat.MarkedText
import dev.aiharness.app.ui.chat.Marks
import dev.aiharness.app.ui.chat.shortDay
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.StationeryIcons
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.letters.LetterHit
import dev.aiharness.core.letters.LetterKind
import dev.aiharness.core.letters.LetterWords
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.model.words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Recent searches, newest first, kept on this device. */
private const val KEY_RECENT = "letter_search_recent"
private const val RECENT_KEPT = 6

/**
 * Search letters: an index-card field ("A word, a name, a place"), whose letters (everyone, or one
 * persona) and what kind, recent searches while the field is empty, and the letters found, newest
 * first, as slips with the writer's tape and the words marked in the writer's ink. A result opens
 * its thread there, with a strip to step through the other matches. [personaId] starts it on one
 * persona's letters (from a thread's "Search these letters").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchLettersScreen(nav: NavController, personaId: String?) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val state = container.platform.appState
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val userPersonas by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)

    var query by rememberSaveable { mutableStateOf("") }
    var who by rememberSaveable { mutableStateOf(personaId) }
    var kind by rememberSaveable { mutableStateOf(LetterKind.EVERYTHING) }
    var recent by remember { mutableStateOf(state.getString(KEY_RECENT)?.lines()?.filter { it.isNotBlank() }.orEmpty()) }
    var hits by remember { mutableStateOf<List<LetterHit>?>(null) }
    val terms = remember(query) { LetterWords.terms(query) }

    LaunchedEffect(query, who, kind) {
        if (terms.isEmpty()) {
            hits = null
            return@LaunchedEffect
        }
        delay(200)
        hits = withContext(Dispatchers.Default) { harness.letterSearch.search(query, who, kind) }
    }

    fun keep(word: String) {
        val updated = (listOf(word) + recent.filterNot { it.equals(word, ignoreCase = true) }).take(RECENT_KEPT)
        recent = updated
        state.putString(KEY_RECENT, updated.joinToString("\n"))
    }

    fun open(hit: LetterHit) {
        val words = query.trim()
        keep(words)
        val matches = hits.orEmpty().filter { it.conversation.id == hit.conversation.id }.map { it.message.id }
        container.letterFocus.open(hit.conversation.id, hit.message.id, LetterFocus.Find(words, matches))
        nav.navigate(Routes.chat(hit.conversation.id))
    }

    val byId = personas.associateBy { it.id }
    val one = who?.let { byId[it] }
    val you = stationery.you
    val line = paper.nijimi0
    Column(Modifier.fillMaxSize().background(paper.hyoshi)) {
        // The header: back, the title, and the index-card field.
        Column(
            Modifier.fillMaxWidth().background(paper.kami)
                .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .statusBarsPadding().padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        ) {
            Row(Modifier.heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = paper.usuzumi0) }
                Text("Search letters", style = PaperType.screenTitle.copy(fontSize = 24.sp, lineHeight = 28.sp), color = paper.sumi)
            }
            SearchField(query, { query = it }, onSearch = { if (terms.isNotEmpty()) keep(query.trim()) }, Modifier.padding(start = 10.dp))
        }

        val listState = rememberLazyListState()
        LazyColumn(
            Modifier.fillMaxSize().scrollbar(listState),
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(Modifier.semantics { contentDescription = "Whose letters" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip("Everyone", who == null, you) { who = null }
                        personas.forEach { p -> FilterChip(p.name, who == p.id, you, persona = p) { who = p.id } }
                    }
                    FlowRow(Modifier.semantics { contentDescription = "What to search" }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            LetterKind.EVERYTHING to "Everything",
                            LetterKind.THEIRS to "Their letters",
                            LetterKind.YOURS to "Your notes",
                            LetterKind.PICTURES to "Pictures",
                        ).forEach { (k, label) -> FilterChip(label, kind == k, you) { kind = k } }
                    }
                }
            }
            val found = hits
            when {
                terms.isEmpty() -> if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column {
                            Text("RECENT", style = PaperType.label, color = paper.usuzumi0, modifier = Modifier.padding(bottom = 2.dp))
                            recent.forEach { word ->
                                Text(
                                    word,
                                    style = PaperType.preview.copy(fontSize = 14.5.sp),
                                    color = paper.sumi,
                                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Search for $word") { query = word }
                                        .heightIn(min = 44.dp).padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
                found == null -> Unit
                found.isEmpty() -> item(key = "none") {
                    Text("Nothing in the letters says “${query.trim()}”.", style = PaperType.preview, color = paper.usuzumi0)
                }
                else -> {
                    item(key = "count") {
                        Text("${found.size} found · newest first", style = PaperType.meta, color = paper.usuzumi0)
                    }
                    items(found, key = { it.message.id }) { hit ->
                        val persona = byId[hit.conversation.personaId]
                        val user = (hit.conversation.userPersonaId ?: settings?.activeUserPersonaId)?.let { id -> userPersonas.firstOrNull { it.id == id } }
                        ResultSlip(hit, persona, user, terms) { open(hit) }
                    }
                }
            }
            item(key = "what") {
                val their = one?.words?.their ?: "their"
                Text(
                    "Searches letters, your notes and what's in pictures. Not $their thoughts.",
                    style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp, lineHeight = 16.sp),
                    color = paper.usuzumi0,
                )
            }
        }
    }
}

/** The index-card field: a red rule along its top, a loupe, the words, and × to clear them. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val rule = lerp(paper.kami, stationery.ink("beni"), 0.4f)
    val card = if (paper.isNight) lerp(paper.kami, paper.hyoshi, 0.3f) else lerp(paper.kami, Color.White, 0.45f)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (value.isEmpty()) runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .paperCard(color = card, shape = RoundedCornerShape(0.dp))
            .drawBehind { drawLine(rule, Offset(0f, 5.dp.toPx()), Offset(size.width, 5.dp.toPx()), 1.5.dp.toPx()) }
            .padding(start = 12.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(StationeryIcons.Loupe, null, tint = paper.usuzumi0, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("A word, a name, a place", style = PaperType.preview.copy(fontSize = 15.sp), color = paper.kasure2)
            BasicTextField(
                value,
                onChange,
                Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search letters" },
                singleLine = true,
                textStyle = PaperType.preview.copy(fontSize = 15.sp, color = paper.sumi),
                cursorBrush = SolidColor(paper.sumi),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Text("×", style = PaperType.preview.copy(fontSize = 18.sp), color = paper.usuzumi0) }
        } else {
            Box(Modifier.size(40.dp))
        }
    }
}

/** A filter chip: pressed, it's edged and tinted in your ink. */
@Composable
private fun FilterChip(label: String, on: Boolean, ink: Color, persona: Persona? = null, onPick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .background(if (on) lerp(paper.kami, ink, 0.25f) else paper.kami, RoundedCornerShape(2.dp))
            .border(1.dp, if (on) ink else paper.nijimi0, RoundedCornerShape(2.dp))
            .toggleable(on, role = Role.Tab, onValueChange = { onPick() })
            .padding(start = if (persona != null) 6.dp else 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        persona?.let { PortraitDot(it, 16.dp) }
        Text(label, style = PaperType.navLabel.copy(fontSize = 12.sp), color = paper.sumi)
    }
}

/**
 * A letter found: a slip with its writer's tape, who wrote it to whom and when, and the sentences
 * around the first match with the words marked in the writer's ink (a picture shows a small polaroid).
 */
@Composable
private fun ResultSlip(hit: LetterHit, persona: Persona?, user: UserPersona?, terms: List<String>, onOpen: () -> Unit) {
    val s = stationery
    val name = persona?.name ?: "…"
    val inkName = if (hit.byYou) user?.ink ?: "sora" else persona?.ink
    val ink = s.ink(inkName)
    val tape = dev.aiharness.app.ui.paper.tape(ink, if (hit.byYou) user?.tapePattern ?: s.youPattern else persona?.tapePattern ?: TapePattern.DOTS, (if (hit.byYou) user?.tapeShade ?: s.youShade else persona?.tapeShade) ?: dev.aiharness.core.model.TapeShade.THEME)
    val day = shortDay(hit.at)
    val meta = when {
        hit.byYou && hit.inPicture -> "Your picture to $name, $day"
        hit.byYou -> "You to $name, $day"
        hit.inPicture -> "$name's picture, $day"
        else -> "$name, $day"
    }
    val where = when {
        hit.byYou -> "your note to $name"
        hit.inPicture -> "$name's picture"
        else -> "$name's letter"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .drawWithCache {
                val start = Offset(10.dp.toPx(), 0f)
                val end = Offset(54.dp.toPx(), 0f)
                onDrawWithContent {
                    drawContent()
                    rotate(-1f, pivot = start) { drawTape(start, end, 11.dp.toPx(), tape) }
                }
            }
            .paperCard()
            .clickable(onClickLabel = "Open $where, $day", onClick = onOpen)
            .padding(start = 12.dp, end = 12.dp, top = 13.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val picture = hit.message.images.firstOrNull()?.takeIf { hit.inPicture }
        if (picture != null) {
            Box(Modifier.rotate(-3f).background(lerp(paper.kami, Color.White, 0.5f)).padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 9.dp)) {
                BlobImage(picture.ref, Modifier.width(44.dp).size(width = 44.dp, height = 34.dp), maxSize = 256)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(meta, style = PaperType.meta.copy(letterSpacing = 0.06.sp), color = paper.usuzumi0)
            MarkedText(
                AnnotatedString(hit.passage),
                Marks(terms, lerp(paper.kami, s.dayColors.ink(inkName), 0.42f)),
                style = PaperType.preview.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                color = paper.sumi,
            )
        }
    }
}
