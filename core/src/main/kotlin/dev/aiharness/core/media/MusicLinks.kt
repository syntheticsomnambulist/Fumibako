package dev.aiharness.core.media

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a song a persona shares can be heard (docs/design/media.md, "Songs from personas"). */
enum class SongSource(val label: String) {
    /** An audio file, played in the app's own player. */
    AUDIO("audio file"),

    /** Services with an official embeddable player, played in the player sheet. */
    YOUTUBE("YouTube"),
    SOUNDCLOUD("SoundCloud"),
    BANDCAMP("Bandcamp"),

    /** Others open in their own app. */
    SPOTIFY("Spotify"),
    APPLE_MUSIC("Apple Music"),
    ;

    val embeds: Boolean get() = this == YOUTUBE || this == SOUNDCLOUD || this == BANDCAMP
}

object MusicLinks {
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")

    fun sourceOf(url: String?): SongSource? {
        val u = url?.toHttpUrlOrNull() ?: return null
        val host = u.host.lowercase().removePrefix("www.").removePrefix("m.")
        val path = u.encodedPath.lowercase()
        return when {
            path.substringAfterLast('.', "") in AUDIO -> SongSource.AUDIO
            host == "youtu.be" || host == "youtube.com" || host == "music.youtube.com" -> SongSource.YOUTUBE.takeIf { youtubeId(url) != null }
            host == "soundcloud.com" && u.pathSegments.filter { it.isNotEmpty() }.size >= 2 -> SongSource.SOUNDCLOUD
            host.endsWith(".bandcamp.com") && (path.startsWith("/track/") || path.startsWith("/album/")) -> SongSource.BANDCAMP
            host == "open.spotify.com" || host == "spotify.link" -> SongSource.SPOTIFY
            host == "music.apple.com" -> SongSource.APPLE_MUSIC
            else -> null
        }
    }

    /** Whether a link is to a music service (or an audio file), so it goes as a ticket by default. */
    fun isMusicOnly(url: String?): Boolean {
        val host = url?.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.") ?: return false
        return sourceOf(url) != null && host != "youtube.com" && host != "m.youtube.com" && host != "youtu.be"
    }

    /**
     * The official embeddable player for a song: YouTube's (privacy-enhanced), SoundCloud's widget,
     * or Bandcamp's, from its page ([embed]). Null when it has none.
     */
    fun embedFor(url: String?, embed: String?): String? = when (sourceOf(url)) {
        SongSource.YOUTUBE -> youtubeId(url)?.let { "https://www.youtube-nocookie.com/embed/$it?playsinline=1&rel=0" }
        SongSource.SOUNDCLOUD -> "https://w.soundcloud.com/player/?url=" + java.net.URLEncoder.encode(url, "UTF-8") +
            "&auto_play=false&visual=false&show_comments=false&hide_related=true"
        SongSource.BANDCAMP -> embed?.takeIf { it.startsWith("https://bandcamp.com/EmbeddedPlayer/") }
        else -> null
    }

    /** A YouTube video's id, from any of its usual addresses. */
    fun youtubeId(url: String?): String? {
        val u = url?.toHttpUrlOrNull() ?: return null
        val host = u.host.lowercase().removePrefix("www.").removePrefix("m.")
        val id = when {
            host == "youtu.be" -> u.pathSegments.firstOrNull()
            host == "youtube.com" || host == "music.youtube.com" -> when (u.pathSegments.firstOrNull()) {
                "watch" -> u.queryParameter("v")
                "shorts", "embed", "live" -> u.pathSegments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { Regex("[A-Za-z0-9_-]{6,20}").matches(it) }
    }

    /** What a Spotify page's description says of a song. */
    private data class SpotifyFacts(val artist: String?, val album: String?, val year: Int?)

    /**
     * "Rick Astley · Whenever You Need Somebody · Song · 1987" (artist, album, kind, year), or the
     * older "Listen to X on Spotify. Song · Artist · 1987".
     */
    private fun spotifyFacts(description: String?): SpotifyFacts {
        val parts = description?.substringAfter("Spotify.", description)?.split(" · ")?.map { it.trim().trimEnd('.') }.orEmpty()
        val i = parts.indexOf("Song")
        return when {
            i == 0 -> SpotifyFacts(parts.getOrNull(1), null, FileFacts.year(parts.getOrNull(2)))
            i > 0 -> SpotifyFacts(parts[0], parts.getOrNull(1)?.takeIf { i >= 2 }, FileFacts.year(parts.getOrNull(i + 1)))
            else -> SpotifyFacts(parts.getOrNull(0)?.takeIf { parts.size >= 2 }, null, null)
        }
    }

    /** Words a video's title carries that a song's doesn't: "(Official Music Video)", "[Lyrics]", "(Visualizer)". */
    private val VIDEO_WORDS = Regex("""\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|visuali[sz]er|mv|hd|4k)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /**
     * A ticket made from the cutting of a music service's page (or its own title lookup): the
     * song's title and artist as the service writes them, and its album, year and length where
     * the page says. "Pavement - Harness Your Hopes (Official Video)" on YouTube is Harness Your
     * Hopes by Pavement.
     */
    fun ticket(cutting: ContentPart.Attachment, source: SongSource): ContentPart.Attachment {
        val url = cutting.url.orEmpty()
        var (title, artist) = titleAndArtist(source, cutting.title, cutting.snippet, url)
        val channel = cutting.artist?.removeSuffix(" - Topic")?.removeSuffix("VEVO")?.removeSuffix(" Official")?.trim()?.ifBlank { null }
        if (artist == null && channel != null) {
            artist = channel
            title = title?.let { t -> Regex("^" + Regex.escape(channel) + "\\s*[-\u2013\u2014:|]\\s*", RegexOption.IGNORE_CASE).replace(t, "") }
        }
        if (source == SongSource.YOUTUBE || source == SongSource.SOUNDCLOUD) title = title?.let { VIDEO_WORDS.replace(it, "") }
        title = title?.trim()?.trim('"', '\u201C', '\u201D', '\'')?.trim()?.ifBlank { null }
        val spotify = if (source == SongSource.SPOTIFY) spotifyFacts(cutting.snippet) else null
        // Apple Music's description: "Song · 1997 · Duration 3:26".
        val apple = if (source == SongSource.APPLE_MUSIC) cutting.snippet else null
        val year = spotify?.year ?: apple?.let { Regex("\\b(1[89]\\d\\d|2\\d\\d\\d)\\b").find(it)?.value?.toIntOrNull() }
        val length = cutting.lengthMs ?: apple?.let { Regex("Duration (\\d+):(\\d\\d)").find(it) }?.let { m -> (m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 }
        return cutting.copy(
            kind = AttachmentKind.SONG, title = title, artist = artist, album = spotify?.album, year = year, lengthMs = length,
            site = source.label, snippet = null,
        )
    }

    /**
     * A song's title and artist from its page's title, as each service writes it:
     * "Artist - Title" (YouTube), "Title, by Artist" (Bandcamp), "Title by Artist on Apple Music",
     * "Song · Artist" in a Spotify page's description. Either may be null.
     */
    fun titleAndArtist(source: SongSource, pageTitle: String?, description: String?, url: String): Pair<String?, String?> {
        val title = pageTitle?.trim()?.removeSuffix(" - YouTube")?.removeSuffix(" - YouTube Music")?.removeSuffix(" | Spotify")
            ?.removeSuffix(" on Apple Music")?.trim()
        return when (source) {
            SongSource.AUDIO -> (title ?: url.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace("%20", " ")) to null
            SongSource.YOUTUBE -> title?.split(" - ", limit = 2)?.takeIf { it.size == 2 }?.let { it[1].trim() to it[0].trim() } ?: (title to null)
            SongSource.BANDCAMP -> title?.split(", by ", limit = 2)?.takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: (title to null)
            SongSource.APPLE_MUSIC -> title?.split(" by ", limit = 2)?.takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: (title to null)
            SongSource.SPOTIFY -> title to spotifyFacts(description).artist
            SongSource.SOUNDCLOUD -> title?.substringBefore(" | ")?.removePrefix("Stream ")?.split(" by ", limit = 2)?.takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: (title to null)
        }
    }
}
