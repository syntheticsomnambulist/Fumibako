package dev.aiharness.app.ui.chats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.drawPatternOver
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.InkKanji
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.LetterBoxes
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import kotlin.math.max
import kotlin.math.min

/** Every box is the same size (chosen 2026-11-09). */
val LetterBoxWidth = 120.dp
val LetterBoxHeight = 80.dp

/**
 * A persona's little letter box (fumibako), drawn flat in their ink mixed into the paper, the way
 * the writing desk is drawn (docs/design/fumibako-studies.html; every finish, lid and tie as SVG in
 * docs/design/fumibako-svg). [open] animates: the bow undoes, the lid tips back on its far edge,
 * their letter rises inside, and the cord's loose ends drop to the table.
 */
@Composable
fun LetterBoxPicture(
    persona: Persona,
    open: Boolean,
    modifier: Modifier = Modifier,
    box: LetterBox = LetterBoxes.of(persona),
    /** Their letters inside, seen when it's open. */
    letters: Boolean = true,
) {
    val progress by animateFloatAsState(if (open) 1f else 0f, tween(OPENING_MILLIS, easing = LinearEasing), label = "letter box")
    val s = stationery
    val colors = paper
    val inks = BoxInks(
        kami = colors.kami,
        ink = s.ink(persona.ink),
        nijimi0 = colors.nijimi0,
        nijimi1 = colors.nijimi1,
        sumi = colors.sumi,
        yamabuki = s.ink("yamabuki"),
        night = s.isNight,
        finish = box.finish,
    )
    val measurer = rememberTextMeasurer()
    val name = persona.name.ifBlank { "…" }
    val kanji = InkKanji[persona.ink] ?: "文"
    Spacer(
        modifier.size(LetterBoxWidth, LetterBoxHeight).drawWithCache {
            val unit = size.width / W
            // Text measured in the box's own units: a size of 14 is 14 of its 120 wide, whatever the font scale.
            fun style(units: Float, family: androidx.compose.ui.text.font.FontFamily, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified) =
                TextStyle(fontFamily = family, fontWeight = weight, fontSize = (units / (density * fontScale)).sp, color = color)
            val markWidth = W - CX - 18
            val nameUnits = min(19f, markWidth / (name.length * 0.6f))
            val texts = LidTexts(
                plate = measurer.measure(name, style(14f, HarnessFonts.Display, FontWeight.SemiBold)),
                cut = measurer.measure(name, style(nameUnits, HarnessFonts.Display, FontWeight.SemiBold)),
                kanjiBig = measurer.measure(kanji, style(if (kanji.length > 1) 19f else 25f, HarnessFonts.Brush)),
                sealChars = kanji.take(2).map { measurer.measure(it.toString(), style(if (kanji.length > 1) 11f else 17f, HarnessFonts.Brush)) },
                hand = measurer.measure(name, style(12f, HarnessFonts.Hand)),
            )
            onDrawBehind {
                scale(unit, unit, pivot = Offset.Zero) {
                    drawBox(box, persona.tapePattern, inks, texts, progress, letters)
                }
            }
        },
    )
}

const val OPENING_MILLIS = 560

/* The box's lines, in dp of its 120 by 80: the lid's top runs from 2 to T, its edge band shows from T
   to E, the box below from E to B, and the table is at B. The cord runs at CX; KY is the lid's middle. */
private const val W = 120f
private const val T = 50f
private const val E = 60f
private const val B = 77f
private const val KY = 26f
private const val CX = 30f

private class LidTexts(
    val plate: TextLayoutResult,
    val cut: TextLayoutResult,
    val kanjiBig: TextLayoutResult,
    val sealChars: List<TextLayoutResult>,
    val hand: TextLayoutResult,
)

/** The box's colors: every fill is their ink mixed into the paper, as the desk's drawn objects are. */
private class BoxInks(
    val kami: Color,
    val ink: Color,
    nijimi0: Color,
    val nijimi1: Color,
    val sumi: Color,
    yamabuki: Color,
    night: Boolean,
    val finish: BoxFinish,
) {
    fun t(f: Float) = lerp(kami, ink, f)
    private val wood = lerp(kami, nijimi0, 0.7f)
    val top = when (finish) { BoxFinish.LACQUER -> t(0.7f); BoxFinish.WASHI -> t(0.25f); BoxFinish.PAULOWNIA -> wood }
    val edge = when (finish) { BoxFinish.LACQUER -> t(0.85f); BoxFinish.WASHI -> t(0.4f); BoxFinish.PAULOWNIA -> nijimi1 }
    val body = top
    val cord = if (finish == BoxFinish.LACQUER) t(0.25f) else t(0.85f)
    val bowLine = if (finish == BoxFinish.LACQUER) t(0.55f) else t(0.4f)
    val sheet = if (night) lerp(kami, Color.White, 0.06f) else Color(0xFFFDF9F4)
    val rule = nijimi0
    val gold = lerp(yamabuki, Color.White, 0.18f)
    val goldDeep = lerp(yamabuki, Color.Black, 0.38f)
    val grain = nijimi1
    val shadow = Color.Black.copy(alpha = if (night) 0.28f else 0.09f)
}

private fun Float.stage(from: Float, to: Float) = ((this - from) / (to - from)).coerceIn(0f, 1f)

/** Ease out with a little overshoot, for the lid settling back. */
private fun Float.settle(): Float {
    val c1 = 1.2f
    val c3 = c1 + 1f
    val x = this - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun DrawScope.drawBox(box: LetterBox, pattern: TapePattern, c: BoxInks, texts: LidTexts, p: Float, letters: Boolean) {
    val bow = p.stage(0f, 0.3f)
    val lid = p.stage(0.18f, 0.8f).settle()
    val rise = p.stage(0.5f, 1f).settle()
    val loose = p.stage(0.3f, 0.6f)
    val cordOnLid = 1f - p.stage(0f, 0.25f)

    // The app's soft shadow, under the box.
    drawRoundRect(c.shadow, Offset(6f, B - 1.5f), Size(W - 12f, 3.5f), CornerRadius(2f))

    // The box under the lid, and the cord up its front (it stays when the box is open).
    face(Rect(5f, T + 4f, W - 5f, B), 3f, c.body, pattern, c)
    if (box.tie != BoxTie.MIZUHIKI) drawRect(c.cord, Offset(CX - 2.5f, E - 2f), Size(5f, B - E + 2f))

    // Inside, seen as the lid lifts: the rim, the floor, the back wall and the side walls, in their ink.
    if (lid > 0f) {
        drawRoundRect(c.body, Offset(5f, 9f), Size(W - 10f, E - 9f), CornerRadius(3f))
        drawRect(c.t(0.4f), Offset(10f, 14f), Size(W - 20f, E - 16f))
        drawRect(c.t(0.7f), Offset(10f, 14f), Size(W - 20f, 8f))
        drawPath(Path().apply { moveTo(10f, 14f); lineTo(16f, 22f); lineTo(16f, E - 2f); lineTo(10f, E - 2f); close() }, c.t(0.55f))
        drawPath(Path().apply { moveTo(W - 10f, 14f); lineTo(W - 16f, 22f); lineTo(W - 16f, E - 2f); lineTo(W - 10f, E - 2f); close() }, c.t(0.55f))
        if (letters && rise > 0f) drawLetters(c, texts, rise)
    }

    // The lid: tipped back on its far edge as it opens.
    val lidDy = 8f - (E - 1f) * 0.42f
    withTransform({
        translate(top = lidDy * lid)
        scale(1f, 1f - 0.58f * lid, pivot = Offset(0f, 1f))
    }) {
        drawRoundRect(c.edge, Offset(0f, T - 12f), Size(W, E - T + 12f), CornerRadius(5f))
        face(Rect(0f, 2f, W, T), 6f, c.top, pattern, c)
        when (box.finish) {
            BoxFinish.LACQUER -> drawRoundRect(c.t(0.4f), Offset(4f, 5.5f), Size(W - 8f, T - 7f), CornerRadius(4f), style = Stroke(1f))
            BoxFinish.PAULOWNIA -> listOf(0.21f, 0.45f, 0.68f).forEach { f ->
                val y = 2f + (T - 2f) * f
                drawPath(Path().apply { moveTo(6f, y); cubicTo(W * 0.27f, y - 2f, W * 0.62f, y + 2f, W - 6f, y) }, c.grain, style = Stroke(1f, cap = StrokeCap.Round))
            }
            BoxFinish.WASHI -> Unit
        }
        if (cordOnLid > 0f && box.tie != BoxTie.MIZUHIKI) {
            drawRect(c.cord, Offset(CX - 2.5f, 1f), Size(5f, E - 1f), alpha = cordOnLid)
            if (box.tie == BoxTie.CROSSWISE) drawRect(c.cord, Offset(0f, T - 10.5f), Size(W, 5f), alpha = cordOnLid)
        }
        drawLidMark(box, c, texts, Offset((CX + 8f + W) / 2f, if (box.tie == BoxTie.CROSSWISE) KY - 4f else KY))
    }

    // The tie.
    when (box.tie) {
        BoxTie.BOW -> {
            vanishing(bow, Offset(CX, KY - 1f)) { a -> drawBow(c, Offset(CX, KY - 1f), a) }
            if (loose > 0f) drawLooseEnds(c, loose)
        }
        BoxTie.CROSSWISE -> {
            val y = T - 8f
            vanishing(bow, Offset(CX, y)) { a ->
                rotate(45f, Offset(CX, y)) { drawRoundRect(c.cord, Offset(CX - 5f, y - 5f), Size(10f, 10f), CornerRadius(2f), alpha = a) }
                drawLine(c.bowLine, Offset(CX - 3f, y), Offset(CX + 3f, y), 1f, alpha = a)
                drawLine(c.bowLine, Offset(CX, y - 3f), Offset(CX, y + 3f), 1f, alpha = a)
                drawPath(polygon(CX + 3f, y + 2f, CX + 13f, y + 10f, CX + 11f, y + 12f, CX + 1f, y + 4f), c.cord, alpha = a)
                drawPath(polygon(CX - 3f, y + 2f, CX - 11f, y + 11f, CX - 9f, y + 13f, CX - 1f, y + 4f), c.cord, alpha = a)
            }
            if (loose > 0f) {
                // The loop round the sides slips down to the box's foot.
                drawRect(c.cord, Offset(5f, B - 6f), Size(W - 10f, 4f), alpha = loose)
                drawLooseEnds(c, loose)
            }
        }
        BoxTie.MIZUHIKI -> {
            // Slipped off whole and laid on its side in front of the box.
            val m = p.stage(0.1f, 0.78f).settle()
            withTransform({
                translate(4f * m, (B - KY - 2f) * m)
                rotate(-90f * m, Offset(CX, KY))
                scale(1f - 0.38f * m, 1f - 0.38f * m, pivot = Offset(CX, KY))
            }) { drawMizuhiki(c) }
        }
        BoxTie.TASSEL -> {
            vanishing(bow, Offset(CX, KY - 1f)) { a ->
                drawCircle(c.cord, 5f, Offset(CX, KY - 1f), alpha = a)
                drawCircle(c.bowLine, 2.2f, Offset(CX, KY - 1f), alpha = a, style = Stroke(1f))
            }
            if (lid < 1f) {
                drawPath(
                    Path().apply { moveTo(CX + 2f, KY + 2f); cubicTo(CX + 10f, KY + 13f, CX + 9f, B - 21f, CX + 6f, B - 9f) },
                    c.cord, alpha = 1f - lid, style = Stroke(2.4f, cap = StrokeCap.Round),
                )
            }
            // The tassel drops and lies on the table.
            val fall = p.stage(0.12f, 0.75f).settle()
            withTransform({
                translate(-4f * fall, 9f * fall)
                rotate(78f * fall, Offset(CX + 6f, B - 10f))
            }) {
                drawRoundRect(c.cord, Offset(CX + 2.5f, B - 10f), Size(7f, 5f), CornerRadius(2f))
                drawRoundRect(c.cord, Offset(CX + 1.5f, B - 6f), Size(9f, 13f), CornerRadius(1f))
                listOf(4f, 6f, 8f).forEach { dx -> drawLine(c.bowLine, Offset(CX + dx, B - 4f), Offset(CX + dx, B + 6f), 0.9f) }
            }
            if (loose > 0f) {
                drawPath(Path().apply { moveTo(CX + 1f, B - 3f); cubicTo(CX + 5f, B + 3f, CX + 17f, B + 5f, CX + 27f, B + 3f) }, c.cord, alpha = loose, style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
    }
}

/** A face of the box: its fill, and washi's pattern over it in the paper's color. */
private fun DrawScope.face(rect: Rect, radius: Float, color: Color, pattern: TapePattern, c: BoxInks) {
    drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(radius))
    if (c.finish != BoxFinish.WASHI || pattern == TapePattern.PLAIN) return
    clipPath(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }) {
        when (pattern) {
            TapePattern.DOTS -> {
                var x = 3.5f
                while (x < rect.right + 7f) {
                    var y = 3.5f
                    while (y < rect.bottom + 7f) {
                        if (x > rect.left - 2f && y > rect.top - 2f) drawCircle(c.kami, 1.25f, Offset(x, y))
                        y += 7f
                    }
                    x += 7f
                }
            }
            TapePattern.STRIPES -> rotate(45f, Offset.Zero) {
                var x = -200f
                while (x < 200f) {
                    drawRect(c.kami, Offset(x, -200f), Size(2.6f, 400f))
                    x += 7f
                }
            }
            TapePattern.GINGHAM -> {
                val band = c.kami.copy(alpha = 0.5f)
                var x = 0f
                while (x < rect.right) { drawRect(band, Offset(x, rect.top), Size(4f, rect.height)); x += 8f }
                var y = 0f
                while (y < rect.bottom) { drawRect(band, Offset(rect.left, y), Size(rect.width, 4f)); y += 8f }
            }
            TapePattern.PLAIN -> Unit
            // The nine from v0.5, from the same tiles as the tape, at the box's own size.
            else -> drawPatternOver(pattern, Rect(0f, 0f, rect.right, rect.bottom), c.kami, color, 0.9f, 1f, centered = false)
        }
    }
}

private fun DrawScope.drawLidMark(box: LetterBox, c: BoxInks, texts: LidTexts, at: Offset) {
    when (box.lid) {
        BoxLid.NAME -> {
            val w = max(texts.plate.size.width + 18f, 40f)
            drawPath(chamfer(at, w, 22f, 5f), c.kami)
            drawCentered(texts.plate, at, c.sumi)
        }
        BoxLid.SEAL -> {
            drawCircle(c.kami, 14f, at)
            drawCircle(c.ink, 11f, at, style = Stroke(1.4f))
            if (texts.sealChars.size > 1) {
                drawCentered(texts.sealChars[0], at + Offset(0f, -5.5f), c.ink)
                drawCentered(texts.sealChars[1], at + Offset(0f, 5.5f), c.ink)
            } else {
                drawCentered(texts.sealChars[0], at, c.ink)
            }
        }
        BoxLid.ENGRAVED, BoxLid.GILDED -> {
            val (under, over, dy) = when {
                box.lid == BoxLid.GILDED -> Triple(c.goldDeep, c.gold, 1f)
                c.finish == BoxFinish.LACQUER -> Triple(c.ink, c.t(0.25f), -1f)
                c.finish == BoxFinish.WASHI -> Triple(c.kami, c.t(0.7f), 1f)
                else -> Triple(c.kami, lerp(c.nijimi1, c.sumi, 0.45f), 1f)
            }
            drawCentered(texts.cut, at + Offset(0f, dy), under)
            drawCentered(texts.cut, at, over)
        }
        BoxLid.KANJI -> drawCentered(texts.kanjiBig, at, if (c.finish == BoxFinish.LACQUER) c.t(0.25f) else c.t(0.7f))
    }
}

private fun DrawScope.drawCentered(text: TextLayoutResult, at: Offset, color: Color) {
    drawText(text, color = color, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
}

/** Their letters lying inside, the top one with their name in their hand. */
private fun DrawScope.drawLetters(c: BoxInks, texts: LidTexts, rise: Float) {
    val floor = E - 16f
    val y = 14f + floor * 0.28f
    val h = floor * 0.72f
    translate(top = 4f * (1f - rise) - 5f * rise) {
        rotate(5f, Offset(W * 0.62f, y + h / 2f)) {
            drawRect(c.sheet, Offset(W * 0.46f, y + 1f), Size(W * 0.34f, h - 2f), alpha = rise)
            drawRect(c.rule, Offset(W * 0.51f, y + h * 0.3f), Size(W * 0.21f, 2f), alpha = rise)
            drawRect(c.rule, Offset(W * 0.51f, y + h * 0.55f), Size(W * 0.16f, 2f), alpha = rise)
        }
        rotate(-4f, Offset(44f, y + h / 2f)) {
            drawRect(c.sheet, Offset(24f, y), Size(max(44f, W * 0.36f), h), alpha = rise)
            drawText(texts.hand, color = c.ink, topLeft = Offset(29f, y + h / 2f - texts.hand.size.height / 2f), alpha = rise)
        }
    }
}

/** Something that undoes as the box opens: it shrinks to nothing where it was tied. */
private inline fun DrawScope.vanishing(progress: Float, at: Offset, draw: DrawScope.(alpha: Float) -> Unit) {
    if (progress >= 1f) return
    scale(1f - 0.85f * progress, 1f - 0.85f * progress, pivot = at) { draw(1f - progress) }
}

private fun DrawScope.drawBow(c: BoxInks, at: Offset, alpha: Float) {
    val (x, y) = at
    drawPath(Path().apply { moveTo(x, y); cubicTo(x - 5f, y - 10f, x - 16f, y - 10f, x - 15f, y - 1f); cubicTo(x - 14f, y + 6f, x - 6f, y + 5f, x, y); close() }, c.cord, alpha = alpha)
    drawPath(Path().apply { moveTo(x, y); cubicTo(x + 5f, y - 10f, x + 16f, y - 10f, x + 15f, y - 1f); cubicTo(x + 14f, y + 6f, x + 6f, y + 5f, x, y); close() }, c.cord, alpha = alpha)
    val line = Path().apply {
        moveTo(x - 3f, y - 1.5f); cubicTo(x - 6f, y - 5f, x - 10f, y - 5f, x - 11f, y - 1.5f)
        moveTo(x + 3f, y - 1.5f); cubicTo(x + 6f, y - 5f, x + 10f, y - 5f, x + 11f, y - 1.5f)
    }
    drawPath(line, c.bowLine, alpha = alpha, style = Stroke(1f, cap = StrokeCap.Round))
    drawPath(polygon(x - 1f, y + 1f, x - 8f, y + 13f, x - 5.5f, y + 11.5f, x - 4.5f, y + 14.5f, x + 2f, y + 2.5f), c.cord, alpha = alpha)
    drawPath(polygon(x + 1f, y + 1f, x + 8f, y + 13f, x + 5.5f, y + 11.5f, x + 4.5f, y + 14.5f, x - 2f, y + 2.5f), c.cord, alpha = alpha)
    drawRoundRect(c.cord, Offset(x - 4f, y - 4f), Size(8f, 8f), CornerRadius(2.5f), alpha = alpha)
}

/** The freed ends, dropped down the front and lying on the table, one curling each way. */
private fun DrawScope.drawLooseEnds(c: BoxInks, alpha: Float) {
    val stroke = Stroke(4f, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(CX - 1f, B - 3f); cubicTo(CX - 4f, B + 3f, CX - 14f, B + 4f, CX - 23f, B + 2f); cubicTo(CX - 27f, B + 1f, CX - 31f, B + 2f, CX - 34f, B + 4f) }, c.cord, alpha = alpha, style = stroke)
    drawPath(Path().apply { moveTo(CX + 1f, B - 3f); cubicTo(CX + 5f, B + 3f, CX + 17f, B + 5f, CX + 27f, B + 3f) }, c.cord, alpha = alpha, style = stroke)
}

/** Mizuhiki: three paper cords (their ink, the paper, their ink) round the box and tied in an awaji knot. */
private fun DrawScope.drawMizuhiki(c: BoxInks) {
    listOf(-2.6f to c.cord, 0f to c.kami, 2.6f to c.cord).forEach { (d, color) ->
        val x = CX + d
        val y = KY
        val path = Path().apply {
            moveTo(x, 1f); lineTo(x, y - 14f)
            moveTo(x - 7f, y + 15f)
            cubicTo(x - 7f, y + 7f, x - 15f, y + 6f, x - 15f, y)
            cubicTo(x - 15f, y - 7f, x - 5f, y - 7f, x, y - 1f)
            cubicTo(x + 5f, y + 5f, x + 15f, y + 6f, x + 15f, y)
            cubicTo(x + 15f, y - 7f, x + 5f, y - 7f, x, y - 1f)
            cubicTo(x - 5f, y - 6f, x - 6f, y - 14f, x, y - 14f)
            cubicTo(x + 6f, y - 14f, x + 5f, y - 6f, x, y + 1f)
            cubicTo(x + 4f, y + 6f, x + 7f, y + 8f, x + 7f, y + 15f)
            moveTo(x - 7f, y + 15f); lineTo(x - 7f, B)
            moveTo(x + 7f, y + 15f); lineTo(x + 7f, B)
        }
        drawPath(path, color, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun polygon(vararg xy: Float) = Path().apply {
    moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
    close()
}

private fun chamfer(center: Offset, w: Float, h: Float, cut: Float): Path {
    val x = center.x - w / 2f
    val y = center.y - h / 2f
    return polygon(x + cut, y, x + w - cut, y, x + w, y + cut, x + w, y + h - cut, x + w - cut, y + h, x + cut, y + h, x, y + h - cut, x, y + cut)
}
