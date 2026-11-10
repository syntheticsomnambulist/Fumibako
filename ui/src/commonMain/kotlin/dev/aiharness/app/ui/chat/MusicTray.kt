package dev.aiharness.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aiharness.app.platform.scrollbar
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.media.FoundSong
import dev.aiharness.core.model.clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import dev.aiharness.app.platform.LruCache
import dev.aiharness.app.platform.SongPreview
import dev.aiharness.app.platform.decodeImage
import dev.aiharness.app.platform.newSongPreview
import dev.aiharness.app.platform.deviceNoun

/** A search for songs in the tray: what was asked, and what came back. */
data class SongSearch(
    val query: String = "",
    val searching: Boolean = false,
    /** What the results are for (they stay while a new search runs). */
    val searched: String = "",
    val results: List<FoundSong> = emptyList(),
    /** The search couldn't be made (offline, or the catalog didn't answer). */
    val failed: Boolean = false,
)

/**
 * The tray's Music page: a search of Apple's catalog, each song with its 30-second preview to
 * hear before picking it (a pick goes as a ticket with that preview and a link to the whole song),
 * and songs from your phone.
 */
@Composable
fun MusicTray(
    search: SongSearch,
    onQuery: (String) -> Unit,
    onPick: (FoundSong) -> Unit,
    onFromPhone: () -> Unit,
    onBack: () -> Unit,
    youInk: Color,
    modifier: Modifier = Modifier,
) {
    val colors = paper
    val preview = rememberPreviewPlayer()
    // Leaving the page puts the keyboard away with it.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val leave = { preview.stop(); focus.clearFocus() }
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "‹ Add",
                style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = colors.usuzumi1,
                modifier = Modifier.clip(PaperCorner).clickable(onClickLabel = "Back to everything you can add") { leave(); onBack() }.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Box(
                Modifier
                    .clip(PaperCorner)
                    .background(colors.kami)
                    .drawBehind { drawLine(youInk, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Music", style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Bold), color = colors.sumi)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "From your $deviceNoun ›",
                style = PaperType.meta.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                color = colors.usuzumi1,
                modifier = Modifier.clip(PaperCorner).clickable(role = Role.Button, onClickLabel = "Pick songs from your $deviceNoun") { leave(); onFromPhone() }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        IndexCardField(
            "Find a song",
            search.query,
            onQuery,
            placeholder = "A title or an artist",
            singleLine = true,
            ink = youInk,
            style = PaperType.preview.copy(fontSize = 15.sp, color = colors.sumi),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(212.dp)) {
            val note = when {
                search.query.isBlank() -> "Find a song by its title or artist. It goes as a ticket with its 30-second preview, and a link to the whole song."
                search.failed -> "Couldn't search just now. Are you online?"
                search.searching && search.results.isEmpty() -> "Looking…"
                !search.searching && search.results.isEmpty() && search.searched.isNotBlank() -> "Nothing found for “${search.searched}”."
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = PaperType.quiet,
                    color = colors.usuzumi1,
                    modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                val found = rememberLazyListState()
                LazyColumn(Modifier.fillMaxSize().scrollbar(found), state = found, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    items(search.results, key = { (it.appleUrl ?: "") + it.title + it.artist }) { song ->
                        FoundRow(song, preview.playing == song.previewUrl, youInk, onPreview = { preview.toggle(song.previewUrl) }) { leave(); onPick(song) }
                    }
                }
            }
        }
        Text(
            "Songs and previews from Apple's catalog.",
            style = PaperType.meta.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
            color = colors.kasure2,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(colors.nijimi1, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun FoundRow(song: FoundSong, playing: Boolean, ink: Color, onPreview: () -> Unit, onPick: () -> Unit) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PaperCorner)
            .clickable(role = Role.Button, onClickLabel = "Send ${song.title} with your note") { onPick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WebPicture(song.thumbUrl, Modifier.size(44.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = PaperType.preview.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.sumi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(song.artist, song.album, song.year?.toString()).joinToString(" · "),
                style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                color = colors.usuzumi1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        song.lengthMs?.let { Text(clock(it), style = PaperType.meta.copy(fontSize = 11.sp, letterSpacing = 0.sp), color = colors.kasure2) }
        if (song.previewUrl != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (playing) ink else colors.nijimi2, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (playing) "Stop the preview" else "Hear a preview of ${song.title}") { onPreview() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    if (playing) "Stop the preview" else "Hear a preview",
                    tint = if (playing) ink else colors.sumi,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberPreviewPlayer(): SongPreview {
    val player = remember { newSongPreview() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

/** Small pictures from the web, kept while the app runs. */
private val webPictures = LruCache<String, ImageBitmap>(80)

/** A small picture from the web (a cover in the search's results); a blank square until it's in. */
@Composable
private fun WebPicture(url: String?, modifier: Modifier) {
    val colors = paper
    val bitmap by produceState(url?.let { webPictures.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                HarnessHttp.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body?.takeIf { r.isSuccessful }?.bytes() ?: return@use null
                    decodeImage(bytes)
                }
            }.getOrNull()?.also { webPictures.put(url, it) }
        }
    }
    val image = bitmap
    if (image != null) Image(image, null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(colors.chitsu))
}
