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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
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
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
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
) {
    val s = stationery
    val ink = s.ink(persona?.ink)
    val mood = writing?.text?.let { dev.aiharness.core.model.MoodTags.parse(it) } ?: letter?.mood
    StuckOn(stickers, inkOf = { stickerInk(it, s, persona, user) }, Modifier.fillMaxWidth(), stickerName) {
        Column(
            Modifier
                .washiStrip(Tape(ink, persona?.tapePattern ?: TapePattern.DOTS), tiltDegrees = -0.7f, overhang = 8.dp)
                .paperCard()
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Letter options")
                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PortraitDot(persona, 26.dp)
                Text(persona?.name ?: "…", style = PaperType.letterName, color = paper.sumi)
                Box(Modifier.weight(1f)) { MoodWord(mood, ink) }
                letter?.let { Text(letterStamp(it.createdAt), style = PaperType.meta, color = paper.kasure2) }
            }

            val reasoning = listOfNotNull(letter?.reasoning, writing?.reasoning?.takeIf { it.isNotBlank() }).joinToString("\n\n")
            if (reasoning.isNotBlank()) Thoughts(reasoning)

            letter?.blocks?.forEach { block ->
                when (block) {
                    is LetterBlock.Words -> Text(formatChatText(block.text, ink), style = PaperType.letterBody, color = paper.sumi)
                    is LetterBlock.Picture -> BlobImage(
                        block.image.ref,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 380.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClickLabel = "View picture") { onImage(block.image.ref) },
                        contentScale = ContentScale.Crop,
                        maxSize = 1000,
                    )
                }
            }
            if (writing != null) {
                val text = dev.aiharness.core.model.MoodTags.stripStreaming(writing.text)
                if (text.isNotBlank()) Text(formatChatText(text, ink), style = PaperType.letterBody, color = paper.sumi)
                Text(writing.status.ifBlank { "writing…" }, style = PaperType.postscript, color = paper.kasure2)
            }
            letter?.errors?.forEach { Text(it, style = PaperType.postscript, color = s.ink("beni")) }
            if (letter?.fromHeartbeat == true) {
                Text("Written while you were away", style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = ink)
            }
            letter?.postscripts?.forEach { ps ->
                Text(
                    buildAnnotatedString {
                        append("P.S. ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.Medium, color = if (ps.isError) s.ink("beni") else ink)) { append(ps.label) }
                        ps.text?.let {
                            append(" ")
                            append(it)
                        }
                    },
                    style = PaperType.postscript,
                    color = paper.usuzumi1,
                )
            }
        }
    }
}

/** The ink of whoever stuck [sticker] (day ink: stickers keep their day colours). */
fun stickerInk(sticker: Sticker, s: Stationery, persona: Persona?, user: UserPersona?): Color =
    if (sticker.byPersonaId != null) s.dayColors.ink(persona?.ink) else s.dayColors.ink(user?.ink ?: "sora")

@Composable
private fun Thoughts(reasoning: String) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (open) "Hide thoughts" else "Show thoughts",
            style = PaperType.meta,
            color = paper.kasure2,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { open = !open }.padding(vertical = 2.dp),
        )
        if (open) Text(reasoning, style = PaperType.postscript, color = paper.usuzumi0)
    }
}

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
) {
    val s = stationery
    val you = user?.ink?.let { s.ink(it) } ?: s.you
    val pattern = user?.tapePattern ?: s.youPattern
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        StuckOn(stickers, inkOf = { stickerInk(it, s, persona, user) }, Modifier.widthIn(max = maxWidth * 0.78f), stickerName) {
            Column(
                Modifier
                    .washiStrip(Tape(you, pattern), side = TapeSide.End, tiltDegrees = 0.9f, overhang = 7.dp)
                    .paperCard(color = lerp(s.colors.kami, you, 0.06f))
                    .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Note options")
                    .padding(start = 13.dp, end = 20.dp, top = 9.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    listOfNotNull(user?.name, letterStamp(message.createdAt)).joinToString(" · ").uppercase(),
                    style = PaperType.meta,
                    color = you,
                )
                if (message.displayText.isNotBlank()) {
                    Text(formatChatText(message.displayText, you), style = PaperType.noteBody, color = paper.usuzumi1)
                }
                message.images.forEach { image ->
                    BlobImage(image.ref, Modifier.widthIn(max = 220.dp).heightIn(max = 260.dp).clip(RoundedCornerShape(2.dp)).clickable { onImage(image.ref) })
                }
            }
        }
    }
}

/** A sticker's `:name:`, for reading aloud and labels. */
fun stickerLabel(sticker: Sticker, art: Map<String, StickerArt>): String = when (val ref = sticker.ref) {
    is StickerRef.Emoji -> if (ref.name == "emoji") "${ref.emoji} sticker" else ":${ref.name}: sticker"
    is StickerRef.Art -> art[ref.artId]?.let { ":${it.name}: sticker" } ?: "sticker"
}

/** "WED 08:14": the stamp on letters and notes. */
fun letterStamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(millis).atZone(zone)
    return "%s %02d:%02d".format(t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), t.hour, t.minute)
}

/** A soft tint of [ink] over paper for small surfaces (notes, banners). */
fun tint(paper: Color, ink: Color, amount: Float = 0.06f): Color = lerp(paper, ink, amount)
