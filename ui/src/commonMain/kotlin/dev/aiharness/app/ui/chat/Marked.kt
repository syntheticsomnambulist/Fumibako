package dev.aiharness.app.ui.chat

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import dev.aiharness.core.letters.LetterWords

/** Words found by a search, marked in text like a highlighter: the search's [terms], in [ink]. */
data class Marks(val terms: List<String>, val ink: Color)

/**
 * Text with the words of a search marked under it like a highlighter (the lower part of each line,
 * so the words stay as they were), in [marks]' ink.
 */
@Composable
fun MarkedText(
    text: AnnotatedString,
    marks: Marks?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    val ranges = remember(text, marks?.terms) { marks?.let { LetterWords.matches(text.text, it.terms) }.orEmpty() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        modifier = if (ranges.isEmpty()) modifier else modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val ink = marks?.ink ?: return@drawBehind
            ranges.forEach { range ->
                for (i in range) {
                    if (i >= text.length) break
                    val box = l.getBoundingBox(i)
                    drawRect(ink, Offset(box.left - 0.5f, box.top + box.height * 0.42f), Size(box.width + 1f, box.height * 0.5f))
                }
            }
        },
        style = style,
        color = color,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
    )
}

/**
 * A search being stepped through in a thread, as one letter or note sees it: its [marks], whether
 * it holds the current match ([ringed]), and [inPicture], the line saying what a picture held when
 * the words were found there rather than in the letter.
 */
data class FindView(val marks: Marks, val ringed: Boolean, val inPicture: String? = null)

/** A ring a little outside the paper, around the current match. */
fun Modifier.matchRing(on: Boolean, ink: Color): Modifier = if (!on) this else drawWithContent {
    drawContent()
    val gap = 3.dp.toPx()
    val width = 2.dp.toPx()
    drawRect(ink, Offset(-gap, -gap), Size(size.width + gap * 2, size.height + gap * 2), style = Stroke(width))
}

/** "Found in the picture: “a ginger cat asleep in a sunny window”" when a search's words are only in its pictures. */
fun foundInPicture(messages: List<dev.aiharness.core.model.ChatMessage>, terms: List<String>): String? {
    val pieces = messages.flatMap { LetterWords.pieces(it) }
    if (pieces.any { !it.inPicture && LetterWords.termsIn(it.text, terms).isNotEmpty() }) return null
    val picture = pieces.firstOrNull { it.inPicture && LetterWords.termsIn(it.text, terms).isNotEmpty() } ?: return null
    return "Found in the picture: “" + dev.aiharness.core.letters.Passages.around(picture.text, LetterWords.matches(picture.text, terms).first(), 160) + "”"
}
