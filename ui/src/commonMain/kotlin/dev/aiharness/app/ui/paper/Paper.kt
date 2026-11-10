package dev.aiharness.app.ui.paper

import dev.aiharness.app.ui.theme.onInk
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.TapePattern
import kotlin.math.max
import kotlin.random.Random

private val CardShape = RoundedCornerShape(3.dp)

/** A sheet of paper on the desk: paper colour, a hairline edge and a soft shadow. */
@Composable
fun Modifier.paperCard(color: Color = paper.kami, shape: Shape = CardShape): Modifier {
    val colors = paper
    return this
        .shadow(if (colors.isNight) 3.dp else 2.dp, shape, ambientColor = colors.chitsu, spotColor = Color.Black.copy(alpha = 0.35f))
        .background(color, shape)
        .border(1.dp, colors.nijimi0, shape)
}

/**
 * The ink-wash portrait that stands in until a persona has real art: soft blooms of its ink on
 * paper, with a faint figure. Rendered once per size into a bitmap.
 */
@Composable
fun InkPortrait(seed: String, ink: Color, modifier: Modifier = Modifier, figure: Boolean = true) {
    val colors = paper
    val second = stationery.you
    Box(
        modifier.drawWithCache {
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(this, layoutDirection, GraphicsCanvas(bitmap), size) {
                val random = Random(seed.hashCode() * 9973L + 1)
                drawRect(colors.kami)
                repeat(8) { i ->
                    val centre = Offset(size.width * (0.15f + random.nextFloat() * 0.7f), size.height * (0.1f + random.nextFloat() * 0.75f))
                    val radius = max(size.width, size.height) * (0.2f + random.nextFloat() * 0.4f)
                    val colour = if (i % 5 == 4) second else ink
                    drawRect(
                        Brush.radialGradient(
                            0f to colour.copy(alpha = 0.55f),
                            0.6f to colour.copy(alpha = 0.18f),
                            1f to colour.copy(alpha = 0f),
                            center = centre,
                            radius = radius,
                        ),
                    )
                }
                if (figure) {
                    val cx = size.width / 2
                    val base = size.height * 1.02f
                    val head = minOf(size.width, size.height) * 0.2f
                    val cy = base - head * 3.2f
                    drawCircle(
                        Brush.radialGradient(0f to colors.sumi.copy(alpha = 0.4f), 1f to colors.sumi.copy(alpha = 0f), center = Offset(cx, cy), radius = head * 1.15f),
                        radius = head * 1.15f,
                        center = Offset(cx, cy),
                    )
                    drawOval(
                        Brush.radialGradient(0f to colors.sumi.copy(alpha = 0.34f), 1f to colors.sumi.copy(alpha = 0f), center = Offset(cx, base), radius = head * 2.6f),
                        topLeft = Offset(cx - head * 2.6f, base - head * 1.7f),
                        size = androidx.compose.ui.geometry.Size(head * 5.2f, head * 3.4f),
                    )
                }
                repeat((size.width * size.height / 60f).toInt().coerceAtMost(6_000)) {
                    drawRect(colors.sumi.copy(alpha = 0.05f * random.nextFloat()), Offset(random.nextFloat() * size.width, random.nextFloat() * size.height), androidx.compose.ui.geometry.Size(1f, 1f))
                }
            }
            onDrawBehind { drawImage(bitmap) }
        },
    )
}

/** A persona's picture: its avatar if it has one, otherwise the ink-wash stand-in. */
@Composable
fun PersonaPortrait(persona: Persona?, modifier: Modifier = Modifier, figure: Boolean = true) {
    val ref = persona?.avatarRef
    if (ref != null) {
        BlobImage(ref, modifier, maxSize = 512)
    } else {
        InkPortrait(persona?.id ?: "?", stationery.ink(persona?.ink), modifier, figure)
    }
}

/** Round portrait for letter headers and small rows. */
@Composable
fun PortraitDot(persona: Persona?, size: Dp, modifier: Modifier = Modifier) {
    PersonaPortrait(persona, modifier.size(size).clip(CircleShape).border(1.dp, paper.nijimi0, CircleShape), figure = false)
}

/** The persona's mood word, in its ink. Nothing when there is no mood yet. */
@Composable
fun MoodWord(mood: String?, ink: Color, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = PaperType.mood) {
    if (mood.isNullOrBlank()) return
    Text(mood, modifier = modifier, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A polaroid taped into the page: a thick frame (the sticker outline colour by default), a square
 * picture and a caption.
 */
@Composable
fun Polaroid(
    persona: Persona,
    modifier: Modifier = Modifier,
    tiltDegrees: Float = 0f,
    tapeTiltDegrees: Float = -4f,
    bottomPadding: Dp = 0.dp,
    framePadding: Dp = 10.dp,
    cornerTaped: Boolean = false,
    foldedCorner: Boolean = false,
    caption: @Composable (() -> Unit)? = null,
) = PolaroidFrame(
    tape = tapeOf(persona),
    modifier = modifier,
    tiltDegrees = tiltDegrees,
    tapeTiltDegrees = tapeTiltDegrees,
    bottomPadding = bottomPadding,
    framePadding = framePadding,
    cornerTaped = cornerTaped,
    foldedCorner = foldedCorner,
    picture = { PersonaPortrait(persona, it) },
    caption = caption,
)

/**
 * A polaroid of anything: [picture] fills the square. With [foldedCorner], its bottom right corner is
 * folded down (the mark of the persona open beside the gallery, docs/plan-desktop.md): the desk shows
 * where the corner was, and the flap (the frame turned over) is tinted with your ink.
 */
@Composable
fun PolaroidFrame(
    tape: Tape,
    modifier: Modifier = Modifier,
    tiltDegrees: Float = 0f,
    tapeTiltDegrees: Float = -4f,
    bottomPadding: Dp = 0.dp,
    framePadding: Dp = 10.dp,
    cornerTaped: Boolean = false,
    foldedCorner: Boolean = false,
    picture: @Composable (Modifier) -> Unit,
    caption: @Composable (() -> Unit)? = null,
) {
    val s = stationery
    val shape = if (foldedCorner) FoldedCornerShape(FOLD, 3.dp) else RoundedCornerShape(3.dp)
    val flap = lerp(s.frame, s.you, 0.26f)
    Column(
        modifier
            .rotate(tiltDegrees)
            .then(
                if (cornerTaped) {
                    Modifier.cornerTape(tape)
                } else {
                    Modifier.topTape(tape, tiltDegrees = tapeTiltDegrees)
                },
            )
            .shadow(3.dp, shape, ambientColor = s.colors.chitsu, spotColor = Color.Black.copy(alpha = 0.35f))
            .background(s.frame, shape)
            .border(1.dp, s.colors.nijimi1.copy(alpha = 0.45f), shape)
            .then(if (foldedCorner) Modifier.drawWithContent { drawContent(); drawFlap(FOLD.toPx(), flap) } else Modifier)
            .padding(start = framePadding, end = framePadding, top = framePadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        picture(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(1.dp)))
        caption?.invoke()
    }
}

/** How far along each edge a polaroid's corner is folded. */
private val FOLD = 22.dp

/** A rounded rectangle with its bottom right corner cut off along the diagonal, [fold] along each edge. */
private class FoldedCornerShape(private val fold: Dp, private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { fold.toPx() }.coerceAtMost(minOf(size.width, size.height))
        val r = with(density) { radius.toPx() }
        val (w, h) = size.width to size.height
        return Outline.Generic(
            Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                quadraticTo(w, 0f, w, r)
                lineTo(w, h - f)
                lineTo(w - f, h)
                lineTo(r, h)
                quadraticTo(0f, h, 0f, h - r)
                lineTo(0f, r)
                quadraticTo(0f, 0f, r, 0f)
                close()
            },
        )
    }
}

/**
 * The folded-down flap: the cut corner turned over onto the frame, with a soft shadow above and to
 * its left, where it lifts off the paper.
 */
private fun DrawScope.drawFlap(fold: Float, color: Color) {
    val (w, h) = size.width to size.height
    fun flap(dx: Float, dy: Float) = Path().apply {
        moveTo(w - fold + dx, h + dy)
        lineTo(w + dx, h - fold + dy)
        lineTo(w - fold + dx, h - fold + dy)
        close()
    }
    val lift = 1.dp.toPx()
    drawPath(flap(-lift, -lift), Color(0xFF1E1208).copy(alpha = 0.10f))
    drawPath(flap(-lift / 2, -lift / 2), Color(0xFF1E1208).copy(alpha = 0.10f))
    drawPath(flap(0f, 0f), color)
}

/** A user persona's picture: their photo if they chose one, otherwise an ink wash in their ink. */
@Composable
fun UserPortrait(user: dev.aiharness.core.model.UserPersona?, modifier: Modifier = Modifier) {
    val ref = user?.avatarRef
    if (ref != null) BlobImage(ref, modifier, maxSize = 512) else InkPortrait(user?.id ?: "you", stationery.ink(user?.ink ?: "sora"), modifier, figure = false)
}

/**
 * The default unread mark: a 新 seal stamped in the persona's day ink, with a die-cut outline.
 * A persona with a sticker for hello wears that instead (see `StickerService.unreadMark`).
 */
@Composable
fun UnreadSeal(inkName: String?, modifier: Modifier = Modifier, size: Dp = 34.dp, kanji: String = "新") {
    val s = stationery
    val ink = s.dayColors.ink(inkName).copy(alpha = 0.92f)
    val paperColour = s.dayColors.kami
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = 0.18f), r, center.copy(y = center.y + 1.5.dp.toPx()))
            drawCircle(s.outline, r)
            drawCircle(paperColour, r * 0.86f)
            drawCircle(ink, r * 0.68f, style = Stroke(width = r * 0.09f))
        }
        Text(kanji, style = PaperType.seal.copy(fontSize = (size.value * 0.42f).sp), color = ink, textAlign = TextAlign.Center)
    }
}

/** A label written on a strip of plain tape (the active tab in the navigation bar). */
@Composable
fun TapeLabel(text: String, ink: Color, modifier: Modifier = Modifier, on: Boolean = true) {
    val colors = paper
    Box(
        modifier.drawWithCache {
            onDrawBehind {
                if (on) {
                    rotate(-2f, pivot = center) {
                        drawTape(
                            Offset(-3.dp.toPx(), size.height / 2),
                            Offset(size.width + 3.dp.toPx(), size.height / 2),
                            size.height + 2.dp.toPx(),
                            // Always the dark shade: the label is written on it in white.
                            Tape(ink, TapePattern.PLAIN, shadow = false),
                            tooth = 2.dp.toPx(),
                        )
                    }
                }
            }
        }.padding(horizontal = 9.dp, vertical = 1.dp),
    ) {
        Text(text, style = PaperType.navLabel.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else null), color = if (on) colors.onInk(ink) else colors.kasure2)
    }
}
