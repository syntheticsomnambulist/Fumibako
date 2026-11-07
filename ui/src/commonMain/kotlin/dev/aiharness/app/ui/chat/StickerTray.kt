package dev.aiharness.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.stickers.StickerBaker
import dev.aiharness.app.ui.stickers.StickerPicture
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.stickers.Emoji

private enum class TrayPage { RECENT, PERSONA, SHARED, EMOJI }

/**
 * The tray under the composer, on its sticker page: Recent, the persona's own sheet, the shared
 * library, and emoji. Tapping a sticker puts it on your note; holding one lets you drag it onto a
 * letter ([onDragStart] gets where it was picked up, in root coordinates).
 */
@Composable
fun StickerTray(
    personaName: String,
    personaId: String?,
    recent: List<StickerRef>,
    art: Map<String, StickerArt>,
    youInk: Color,
    onPick: (StickerRef) -> Unit,
    onDragStart: (StickerRef, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** What the back button says, and what it does for TalkBack. */
    backLabel: String = "‹ Add",
    backHint: String = "Back to everything you can add",
    /** The line at the foot of the tray, on where a tap and a drag put a sticker. */
    hint: String = "Tap: onto your note. Hold and drag: onto any letter.",
) {
    val colors = paper
    var page by rememberSaveable { mutableStateOf(if (recent.isEmpty()) TrayPage.EMOJI else TrayPage.RECENT) }
    val own = art.values.filter { it.ownerPersonaId != null && it.ownerPersonaId == personaId }.sortedBy { it.createdAt }
    val shared = art.values.filter { it.ownerPersonaId == null }.sortedBy { it.createdAt }
    val named: (StickerRef) -> String = { ref ->
        when (ref) {
            is StickerRef.Emoji -> if (ref.name == "emoji") ref.emoji else ref.name
            is StickerRef.Art -> art[ref.artId]?.name ?: "sticker"
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi1, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                .drawBehind { drawLine(colors.nijimi1, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            onBack?.let { back ->
                Text(
                    backLabel,
                    style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    color = colors.usuzumi1,
                    modifier = Modifier.clip(PaperCorner).clickable(onClickLabel = backHint) { back() }.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            // Without a persona (the planner) there's no persona's sheet to show.
            listOfNotNull(TrayPage.RECENT to "Recent", (TrayPage.PERSONA to "$personaName's").takeIf { personaId != null }, TrayPage.SHARED to "Shared", TrayPage.EMOJI to "Emoji").forEach { (p, label) ->
                val on = page == p
                Box(
                    Modifier
                        .clip(PaperCorner)
                        .background(if (on) colors.kami else colors.chitsu)
                        .drawBehind { if (on) drawLine(youInk, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) }
                        .selectable(on, role = Role.Tab) { page = p }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(label, style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) colors.sumi else colors.usuzumi1, maxLines = 1)
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().height(260.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            val cell: LazyGridScope.(List<StickerRef>) -> Unit = { refs ->
                items(refs, key = { it.toString() }) { ref ->
                    TrayCell(ref, named(ref), youInk, onPick, onDragStart, onDrag, onDragEnd)
                }
            }
            when (page) {
                TrayPage.RECENT -> if (recent.isEmpty()) note("Stickers you use show up here.") else cell(recent)
                TrayPage.PERSONA -> if (own.isEmpty()) {
                    note("No stickers of $personaName's yet. They can be drawn on $personaName's sticker page.")
                } else {
                    cell(own.map { StickerRef.Art(it.id) })
                }
                TrayPage.SHARED -> if (shared.isEmpty()) {
                    note("Nothing in the shared library yet. Add some in Settings, Stickers.")
                } else {
                    listOf(StickerArtKind.STICKER to "Stickers", StickerArtKind.STAMP to "Stamps", StickerArtKind.DECOR to "Decor").forEach { (kind, label) ->
                        val these = shared.filter { it.kind == kind }
                        if (these.isNotEmpty()) {
                            heading(label)
                            cell(these.map { StickerRef.Art(it.id) })
                        }
                    }
                }
                TrayPage.EMOJI -> Emoji.groups.forEach { group ->
                    heading(group.label)
                    cell(group.entries.map { StickerRef.Emoji(it.emoji, it.name) })
                }
            }
        }
        Text(
            hint,
            style = PaperType.meta.copy(fontSize = 11.5.sp, letterSpacing = 0.sp),
            color = colors.kasure2,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        )
    }
}

private fun LazyGridScope.heading(label: String) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$label") {
        Text(label.uppercase(), style = PaperType.meta, color = paper.kasure2, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
    }
}

private fun LazyGridScope.note(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "note") {
        Text(text, style = PaperType.postscript, color = paper.kasure2, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun TrayCell(
    ref: StickerRef,
    name: String,
    ink: Color,
    onPick: (StickerRef) -> Unit,
    onDragStart: (StickerRef, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .clip(PaperCorner)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(ref) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at -> onDragStart(ref, origin + at) },
                    onDrag = { change, amount -> change.consume(); onDrag(amount) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(onClickLabel = "Stick on your note") { onPick(ref) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StickerPicture(ref, ink, Modifier.size((40 * StickerBaker.BOX).dp), name = ":$name:")
        Text(":$name:", style = PaperType.meta.copy(fontSize = 9.5.sp, letterSpacing = 0.sp), color = paper.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What you've typed after a colon, when it could be a sticker's name: `ari` in "Night :ari". */
fun partialStickerName(text: String): String? = Regex(":([a-z0-9_-]{2,31})$").find(text)?.groupValues?.get(1)

/**
 * Stickers whose names fit [partial]: the persona's own and the shared ones first, then emoji;
 * names starting with it before names containing it.
 */
fun stickerSuggestions(partial: String, art: Collection<StickerArt>, limit: Int = 8): List<Pair<String, StickerRef>> {
    val p = partial.lowercase()
    val pictures = art.map { it.name to (StickerRef.Art(it.id) as StickerRef) }
    val emoji = Emoji.all.map { it.name to (StickerRef.Emoji(it.emoji, it.name) as StickerRef) }
    fun pick(from: List<Pair<String, StickerRef>>) = from.filter { it.first.startsWith(p) } + from.filter { !it.first.startsWith(p) && p in it.first }
    return (pick(pictures) + pick(emoji)).distinctBy { it.first }.take(limit)
}

/** Chips above the composer for the stickers a half-typed `:name` could be; tapping one finishes it. */
@Composable
fun StickerSuggestions(partial: String, matches: List<Pair<String, StickerRef>>, ink: Color, onPick: (String) -> Unit) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi0, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matches.forEach { (name, ref) ->
            Row(
                Modifier
                    .clip(PaperCorner)
                    .background(colors.hyoshi)
                    .border(1.dp, colors.nijimi1, PaperCorner)
                    .clickable(onClickLabel = "Write :$name:") { onPick(name) }
                    .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                StickerPicture(ref, ink, Modifier.size((24 * StickerBaker.BOX).dp))
                val at = name.indexOf(partial)
                Text(
                    buildAnnotatedString {
                        append(":")
                        if (at >= 0) {
                            append(name.substring(0, at))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink)) { append(partial) }
                            append(name.substring(at + partial.length))
                        } else {
                            append(name)
                        }
                        append(":")
                    },
                    style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    color = colors.usuzumi1,
                    maxLines = 1,
                )
            }
        }
    }
}

