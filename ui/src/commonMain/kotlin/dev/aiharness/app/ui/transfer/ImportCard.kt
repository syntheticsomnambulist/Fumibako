package dev.aiharness.app.ui.transfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.aiharness.app.platform.decodeImage
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.InkPicker
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.theme.InkNames
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.words
import dev.aiharness.core.transfer.CardChoices
import dev.aiharness.core.transfer.CardFormat
import dev.aiharness.core.transfer.CardPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import a card: her portrait, name, creator and tags, the card creator's notes (shown to you,
 * never sent), what comes in (with ticks), the card's own prompts (off, readable first), her ink
 * (suggested from the portrait) and what her card costs every letter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportCardScreen(nav: NavController) {
    val container = LocalAppContainer.current
    // Its own copy: clearing the waiting file when done mustn't take this page away a second time.
    val opened = remember { container.transfers.opened }
    val file = opened?.card
    if (file == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val preview = remember(file) { CardPreview(file) }
    val card = preview.card
    val portrait by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.Default) { file.portrait?.bytes?.let(::decodeImage) } }
    val inks = stationery.colors.inks
    val suggested by produceState<String?>(null, portrait) { value = portrait?.let { p -> withContext(Dispatchers.Default) { inkFromPicture(p, inks) } } }

    var greetings by rememberSaveable { mutableStateOf(true) }
    var example by rememberSaveable { mutableStateOf(true) }
    var lore by rememberSaveable { mutableStateOf(true) }
    var wontFade by rememberSaveable { mutableStateOf(true) }
    var seeLore by rememberSaveable { mutableStateOf(false) }
    var moods by rememberSaveable { mutableStateOf(true) }
    var system by rememberSaveable { mutableStateOf(false) }
    var readSystem by rememberSaveable { mutableStateOf(false) }
    var post by rememberSaveable { mutableStateOf(false) }
    var readPost by rememberSaveable { mutableStateOf(false) }
    var chosenInk by rememberSaveable { mutableStateOf<String?>(null) }
    var bringing by remember { mutableStateOf(false) }
    val ink = chosenInk ?: preview.fumibako?.ink ?: suggested ?: "sakura"
    val inkColor = stationery.ink(ink)
    val choices = CardChoices(greetings, example, lore, wontFade, moods, system, post, ink)
    val w = Persona(id = "", name = card.name, pronouns = preview.fumibako?.pronouns, createdAt = 0, updatedAt = 0).words
    val scope = rememberCoroutineScope()

    val tape = Tape(inkColor, preview.fumibako?.tapePattern ?: TapePattern.DOTS)
    val layouts = tapeLayouts("import-${card.name}", 6)
    TransferPage(nav, "Import a card", "Cancel") {
        item {
            Text(
                "${opened.name} · Character Card ${card.spec.name}" + if (file.format == CardFormat.CHARX) " (.charx)" else "",
                style = PaperType.meta.copy(letterSpacing = 0.sp),
                color = paper.kasure2,
            )
        }
        item {
            TapedCard(layouts[0], tape, tilt = cardTilt("import-who")) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Polaroid(portrait, card.name, inkColor)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.name, style = PaperType.letterName.copy(fontSize = 24.sp, lineHeight = 28.sp), color = paper.sumi)
                        val by = listOfNotNull(card.creator.takeIf { it.isNotBlank() }?.let { "by $it" }, card.version.takeIf { it.isNotBlank() }?.let { "version $it" })
                        if (by.isNotEmpty()) Text(by.joinToString(" · "), style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.kasure2)
                        if (preview.tagline.isNotBlank()) Text(preview.tagline, style = PaperType.prose.copy(fontSize = 14.sp, lineHeight = 20.sp), color = paper.usuzumi1)
                        if (card.tags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                card.tags.take(8).forEach { tag ->
                                    Text(
                                        tag,
                                        style = PaperType.meta.copy(letterSpacing = 0.sp),
                                        color = paper.usuzumi1,
                                        modifier = Modifier.border(1.dp, paper.nijimi1, PaperCorner).padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (card.creatorNotes.isNotBlank()) {
            item {
                TapedCard(layouts[1], tape, tilt = cardTilt("import-notes")) {
                    CardTitle("From the card's creator")
                    Text(card.creatorNotes.trim(), style = PaperType.prose.copy(fontSize = 14.sp, lineHeight = 20.sp), color = paper.sumi)
                    Note("Shown to you only; never sent to ${w.them}.")
                }
            }
        }
        item {
            TapedCard(layouts[2], tape, tilt = cardTilt("import-what")) {
                CardTitle("What comes in")
                TickRow(
                    "Who ${w.they} ${w.be}",
                    "Description, personality and scenario · ${counted(preview.whoWords)} words",
                    true,
                    null,
                    size = "always",
                    ink = inkColor,
                )
                if (preview.greetings > 0) {
                    val others = preview.greetings - 1
                    TickRow(
                        "Greetings · ${preview.greetings}",
                        if (others > 0) "${w.their.replaceFirstChar { it.uppercase() }} first letter, and $others other${if (others == 1) "" else "s"} to pick from when you start writing" else "${w.their.replaceFirstChar { it.uppercase() }} first letter",
                        greetings,
                        { greetings = it },
                        ink = inkColor,
                    )
                }
                if (preview.exampleWords > 0) TickRow("Example dialogue", "How ${w.they} talk${w.verb("s", "")} · ${counted(preview.exampleWords)} words", example, { example = it }, ink = inkColor)
                if (preview.lore.isNotEmpty()) {
                    val notes = listOfNotNull(
                        "Become lore memories in ${w.their} book",
                        preview.loreAlwaysInMind.takeIf { it > 0 }?.let { "$it always in mind" },
                        preview.loreSwitchedOff.takeIf { it > 0 }?.let { "$it switched off on the card, left out" },
                    )
                    TickRow("Lore · ${preview.lore.size} ${if (preview.lore.size == 1) "entry" else "entries"}", notes.joinToString(" · "), lore, { lore = it }, ink = inkColor)
                    if (lore) {
                        Column(Modifier.padding(start = 34.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TickRow("Won't fade", "Still recalled only when it's relevant", wontFade, { wontFade = it }, ink = inkColor)
                            InkLink(if (seeLore) "Hide them" else "See them", inkColor) { seeLore = !seeLore }
                            if (seeLore) preview.lore.take(40).forEach { entry -> LoreSlip(entry.content, entry.keys, entry.constant, wontFade, inkColor) }
                            if (seeLore && preview.lore.size > 40) Note("And ${preview.lore.size - 40} more.")
                        }
                    }
                }
                if (preview.moods.isNotEmpty()) {
                    TickRow("Mood portraits · ${preview.moods.size}", moodLine(preview.moods, w.their), moods, { moods = it }, ink = inkColor)
                }
            }
        }
        if (preview.systemPromptWords > 0 || preview.postHistoryWords > 0) {
            item {
                TapedCard(layouts[3], tape, tilt = cardTilt("import-prompts")) {
                    CardTitle("The card's own prompts")
                    Note("Written for other apps. Fumibako writes its own, so these start off; read them first.")
                    if (preview.systemPromptWords > 0) {
                        TickRow("Its system prompt", "Would replace Fumibako's role section · ${counted(preview.systemPromptWords)} words", system, { system = it }, ink = inkColor)
                        InkLink(if (readSystem) "Hide" else "Read it", inkColor) { readSystem = !readSystem }
                        if (readSystem) Quoted(card.systemPrompt)
                    }
                    if (preview.postHistoryWords > 0) {
                        TickRow("Its post-history instructions", "Would go at the end of each turn's context · ${counted(preview.postHistoryWords)} words", post, { post = it }, ink = inkColor)
                        InkLink(if (readPost) "Hide" else "Read it", inkColor) { readPost = !readPost }
                        if (readPost) Quoted(card.postHistoryInstructions)
                    }
                }
            }
        }
        item {
            TapedCard(layouts[4], tape, tilt = cardTilt("import-ink")) {
                CardTitle("${w.their.replaceFirstChar { it.uppercase() }} ink")
                InkPicker(ink, { chosenInk = it })
                val name = ink.replaceFirstChar { it.uppercase() }
                Note(
                    when {
                        chosenInk == null && preview.fumibako?.ink != null -> "$name, as ${w.their} card has it. Change it later on ${w.their} page."
                        chosenInk == null && suggested != null -> "$name, the nearest ink to ${w.their} portrait. Change it later on ${w.their} page."
                        else -> "$name. Change it later on ${w.their} page."
                    },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Every letter carries about ${counted(preview.tokens(choices))} tokens of ${w.their} card.",
                    style = PaperType.noteBody.copy(fontSize = 14.sp),
                    color = paper.sumi,
                )
                Note("{{char}} and {{user}} work as they do in SillyTavern. The rest of the card is kept, so exporting ${w.them} gives it back.")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperButton("Import ${card.name}", Modifier.weight(1f), ink = inkColor, enabled = !bringing) {
                    bringing = true
                    scope.launch {
                        val persona = runCatching { container.harness.cards.bring(file, choices) }.getOrNull()
                        dev.aiharness.app.ui.onMain {
                            if (persona != null) {
                                container.transfers.opened = null
                                opened.file.delete()
                                nav.popBackStack()
                                nav.navigate(Routes.personaPage(persona.id))
                            } else {
                                bringing = false
                            }
                        }
                    }
                }
                PaperButton("Cancel") { nav.popBackStack() }
            }
        }
    }
}

/** Her portrait as a small polaroid, tilted a little. */
@Composable
private fun Polaroid(picture: ImageBitmap?, name: String, ink: Color) {
    Column(
        Modifier
            .rotate(-2f)
            .width(104.dp)
            .background(paper.kami, PaperCorner)
            .border(1.dp, paper.nijimi0, PaperCorner)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(92.dp, 112.dp).background(paper.chitsu)) {
            if (picture != null) Image(picture, "$name's portrait", Modifier.size(92.dp, 112.dp), contentScale = ContentScale.Crop)
            else Box(Modifier.size(92.dp, 112.dp).background(ink.copy(alpha = 0.25f)))
        }
        Text(name, style = PaperType.letterName.copy(fontSize = 15.sp), color = paper.sumi, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** One lore entry as it will be in her memory book: its words, recall words, and seals. */
@Composable
private fun LoreSlip(text: String, keys: List<String>, always: Boolean, wontFade: Boolean, ink: Color) {
    Column(
        Modifier.fillMaxWidth().background(paper.kami, PaperCorner).border(1.dp, paper.nijimi0, PaperCorner).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LORE", style = PaperType.label.copy(fontSize = 10.sp), color = paper.kasure2)
            if (always) Text("always in mind", style = PaperType.meta.copy(letterSpacing = 0.sp), color = ink)
            Box(Modifier.weight(1f))
            if (wontFade) Text("永", style = PaperType.label.copy(fontSize = 13.sp), color = stationery.ink("beni"))
        }
        Text(text, style = PaperType.noteBody.copy(fontSize = 13.5.sp, lineHeight = 19.sp), color = paper.sumi)
        if (keys.isNotEmpty()) Text("Recall words: " + keys.joinToString(", "), style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.usuzumi0)
    }
}

/** A prompt from the card, as written. */
@Composable
private fun Quoted(text: String) {
    Text(
        text.trim(),
        style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
        color = paper.usuzumi1,
        modifier = Modifier.fillMaxWidth().background(paper.chitsu, PaperCorner).padding(10.dp),
    )
}

/** "Happy, sad, flustered and three more, beside her portrait". */
private fun moodLine(moods: List<String>, their: String): String {
    val named = moods.take(3).map { it.replace('_', ' ') }
    val more = moods.size - named.size
    val list = when {
        more > 0 -> named.joinToString(", ") + " and " + (NUMBERS.getOrNull(more) ?: more.toString()) + " more"
        named.size > 1 -> named.dropLast(1).joinToString(", ") + " and " + named.last()
        else -> named.single()
    }
    return list.replaceFirstChar { it.uppercase() } + ", beside $their portrait"
}

private val NUMBERS = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

/** The ink nearest [picture] (see [dev.aiharness.core.transfer.PortraitInk]), of the app's inks. */
internal fun inkFromPicture(picture: ImageBitmap, inks: Map<String, Color>): String? {
    val pixels = IntArray(picture.width * picture.height)
    picture.readPixels(pixels)
    return dev.aiharness.core.transfer.PortraitInk.nearest(pixels, picture.width, inks.filterKeys { it in InkNames }.mapValues { it.value.toArgb() })
}
