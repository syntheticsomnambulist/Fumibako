package dev.aiharness.app.ui.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import dev.aiharness.app.ui.theme.stationery
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PolaroidFrame
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapeSide
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.paper.paperCard
import dev.aiharness.app.ui.paper.washiStrip
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.core.memory.FeelingFamily
import dev.aiharness.core.memory.FeelingWords
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.memory.Feeling
import dev.aiharness.core.memory.MemoryAge
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.TapePattern
import kotlin.math.abs
import kotlin.random.Random

/** One memory as the book shows it: how it feels, how it has aged, and what replaced it. */
data class BookPage(
    val memory: MemoryEntry,
    val feeling: Feeling?,
    /** Null when aging isn't shown. */
    val age: MemoryAge?,
    val replacedBy: MemoryEntry? = null,
    /** This memory is the newer version of one it replaced. */
    val isUpdate: Boolean = false,
)

private val StainBrown = Color(0xFF7A4E1C)

/** A tilt between -[max] and [max] degrees, the same for the same seed. */
fun seededTilt(seed: String, max: Float): Float = ((abs(seed.hashCode()) % 1000) / 1000f * 2 - 1) * max

/** Text colour on a page of this [age]: faded memories go pale, worn ones brown with handling. */
@Composable
fun agedInk(age: MemoryAge?): Color = when (age) {
    MemoryAge.FADING -> lerp(paper.sumi, paper.kami, 0.42f)
    MemoryAge.WORN -> lerp(paper.sumi, Color(0xFF8A6A3A), 0.52f)
    else -> paper.sumi
}

/** How strongly the tape and stamp still show on a page of this [age]. */
fun agedAlpha(age: MemoryAge?): Float = when (age) {
    MemoryAge.FADING -> 0.62f
    MemoryAge.WORN -> 0.8f
    else -> 1f
}

/** Coffee rings, spots and darkened edges on a worn page; drawn under its text. */
fun Modifier.worn(age: MemoryAge?, seed: String): Modifier =
    if (age != MemoryAge.WORN) this else drawBehind {
        val random = Random(seed.hashCode())
        clipRect {
            val ring = Offset(size.width * (0.7f + random.nextFloat() * 0.18f), size.height * (0.25f + random.nextFloat() * 0.3f))
            val radius = 18.dp.toPx()
            drawCircle(StainBrown.copy(alpha = 0.10f), radius + 2.dp.toPx(), ring, style = Stroke(5.dp.toPx()))
            drawCircle(StainBrown.copy(alpha = 0.26f), radius, ring, style = Stroke(1.6.dp.toPx()))
            repeat(3) {
                drawCircle(StainBrown.copy(alpha = 0.14f + random.nextFloat() * 0.1f), (1.2f + random.nextFloat() * 1.6f).dp.toPx(), Offset(size.width * random.nextFloat(), size.height * (0.55f + random.nextFloat() * 0.4f)))
            }
            val edge = 10.dp.toPx()
            val shade = StainBrown.copy(alpha = 0.16f)
            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to shade, 1f to Color.Transparent, endX = edge), size = Size(edge, size.height))
            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Transparent, 1f to shade, startX = size.width - edge, endX = size.width), topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 1f to shade, startY = size.height - edge, endY = size.height), topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
        }
    }

enum class TapeCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT }

/** One small piece of tape across a corner. */
fun Modifier.pieceOfTape(tape: Tape, corner: TapeCorner, length: Dp = 34.dp, thickness: Dp = 11.dp): Modifier = drawWithCache {
    val l = length.toPx() / 2
    val (centre, angle) = when (corner) {
        TapeCorner.TOP_LEFT -> Offset(6.dp.toPx(), 2.dp.toPx()) to -35f
        TapeCorner.TOP_RIGHT -> Offset(size.width - 6.dp.toPx(), 1.dp.toPx()) to 32f
        TapeCorner.BOTTOM_RIGHT -> Offset(size.width - 8.dp.toPx(), size.height - 2.dp.toPx()) to -30f
    }
    val rad = Math.toRadians(angle.toDouble())
    val half = Offset((kotlin.math.cos(rad) * l).toFloat(), (kotlin.math.sin(rad) * l).toFloat())
    onDrawWithContent {
        drawContent()
        drawTape(centre - half, centre + half, thickness.toPx(), tape)
    }
}

/** Paper torn along the top and bottom: a row of small teeth. */
class TornEdges(private val tooth: Dp = 7.dp, private val depth: Dp = 3.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val step = with(density) { tooth.toPx() }
        val d = with(density) { depth.toPx() }
        val path = Path().apply {
            moveTo(0f, d)
            var x = 0f
            while (x < size.width) {
                lineTo((x + step / 2).coerceAtMost(size.width), 0f)
                lineTo((x + step).coerceAtMost(size.width), d)
                x += step
            }
            lineTo(size.width, size.height - d)
            x = size.width
            while (x > 0f) {
                lineTo((x - step / 2).coerceAtLeast(0f), size.height)
                lineTo((x - step).coerceAtLeast(0f), size.height - d)
                x -= step
            }
            close()
        }
        return Outline.Generic(path)
    }
}

private val Torn = TornEdges()

/** The persona's own stickers, which stand in for its feeling stamps when one stands for that feeling. */
val LocalFeelingStickers = staticCompositionLocalOf<List<StickerArt>> { emptyList() }

/** The persona's sticker for [word] (or, failing that, for another feeling of its [family]), if it has one. */
@Composable
fun feelingSticker(word: String?, family: FeelingFamily?): StickerArt? {
    val sheet = LocalFeelingStickers.current.filter { it.feeling != null }
    val learned = LocalAppContainer.current.harness.settings.settings.collectAsStateWithLifecycle(null).value?.learnedFeelings.orEmpty()
    return sheet.firstOrNull { it.feeling.equals(word, ignoreCase = true) }
        ?: family?.let { f -> sheet.firstOrNull { FeelingWords.lookup(it.feeling!!, learned)?.family == f } }
}

/** The page's stamp, for strong feelings only: the persona's own sticker for the feeling, or the rubber stamp. */
@Composable
private fun PageStamp(page: BookPage, ink: Color, modifier: Modifier = Modifier) {
    val feeling = page.feeling
    if (feeling.stamped && feeling != null) {
        val sticker = feelingSticker(page.memory.currentMood, feeling.family)
        if (sticker != null) {
            StickerPicture(
                StickerRef.Art(sticker.id),
                stationery.dayColors.ink("sora"),
                modifier.size((38 * StickerBaker.BOX).dp).graphicsLayer { rotationZ = seededTilt(page.memory.id, 12f); alpha = agedAlpha(page.age) },
                name = page.memory.currentMood ?: sticker.feeling,
            )
        } else {
            FeelingStamp(feeling.family, ink, page.memory.id, modifier, alpha = agedAlpha(page.age), word = page.memory.currentMood)
        }
    }
}

/** The page with its stamp pinned over the top right corner. */
@Composable
private fun StampCorner(page: BookPage, ink: Color, content: @Composable () -> Unit) {
    Box {
        content()
        PageStamp(page, ink, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-15).dp))
    }
}

/** A fact on a small label slip. */
@Composable
fun LabelSlip(page: BookPage, ink: Color, onOpen: () -> Unit) {
    val m = page.memory
    val colors = paper
    val taped = abs(m.id.hashCode()) % 3 == 0
    // Tape and stamp never share a corner.
    val tapeCorner = if (page.feeling.stamped) TapeCorner.TOP_LEFT else TapeCorner.TOP_RIGHT
    StampCorner(page, ink) {
        Text(
            m.content,
            style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = agedInk(page.age),
            modifier = Modifier
                .rotate(seededTilt(m.id, 1.6f))
                .then(if (taped) Modifier.pieceOfTape(Tape(ink, TapePattern.PLAIN, alpha = agedAlpha(page.age)), tapeCorner) else Modifier)
                .widthIn(max = 300.dp)
                .shadow(1.dp, PaperCorner, ambientColor = colors.chitsu)
                .background(colors.kami, PaperCorner)
                .border(1.dp, colors.nijimi1, PaperCorner)
                .worn(page.age, m.id)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Torn paper: thoughts, and quotes from letters. */
@Composable
fun TornNote(text: String, caption: String?, modifier: Modifier = Modifier, color: Color = paper.sumi, italic: Boolean = true, age: MemoryAge? = null, seed: String = text, onClick: () -> Unit) {
    val colors = paper
    Column(
        modifier
            .rotate(seededTilt(seed, 1.8f))
            .widthIn(max = 320.dp)
            .shadow(1.5.dp, Torn, ambientColor = colors.chitsu)
            .background(colors.kami, Torn)
            .worn(age, seed)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text, style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal), color = color)
        caption?.let { Text(it.uppercase(), style = PaperType.meta, color = colors.kasure2) }
    }
}

/** The persona's own thought, on a torn note. */
@Composable
fun ThoughtNote(page: BookPage, ink: Color, personaName: String, onOpen: () -> Unit) {
    StampCorner(page, ink) {
        TornNote(page.memory.content, "$personaName's thought", color = lerp(agedInk(page.age), paper.usuzumi1, 0.3f), age = page.age, seed = page.memory.id, onClick = onOpen)
    }
}

/** Something that happened: a full-width page held by a strip of tape. */
@Composable
fun HappenedPage(page: BookPage, ink: Color, tape: Tape, onOpen: () -> Unit) {
    val m = page.memory
    Box {
        Column(
            Modifier
                .rotate(seededTilt(m.id, 0.6f))
                .washiStrip(tape.copy(alpha = agedAlpha(page.age)), if (abs(m.id.hashCode()) % 2 == 0) TapeSide.Start else TapeSide.End, tiltDegrees = -0.7f)
                .paperCard()
                .worn(page.age, m.id)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (page.isUpdate) "WHAT HAPPENED · UPDATED" else "WHAT HAPPENED", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.weight(1f))
                PageStamp(page, ink, Modifier.offset(y = 2.dp))
            }
            Text(m.content, style = PaperType.prose.copy(fontSize = 14.5.sp, lineHeight = 22.sp), color = agedInk(page.age))
        }
    }
}

/** A newer version of something kept before: a page taped at two corners, marked "Updated". */
@Composable
fun UpdatedPage(page: BookPage, ink: Color, onOpen: () -> Unit) {
    val m = page.memory
    val tape = Tape(ink, TapePattern.PLAIN, alpha = agedAlpha(page.age))
    StampCorner(page, ink) {
        Column(
            Modifier
                .rotate(seededTilt(m.id, 0.9f))
                .pieceOfTape(tape, TapeCorner.TOP_LEFT, length = 26.dp, thickness = 9.dp)
                .pieceOfTape(tape, TapeCorner.BOTTOM_RIGHT, length = 26.dp, thickness = 9.dp)
                .paperCard()
                .worn(page.age, m.id)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("UPDATED", style = PaperType.meta, color = paper.kasure2)
            Text(m.content, style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 20.sp), color = agedInk(page.age))
        }
    }
}

/** Something no longer true, struck through, with what replaced it on a slip. Kept as history. */
@Composable
fun ReplacedPage(page: BookPage, ink: Color, onOpen: () -> Unit) {
    val colors = paper
    val m = page.memory
    // The "Now:" slip hangs below the old page, overlapping only its empty bottom margin.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier
                .rotate(seededTilt(m.id, 0.7f))
                .fillMaxWidth()
                .background(lerp(colors.kami, colors.hyoshi, 0.5f), PaperCorner)
                .border(1.dp, colors.nijimi0, PaperCorner)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 22.dp),
        ) {
            Text("NO LONGER SO", style = PaperType.meta, color = colors.kasure1)
            Text(
                m.content,
                style = PaperType.prose.copy(fontSize = 13.5.sp, lineHeight = 20.sp, textDecoration = TextDecoration.LineThrough),
                color = colors.kasure2,
            )
        }
        page.replacedBy?.let { now ->
            Text(
                "Now: ${now.content}",
                style = PaperType.prose.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.sumi,
                modifier = Modifier
                    .offset(x = (-10).dp, y = (-14).dp)
                    .rotate(-2f)
                    .pieceOfTape(Tape(ink, TapePattern.PLAIN), TapeCorner.TOP_LEFT, length = 24.dp, thickness = 9.dp)
                    .widthIn(max = 260.dp)
                    .shadow(1.dp, PaperCorner)
                    .background(colors.kami, PaperCorner)
                    .border(1.dp, colors.nijimi1, PaperCorner)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** A picture from that day's letters. */
@Composable
fun BookPolaroid(ref: String, caption: String?, ink: Color, seed: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PolaroidFrame(
        tape = Tape(ink, TapePattern.DOTS),
        modifier = modifier.width(150.dp).clickable(role = Role.Button, onClickLabel = "Open the letter", onClick = onClick),
        tiltDegrees = seededTilt(seed, 3.5f),
        framePadding = 8.dp,
        bottomPadding = if (caption.isNullOrBlank()) 22.dp else 4.dp,
        picture = { BlobImage(ref, it, maxSize = 512) },
        caption = caption?.takeIf { it.isNotBlank() }?.let {
            {
                Text(
                    it,
                    style = PaperType.mood.copy(fontSize = 12.sp),
                    color = dev.aiharness.app.ui.theme.stationery.frameInk,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
        },
    )
}

/** A date stamped in ink: "5 NOV". */
@Composable
fun DateStamp(text: String, ink: Color, modifier: Modifier = Modifier, tilt: Float = -3f) {
    Text(
        text.uppercase(),
        style = PaperType.label.copy(fontSize = 11.sp, letterSpacing = 0.14.em, fontWeight = FontWeight.Bold),
        color = ink,
        modifier = modifier
            .rotate(tilt)
            .border(1.5.dp, ink, PaperCorner)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * An earlier month, tucked into an envelope. Closed, only its V flap shows; opening it folds the
 * flap up and back, and a few notes peek out of the top.
 */
@Composable
fun MonthEnvelope(month: String, summary: String, open: Boolean, ink: Color, peek: dev.aiharness.core.model.AttachmentKind? = null, onClick: () -> Unit) {
    val colors = paper
    val progress by animateFloatAsState(if (open) 1f else 0f, tween(520), label = "envelope")
    // The open flap stands above the envelope, so room is made for it as it rises.
    val raised = ((progress - 0.5f) * 2).coerceIn(0f, 1f)
    val flapDepth = 58.dp
    val body = 92.dp
    // A kept ticket stub or a J-card's corner peeks out of a closed month.
    val peekRoom = if (peek != null) 14.dp * (1f - raised) else 0.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(body + flapDepth * raised + peekRoom)
            .clickable(role = Role.Button, onClickLabel = if (open) "Close $month" else "Open $month", onClick = onClick),
    ) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val top = flapDepth.toPx() * raised + peekRoom.toPx()
            val w = size.width
            val depth = flapDepth.toPx()
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            val line = 1.dp.toPx()
            val inside = lerp(colors.kami, colors.hyoshi, 0.55f)
            // 1. The open flap, seen from inside, behind everything.
            if (progress > 0.5f) {
                val flap = Path().apply {
                    moveTo(0f, top)
                    lineTo(w / 2, top - depth * raised)
                    lineTo(w, top)
                    close()
                }
                drawPath(flap, inside)
                drawPath(flap, colors.nijimi1, style = Stroke(line))
            }
            // 2. Notes peeking out of the opening.
            if (progress > 0.6f) {
                val rise = 24.dp.toPx() * ((progress - 0.6f) / 0.4f)
                listOf(Triple(0.12f, 0.30f, -4f), Triple(0.40f, 0.28f, 2.5f), Triple(0.64f, 0.26f, -1.5f)).forEachIndexed { i, (x, width, tilt) ->
                    val left = w * x
                    val noteTop = top - rise * (1f - i * 0.18f)
                    rotate(tilt, pivot = Offset(left + w * width / 2, top)) {
                        drawRect(lerp(colors.kami, Color.White, if (colors.isNight) 0.04f else 0.5f), Offset(left, noteTop), Size(w * width, top + 30.dp.toPx() - noteTop))
                        drawRect(colors.nijimi1, Offset(left, noteTop), Size(w * width, top + 30.dp.toPx() - noteTop), style = Stroke(line))
                        repeat(2) { r ->
                            val y = noteTop + (9 + r * 7).dp.toPx()
                            drawLine(colors.nijimi1, Offset(left + 7.dp.toPx(), y), Offset(left + w * width * (0.8f - r * 0.25f), y), line)
                        }
                    }
                }
            }
            // 2b. A keepsake tucked in among the notes, its top showing above a closed envelope.
            if (peek != null && progress < 0.5f) {
                val pw = 78.dp.toPx()
                val ph = 34.dp.toPx()
                val left = w * 0.66f
                val pieceTop = top - 13.dp.toPx()
                rotate(-6f, pivot = Offset(left + pw / 2, top)) {
                    if (peek == dev.aiharness.core.model.AttachmentKind.PLAYLIST) {
                        val card = Color(0xFFF6ECD9)
                        drawRect(card, Offset(left, pieceTop), Size(pw, ph))
                        drawRect(lerp(card, ink, 0.25f), Offset(left, pieceTop), Size(10.dp.toPx(), ph))
                        drawLine(ink.copy(alpha = 0.6f), Offset(left + 16.dp.toPx(), pieceTop + 7.dp.toPx()), Offset(left + pw * 0.8f, pieceTop + 7.dp.toPx()), line * 1.6f)
                    } else {
                        val ticket = lerp(Color(0xFFF7F0E4), ink, 0.18f)
                        drawRect(ticket, Offset(left, pieceTop), Size(pw, ph))
                        drawLine(lerp(ticket, ink, 0.55f), Offset(left + pw * 0.74f, pieceTop), Offset(left + pw * 0.74f, pieceTop + ph), line * 1.4f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                        drawLine(ink.copy(alpha = 0.7f), Offset(left + 6.dp.toPx(), pieceTop + 7.dp.toPx()), Offset(left + pw * 0.5f, pieceTop + 7.dp.toPx()), line * 1.6f)
                    }
                    drawRect(colors.nijimi1, Offset(left, pieceTop), Size(pw, ph), style = Stroke(line))
                }
            }
            // 3. The envelope itself.
            drawRoundRect(Color.Black.copy(alpha = if (colors.isNight) 0.35f else 0.12f), Offset(0f, top + 2.dp.toPx()), Size(w, body.toPx()), corner)
            drawRoundRect(colors.kami, Offset(0f, top), Size(w, body.toPx()), corner)
            drawRoundRect(colors.nijimi0, Offset(0f, top), Size(w, body.toPx()), corner, style = Stroke(line))
            // 4. The closed flap on the front, folding up as it opens.
            if (progress <= 0.5f) {
                val tip = Offset(w / 2, top + depth * (1 - progress * 2))
                drawLine(colors.nijimi2, Offset(0f, top), tip, line)
                drawLine(colors.nijimi2, Offset(w, top), tip, line)
            } else {
                drawLine(colors.nijimi2, Offset(0f, top), Offset(w, top), line)
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(month, style = PaperType.entryTitle.copy(fontSize = 20.sp), color = colors.sumi)
            Text(summary.uppercase(), style = PaperType.meta, color = colors.kasure2, modifier = Modifier.padding(bottom = 3.dp))
        }
        DateStamp(if (open) "Close" else "Open", ink, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp))
    }
}

/** Text written on ruled lines, the lines sitting just under each line of text. */
@Composable
fun RuledText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, rule: Color = paper.nijimi1) {
    // Only the baselines, and only when they move (see IndexCardField).
    var baselines by remember { mutableStateOf<List<Float>>(emptyList()) }
    Text(
        text,
        style = style,
        color = color,
        onTextLayout = { l ->
            val lines = List(l.lineCount) { l.getLineBaseline(it) }
            if (lines != baselines) baselines = lines
        },
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val gap = 4.dp.toPx()
                baselines.forEach { baseline ->
                    val y = baseline + gap
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            .padding(bottom = 6.dp),
    )
}
