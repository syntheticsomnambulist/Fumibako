package dev.aiharness.app.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.data.BlobSweep
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.humanSize
import kotlinx.coroutines.launch
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.MediaSettings
import dev.aiharness.core.model.SongsOpenIn
import dev.aiharness.app.platform.deviceNoun
import dev.aiharness.app.platform.systemMotionSetting

/** 九 Media: links, pictures you send, playback, and the paper's motion (docs/design/media.md, "Settings: a Media page"). */
internal fun SettingsCards.mediaPage(s: AppSettings, update: Update) {
    fun media(transform: (MediaSettings) -> MediaSettings) = update { it.copy(media = transform(it.media)) }
    val m = s.media
    card {
        CardTitle("Links")
        Text("OPEN LINKS IN", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(listOf(true to "The app", false to "Your browser"), m.linksInApp, { v -> media { it.copy(linksInApp = v) } })
        SwitchRow(
            "Link previews", m.linkPreviews, { v -> media { it.copy(linkPreviews = v) } },
            subtitle = "A cutting shows the page's title and picture, which means contacting the site. Off, a cutting is just the address.",
        )
        SwitchRow(
            "Personas open links you share", m.personasOpenLinks, { v -> media { it.copy(personasOpenLinks = v) } },
            subtitle = "Only links already in your letters. Claude opens them on Anthropic's servers; other models through this $deviceNoun, so sites see your address.",
        )
    }
    card {
        CardTitle("Pictures and files")
        Text("PICTURES YOU SEND", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(listOf(false to "Smaller", true to "Full size"), m.fullSizePictures, { v -> media { it.copy(fullSizePictures = v) } })
        Hint("Smaller is about 1280 px on the long side, which is plenty for a letter and uses fewer tokens each time a model sees it.")
        SwitchRow(
            "Describe what arrives", m.describe, { v -> media { it.copy(describe = v) } },
            subtitle = "A few lines about each photo and file, written once, so later in the conversation the persona reads those instead of being sent the file again.",
        )
    }
    card {
        CardTitle("Playback")
        SwitchRow("Keep music playing after leaving the letters", m.keepPlaying, { v -> media { it.copy(keepPlaying = v) } })
        SwitchRow("Start videos muted", m.videosMuted, { v -> media { it.copy(videosMuted = v) } })
        Text("OPEN SONGS IN", style = PaperType.meta, color = paper.kasure2)
        PaperChoice(
            listOf(SongsOpenIn.AS_LINKED to "Its own app", SongsOpenIn.SPOTIFY to "Spotify", SongsOpenIn.APPLE_MUSIC to "Apple Music", SongsOpenIn.YOUTUBE_MUSIC to "YouTube Music"),
            m.songsOpenIn,
            { v -> media { it.copy(songsOpenIn = v) } },
            columns = 2,
        )
        Hint(
            "Where a song opens to be heard whole. \"Its own app\" opens a Spotify link in Spotify, and so on. Otherwise Apple Music finds the same song; " +
                "Spotify and YouTube Music open a search for it (a YouTube video opens as itself in YouTube Music).",
        )
    }
    card {
        CardTitle("Paper motion")
        SwitchRow(
            "Frames move", m.paperMotion, { v -> media { it.copy(paperMotion = v) } },
            subtitle = "Photos lifting, tags swinging, reels turning, letters' things dropping onto the page." + (systemMotionSetting?.let { " $it stills them too." } ?: ""),
        )
        SwitchRow("Haptic ticks", m.haptics, { v -> media { it.copy(haptics = v) } }, subtitle = "A light tick when a read stamp lands.")
    }
    card { Storage() }
}

/** What the letters' files take up, and clearing the ones nothing uses any more (see BlobSweep). */
@Composable
private fun Storage() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<BlobSweep.Report?>(null) }
    var failed by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { container.storage.look() }.onSuccess { report = it }.onFailure { failed = true }
    }
    val r = report
    CardTitle("Storage", note = r?.let { humanSize(it.bytes) })
    when {
        failed -> Hint("Couldn't look through the letters' files just now.")
        r == null -> Hint("Looking through the letters' files…")
        else -> {
            val kinds = listOf("videos" to r.videos, "songs" to r.songs, "pictures" to r.pictures, "other files" to r.other)
                .filter { it.second > 0 }
                .joinToString(", ") { (kind, bytes) -> "$kind ${humanSize(bytes)}" }
            Hint(if (kinds.isEmpty()) "No files in your letters yet." else "Photos, songs, videos and files in letters: $kinds.")
            if (r.unusedFiles == 0) {
                Hint("Nothing unused: every file belongs to a letter, a memory, a draft or a sticker.")
            } else {
                Hint(
                    "${humanSize(r.unusedBytes)} of that is ${if (r.unusedFiles == 1) "a file" else "${r.unusedFiles} files"} nothing uses any more: " +
                        "from letters you deleted, replies written again, or things taken out of a note before it went. " +
                        "Memories' keepsakes, drafts, portraits and stickers keep theirs.",
                )
                PaperButton(if (clearing) "Clearing…" else "Clear unused files", ink = stationery.you, enabled = !clearing) {
                    clearing = true
                    scope.launch {
                        runCatching { container.storage.clear() }.onSuccess { report = it }.onFailure { failed = true }
                        clearing = false
                    }
                }
            }
        }
    }
    if (container.media.keepsSmallerCopies) {
        Hint("Videos you add are kept at 720p, and lossless songs as AAC, which takes a fraction of the space. Ones already in letters stay as they are.")
    }
}
