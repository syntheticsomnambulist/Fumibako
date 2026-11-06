package dev.aiharness.app.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.chat.ThreadRow
import dev.aiharness.app.ui.chat.buildThread
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.cornerTape
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.Harness
import dev.aiharness.core.memory.FeelingFamily
import dev.aiharness.core.memory.FeelingWords
import dev.aiharness.core.memory.MemoryWeights
import dev.aiharness.core.memory.TextTokens
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The book's tabs: everything, or one kind of memory. */
enum class BookTab(val label: String) {
    ALL("All"),
    ALWAYS("Always"),
    ABOUT_YOU("About you"),
    HAPPENED("Happened"),
    THOUGHTS("Thoughts"),
    ;

    fun includes(kind: MemoryKind, isCore: Boolean): Boolean = when (this) {
        ALL -> true
        ALWAYS -> isCore
        ABOUT_YOU -> kind == MemoryKind.FACT || kind == MemoryKind.CORE
        HAPPENED -> kind == MemoryKind.EPISODE
        THOUGHTS -> kind == MemoryKind.REFLECTION
    }
}

/** A picture from one of the persona's letters. */
data class LetterPicture(val ref: String, val caption: String?, val conversationId: String)

/** What the book knows about the persona's letters: quotes, pictures by day, letters per month. */
class LetterIndex(
    val messages: Map<String, ChatMessage> = emptyMap(),
    val pictures: Map<LocalDate, List<LetterPicture>> = emptyMap(),
    val lettersPerMonth: Map<YearMonth, Int> = emptyMap(),
) {
    companion object {
        suspend fun load(harness: Harness, conversations: List<Conversation>, zone: ZoneId): LetterIndex {
            val messages = mutableMapOf<String, ChatMessage>()
            val pictures = mutableMapOf<LocalDate, MutableList<LetterPicture>>()
            val letters = mutableMapOf<YearMonth, Int>()
            conversations.forEach { c ->
                val all = harness.conversations.getMessages(c.id)
                all.forEach { m ->
                    if (m.role == MessageRole.ASSISTANT) messages[m.id] = m
                    m.parts.filterIsInstance<ContentPart.Image>().forEach { image ->
                        if (m.role != MessageRole.USER) pictures.getOrPut(dayOf(m.createdAt, zone)) { mutableListOf() } += LetterPicture(image.ref, image.caption, c.id)
                    }
                }
                buildThread(all, zone).filterIsInstance<ThreadRow.Letter>().forEach { letter ->
                    val month = YearMonth.from(dayOf(letter.createdAt, zone))
                    letters[month] = (letters[month] ?: 0) + 1
                }
            }
            return LetterIndex(messages, pictures, letters)
        }
    }
}

fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * A short line from a letter to quote in the book: the opening sentences, without actions
 * (*waves*), mood tags or markdown, up to about a hundred characters.
 */
fun quoteFrom(text: String): String? {
    val plain = text
        .replace(Regex("\\[mood:[^]]*]", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("\\*[^*]+\\*"), " ")
        .replace(Regex("[_`#>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (plain.isEmpty()) return null
    val sentences = Regex("[^.!?…]+[.!?…]+[\"”’)]*|[^.!?…]+$").findAll(plain).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
    val quote = StringBuilder()
    for (s in sentences) {
        if (quote.isNotEmpty() && quote.length + s.length + 1 > 110) break
        if (quote.isNotEmpty()) quote.append(' ')
        quote.append(s)
        if (quote.length >= 24) break
    }
    val line = quote.toString().take(120).trim()
    return line.takeIf { it.length >= 4 }
}

/**
 * A persona's memories as a scrapbook: tabs by kind, filters by feeling and by who it's about,
 * days as small collages (a date stamp, the day's picture, a line from a letter, slips and notes),
 * and earlier months tucked into envelopes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryBookScreen(nav: NavController, personaId: String) {
    val harness = LocalAppContainer.current.harness
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() }
    val memories by remember(personaId) { harness.memories.observeForPersona(personaId) }.collectAsStateWithLifecycle(null)
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val conversations by remember { harness.conversations.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val mine = remember(conversations, personaId) { conversations.filter { it.personaId == personaId } }
    val letters by produceState(LetterIndex(), mine) { value = withContext(Dispatchers.IO) { LetterIndex.load(harness, mine, zone) } }

    var tab by rememberSaveable { mutableStateOf(BookTab.ALL) }
    var felt by rememberSaveable { mutableStateOf<FeelingFamily?>(null) }
    var about by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val openMonths = remember { mutableStateListOf<YearMonth>() }

    val persona = personas.firstOrNull { it.id == personaId }
    val name = persona?.name ?: "…"
    val ink = stationery.ink(persona?.ink)
    val tape = Tape(ink, persona?.tapePattern ?: TapePattern.DOTS)
    val all = memories ?: return
    val s = settings ?: return
    val canEdit = s.memory.userCanEdit

    val pages = remember(all, s.learnedFeelings, s.memory.showAgeing) {
        val byId = all.associateBy { it.id }
        val updates = all.mapNotNull { it.replacedById }.toSet()
        all.map { m ->
            val feeling = FeelingWords.lookup(m.currentMood, s.learnedFeelings)
            BookPage(
                memory = m,
                feeling = feeling,
                age = if (s.memory.showAgeing && !m.isReplaced) MemoryWeights.age(m, now, feeling) else null,
                replacedBy = m.replacedById?.let(byId::get),
                isUpdate = m.id in updates,
            )
        }
    }
    val terms = remember(query) { TextTokens.tokenize(query).toSet() }
    fun matches(p: BookPage): Boolean {
        val m = p.memory
        if (!tab.includes(m.kind, m.isCore)) return false
        if (felt != null && p.feeling?.family != felt) return false
        if (about != null && m.aboutUserPersonaId != about) return false
        if (terms.isNotEmpty()) {
            val words = TextTokens.tokenize(m.content + " " + m.tags.joinToString(" ") + " " + m.currentMood.orEmpty())
            if (words.none { it in terms }) return false
        }
        return true
    }
    val shown = pages.filter(::matches)
    val showAlways = tab == BookTab.ALL || tab == BookTab.ALWAYS
    val always = if (showAlways) shown.filter { it.memory.isCore && !it.memory.isReplaced }.sortedByDescending { it.memory.level.ordinal } else emptyList()
    val dayPages = if (tab == BookTab.ALWAYS) emptyList() else shown.filter { !(showAlways && it.memory.isCore && !it.memory.isReplaced) }
    val days = dayPages.groupBy { dayOf(it.memory.createdAt, zone) }.toSortedMap(compareByDescending { it })
    val months = days.keys.groupBy { YearMonth.from(it) }
    val newest = months.keys.maxOrNull()
    val filtering = felt != null || about != null || terms.isNotEmpty()
    val kept = all.count { !it.isReplaced }
    val since = all.minOfOrNull { it.createdAt }
    val scopedUsers = users.filter { u -> all.any { it.aboutUserPersonaId == u.id } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("bar") {
            BookBar(
                title = "$name's memory book",
                meta = listOfNotNull(if (kept == 1) "1 memory" else "$kept memories", since?.let { "since ${longDate(dayOf(it, zone))}" }).joinToString(" · "),
                searching = searching,
                onBack = { nav.popBackStack() },
                onSearch = {
                    searching = !searching
                    if (!searching) query = ""
                },
                onAdd = if (canEdit) ({ nav.navigate(Routes.memoryCard(personaId, Routes.NEW)) }) else null,
            )
        }
        if (searching) {
            item("search") { IndexCardField("Search $name's memories", query, { query = it }, ink = ink, style = PaperType.prose, singleLine = true, placeholder = "A word, a name, a feeling") }
        }
        item("tabs") { BookTabs(tab, ink) { tab = it } }
        item("filters") {
            Row(Modifier.fillMaxWidth().padding(top = 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterTag(
                    "Felt: ${felt?.label ?: "any"}",
                    options = listOf<Pair<FeelingFamily?, String>>(null to "Any feeling") + FeelingFamily.entries.map { it to it.label },
                    onPick = { felt = it },
                )
                if (scopedUsers.isNotEmpty()) {
                    FilterTag(
                        "About: ${users.firstOrNull { it.id == about }?.name ?: "anyone"}",
                        options = listOf<Pair<String?, String>>(null to "Anyone") + scopedUsers.map { it.id to it.name },
                        onPick = { about = it },
                    )
                }
                Box(Modifier.weight(1f))
                Text("NEWEST FIRST", style = PaperType.meta, color = paper.kasure2)
            }
        }
        if (always.isNotEmpty()) {
            item("always") { AlwaysInMind(always, ink) { nav.navigate(Routes.memoryCard(personaId, it)) } }
        }
        if (all.isEmpty()) {
            item("empty") {
                TornNote("Nothing kept yet. Memories gather here as you write to each other.", null, color = paper.usuzumi1) {}
            }
        } else if (always.isEmpty() && days.isEmpty()) {
            item("none") { Text("Nothing here matches.", style = PaperType.prose.copy(fontStyle = FontStyle.Italic), color = paper.usuzumi0) }
        }
        months.forEach { (month, monthDays) ->
            val open = filtering || month == newest || month in openMonths
            if (month != newest && !filtering) {
                item("env-$month") {
                    val count = monthDays.sumOf { days[it]?.size ?: 0 }
                    val letterCount = letters.lettersPerMonth[month] ?: 0
                    MonthEnvelope(
                        monthName(month),
                        listOfNotNull(if (count == 1) "1 memory" else "$count memories", letterCount.takeIf { it > 0 }?.let { if (it == 1) "1 letter" else "$it letters" }).joinToString(" · "),
                        open = open,
                        ink = ink,
                    ) { if (month in openMonths) openMonths.remove(month) else openMonths.add(month) }
                }
            }
            if (open) {
                items(monthDays, key = { "day-$it" }) { day ->
                    BookDay(
                        day = day,
                        pages = days[day].orEmpty().sortedBy { it.memory.createdAt },
                        letters = letters,
                        name = name,
                        ink = ink,
                        tape = tape,
                        onOpen = { nav.navigate(Routes.memoryCard(personaId, it)) },
                        onLetter = { nav.navigate(Routes.chat(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookBar(title: String, meta: String, searching: Boolean, onBack: () -> Unit, onSearch: () -> Unit, onAdd: (() -> Unit)?) {
    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = paper.usuzumi0) }
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(title, style = PaperType.entryTitle.copy(fontSize = 22.sp, lineHeight = 26.sp), color = paper.sumi)
            Text(meta.uppercase(), style = PaperType.meta, color = paper.kasure2)
        }
        IconButton(onClick = onSearch) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, if (searching) "Close search" else "Search", tint = paper.usuzumi0) }
        if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add a memory", tint = paper.usuzumi0) }
    }
}

/** Index tabs along the top of the book. */
@Composable
private fun BookTabs(selected: BookTab, ink: Color, onSelect: (BookTab) -> Unit) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(colors.nijimi1, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BookTab.entries.forEach { t ->
            val on = t == selected
            val edge = if (on) colors.nijimi1 else colors.nijimi0
            Text(
                t.label,
                style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                color = if (on) colors.sumi else colors.usuzumi1,
                maxLines = 1,
                modifier = Modifier
                    .background(if (on) colors.kami else colors.chitsu)
                    .drawBehind {
                        val w = 1.dp.toPx()
                        drawLine(edge, Offset(0f, size.height), Offset(0f, 0f), w)
                        drawLine(edge, Offset(0f, 0f), Offset(size.width, 0f), w)
                        drawLine(edge, Offset(size.width, 0f), Offset(size.width, size.height), w)
                        if (on) drawLine(ink, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
                    }
                    .clickable(role = Role.Tab, onClickLabel = t.label) { onSelect(t) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun <T> FilterTag(label: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = paper
    Box {
        Text(
            "$label ▾",
            style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.usuzumi1,
            modifier = Modifier
                .background(colors.kami, PaperCorner)
                .drawBehind { drawRect(colors.nijimi1, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}

/** The index card of memories kept always in mind. */
@Composable
private fun AlwaysInMind(pages: List<BookPage>, ink: Color, onOpen: (String) -> Unit) {
    val colors = paper
    Column(
        Modifier
            .rotate(0.5f)
            .cornerTape(Tape(ink, TapePattern.PLAIN), length = 32.dp, thickness = 12.dp, reach = 10.dp)
            .background(colors.kami, PaperCorner)
            .drawBehind { drawRect(colors.nijimi1, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            "ALWAYS IN MIND",
            style = PaperType.meta,
            color = colors.kasure2,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ink.copy(alpha = 0.75f), Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }
                .padding(bottom = 5.dp),
        )
        pages.forEach { p ->
            Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(p.memory.id) }.padding(top = 4.dp)) {
                RuledText(p.memory.content, PaperType.prose.copy(fontSize = 14.5.sp, lineHeight = 24.sp), agedInk(p.age))
            }
        }
    }
}

/** One day of the book: its date stamp, then a small collage of what was kept. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDay(
    day: LocalDate,
    pages: List<BookPage>,
    letters: LetterIndex,
    name: String,
    ink: Color,
    tape: Tape,
    onOpen: (String) -> Unit,
    onLetter: (String) -> Unit,
) {
    val sources = pages.mapNotNull { p -> p.memory.sourceMessageId?.let { letters.messages[it] } }
    val away = sources.isNotEmpty() && sources.size == pages.size && sources.all { it.origin == MessageOrigin.HEARTBEAT }
    val picture = letters.pictures[day]?.firstOrNull()
    val quoted = sources.firstNotNullOfOrNull { m -> quoteFrom(m.displayText)?.let { it to m } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateStamp(shortDate(day), ink, tilt = seededTilt(day.toString(), 3f))
            Text(day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = PaperType.dayDivider.copy(fontSize = 18.sp), color = paper.usuzumi0)
            if (away) Text("WHILE YOU WERE AWAY", style = PaperType.meta, color = paper.kasure2)
        }
        val quote: @Composable (() -> Unit)? = quoted?.let { (line, message) ->
            {
                TornNote("“$line”", "A line from $name's letter", seed = message.id) { onLetter(message.conversationId) }
            }
        }
        val small = pages.filter { !it.memory.isReplaced && !it.isUpdate && it.memory.kind != MemoryKind.EPISODE }
        // Beside the day's picture: the quote, or else the first small pieces of the day.
        val beside = if (picture != null && quote == null) small.take(2) else emptyList()
        @Composable
        fun piece(p: BookPage) {
            if (p.memory.kind == MemoryKind.REFLECTION) ThoughtNote(p, ink, name) { onOpen(p.memory.id) } else LabelSlip(p, ink) { onOpen(p.memory.id) }
        }
        if (picture != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                BookPolaroid(picture.ref, picture.caption, ink, "${day}-pic", Modifier.padding(top = 10.dp)) { onLetter(picture.conversationId) }
                Column(Modifier.weight(1f).padding(top = 18.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    quote?.invoke()
                    beside.forEach { piece(it) }
                }
            }
        } else {
            quote?.invoke()
        }
        pages.filter { it.memory.isReplaced }.forEach { ReplacedPage(it, ink) { onOpen(it.memory.id) } }
        pages.filter { !it.memory.isReplaced && it.memory.kind == MemoryKind.EPISODE }.forEach { HappenedPage(it, ink, tape) { onOpen(it.memory.id) } }
        pages.filter { !it.memory.isReplaced && it.isUpdate && it.memory.kind != MemoryKind.EPISODE }.forEach { UpdatedPage(it, ink) { onOpen(it.memory.id) } }
        val rest = small - beside.toSet()
        if (rest.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 6.dp, end = 10.dp)) {
                rest.forEach { piece(it) }
            }
        }
    }
}

fun shortDate(day: LocalDate): String = "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"

fun longDate(day: LocalDate, today: LocalDate = LocalDate.now()): String {
    val month = day.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (day.year == today.year) "${day.dayOfMonth} $month" else "${day.dayOfMonth} $month ${day.year}"
}

private fun monthName(month: YearMonth, today: LocalDate = LocalDate.now()): String {
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return if (month.year == today.year) name else "$name ${month.year}"
}
