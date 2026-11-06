package dev.aiharness.app.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.drawTape
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Lamplight
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.clock
import dev.aiharness.core.model.humanSize
import kotlin.math.abs

/*
 * Media frames: each attachment as a stationery object, in its sender's ink (tape, paperclip, labels
 * and tints), after the design session's "Media frames" canvas. Frames are objects on the desk, so
 * they keep their day colours; at night they're lit as if by a desk lamp (see [lamplit]).
 * Sizes are in dp at [scale] 1 (letters); notes draw them smaller.
 */

private val FramePaper = Color(0xFFFFFDF9)
private val FrameEdge = Color(0xFFCEBEAF)
private val Brown = Color(0xFF3E3226)
private val SoftBrown = Color(0xFF4A3B2E)
private val Newsprint = Color(0xFFF2EDE2)
private val NewsInk = Color(0xFF2E2620)

/** A slight tilt for a frame, the same every time for the same thing (about ±2°). */
fun frameTilt(seed: String): Float = ((abs(seed.hashCode()) % 400) / 100f) - 2f

/**
 * Lamplight for objects on the desk: at night they're dimmed a little and warmed, unless the
 * setting leaves them be ([photo]: photos are left alone by "Leave photos as they are").
 */
@Composable
fun Modifier.lamplit(photo: Boolean = false): Modifier {
    val s = stationery
    val on = s.isNight && when (s.lamplight) {
        Lamplight.DIM_ALL -> true
        Lamplight.KEEP_PHOTOS -> !photo
        Lamplight.NONE -> false
    }
    if (!on) return this
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        // Tint only what's drawn: toward a warm brown, which dims and warms at once.
        drawRect(Color(0xFF3A2410).copy(alpha = 0.2f), blendMode = BlendMode.SrcAtop)
    }
}

/** What a picture's caption on a polaroid should be: short ones only (a drawing's prompt isn't a caption). */
fun polaroidCaption(caption: String?): String? = caption?.trim()?.takeIf { it.isNotEmpty() && it.length <= 28 && ',' !in it }

/** One photo as a polaroid with a piece of tape in [ink] at the top. */
@Composable
fun PolaroidFrame(image: ContentPart.Image, ink: Color, pattern: TapePattern, scale: Float = 1f, modifier: Modifier = Modifier) {
    val caption = polaroidCaption(image.caption)
    val lift = LocalLift.current
    Box(modifier.lamplit(photo = true)) {
        Column(
            Modifier
                .padding(top = (9 * scale).dp)
                .width((236 * scale).dp)
                .shadow(((2 + 8 * lift) * scale).dp, RoundedCornerShape(3.dp))
                .background(FramePaper, RoundedCornerShape(3.dp))
                .border(1.dp, FrameEdge.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(start = (12 * scale).dp, end = (12 * scale).dp, top = (12 * scale).dp),
        ) {
            BlobImage(image.ref, Modifier.size((210 * scale).dp), contentScale = ContentScale.Crop, maxSize = 720)
            Box(Modifier.fillMaxWidth().height(((if (caption != null) 46 else 30) * scale).dp), contentAlignment = Alignment.Center) {
                caption?.let { Text(it, style = hand(15f * scale), color = SoftBrown, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        TapePiece(ink, pattern, Modifier.align(Alignment.TopCenter).offset(y = 0.dp).size((68 * scale).dp, (18 * scale).dp), tilt = -4f)
    }
}

/**
 * Several photos: a small stack of up to three polaroids, the first on top, with a count. Tapped,
 * it fans out ([fan], 0 to 1, overshooting a little): the back photos swing to ±15°, about 75dp
 * out, and each can be tapped ([onPhoto], by index).
 */
@Composable
fun PhotoStack(
    images: List<ContentPart.Image>,
    ink: Color,
    pattern: TapePattern,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    fan: Float = 0f,
    onPhoto: ((Int) -> Unit)? = null,
) {
    val behind = images.drop(1).take(2)
    fun Modifier.openable(index: Int) = if (onPhoto == null) this else
        clickable(onClickLabel = "Open this photo") { onPhoto(index) }.semantics { contentDescription = "Photo ${index + 1} of ${images.size}" }
    // Lit photo by photo (not the stack as one layer), so a photo fanned past the stack isn't cut off.
    val lit = Modifier.lamplit(photo = true)
    Box(modifier.size((272 * scale).dp, (300 * scale).dp)) {
        // Three fan to ±15° about 75dp out; a pair opens like two cards in a hand, the front leaning left.
        val pair = behind.size == 1
        behind.forEachIndexed { i, image ->
            val out = if (pair) 36f else if (i == 0) 75f else -75f
            val tilt = if (i == 0) 6f else -7f
            val fanned = if (pair) 12f else if (i == 0) 15f else -15f
            Box(
                Modifier
                    .offset(x = ((if (i == 0) 66 else 4) * scale).dp, y = ((if (i == 0) 8 else 22) * scale).dp)
                    .graphicsLayer {
                        translationX = out * fan * scale * density
                        rotationZ = tilt + (fanned - tilt) * fan
                    }
                    .then(lit)
                    .openable(i + 1)
                    .width((200 * scale).dp)
                    .shadow((1.5f * scale).dp, RoundedCornerShape(3.dp))
                    .background(FramePaper, RoundedCornerShape(3.dp))
                    .padding(start = (10 * scale).dp, end = (10 * scale).dp, top = (10 * scale).dp, bottom = (34 * scale).dp),
            ) {
                BlobImage(image.ref, Modifier.size((178 * scale).dp), contentScale = ContentScale.Crop, maxSize = 480)
            }
        }
        Box(
            Modifier.offset(x = (30 * scale).dp, y = (21 * scale).dp)
                .graphicsLayer {
                    if (pair) {
                        translationX = -30f * fan * scale * density
                        rotationZ = -5f * fan
                    }
                }
                .then(lit)
                .openable(0),
        ) {
            Column(
                Modifier
                    .padding(top = (9 * scale).dp)
                    .width((212 * scale).dp)
                    .shadow((2.5f * scale).dp, RoundedCornerShape(3.dp))
                    .background(FramePaper, RoundedCornerShape(3.dp))
                    .padding(start = (12 * scale).dp, end = (12 * scale).dp, top = (12 * scale).dp),
            ) {
                BlobImage(images.first().ref, Modifier.size((186 * scale).dp), contentScale = ContentScale.Crop, maxSize = 720)
                Spacer(Modifier.height((40 * scale).dp))
            }
            TapePiece(ink, pattern, Modifier.align(Alignment.TopCenter).size((64 * scale).dp, (18 * scale).dp), tilt = 3f)
        }
        Text(
            "${images.size} photos",
            style = hand(13f * scale),
            color = Brown,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(lit)
                .offset(x = (-2 * scale).dp, y = (-4 * scale).dp)
                .graphicsLayer { rotationZ = 4f }
                .background(Color(0xFFFBF6F1))
                .border(1.dp, Color(0xFFDFCEBF))
                .padding(horizontal = (9 * scale).dp, vertical = (2 * scale).dp),
        )
    }
}

/** A file as a luggage tag tinted with the sender's [ink]: its name, kind and size, on a string. */
@Composable
fun LuggageTag(file: ContentPart.Attachment, ink: Color, scale: Float = 1f, modifier: Modifier = Modifier) {
    val tag = lerp(Color(0xFFF4EFE6), ink, 0.22f)
    val meta = listOfNotNull(
        (file.mimeType?.substringAfter('/')?.takeIf { it.length <= 12 } ?: file.name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() })?.uppercase(),
        file.pages?.let { if (it == 1) "1 PAGE" else "$it PAGES" },
        file.sizeBytes?.let { humanSize(it).uppercase() },
    ).joinToString(" · ")
    Box(modifier.size((268 * scale).dp, (104 * scale).dp).lamplit()) {
        Box(
            Modifier
                .offset(x = (14 * scale).dp, y = (4 * scale).dp)
                .size((252 * scale).dp, (96 * scale).dp)
                .shadow((1.5f * scale).dp, TagShape)
                .background(tag, TagShape)
                .padding(start = (62 * scale).dp, end = (16 * scale).dp, top = (12 * scale).dp, bottom = (10 * scale).dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
                Text(file.name ?: "a file", style = hand(17f * scale), color = Brown, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotEmpty()) Text(meta, style = ui(10.5f * scale, spacing = 0.08f), color = SoftBrown)
            }
        }
        // The eyelet, and the string through it.
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 268f
            drawPath(
                Path().apply { moveTo(48 * k, 52 * k); cubicTo(32 * k, 56 * k, 20 * k, 76 * k, 8 * k, 68 * k); cubicTo(-2 * k, 61 * k, 4 * k, 44 * k, -10 * k, 36 * k) },
                lerp(Color(0xFF8D7D6F), ink, 0.35f),
                style = Stroke(1.6f * k, cap = StrokeCap.Round),
            )
            drawCircle(Color(0xFFFBF3E8), 8 * k, Offset(48 * k, 52 * k))
            drawCircle(lerp(ink, Color.White, 0.25f), 7 * k, Offset(48 * k, 52 * k), style = Stroke(2 * k))
            drawCircle(Color(0xFF8D7D6F), 3 * k, Offset(48 * k, 52 * k))
        }
    }
}

private val TagShape = GenericShape { size, _ ->
    moveTo(size.width * 0.16f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width * 0.16f, size.height)
    lineTo(0f, size.height * 0.7f)
    lineTo(0f, size.height * 0.3f)
    close()
}

/**
 * A playlist as a cassette: the label handwritten in the sender's [ink], side A, the length, the
 * reels. While it plays the hubs [turn] (1.4 s a turn) and the tape winds from one reel to the
 * other as the playlist goes ([wound], 0 to 1).
 */
@Composable
fun Cassette(mix: ContentPart.Attachment, ink: Color, scale: Float = 1f, modifier: Modifier = Modifier, turn: Boolean = false, wound: Float = 0f) {
    val label = Color(0xFFF6ECD9)
    Box(
        modifier
            .size((240 * scale).dp, (150 * scale).dp)
            .lamplit()
            .shadow((2 * scale).dp, RoundedCornerShape((9 * scale).dp))
            .background(Color(0xFF3B3530), RoundedCornerShape((9 * scale).dp))
            .padding(start = (12 * scale).dp, end = (12 * scale).dp, top = (10 * scale).dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((92 * scale).dp)
                .background(label, RoundedCornerShape((4 * scale).dp))
                .ruled(scale),
        ) {
            Box(
                Modifier.offset(x = (7 * scale).dp, y = (7 * scale).dp).size((17 * scale).dp).background(ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("A", style = ui(10f * scale, bold = true), color = FramePaper) }
            Column(Modifier.padding(start = (30 * scale).dp, end = (44 * scale).dp, top = (6 * scale).dp)) {
                Text(mix.title ?: "a mix", style = hand(16f * scale, semibold = true), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                mix.subtitle?.let { Text(it, style = hand(12f * scale), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            mix.lengthMs?.takeIf { it > 0 }?.let {
                Text(clock(it), style = ui(10f * scale), color = SoftBrown, modifier = Modifier.align(Alignment.TopEnd).padding(top = (8 * scale).dp, end = (9 * scale).dp))
            }
            Reels(scale, Modifier.align(Alignment.BottomCenter).padding(bottom = (8 * scale).dp), turn, wound)
        }
        // The cassette's lower edge, where the tape shows.
        Canvas(Modifier.align(Alignment.BottomCenter).size((180 * scale).dp, (36 * scale).dp)) {
            val k = size.width / 180f
            drawPath(
                Path().apply { moveTo(20 * k, 8 * k); lineTo(160 * k, 8 * k); lineTo(172 * k, 36 * k); lineTo(8 * k, 36 * k); close() },
                Color(0xFF4A433D),
            )
            drawCircle(Color(0xFF2B2622), 3.5f * k, Offset(46 * k, 24 * k))
            drawCircle(Color(0xFF2B2622), 3.5f * k, Offset(134 * k, 24 * k))
        }
    }
}

@Composable
private fun Reels(scale: Float, modifier: Modifier, turn: Boolean = false, wound: Float = 0f) {
    val angle = if (turn && !reducedMotion()) {
        rememberInfiniteTransition(label = "reels").animateFloat(0f, 360f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "hubs").value
    } else {
        0f
    }
    val w = wound.coerceIn(0f, 1f)
    Canvas(modifier.size((124 * scale).dp, (34 * scale).dp)) {
        val k = size.width / 124f
        drawRoundRect(Color(0xFF2B2622), Offset.Zero, size, CornerRadius(17 * k))
        drawRoundRect(Color(0xFF5A4A3C), Offset(36 * k, 11 * k), Size(52 * k, 12 * k), CornerRadius(3 * k))
        // The tape winds from the left reel onto the right one.
        listOf(18f to 13f - 6f * w, 106f to 7f + 6f * w).forEach { (x, pack) ->
            val hub = Offset(x * k, 17 * k)
            drawCircle(Color(0xFF3D342D), 14 * k, hub)
            drawCircle(Color(0xFF6B5443), pack * k, hub)
            drawCircle(Color(0xFFF3E4CC), 6 * k, hub)
            rotate(angle, hub) {
                drawCircle(Color(0xFF2B2622), 6 * k, hub, style = Stroke(2f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * k, 2 * k))))
            }
        }
    }
}

/** How far a frame is lifted off the page (0 to 1) as it's tapped open: its shadow deepens. */
val LocalLift = compositionLocalOf { 0f }

/** Whether Android's "Remove animations" is on, so every effect is instant. */
@Composable
fun reducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val system = remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    // Or the Media page's "Paper motion" switch.
    return system || !LocalMediaSettings.current.paperMotion
}

private fun Modifier.ruled(scale: Float) = drawWithContent {
    val step = 16.dp.toPx() * scale
    var y = step
    while (y < size.height) {
        drawLine(Color(0xFF5D4C3C).copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    drawContent()
}

/** A single song as a ticket stub tinted with the sender's [ink], punched once it's been played. */
@Composable
fun TicketStub(song: ContentPart.Attachment, ink: Color, behind: Color, scale: Float = 1f, punched: Boolean = false, modifier: Modifier = Modifier) {
    val ticket = lerp(Color(0xFFF7F0E4), ink, 0.18f)
    // Punched the first time it plays: the hole pops open (260ms) and the chad falls and fades (700ms).
    val instant = reducedMotion()
    val wasPunched = remember { punched }
    val hole = remember { Animatable(if (punched) 1f else 0f) }
    val chad = remember { Animatable(0f) }
    LaunchedEffect(punched) {
        if (!punched) return@LaunchedEffect hole.snapTo(0f)
        if (wasPunched || instant) return@LaunchedEffect hole.snapTo(1f)
        launch { hole.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        chad.snapTo(0.001f)
        chad.animateTo(1f, tween(700, delayMillis = 120, easing = FastOutLinearInEasing))
        chad.snapTo(0f)
    }
    Box(modifier.size((280 * scale).dp, (108 * scale).dp).lamplit()) {
        Row(Modifier.fillMaxSize().shadow((1.5f * scale).dp, RoundedCornerShape((4 * scale).dp)).background(ticket, RoundedCornerShape((4 * scale).dp))) {
            Column(
                Modifier.weight(1f).fillMaxSize().padding(horizontal = (14 * scale).dp, vertical = (11 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((2 * scale).dp),
            ) {
                // A song shared from a service says where it plays.
                val label = song.site?.takeIf { song.ref == null && song.url != null }?.uppercase() ?: "SIDE A · TRACK 1"
                Text(label, style = ui(9.5f * scale, bold = true, spacing = 0.16f), color = ink)
                Text(song.title ?: "a song", style = news(20f * scale, bold = true), color = Brown, maxLines = 1, overflow = TextOverflow.Ellipsis)
                song.artist?.let { Text(it, style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = (13 * scale).sp), color = SoftBrown, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                song.lengthMs?.let { Text(clock(it), style = ui(10f * scale, spacing = 0.08f), color = SoftBrown) }
            }
            Column(Modifier.width((68 * scale).dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Canvas(Modifier.size((28 * scale).dp)) {
                    val k = size.width / 28f
                    drawCircle(ink, 12 * k, center, style = Stroke(1.6f * k))
                    drawPath(Path().apply { moveTo(11 * k, 9 * k); lineTo(19 * k, 14 * k); lineTo(11 * k, 19 * k); close() }, ink)
                }
                Text("PLAY", style = ui(9f * scale, bold = true, spacing = 0.16f), color = ink, modifier = Modifier.padding(top = (5 * scale).dp))
            }
        }
        // The perforation, its notches showing the paper behind, and the punch once played.
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 280f
            val x = 212 * k
            drawLine(lerp(ticket, ink, 0.55f), Offset(x, 4 * k), Offset(x, size.height - 4 * k), 2 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * k, 4 * k)))
            drawCircle(behind, 7 * k, Offset(x, 0f))
            drawCircle(behind, 7 * k, Offset(x, size.height))
            if (hole.value > 0f) {
                val pop = if (hole.value < 1f) 1f + 0.25f * kotlin.math.sin(hole.value * Math.PI).toFloat() else 1f
                drawCircle(behind, 6 * k * hole.value * pop, Offset(246 * k, 18 * k))
            }
            if (chad.value > 0f) {
                drawCircle(ticket.copy(alpha = 1f - chad.value), 6 * k, Offset(246 * k + 6 * k * chad.value, 18 * k + 46 * k * chad.value))
            }
        }
    }
}

/**
 * A link as a newspaper cutting with uneven cut edges, held by a paperclip in the sender's [ink]:
 * the clip sits mostly above the top edge, its inner loop behind the paper and its outer loop in
 * front, as if gripping it.
 */
@Composable
fun Cutting(link: ContentPart.Attachment, ink: Color, scale: Float = 1f, modifier: Modifier = Modifier, readBy: Color? = null) {
    Box(modifier.width((288 * scale).dp).lamplit()) {
        val clipAt = Modifier.align(Alignment.TopEnd).padding(end = (24 * scale).dp)
        Paperclip(ink, scale, front = false, clipAt)
        Column(
            Modifier
                .padding(top = (CLIP_EDGE * CLIP_UNIT * scale).dp)
                .fillMaxWidth()
                .shadow((1.5f * scale).dp, CutShape)
                .background(Newsprint, CutShape)
                .padding(start = (16 * scale).dp, end = (16 * scale).dp, top = (16 * scale).dp, bottom = (18 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
        ) {
            Text(
                (link.site ?: link.url?.substringAfter("://")?.substringBefore('/') ?: "").uppercase(),
                style = news(10.5f * scale, spacing = 0.22f),
                color = Color(0xFF3E342B),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Kept narrower than the rules, so a long site name stays clear of the clip.
                modifier = Modifier.fillMaxWidth().rules().padding(vertical = (3 * scale).dp, horizontal = (34 * scale).dp),
            )
            Text(link.title ?: link.url ?: "", style = news(19f * scale, bold = true, height = 1.22f), color = NewsInk, maxLines = 4, overflow = TextOverflow.Ellipsis)
            link.pictureRef?.let { ref ->
                BlobImage(ref, Modifier.fillMaxWidth().height((100 * scale).dp).graphicsLayer {}.greyscale(), contentScale = ContentScale.Crop, maxSize = 640)
            }
            link.snippet?.let { Text(it, style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = (12.5f * scale).sp, lineHeight = (18.75f * scale).sp), color = Color(0xFF3E342B), maxLines = 4, overflow = TextOverflow.Ellipsis) }
        }
        Paperclip(ink, scale, front = true, clipAt)
        readBy?.let { ReadStamp(it, scale, Modifier.align(Alignment.BottomEnd).padding(end = (14 * scale).dp, bottom = (12 * scale).dp)) }
    }
}

/**
 * The persona's 読 ("read") stamp, inked on a cutting it opened. When it lands while you watch it
 * comes down from 1.9× and −26° to 1× and −12° with a slight bounce (460ms) and a light tick.
 */
@Composable
fun ReadStamp(ink: Color, scale: Float, modifier: Modifier = Modifier) {
    val instant = reducedMotion()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // At rest unless it's just been made (see LandStamp).
    val landing = remember { Animatable(1f) }
    val size = (50 * scale).dp
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val t = landing.value
                val grow = 1f + 0.9f * (1f - t)
                scaleX = grow
                scaleY = grow
                rotationZ = -12f - 14f * (1f - t)
                alpha = (0.15f + t).coerceAtMost(1f) * 0.88f
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2
            drawCircle(ink, r * 0.94f, style = Stroke(width = r * 0.11f))
            drawCircle(ink, r * 0.78f, style = Stroke(width = r * 0.04f))
        }
        Text("読", style = TextStyle(fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Bold), color = ink)
    }
    val tick = LocalMediaSettings.current.haptics
    LandStamp(landing, instant) { if (tick) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick) }
}

/** Runs a stamp's landing once, when it first appears in a letter already on screen. */
@Composable
private fun LandStamp(landing: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, instant: Boolean, tick: () -> Unit) {
    val fresh = LocalFreshStamp.current
    LaunchedEffect(Unit) {
        if (!fresh || instant) return@LaunchedEffect
        landing.snapTo(0f)
        landing.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        tick()
    }
}

/** True where a read stamp has just been made (not one that was already there when the letter opened). */
val LocalFreshStamp = compositionLocalOf { false }

private val CutShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.006f * w, 0.014f * h)
    lineTo(0.28f * w, 0f); lineTo(0.61f * w, 0.01f * h); lineTo(w, 0.002f * h); lineTo(0.993f * w, 0.38f * h); lineTo(w, 0.72f * h)
    lineTo(0.994f * w, h); lineTo(0.57f * w, 0.99f * h); lineTo(0.22f * w, h); lineTo(0f, 0.993f * h); lineTo(0.009f * w, 0.61f * h); lineTo(0f, 0.24f * h)
    close()
}

private fun Modifier.rules() = drawWithContent {
    drawLine(Color(0xFF3E342B), Offset(0f, 0f), Offset(size.width, 0f), 1f)
    drawLine(Color(0xFF3E342B), Offset(0f, size.height), Offset(size.width, size.height), 1f)
    drawContent()
}

private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Pictures on cuttings are printed in greyscale. */
private fun Modifier.greyscale() = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(Color.Gray, blendMode = BlendMode.Saturation)
}

/**
 * Half of a three-bend paperclip, in units of [CLIP_UNIT] dp: 16 wide, 46 tall, the paper's top
 * edge at [CLIP_EDGE]. The [front] half is the outer loop and the bend over the paper's edge,
 * with a faint shadow where it lies on the paper; the other half is the inner loop, drawn behind
 * the paper so only its top shows above the edge.
 */
@Composable
private fun Paperclip(ink: Color, scale: Float, front: Boolean, modifier: Modifier) {
    Canvas(modifier.size((16 * CLIP_UNIT * scale).dp, (46 * CLIP_UNIT * scale).dp).graphicsLayer { rotationZ = -3f }) {
        val k = size.width / 16f
        fun arc(l: Float, t: Float, r: Float, b: Float) = androidx.compose.ui.geometry.Rect(l * k, t * k, r * k, b * k)
        val wire = Stroke(1.7f * k, cap = StrokeCap.Round)
        if (front) {
            val outer = Path().apply {
                moveTo(2 * k, 6 * k); lineTo(2 * k, 38 * k)
                arcTo(arc(2f, 32f, 14f, 44f), 180f, -180f, false)
                lineTo(14 * k, 6 * k)
                arcTo(arc(6f, 2f, 14f, 10f), 0f, -180f, false)
            }
            clipRect(top = CLIP_EDGE * k) {
                translate(0.7f * k, 1.1f * k) { drawPath(outer, Color.Black.copy(alpha = 0.18f), style = wire) }
            }
            drawPath(outer, ink, style = wire)
        } else {
            val inner = Path().apply {
                moveTo(6 * k, 6 * k); lineTo(6 * k, 34 * k)
                arcTo(arc(6f, 32f, 10f, 36f), 180f, -180f, false)
                lineTo(10 * k, 14 * k)
            }
            drawPath(inner, ink, style = wire)
        }
    }
}

/** The paperclip's unit, in dp, and where the paper's top edge crosses it (mostly above, as clipped). */
private const val CLIP_UNIT = 1.125f
private const val CLIP_EDGE = 27f

/** A video as a film strip: sprocket holes, its three frames, the play mark and its length on a label. */
@Composable
fun FilmStrip(video: ContentPart.Attachment, scale: Float = 1f, modifier: Modifier = Modifier, run: Boolean = false) {
    // Running, the strip moves through as if in a projector: one set of frames each 1.1 s.
    val through = if (run && !reducedMotion()) {
        rememberInfiniteTransition(label = "film").animateFloat(0f, 280f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "frames").value
    } else {
        0f
    }
    Box(modifier.size((300 * scale).dp, (132 * scale).dp)) {
        Box(
            Modifier.fillMaxSize().shadow((2 * scale).dp, RoundedCornerShape(2.dp)).background(Color(0xFF2A2420), RoundedCornerShape(2.dp))
                .clip(RoundedCornerShape(2.dp)).lamplit(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / 300f
                var x = 10 * k - (through % 20f) * k
                while (x < size.width) {
                    drawRoundRect(Color(0xFFF0E6DD), Offset(x, 5 * k), Size(10 * k, 7 * k), CornerRadius(1.5f * k))
                    drawRoundRect(Color(0xFFF0E6DD), Offset(x, size.height - 12 * k), Size(10 * k, 7 * k), CornerRadius(1.5f * k))
                    x += 20 * k
                }
            }
            Row(
                Modifier.padding(start = (10 * scale).dp, top = (19 * scale).dp).wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = -through * scale * density },
                horizontalArrangement = Arrangement.spacedBy((5.33f * scale).dp),
            ) {
                val frames = video.frameRefs.ifEmpty { listOfNotNull(video.thumbRef) }
                // Running, a second set follows the first, so the strip never runs out.
                repeat(if (through > 0f) 6 else 3) { n ->
                    val i = n % 3
                    val ref = frames.getOrNull(i)
                    if (ref != null) {
                        BlobImage(ref, Modifier.size((88 * scale).dp, (94 * scale).dp), contentScale = ContentScale.Crop, maxSize = 320)
                    } else {
                        Box(Modifier.size((88 * scale).dp, (94 * scale).dp).background(Color(0xFF4A3F38)))
                    }
                }
            }
            Box(
                Modifier.align(Alignment.Center).size((40 * scale).dp).background(FramePaper.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size((16 * scale).dp)) {
                    val k = size.width / 16f
                    drawPath(Path().apply { moveTo(5 * k, 3 * k); lineTo(13 * k, 8 * k); lineTo(5 * k, 13 * k); close() }, Brown)
                }
            }
        }
        video.lengthMs?.let {
            Text(
                clock(it),
                style = hand(12f * scale),
                color = Brown,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (8 * scale).dp, y = (12 * scale).dp)
                    .graphicsLayer { rotationZ = 3f }
                    .background(Color(0xFFFBF6F1))
                    .border(1.dp, Color(0xFFDFCEBF))
                    .padding(horizontal = (8 * scale).dp, vertical = (2 * scale).dp),
            )
        }
    }
}

/** A short piece of washi tape in [ink] and [pattern], torn at the ends. */
@Composable
fun TapePiece(ink: Color, pattern: TapePattern, modifier: Modifier, tilt: Float = 0f) {
    Canvas(modifier.graphicsLayer { rotationZ = tilt }) {
        drawTape(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, Tape(ink, pattern), tooth = 2.dp.toPx())
    }
}

/** An attachment's frame by its kind. */
@Composable
fun AttachmentFrame(
    attachment: ContentPart.Attachment,
    ink: Color,
    behind: Color,
    scale: Float = 1f,
    punched: Boolean = false,
    modifier: Modifier = Modifier,
    run: Boolean = false,
    readBy: Color? = null,
) {
    when (attachment.kind) {
        AttachmentKind.FILE -> LuggageTag(attachment, ink, scale, modifier)
        AttachmentKind.PLAYLIST -> Cassette(attachment, ink, scale, modifier)
        AttachmentKind.SONG -> TicketStub(attachment, ink, behind, scale, punched, modifier)
        AttachmentKind.LINK -> Cutting(attachment, ink, scale, modifier, readBy)
        AttachmentKind.VIDEO -> FilmStrip(attachment, scale, modifier, run)
    }
}

/** Frames are drawn smaller on notes, which are narrower than letters. */
fun noteScale(kind: AttachmentKind?): Float = when (kind) {
    AttachmentKind.SONG -> 0.86f
    AttachmentKind.VIDEO -> 0.82f
    AttachmentKind.LINK -> 0.84f
    else -> 0.86f
}

internal fun hand(size: Float, semibold: Boolean = false) =
    TextStyle(fontFamily = HarnessFonts.Hand, fontWeight = if (semibold) FontWeight.SemiBold else FontWeight.Normal, fontSize = size.sp, lineHeight = (size * 1.25f).sp)

internal fun news(size: Float, bold: Boolean = false, spacing: Float = 0f, height: Float = 1.2f) =
    TextStyle(fontFamily = HarnessFonts.News, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp, lineHeight = (size * height).sp, letterSpacing = spacing.em())

internal fun ui(size: Float, bold: Boolean = false, spacing: Float = 0f) =
    TextStyle(fontFamily = HarnessFonts.Ui, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp, letterSpacing = spacing.em())

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
