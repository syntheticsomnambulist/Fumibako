package dev.aiharness.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.platform.onRightClick
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.agent.ContextSlip
import dev.aiharness.core.agent.ModelChanges
import dev.aiharness.core.model.FoundLetter
import dev.aiharness.core.model.Words
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** How high the tabs stand above a letter's top edge (their touch area reaches this far up). */
val TabsAbove = 40.dp

/**
 * The two paper tabs sticking up behind a letter's top edge at the right: an index card for what
 * the persona had in mind, and a scribbled scrap for its thoughts (only when the model gave any).
 * Icons only; screen readers get the names, and a long press shows them.
 */
@Composable
fun LetterTabs(
    words: Words,
    ink: Color,
    hasMind: Boolean,
    mindOpen: Boolean,
    onMind: () -> Unit,
    hasThoughts: Boolean,
    thoughtsOpen: Boolean,
    onThoughts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        if (hasMind) PaperTab(mindName(words), mindOpen, onMind) { open -> IndexCardTab(open, ink) }
        if (hasThoughts) PaperTab(thoughtsName(words), thoughtsOpen, onThoughts) { open -> ScrapTab(open, ink) }
    }
}

fun mindName(words: Words) = "What ${words.they} had in mind"
fun thoughtsName(words: Words) = "${words.their.replaceFirstChar { it.uppercase() }} thoughts"

/** One tab in a 44dp touch area, its paper at the bottom (a little taller when its slip is out). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaperTab(name: String, open: Boolean, onToggle: () -> Unit, paperPiece: @Composable (Boolean) -> Unit) {
    var showName by remember { mutableStateOf(false) }
    LaunchedEffect(showName) {
        if (showName) {
            delay(1_600)
            showName = false
        }
    }
    Box(
        Modifier
            .size(width = 44.dp, height = TabsAbove + 4.dp)
            .semantics {
                contentDescription = name
                stateDescription = if (open) "Out" else "Tucked away"
            }
            .combinedClickable(role = Role.Button, onClick = onToggle, onLongClick = { showName = true }, onLongClickLabel = "Show its name")
            .onRightClick { showName = true },
        contentAlignment = Alignment.BottomCenter,
    ) {
        paperPiece(open)
        if (showName) {
            Text(
                name,
                style = PaperType.meta.copy(letterSpacing = 0.sp),
                color = paper.kami,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = (-6).dp).background(paper.sumi, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** The index card's paper: lighter than the letter, with its red rule. */
@Composable
private fun indexPaper(): Color = if (paper.isNight) lerp(paper.kami, paper.hyoshi, 0.3f) else lerp(paper.kami, Color.White, 0.45f)

@Composable
private fun indexRule(): Color = lerp(paper.kami, stationery.ink("beni"), 0.4f)

@Composable
private fun scrapPaper(): Color = lerp(paper.kami, stationery.ink("yamabuki"), if (paper.isNight) 0.12f else 0.18f)

private val TabCorners = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)

/** A torn top edge, like the thoughts' scrap. */
private fun tornTop(teeth: Int, depth: Float) = GenericShape { size, _ ->
    moveTo(0f, depth)
    for (i in 1..teeth) {
        val x = size.width * i / teeth
        lineTo(x, if (i % 2 == 1) 0f else depth * if (i % 4 == 0) 0.3f else 1f)
    }
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun IndexCardTab(open: Boolean, ink: Color) {
    val height by animateDpAsState(if (open) 31.dp else 26.dp, tween(160))
    val icon = if (open) ink else paper.usuzumi0
    val rule = indexRule()
    val clip = stationery.ink("sakura")
    Box(
        Modifier
            .width(38.dp).height(height)
            .shadow(2.dp, TabCorners, ambientColor = paper.chitsu, spotColor = Color.Black.copy(alpha = 0.3f))
            .background(indexPaper(), TabCorners)
            .border(1.dp, paper.nijimi0, TabCorners)
            .drawBehind { drawLine(rule, Offset(0f, 3.dp.toPx()), Offset(size.width, 3.dp.toPx()), 1.5.dp.toPx()) }
            .padding(top = 5.5.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.size(18.dp)) { indexCardIcon(icon, rule, clip) }
    }
}

@Composable
private fun ScrapTab(open: Boolean, ink: Color) {
    val height by animateDpAsState(if (open) 31.dp else 26.dp, tween(160))
    val icon = if (open) ink else paper.usuzumi0
    val shape = remember { tornTop(teeth = 7, depth = 8f) }
    Box(
        Modifier
            .width(38.dp).height(height)
            .shadow(1.5.dp, shape, ambientColor = paper.chitsu, spotColor = Color.Black.copy(alpha = 0.3f))
            .background(scrapPaper(), shape)
            .padding(top = 5.5.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.size(18.dp)) { scrapIcon(icon) }
    }
}

/** An index card with a paperclip, in a 16-unit box. */
private fun DrawScope.indexCardIcon(ink: Color, rule: Color, clip: Color) {
    val u = size.width / 16f
    val stroke = Stroke(1.3f * u)
    drawRect(ink, Offset(2 * u, 4 * u), androidx.compose.ui.geometry.Size(12 * u, 9.5f * u), style = stroke)
    drawLine(rule, Offset(2 * u, 6.6f * u), Offset(14 * u, 6.6f * u), 1.2f * u)
    drawLine(ink, Offset(4.5f * u, 9.2f * u), Offset(11.5f * u, 9.2f * u), 1.1f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(ink, Offset(4.5f * u, 11.5f * u), Offset(9 * u, 11.5f * u), 1.1f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val paperclip = Path().apply {
        moveTo(10.5f * u, 1.5f * u)
        lineTo(10.5f * u, 6f * u)
        arcTo(Rect(10.5f * u, 4.6f * u, 13.3f * u, 7.4f * u), 180f, -180f, false)
        lineTo(13.3f * u, 2.6f * u)
        arcTo(Rect(9.1f * u, 0.5f * u, 13.3f * u, 4.7f * u), 0f, -180f, false)
        lineTo(9.1f * u, 5.6f * u)
    }
    drawPath(paperclip, clip, style = Stroke(1.1f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/** A scrap of paper, a little askew, with two scribbled lines. */
private fun DrawScope.scrapIcon(ink: Color) {
    val u = size.width / 16f
    val scrap = Path().apply {
        moveTo(2.5f * u, 3f * u); lineTo(13.5f * u, 2f * u); lineTo(14f * u, 13.5f * u); lineTo(3f * u, 14f * u); close()
    }
    drawPath(scrap, ink, style = Stroke(1.3f * u, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    fun squiggle(y: Float, end: Float) = Path().apply {
        moveTo(5 * u, y * u)
        var x = 5f
        var up = true
        while (x < end) {
            quadraticTo((x + 0.65f) * u, (y + if (up) -1.6f else 1.6f) * u, (x + 1.3f) * u, y * u)
            x += 1.3f
            up = !up
        }
    }
    val line = Stroke(1.2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawPath(squiggle(6.5f, 10.2f), ink, style = line)
    drawPath(squiggle(10f, 9.4f), ink, style = line)
}

/** A slip sliding out at the top of the letter, under the row with the persona's name. */
@Composable
fun SlipOut(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(220)) { -it / 4 } + expandVertically(tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(160)) + fadeOut(tween(160)),
    ) { content() }
}

/**
 * What it had in mind: an index card (a red rule at the top, faint ruled lines) with what the
 * turn's context held, in sections, and the turn's tokens at the foot.
 */
@Composable
fun MindCard(
    slip: ContextSlip,
    words: Words,
    ink: Color,
    stamp: String?,
    youName: String,
    personaName: String,
    onOpen: (FoundLetter) -> Unit,
) {
    val rule = indexRule()
    val lines = lerp(paper.kami, stationery.ink("sora"), 0.1f)
    val beni = stationery.ink("beni")
    val body = PaperType.preview.copy(fontSize = 13.sp, lineHeight = 22.sp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(0.dp), ambientColor = paper.chitsu, spotColor = Color.Black.copy(alpha = 0.3f))
            .background(indexPaper())
            .border(1.dp, paper.nijimi0)
            .drawBehind {
                val top = 30.dp.toPx()
                drawLine(rule, Offset(0f, top), Offset(size.width, top), 1.5.dp.toPx())
                var y = top + 22.dp.toPx()
                while (y < size.height) {
                    drawLine(lines, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += 22.dp.toPx()
                }
            }
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mindName(words).uppercase(), style = PaperType.label, color = paper.usuzumi0, modifier = Modifier.weight(1f))
            stamp?.let { Text(it, style = PaperType.meta, color = paper.usuzumi0) }
        }
        Box(Modifier.height(6.dp))
        @Composable
        fun heading(text: String) = Text(text.uppercase(), style = PaperType.label.copy(lineHeight = 22.sp), color = beni, modifier = Modifier.padding(top = 2.dp))
        @Composable
        fun line(text: String) = Text(text, style = body, color = paper.sumi)

        if (slip.time.isNotEmpty()) {
            heading("The time")
            slip.time.forEach { line(timeLine(it)) }
        }
        if (slip.model.isNotEmpty()) {
            heading("${words.their.replaceFirstChar { it.uppercase() }} model")
            slip.model.forEach { line(it) }
        }
        if (slip.planner.isNotEmpty()) {
            heading("Your planner")
            slip.planner.forEach { line(it) }
        }
        if (slip.memories.isNotEmpty()) {
            heading("Memories that came up · ${slip.memories.size}")
            slip.memories.forEach { memory ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val feeling = if (memory.core) "always in mind" else memory.feeling
                    feeling?.let {
                        Text(
                            it,
                            style = PaperType.mood.copy(lineHeight = 16.sp),
                            color = paper.sumi,
                            modifier = Modifier.padding(top = 3.dp).background(lerp(paper.kami, ink, 0.22f)).padding(horizontal = 5.dp),
                        )
                    }
                    Text(memory.text, style = body, color = paper.sumi)
                }
            }
        }
        if (slip.stickers.isNotEmpty()) {
            heading("Stickers")
            slip.stickers.forEach { line(it) }
        }
        if (slip.read.isNotEmpty()) {
            heading("What ${words.they} read")
            slip.read.forEach { line(it) }
        }
        if (slip.searches.isNotEmpty()) {
            val n = slip.searches.size
            heading("Letters ${words.they} looked up · $n ${if (n == 1) "search" else "searches"}")
            slip.searches.forEach { search ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = PaperType.meta.fontFamily, fontSize = 12.sp, color = paper.usuzumi0)) {
                            append("${words.theyCapital} looked for ")
                        }
                        append("“${search.query}”")
                        withStyle(SpanStyle(fontFamily = PaperType.meta.fontFamily, fontSize = 12.sp, color = paper.usuzumi0)) {
                            append(" · ${search.found.size} found")
                        }
                    },
                    style = body,
                    color = paper.sumi,
                )
                search.found.forEach { found ->
                    val who = if (found.byYou) youName else personaName
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontFamily = PaperType.meta.fontFamily, fontSize = 11.sp, color = paper.usuzumi0)) {
                                append("$who, ${shortDay(found.at)} · ")
                            }
                            append(found.passage)
                        },
                        style = body,
                        color = paper.sumi,
                        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open this letter") { onOpen(found) },
                    )
                }
            }
        }
        if (slip.also.isNotEmpty()) {
            heading("Also")
            slip.also.forEach { line(it) }
        }
        if (slip.tokensRead > 0 || slip.tokensWritten > 0) {
            val n = NumberFormat.getIntegerInstance()
            Text(
                "This turn: ${n.format(slip.tokensRead)} tokens read · ${n.format(slip.tokensWritten)} written",
                style = PaperType.meta.copy(letterSpacing = 0.sp, lineHeight = 22.sp),
                color = paper.usuzumi0,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** "Current time: Friday, 6 November 2026, 16:04." → "Friday, 6 November 2026, 16:04."; the gap as "5 hours since the letter before." */
private fun timeLine(line: String): String = when {
    line.startsWith("Current time: ") -> line.removePrefix("Current time: ")
    line.startsWith("Time since the previous message: ") -> line.removePrefix("Time since the previous message: ").removeSuffix(".") + " since the letter before."
    else -> line
}

/** Its thoughts: a scrap of squared paper, pale yamabuki, torn along the top. */
@Composable
fun ThoughtsScrap(reasoning: String, words: Words) {
    val grid = lerp(paper.kami, stationery.ink("yamabuki"), if (paper.isNight) 0.2f else 0.28f)
    val shape = remember { tornTop(teeth = 34, depth = 9f) }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.5.dp, shape, ambientColor = paper.chitsu, spotColor = Color.Black.copy(alpha = 0.3f))
            .background(scrapPaper(), shape)
            .drawBehind {
                val step = 11.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(thoughtsName(words).uppercase(), style = PaperType.label, color = paper.usuzumi0)
        Text(reasoning, style = PaperType.postscript.copy(lineHeight = 22.sp), color = paper.usuzumi0)
    }
}

/**
 * Where the persona's model changed: a small notice like a change-of-address card, with a 改 stamp,
 * between your note and its first letter with the new model.
 */
@Composable
fun ModelChangeNotice(change: dev.aiharness.app.ui.chat.ThreadRow.ModelChange, words: Words, name: String, changedAt: Long?) {
    val (was, now) = ModelChanges.names(change.notice.was, change.notice.now)
    val told = if (change.told) "${words.they}${words.verb("'s", "'ve")} been told" else "${words.they} ${words.verb("wasn't", "weren't")} told"
    val details = listOfNotNull("Was $was", changedAt?.let { "changed ${dev.aiharness.app.ui.planner.AppClock.format.time(minuteOfDay(it))}" }, told)
    val dashed = paper.kasure0
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = "$name's model changed: from here, $name writes with $now. ${details.joinToString(". ")}."
                }
                .background(paper.kami)
                .drawBehind {
                    drawRect(dashed, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
                .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(26.dp).rotate(-10f).border(1.5.dp, paper.usuzumi0, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("改", style = PaperType.seal.copy(fontSize = 13.sp, lineHeight = 14.sp), color = paper.usuzumi0)
            }
            Column {
                Text(
                    buildAnnotatedString {
                        append("From here, $name writes with ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(now) }
                    },
                    style = PaperType.noteBody.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = paper.sumi,
                )
                Text(details.joinToString(" · "), style = PaperType.meta.copy(letterSpacing = 0.sp), color = paper.usuzumi0)
            }
        }
    }
}

/** "your note of Mon 12 Oct", or "Aria's letter of Mon 12 Oct": a letter a persona looked back at. */
fun foundName(found: FoundLetter, personaName: String): String =
    (if (found.byYou) "your note of " else "$personaName's letter of ") + shortDay(found.at)

/** "Mon 12 Oct", with the year when it isn't this year's. */
fun shortDay(millis: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val day = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val month = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$day ${d.dayOfMonth} $month" + if (d.year != today.year) " ${d.year}" else ""
}

private fun minuteOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }
