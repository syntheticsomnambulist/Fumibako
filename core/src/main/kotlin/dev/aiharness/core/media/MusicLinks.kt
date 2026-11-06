package dev.aiharness.core.media

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
            host == "open.spotify.com" -> SongSource.SPOTIFY
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
            SongSource.SPOTIFY -> {
                val parts = description?.substringAfter("Spotify.", description)?.split(" · ")?.map { it.trim().trimEnd('.') }.orEmpty()
                val artist = parts.indexOf("Song").takeIf { it >= 0 }?.let { parts.getOrNull(it + 1) } ?: parts.getOrNull(1)?.takeIf { parts.size >= 3 }
                title to artist
            }
            SongSource.SOUNDCLOUD -> title?.substringBefore(" | ")?.removePrefix("Stream ")?.split(" by ", limit = 2)?.takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } ?: (title to null)
        }
    }
}
