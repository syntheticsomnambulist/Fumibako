package dev.aiharness.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.STICKER_PREFIX
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.app.ui.formatChatText
import dev.aiharness.app.ui.paper.MoodWord
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapeSide
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.paper.washiStrip
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.app.ui.theme.Stationery
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.core.media.MediaSupport
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.app.platform.onRightClick
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.FoundLetter
import dev.aiharness.core.model.Words
import dev.aiharness.core.model.words
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.UserPersona
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** What is still being written into a letter. */
data class Writing(val text: String, val reasoning: String, val status: String)

/** "Today", "Yesterday", or "Wednesday 4 November", centred between letters. */
@Composable
fun DayHeading(date: LocalDate, today: LocalDate = LocalDate.now()) {
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> buildString {
            append(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            append(' ').append(date.dayOfMonth).append(' ')
            append(date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            if (date.year != today.year) append(' ').append(date.year)
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(label, style = PaperType.dayDivider, color = paper.usuzumi0)
    }
}

/**
 * One of the persona's letters: a sheet of paper held by its washi strip, with its portrait, name,
 * mood and time, then the letter, any pictures, and postscripts for memories it kept.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LetterSheet(
    letter: ThreadRow.Letter?,
    persona: Persona?,
    writing: Writing?,
    onLongPress: () -> Unit,
    onImage: (String) -> Unit,
    stickers: List<Sticker> = emptyList(),
    user: UserPersona? = null,
    stickerName: (Sticker) -> String? = { null },
    handling: StickerHandling? = null,
    /** A letter its P.S. or index card names, to open the thread there. */
    onOpenLetter: (FoundLetter) -> Unit = {},
    /** A search being stepped through: its words marked, and a ring when this holds the current match. */
    find: FindView? = null,
) {
    val s = stationery
    val ink = s.ink(persona?.ink)
    val mood = writing?.text?.let { dev.aiharness.core.model.MoodTags.parse(it) } ?: letter?.mood
    val words = persona?.words ?: Words("…", null)
    val personaName = persona?.name ?: "…"
    val slip = remember(letter) { letter?.slip?.takeUnless { it.isEmpty } }
    val reasoning = listOfNotNull(letter?.reasoning, writing?.reasoning?.takeIf { it.isNotBlank() }).joinToString("\n\n")
    var mindOpen by remember(letter?.key) { mutableStateOf(false) }
    var thoughtsOpen by remember(letter?.key) { mutableStateOf(false) }
    val hasTabs = slip != null || reasoning.isNotBlank()
    Box(Modifier.fillMaxWidth()) {
    // The tabs stand behind the letter's top edge, at the right (its washi strip keeps the left).
    if (hasTabs) {
        LetterTabs(
            words, ink,
            hasMind = slip != null, mindOpen = mindOpen, onMind = { mindOpen = !mindOpen },
            hasThoughts = reasoning.isNotBlank(), thoughtsOpen = thoughtsOpen, onThoughts = { thoughtsOpen = !thoughtsOpen },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp),
        )
    }
    StuckOn(
        stickers,
        inkOf = { stickerInk(it, s, persona, user) },
        Modifier.fillMaxWidth().padding(top = if (hasTabs) TabsAbove else 0.dp).then(letter?.let { handling?.paper(StickerTarget.message(it.messages.first().id)) } ?: Modifier),
        stickerName,
        selectedId = handling?.selectedId,
        onTap = handling?.onTap,
        onChange = handling?.onChange,
    ) {
        Column(
            Modifier
                .matchRing(find?.ringed == true, ink)
                .washiStrip(Tape(ink, persona?.tapePattern ?: TapePattern.DOTS), tiltDegrees = -0.7f, overhang = 8.dp)
                .paperCard()
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Letter options")
                .onRightClick(onLongPress)
                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortraitDot(persona, 26.dp)
                Text(persona?.name ?: "…", style = PaperType.letterName, color = paper.sumi)
                Box(Modifier.weight(1f)) { MoodWord(mood, ink) }
                letter?.let { Text(letterStamp(it.arrivedAt), style = PaperType.meta, color = paper.kasure2) }
            }

            // Its slips, in the order of the turn: what it had, what it made of it, then what it wrote.
            SlipOut(mindOpen && slip != null) {
                slip?.let { MindCard(it, words, ink, letter?.let { l -> letterStamp(l.arrivedAt) }, user?.name ?: "You", personaName, onOpenLetter) }
            }
            SlipOut(thoughtsOpen && reasoning.isNotBlank()) { ThoughtsScrap(reasoning, words) }

            letter?.blocks?.forEach { block ->
                when (block) {
                    is LetterBlock.Words -> StickerText(block.text, ink, PaperType.letterBody, paper.sumi, find?.marks)
                    // Photos in letters are polaroids taped in the persona's ink; attachments get their frames.
                    is LetterBlock.Picture -> FramedPictures(
                        listOf(block.image), s.dayColors.ink(persona?.ink), persona?.tapePattern ?: TapePattern.DOTS, 1f,
                        handling, { stickerInk(it, s, persona, user) }, stickerName, onImage,
                    )
                    is LetterBlock.Attached -> FramedAttachment(
                        block.attachment, s.dayColors.ink(persona?.ink), paper.kami, inNote = false,
                        handling = handling, inkOf = { stickerInk(it, s, persona, user) }, nameOf = stickerName,
                    )
                }
            }
            if (writing != null) {
                val text = dev.aiharness.core.model.MoodTags.stripStreaming(writing.text)
                if (text.isNotBlank()) StickerText(text, ink, PaperType.letterBody, paper.sumi)
                // Straight away: a plain line, its ellipsis writing itself (no lines of her own, none per tool).
                StillWriting(persona?.name ?: "They")
            }
            find?.inPicture?.takeIf { find.ringed }?.let { Text(it, style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp, lineHeight = 15.sp), color = paper.usuzumi0) }
            letter?.errors?.forEach { Text(it, style = PaperType.postscript, color = s.ink("beni")) }
            if (letter?.fromHeartbeat == true) {
                Text("Written while you were away", style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = ink)
            }
            letter?.postscripts?.forEach { ps ->
                val sumi = paper.sumi
                Text(
                    buildAnnotatedString {
                        append("P.S. ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Medium, color = if (ps.isError) s.ink("beni") else ink)) { append(ps.label) }
                        ps.text?.let {
                            append(" ")
                            append(it)
                        }
                        ps.found?.let { found ->
                            append(" ")
                            val link = LinkAnnotation.Clickable(
                                "found",
                                TextLinkStyles(SpanStyle(color = sumi, textDecoration = TextDecoration.Underline)),
                            ) { onOpenLetter(found) }
                            withLink(link) { append(foundName(found, personaName)) }
                        }
                    },
                    style = PaperType.postscript,
                    color = paper.usuzumi1,
                )
            }
        }
    }
    }
}

/**
 * How the stickers in the thread respond: which one is selected, what tapping or moving one does,
 * and [paper], which a letter or note puts on its paper so stickers can be dropped onto it.
 */
class StickerHandling(
    val selectedId: String?,
    val onTap: (Sticker) -> Unit,
    val onChange: (Sticker) -> Unit,
    val paper: (StickerTarget) -> Modifier,
    /** The stickers on a frame (by attachment id, or a picture's ref). */
    val on: (String) -> List<Sticker> = { emptyList() },
    /** A frame or sheet that left the screen can't be dropped on. */
    val forget: (StickerTarget) -> Unit = {},
)

/** The ink of whoever stuck [sticker] (day ink: stickers keep their day colours). */
fun stickerInk(sticker: Sticker, s: Stationery, persona: Persona?, user: UserPersona?): Color =
    if (sticker.byPersonaId != null) s.dayColors.ink(persona?.ink) else s.dayColors.ink(user?.ink ?: "sora")

/** One of the user's notes: a smaller slip on the right with their own tape. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteSlip(
    message: ChatMessage,
    user: UserPersona?,
    onLongPress: () -> Unit,
    onImage: (String) -> Unit,
    stickers: List<Sticker> = emptyList(),
    persona: Persona? = null,
    stickerName: (Sticker) -> String? = { null },
    handling: StickerHandling? = null,
    find: FindView? = null,
) {
    val s = stationery
    val you = user?.ink?.let { s.ink(it) } ?: s.you
    val pattern = user?.tapePattern ?: s.youPattern
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        StuckOn(
            stickers,
            inkOf = { stickerInk(it, s, persona, user) },
            Modifier.widthIn(max = maxWidth * 0.78f).then(handling?.paper(StickerTarget.message(message.id)) ?: Modifier),
            stickerName,
            selectedId = handling?.selectedId,
            onTap = handling?.onTap,
            onChange = handling?.onChange,
        ) {
            Column(
                Modifier
                    .matchRing(find?.ringed == true, s.ink(persona?.ink))
                    .washiStrip(Tape(you, pattern), side = TapeSide.End, tiltDegrees = 0.9f, overhang = 7.dp)
                    .paperCard(color = lerp(s.colors.kami, you, 0.06f))
                    .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Note options")
                    .onRightClick(onLongPress)
                    .padding(start = 13.dp, end = 20.dp, top = 9.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    listOfNotNull(user?.name, letterStamp(message.createdAt)).joinToString(" · ").uppercase(),
                    style = PaperType.meta,
                    color = you,
                )
                if (message.displayText.isNotBlank()) {
                    StickerText(message.displayText, you, PaperType.noteBody, paper.usuzumi1, find?.marks)
                }
                val noteInk = s.dayColors.ink(user?.ink ?: "sora")
                val notePaper = lerp(s.colors.kami, you, 0.06f)
                val reader = LocalPersonaReader.current
                val media = dev.aiharness.app.ui.media.LocalMediaSettings.current
                FramedPictures(
                    message.images, noteInk, pattern, 0.86f, handling, { stickerInk(it, s, persona, user) }, stickerName, onImage, Alignment.CenterStart,
                )
                find?.inPicture?.takeIf { find.ringed }?.let { Text(it, style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp, lineHeight = 15.sp), color = paper.usuzumi0) }
                if (message.images.isNotEmpty()) reader?.let { MediaSupport.cantSee(it.profile, it.name, message.images.size > 1) }?.let { CantTakeIn(it) }
                message.attachments.forEach { attachment ->
                    FramedAttachment(attachment, noteInk, notePaper, inNote = true, handling = handling, inkOf = { stickerInk(it, s, persona, user) }, nameOf = stickerName, alignment = Alignment.CenterStart)
                    reader?.let { MediaSupport.cantTakeIn(attachment, it.profile, it.name, opensLinks = media.personasOpenLinks) }?.let { CantTakeIn(it) }
                }
            }
        }
    }
}

/** Who reads your notes here, and with which model: what decides the line under a frame. */
data class PersonaReader(val name: String, val profile: ModelProfile)

val LocalPersonaReader = staticCompositionLocalOf<PersonaReader?> { null }

/** The app's line under a frame on what the persona can't take in, in the postscript style. */
@Composable
private fun CantTakeIn(text: String) {
    Text(text, style = PaperType.postscript, color = paper.kasure2, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
}

/** Stickers that can be written inline as `:name:` here (the persona's own and the shared ones), by name. */
val LocalInlineStickers = staticCompositionLocalOf<Map<String, StickerArt>> { emptyMap() }

/** Letter text with its `:name:` stickers drawn inline at the text's height, and a search's words marked. */
@Composable
fun StickerText(text: String, accent: Color, style: androidx.compose.ui.text.TextStyle, color: Color, marks: Marks? = null) {
    val stickers = LocalInlineStickers.current
    val formatted = formatChatText(text, accent, stickers.keys)
    val used = stickers.filterKeys { formatted.text.isNotEmpty() && text.contains(":$it:") }
    MarkedText(
        formatted,
        marks,
        style = style,
        color = color,
        inlineContent = used.mapKeys { STICKER_PREFIX + it.key }.mapValues { (_, art) ->
            InlineTextContent(Placeholder(1.45.em, 1.45.em, PlaceholderVerticalAlign.TextCenter)) {
                StickerPicture(StickerRef.Art(art.id), stationery.dayColors.ink("sora"), Modifier.fillMaxSize(), name = ":${art.name}:")
            }
        },
    )
}

/** A sticker's `:name:`, for reading aloud and labels. */
fun stickerLabel(sticker: Sticker, art: Map<String, StickerArt>): String = when (val ref = sticker.ref) {
    is StickerRef.Emoji -> if (ref.name == "emoji") "${ref.emoji} sticker" else ":${ref.name}: sticker"
    is StickerRef.Art -> art[ref.artId]?.let { ":${it.name}: sticker" } ?: "sticker"
}

/** "WED 08:14": the stamp on letters and notes. */
fun letterStamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(millis).atZone(zone)
    // The time as the clock setting writes it ("FRI 2:10 pm", or "FRI 14:10").
    return "%s %s".format(t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), dev.aiharness.app.ui.planner.AppClock.format.time(t.hour * 60 + t.minute))
}

/** A soft tint of [ink] over paper for small surfaces (notes, banners). */
fun tint(paper: Color, ink: Color, amount: Float = 0.06f): Color = lerp(paper, ink, amount)

/** "Aria is writing", its ellipsis writing itself a dot at a time (all three at once with reduced motion). */
@Composable
private fun StillWriting(name: String) {
    val still = dev.aiharness.app.platform.systemReducedMotion()
    var dots by remember { mutableStateOf(3) }
    if (!still) {
        LaunchedEffect(Unit) {
            while (true) {
                dots = 0
                repeat(3) {
                    kotlinx.coroutines.delay(380)
                    dots++
                }
                kotlinx.coroutines.delay(700)
            }
        }
    }
    Box {
        // The full line sets the width, so the words don't move as the dots come.
        Text("$name is writing…", style = PaperType.postscript, color = androidx.compose.ui.graphics.Color.Transparent)
        Text("$name is writing" + ".".repeat(dots).let { if (dots == 3) "…" else it }, style = PaperType.postscript, color = paper.kasure2)
    }
}
