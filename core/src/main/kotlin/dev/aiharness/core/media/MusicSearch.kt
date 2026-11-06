package dev.aiharness.core.media

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.SongsOpenIn
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A song found in the catalog. */
data class FoundSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val year: Int? = null,
    val lengthMs: Long? = null,
    /** The cover, 600 px square. */
    val artworkUrl: String? = null,
    /** The cover small, for a list. */
    val thumbUrl: String? = null,
    /** Its 30-second preview, to play here and send with the ticket. */
    val previewUrl: String? = null,
    /** The song in Apple Music. */
    val appleUrl: String? = null,
)

/**
 * Songs from Apple's catalog (the iTunes Search API, which needs no key or account), so a song
 * can go with a note without its file: picked, it becomes a ticket carrying the song's title,
 * artist, album, year and cover, its 30-second preview (which plays here, and which a model that
 * hears songs hears), and a link to the whole song.
 */
class MusicSearch(
    private val blobs: BlobStore,
    private val client: OkHttpClient = HarnessHttp.client,
    /** Where the catalog is asked (a screenshot tour points the app's at a local server). */
    @Volatile var base: String = "https://itunes.apple.com",
    private val log: (String) -> Unit = {},
    /** For tests against a local server only: fetch previews and covers over plain http. */
    private val allowHttp: Boolean = false,
) {
    /** Where each song was last found in Apple Music, so it's asked once. */
    private val found = ConcurrentHashMap<String, String>()

    /** Songs matching [term], in the phone's country's storefront; null when the search couldn't be made (offline). */
    suspend fun songs(term: String, limit: Int = 20, country: String = storefront()): List<FoundSong>? = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val url = "$base/search?media=music&entity=song&limit=$limit&country=$country&term=" + URLEncoder.encode(term.trim(), "UTF-8")
        runCatching {
            val body = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (!r.isSuccessful) error("the search answered ${r.code}")
                r.body?.string().orEmpty()
            }
            Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty()
                .mapNotNull { (it as? JsonObject)?.let(::songOf) }
        }.onFailure { log("Song search failed: ${it.message}") }.getOrNull()
    }

    /**
     * A ticket for [song]: its preview and cover saved here, the whole song linked. If the preview
     * can't be fetched the ticket still goes, as a link to the song.
     */
    suspend fun ticket(song: FoundSong): ContentPart.Attachment = withContext(Dispatchers.IO) {
        val preview = song.previewUrl?.let { download(it, MAX_PREVIEW) }
        val cover = song.artworkUrl?.let { download(it, MAX_COVER) }
        ContentPart.Attachment(
            id = newId(),
            kind = AttachmentKind.SONG,
            ref = preview?.let { blobs.put(it, "m4a") },
            mimeType = preview?.let { "audio/mp4" },
            sizeBytes = preview?.size?.toLong(),
            title = song.title,
            artist = song.artist,
            album = song.album,
            year = song.year,
            lengthMs = song.lengthMs,
            url = song.appleUrl,
            pictureRef = cover?.let { blobs.put(it, "jpg") },
            previewOnly = preview != null,
        )
    }

    /**
     * Where to hear the whole of [song] in [app]: its own link when it's from there (or [app] is
     * "wherever it's from"), the same song found in Apple Music, the same video in YouTube Music,
     * or else a search for it there. Null for a song with no link and nothing to search by.
     */
    suspend fun whereToHear(song: ContentPart.Attachment, app: SongsOpenIn): String? {
        val own = song.url
        val source = MusicLinks.sourceOf(own)
        if (app == SongsOpenIn.AS_LINKED || (own != null && SongLinks.isIn(own, app))) return own
        val query = listOfNotNull(song.title, song.artist).joinToString(" ").ifBlank { null } ?: return own
        return when (app) {
            SongsOpenIn.APPLE_MUSIC -> {
                val key = query.lowercase()
                found[key] ?: songs(query, limit = 5)?.firstOrNull { SongLinks.sameSong(it, song) }?.appleUrl?.also { found[key] = it }
                    ?: SongLinks.search(app, query)
            }
            SongsOpenIn.YOUTUBE_MUSIC -> if (source == SongSource.YOUTUBE) MusicLinks.youtubeId(own)?.let { "https://music.youtube.com/watch?v=$it" } else SongLinks.search(app, query)
            else -> SongLinks.search(app, query)
        }
    }

    private fun download(url: String, limit: Long): ByteArray? = runCatching {
        if (url.toHttpUrlOrNull()?.scheme != "https" && !allowHttp) return null
        client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
            val body = r.body ?: return null
            if (!r.isSuccessful || body.contentLength() > limit) return null
            body.bytes().takeIf { it.isNotEmpty() && it.size <= limit }
        }
    }.onFailure { log("Couldn't fetch ${url.substringBefore('?')}: ${it.message}") }.getOrNull()

    companion object {
        private const val USER_AGENT = "Fumibako (Android)"
        private const val MAX_PREVIEW = 4L * 1024 * 1024
        private const val MAX_COVER = 2L * 1024 * 1024

        private fun storefront(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

        /** A song from one of the search's results; null for anything that isn't a song with a title. */
        fun songOf(r: JsonObject): FoundSong? {
            fun text(key: String) = (r[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifBlank { null }
            if (text("kind") != "song") return null
            return FoundSong(
                title = text("trackName") ?: return null,
                artist = text("artistName") ?: return null,
                album = text("collectionName"),
                year = text("releaseDate")?.let(FileFacts::year),
                lengthMs = (r["trackTimeMillis"] as? JsonPrimitive)?.content?.toLongOrNull(),
                artworkUrl = text("artworkUrl100")?.replace("100x100bb", "600x600bb"),
                thumbUrl = text("artworkUrl100")?.replace("100x100bb", "120x120bb"),
                previewUrl = text("previewUrl"),
                appleUrl = text("trackViewUrl")?.let { u -> u.toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters("uo")?.build()?.toString() ?: u },
            )
        }
    }
}

/** Where a song can be heard, across the services. */
object SongLinks {
    /** A search for [query] in [app], which opens in its app when it's installed. */
    fun search(app: SongsOpenIn, query: String): String? {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (app) {
            SongsOpenIn.SPOTIFY -> "https://open.spotify.com/search/" + q.replace("+", "%20")
            SongsOpenIn.APPLE_MUSIC -> "https://music.apple.com/search?term=$q"
            SongsOpenIn.YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=$q"
            SongsOpenIn.AS_LINKED -> null
        }
    }

    /** Whether [url] is already in [app]. */
    fun isIn(url: String, app: SongsOpenIn): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.") ?: return false
        return when (app) {
            SongsOpenIn.SPOTIFY -> host == "open.spotify.com" || host == "spotify.link"
            SongsOpenIn.APPLE_MUSIC -> host == "music.apple.com"
            SongsOpenIn.YOUTUBE_MUSIC -> host == "music.youtube.com"
            SongsOpenIn.AS_LINKED -> true
        }
    }

    /** Whether a found song is [song] (the same title's start and the same artist, roughly). */
    fun sameSong(found: FoundSong, song: ContentPart.Attachment): Boolean {
        fun norm(s: String?) = s.orEmpty().lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val title = norm(song.title)
        val artist = norm(song.artist)
        val titleMatches = title.isNotEmpty() && (norm(found.title).startsWith(title) || title.startsWith(norm(found.title)))
        val artistMatches = artist.isEmpty() || norm(found.artist).contains(artist) || artist.contains(norm(found.artist))
        return titleMatches && artistMatches
    }
}
