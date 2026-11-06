package dev.aiharness.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.BlobImage
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Track
import dev.aiharness.core.model.clock

/** What the + tray offers. */
enum class TrayItem(val label: String) { PHOTOS("Photos"), CAMERA("Camera"), FILE("File"), MUSIC("Music"), VIDEO("Video"), STICKERS("Stickers") }

/** Something waiting to go with your note: a picture, an attachment, or one still being prepared. */
sealed interface Going {
    val key: String

    data class Picture(val image: ContentPart.Image) : Going {
        override val key get() = image.ref
    }

    data class Item(val attachment: ContentPart.Attachment) : Going {
        override val key get() = attachment.id
    }

    /** Still being made ready: [kind] for its drawing, and [progress] (0 to 1) once the work says how far along it is. */
    data class Preparing(override val key: String, val label: String, val kind: TrayItem? = null, val progress: Float? = null) : Going
}

/** The tray's first page: a paper button for each kind of thing, each with a small drawing of it. */
@Composable
fun TrayButtons(onPick: (TrayItem) -> Unit, modifier: Modifier = Modifier) {
    val colors = paper
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi1, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrayItem.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(PaperCorner)
                            .background(colors.hyoshi)
                            .border(1.dp, colors.nijimi1, PaperCorner)
                            .clickable(role = Role.Button, onClickLabel = item.label) { onPick(item) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TrayDrawing(item, Modifier.size(64.dp, 48.dp))
                        Text(item.label, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp), color = colors.usuzumi1)
                    }
                }
            }
        }
    }
}

/** The small drawings on the tray's buttons (from the design canvas's tray), on a 64 by 48 grid. */
@Composable
fun TrayDrawing(item: TrayItem, modifier: Modifier = Modifier) {
    val ink = paper.sumi
    Canvas(modifier) {
        scale(size.width / 64f, size.height / 48f, pivot = Offset.Zero) {
            when (item) {
                TrayItem.PHOTOS -> {
                    rotate(-4f, Offset(32f, 24f)) {
                        drawRect(Color.White, Offset(18f, 6f), Size(28f, 34f))
                        drawRect(Color(0xFFCEBEAF), Offset(18f, 6f), Size(28f, 34f), style = Stroke(1f))
                        drawRect(Color(0xFF9DBFD6), Offset(21f, 9f), Size(22f, 22f))
                        drawRect(Color(0xFFB6D3A2), Offset(21f, 21f), Size(22f, 10f))
                        drawCircle(Color(0xFFF2D27A), 3f, Offset(37f, 14f))
                        drawRect(Color(0xFFA26480).copy(alpha = 0.85f), Offset(26f, 3f), Size(12f, 5f))
                    }
                }
                TrayItem.CAMERA -> {
                    val body = Path().apply {
                        moveTo(18f, 16f); lineTo(24f, 16f); lineTo(27f, 12f); lineTo(37f, 12f); lineTo(40f, 16f); lineTo(46f, 16f)
                        quadraticTo(48f, 16f, 48f, 18f); lineTo(48f, 36f); quadraticTo(48f, 38f, 46f, 38f); lineTo(18f, 38f)
                        quadraticTo(16f, 38f, 16f, 36f); lineTo(16f, 18f); quadraticTo(16f, 16f, 18f, 16f); close()
                    }
                    drawPath(body, ink, style = Stroke(1.6f))
                    drawCircle(ink, 7f, Offset(32f, 26f), style = Stroke(1.6f))
                }
                TrayItem.FILE -> {
                    val tag = Path().apply { moveTo(20f, 12f); lineTo(50f, 12f); lineTo(50f, 36f); lineTo(20f, 36f); lineTo(12f, 28f); lineTo(12f, 20f); close() }
                    drawPath(tag, Color(0xFFC5D2C5))
                    drawCircle(Color(0xFFFBF3E8), 2.5f, Offset(19f, 24f))
                    drawCircle(Color(0xFF66B0B8), 2.5f, Offset(19f, 24f), style = Stroke(1f))
                    drawPath(Path().apply { moveTo(19f, 24f); cubicTo(12f, 26f, 8f, 34f, 4f, 30f) }, Color(0xFF5F7F7C), style = Stroke(1.2f))
                    drawRect(Color(0xFF4A5A55), Offset(26f, 19f), Size(18f, 2f))
                    drawRect(Color(0xFF6F7F7A), Offset(26f, 25f), Size(12f, 2f))
                }
                TrayItem.MUSIC -> cassette(Offset(12f, 10f), 1f)
                TrayItem.VIDEO -> {
                    drawRect(Color(0xFF2A2420), Offset(8f, 12f), Size(48f, 24f))
                    for (i in 0 until 8) {
                        drawRect(Color(0xFFF8F0E9), Offset(10f + i * 6f, 13.5f), Size(3f, 2f))
                        drawRect(Color(0xFFF8F0E9), Offset(10f + i * 6f, 32.5f), Size(3f, 2f))
                    }
                    drawRect(Color(0xFF3F84AD), Offset(11f, 17f), Size(13f, 14f))
                    drawRect(Color(0xFFCFE3E2), Offset(26f, 17f), Size(13f, 14f))
                    drawRect(Color(0xFFE2B66B), Offset(41f, 17f), Size(12f, 14f))
                }
                TrayItem.STICKERS -> {
                    drawCircle(Color.White, 13f, Offset(26f, 25f))
                    drawCircle(Color(0xFFCEBEAF), 13f, Offset(26f, 25f), style = Stroke(1f))
                    drawCircle(Color(0xFFE3940B), 10f, Offset(26f, 25f))
                    drawCircle(Color.White, 9f, Offset(42f, 22f))
                    drawCircle(Color(0xFFCEBEAF), 9f, Offset(42f, 22f), style = Stroke(1f))
                    drawCircle(Color(0xFFD80638), 6.5f, Offset(42f, 22f))
                }
            }
        }
    }
}

/** A small cassette, as on the Music button; [at] is its top-left on the 64 by 48 grid. */
private fun DrawScope.cassette(at: Offset, s: Float, label: Color = Color(0xFF116671)) {
    fun o(x: Float, y: Float) = Offset(at.x + x * s, at.y + y * s)
    drawRoundRect(Color(0xFF3B3530), o(0f, 0f), Size(40f * s, 28f * s), CornerRadius(3f * s))
    drawRoundRect(Color(0xFFF6ECD9), o(3f, 3f), Size(34f * s, 16f * s), CornerRadius(1.5f * s))
    drawRect(label, o(6f, 5.5f), Size(14f * s, 1.6f * s))
    drawRoundRect(Color(0xFF2B2622), o(10f, 11f), Size(20f * s, 6f * s), CornerRadius(3f * s))
    drawCircle(Color(0xFFF3E4CC), 2f * s, o(13f, 14f))
    drawCircle(Color(0xFFF3E4CC), 2f * s, o(27f, 14f))
    drawPath(Path().apply { moveTo(o(8f, 23f).x, o(8f, 23f).y); lineTo(o(32f, 23f).x, o(32f, 23f).y); lineTo(o(34f, 28f).x, o(34f, 28f).y); lineTo(o(6f, 28f).x, o(6f, 28f).y); close() }, Color(0xFF4A433D))
}

/** What's waiting to go with your note, as small versions of their frames, each with a remove button. */
@Composable
fun GoingWithYourNote(items: List<Going>, onRemove: (Going) -> Unit, onOpen: (Going) -> Unit, switchesTo: (Going) -> String? = { null }) {
    if (items.isEmpty()) return
    val colors = paper
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.hyoshi)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("GOING WITH YOUR NOTE", style = PaperType.meta, color = colors.kasure2)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                // Room for the × that sits on the corner: the strip clips whatever sticks out.
                Box(Modifier.padding(top = 10.dp, end = 10.dp)) {
                    val switch = switchesTo(item)
                    // Clipped for its ripple: the thumbs keep a little room at their foot, so a "g" keeps its tail.
                    Box(Modifier.clip(PaperCorner).clickable(role = Role.Button, onClickLabel = switch?.let { "Send it as $it" }) { onOpen(item) }) { GoingThumb(item, switch) }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.kami)
                            .border(1.dp, colors.nijimi2, CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Take it out") { onRemove(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = PaperType.meta.copy(fontSize = 13.sp, letterSpacing = 0.sp), color = colors.sumi)
                    }
                }
            }
        }
    }
}

@Composable
private fun GoingThumb(item: Going, switchesTo: String? = null) {
    val colors = paper
    val label: String
    when (item) {
        is Going.Picture -> {
            Box(Modifier.background(Color.White).padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 9.dp)) {
                BlobImage(item.image.ref, Modifier.size(44.dp), contentScale = ContentScale.Crop, maxSize = 160)
            }
            return
        }
        is Going.Preparing -> {
            Preparing(item)
            return
        }
        is Going.Item -> label = when (item.attachment.kind) {
            AttachmentKind.FILE -> item.attachment.name ?: "a file"
            AttachmentKind.SONG -> item.attachment.title ?: "a song"
            AttachmentKind.PLAYLIST -> item.attachment.title ?: "a mix"
            AttachmentKind.VIDEO -> item.attachment.title ?: "a video"
            AttachmentKind.LINK -> item.attachment.title ?: item.attachment.site ?: "a link"
        }
    }
    val drawing = when ((item as? Going.Item)?.attachment?.kind) {
        AttachmentKind.FILE -> TrayItem.FILE
        AttachmentKind.SONG, AttachmentKind.PLAYLIST -> TrayItem.MUSIC
        AttachmentKind.VIDEO -> TrayItem.VIDEO
        AttachmentKind.LINK, null -> null
    }
    Column(Modifier.width(78.dp).padding(bottom = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (drawing != null) TrayDrawing(drawing, Modifier.size(56.dp, 42.dp)) else Box(Modifier.size(56.dp, 42.dp).background(colors.kami).border(1.dp, colors.nijimi1, PaperCorner))
        Text(label, style = PaperType.meta.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = colors.usuzumi1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // A music link can go as a ticket or a cutting; a tap switches.
        switchesTo?.let { Text("tap: $it", style = PaperType.meta.copy(fontSize = 9.5.sp, letterSpacing = 0.sp), color = colors.kasure2, maxLines = 1) }
    }
}

/**
 * Something still being made ready, under "Going with your note": its kind's drawing, at work
 * (a video's frames run through as if in a projector), and a thin line beneath that fills as it
 * goes ("shrinking 42%" for a video getting its smaller copy), or sweeps while it can't say.
 * Still with "Remove animations" on.
 */
@Composable
private fun Preparing(item: Going.Preparing) {
    val colors = paper
    val still = dev.aiharness.app.ui.media.reducedMotion()
    val time = androidx.compose.animation.core.rememberInfiniteTransition(label = "preparing")
    val run by if (still) remember { mutableStateOf(0f) } else time.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2_200, easing = androidx.compose.animation.core.LinearEasing)), label = "run",
    )
    val progress = item.progress
    val label = when {
        progress != null && item.kind == TrayItem.VIDEO -> "shrinking ${(progress * 100).toInt()}%"
        progress != null -> "${item.label} · ${(progress * 100).toInt()}%"
        else -> item.label
    }
    Column(
        Modifier.width(78.dp).padding(bottom = 3.dp).semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull("Getting ${item.label} ready", progress?.let { "${(it * 100).toInt()}% done" }).joinToString(", ")
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp, 42.dp)) {
            when (item.kind) {
                TrayItem.VIDEO -> RunningFilm(run, Modifier.matchParentSize())
                null -> Box(Modifier.matchParentSize().background(colors.kami).border(1.dp, colors.nijimi1, PaperCorner))
                else -> TrayDrawing(item.kind, Modifier.matchParentSize().graphicsLayer { alpha = 0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(run * 2 * Math.PI.toFloat())) })
            }
        }
        // How far along: filled when it says, a short sweep while it can't.
        Canvas(Modifier.padding(top = 3.dp).size(52.dp, 2.dp)) {
            drawRect(colors.nijimi1)
            when {
                progress != null -> drawRect(colors.usuzumi1, size = Size(size.width * progress, size.height))
                !still -> {
                    val w = size.width * 0.3f
                    val x = (run * 2 % 1f) * (size.width + w) - w
                    drawRect(colors.usuzumi1, Offset(x.coerceAtLeast(0f), 0f), Size((w + x.coerceAtMost(0f)).coerceAtMost(size.width - x.coerceAtLeast(0f)), size.height))
                }
            }
        }
        // Two lines if it must (large text), so the percentage isn't cut off. Centered across the
        // whole width: centered in less, Compose counts it as overflowing and clips the "g" of "shrinking".
        Text(
            label,
            style = PaperType.meta.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = colors.usuzumi1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The tray's little film strip with its frames running through, [run] (0 to 1) a full turn of six frames. */
@Composable
private fun RunningFilm(run: Float, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 64f, size.height / 48f, pivot = Offset.Zero) {
            drawRect(Color(0xFF2A2420), Offset(8f, 12f), Size(48f, 24f))
            clipRect(9f, 12f, 55f, 36f) {
                // Holes every 6 and frames every 15 (three colors, so the pattern repeats every 45):
                // 90 of travel is a whole number of both, so the loop has no seam.
                val shift = run * 90f
                for (i in -1 until 10) {
                    val x = 10f + i * 6f - shift % 6f
                    drawRect(Color(0xFFF8F0E9), Offset(x, 13.5f), Size(3f, 2f))
                    drawRect(Color(0xFFF8F0E9), Offset(x, 32.5f), Size(3f, 2f))
                }
                // Enough frames to cover the strip at every point of the loop (it shifts up to 45 before
                // starting over): with fewer, its right end showed bare until the loop came round.
                val frames = listOf(Color(0xFF3F84AD), Color(0xFFCFE3E2), Color(0xFFE2B66B))
                for (k in -1 until 7) {
                    drawRect(frames[(k + 3) % 3], Offset(11f + k * 15f - shift % 45f, 17f), Size(13f, 14f))
                }
            }
        }
    }
}

/**
 * The playlist editor ("Your mix"): the label and the line beneath it, the songs in order (move up
 * or down, or take one out), and adding more. Down to one song, the mix goes as a ticket.
 */
@Composable
fun PlaylistEditor(
    mix: ContentPart.Attachment,
    ink: Color,
    onChange: (ContentPart.Attachment) -> Unit,
    onAddSongs: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = paper
    val tracks = mix.tracks
    fun withTracks(list: List<Track>) = mix.copy(tracks = list, lengthMs = list.sumOf { it.lengthMs ?: 0L })
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Your mix", style = PaperType.entryTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IndexCardField("On the label", mix.title.orEmpty(), { onChange(mix.copy(title = it)) }, singleLine = true, ink = ink)
                IndexCardField("Beneath it", mix.subtitle.orEmpty(), { onChange(mix.copy(subtitle = it)) }, singleLine = true, ink = ink)
                Text(
                    "SIDE A · ${tracks.size} ${if (tracks.size == 1) "SONG" else "SONGS"}" + (mix.lengthMs?.takeIf { it > 0 }?.let { " · ${clock(it)}" } ?: ""),
                    style = PaperType.meta,
                    color = colors.kasure2,
                )
                tracks.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${i + 1}", style = PaperType.meta, color = colors.kasure2, modifier = Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title ?: t.name ?: "a song", style = PaperType.noteBody.copy(fontSize = 14.sp), color = colors.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            listOfNotNull(t.artist, t.lengthMs?.let(::clock)).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                                Text(it, style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp), color = colors.kasure2, maxLines = 1)
                            }
                        }
                        SmallButton("↑", "Move up", enabled = i > 0) { onChange(withTracks(tracks.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) })) }
                        SmallButton("↓", "Move down", enabled = i < tracks.lastIndex) { onChange(withTracks(tracks.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) })) }
                        SmallButton("×", "Take it off the mix") { onChange(withTracks(tracks.filterIndexed { j, _ -> j != i })) }
                    }
                }
                if (tracks.size == 1) Text("One song goes as a ticket.", style = PaperType.postscript, color = colors.kasure2)
                TextButton(onClick = onAddSongs) { Text("Add songs") }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun SmallButton(symbol: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = paper
    Box(
        Modifier
            .size(28.dp)
            .clip(PaperCorner)
            .border(1.dp, colors.nijimi1, PaperCorner)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = PaperType.meta.copy(fontSize = 13.sp), color = if (enabled) colors.sumi else colors.nijimi2)
    }
}
