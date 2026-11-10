package dev.aiharness.core.media

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Newspaper cuttings for links, made once when the link is attached: the page's headline, site,
 * picture and description, from the service's own title lookup (oEmbed: YouTube, Spotify,
 * SoundCloud, Vimeo) or else the top of the page (its Open Graph tags and title). Only public web
 * addresses are fetched; private and local ones (your router, your computer) are refused, and get
 * a plain cutting with just the address, as does any page that can't be read.
 *
 * All of it runs off the caller's thread: Android refuses network work (even looking up an
 * address) on the main thread, which once left every cutting plain.
 */
class LinkPreviews(
    private val blobs: BlobStore,
    private val client: OkHttpClient = HarnessHttp.client,
    /** For tests against a local server only. */
    private val allowPrivate: Boolean = false,
    private val resolve: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() },
    private val log: (String) -> Unit = {},
    /** Where the services' title lookups are asked (tests point it at a local server). */
    private val oEmbedFor: (HttpUrl) -> String? = ::oEmbedAddress,
) {
    /** A cutting for [url]; with [fetch] off (the setting), or when the page doesn't open, just the address. */
    suspend fun cutting(url: String, fetch: Boolean = true): ContentPart.Attachment =
        (if (fetch) fetched(url) else null) ?: plainCutting(url)

    /** A cutting from the page at [url], or null when it doesn't open (private, gone, not a page). */
    suspend fun fetched(url: String): ContentPart.Attachment? = withContext(Dispatchers.IO) {
        try {
            fetchedNow(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Couldn't make a cutting for $url: ${e.javaClass.simpleName} ${e.message.orEmpty()}")
            null
        }
    }

    private suspend fun fetchedNow(url: String): ContentPart.Attachment? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!isPublic(parsed.host)) {
            log("Not making a cutting for ${parsed.host}: not a public address")
            return null
        }
        val plain = plainCutting(url)
        val embedded = oEmbedFor(parsed)?.let { lookup -> runCatching { oEmbed(lookup) }.getOrNull() }
        // The page too, unless the service's lookup said all a cutting needs (YouTube's page is a megabyte).
        val page = if (embedded?.title != null && embedded.author != null) null else fetch(url, MAX_PAGE, untilHeadEnds = true)
        val html = page?.bytes?.toString(Charsets.UTF_8)
        if (embedded == null && html == null) return null
        // A bot check ("Just a moment...") says nothing about the page.
        val readable = html?.takeUnless { h -> titleOf(h)?.let(::clean)?.let { t -> CHALLENGES.any { t.startsWith(it, ignoreCase = true) } } == true }
        if (embedded == null && readable == null) {
            log("${parsed.host} sent a bot check instead of its page")
            return null
        }
        val tags = readable?.let(::metaTags).orEmpty()
        val site = tags["og:site_name"]?.let(::clean)?.take(60) ?: embedded?.provider ?: plain.site
        val title = embedded?.title ?: (tags["og:title"] ?: tags["twitter:title"] ?: readable?.let(::titleOf))?.let(::clean)
        val picture = embedded?.thumbnail ?: (tags["og:image"] ?: tags["twitter:image"])?.let { parsed.resolve(it)?.toString() }
        return plain.copy(
            title = title?.let { withoutSiteName(it, site, parsed.host) }?.take(200),
            artist = embedded?.author?.take(80),
            lengthMs = embedded?.seconds?.let { it * 1000 },
            site = site,
            snippet = ((tags["og:description"] ?: tags["description"])?.let(::clean) ?: embedded?.description)?.take(300)?.ifBlank { null },
            pictureRef = picture?.let { savedPicture(it) },
            embed = (tags["og:video:secure_url"] ?: tags["og:video:url"] ?: tags["og:video"])?.takeIf { it.startsWith("https://") },
        )
    }

    /** The picture at [url], saved; null when it can't be (a missing picture doesn't spoil the cutting). */
    private suspend fun savedPicture(url: String): String? = runCatching {
        val p = url.toHttpUrlOrNull()?.takeIf { isPublic(it.host) } ?: return null
        fetch(p.toString(), MAX_PICTURE, cutOff = false)?.bytes?.let { blobs.put(it, extensionOf(p.encodedPath)) }
    }.getOrNull()

    private class Embedded(val title: String?, val author: String?, val provider: String?, val thumbnail: String?, val description: String?, val seconds: Long?)

    private fun oEmbed(lookup: String): Embedded? {
        val body = fetch(lookup, MAX_OEMBED, cutOff = false)?.bytes?.toString(Charsets.UTF_8) ?: return null
        val json = Json.parseToJsonElement(body).jsonObject
        fun text(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(::clean)?.ifBlank { null }
        return Embedded(
            title = text("title"),
            author = text("author_name"),
            provider = text("provider_name"),
            thumbnail = text("thumbnail_url")?.takeIf { it.startsWith("https://") },
            description = text("description")?.let { Regex("<[^>]+>").replace(it, " ") }?.let(::clean),
            seconds = (json["duration"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 },
        ).takeIf { it.title != null }
    }

    /** The type of what's at [url] when it opens (asked with HEAD, redirects checked), or null. */
    suspend fun probe(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = url.toHttpUrlOrNull() ?: return@runCatching null
            val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            var next = parsed
            repeat(6) {
                if (!isPublic(next.host)) return@runCatching null
                val request = Request.Builder().url(next).head().header("User-Agent", USER_AGENT).build()
                noRedirects.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        next = response.header("Location")?.let { next.resolve(it) } ?: return@runCatching null
                        return@use
                    }
                    return@runCatching if (response.isSuccessful) response.header("Content-Type") ?: "" else null
                }
            }
            null
        }.getOrNull()
    }

    /**
     * The words of the page at [url] for a persona to read, at most [maxChars] of them: an HTML
     * page's readable text, or a text file as it is. Null when it can't be read (a private
     * address, an error, or not text).
     */
    suspend fun read(url: String, maxChars: Int = 12_000): PageText? = withContext(Dispatchers.IO) {
        runCatching {
            val page = fetch(url, MAX_READ) ?: return@runCatching null
            val type = page.type?.lowercase().orEmpty()
            val raw = page.bytes.toString(Charsets.UTF_8)
            when {
                type.startsWith("text/html") || type.contains("xhtml") || (type.isEmpty() && raw.contains("<html", ignoreCase = true)) -> {
                    val (text, cut) = Readable.of(raw, maxChars)
                    PageText(page.url, metaTags(raw)["og:title"]?.let(::clean) ?: titleOf(raw)?.let(::clean), text, cut || page.cut)
                }
                type.startsWith("text/") || type.contains("json") -> PageText(page.url, null, raw.take(maxChars), raw.length > maxChars || page.cut)
                else -> null
            }
        }.getOrNull()
    }

    /** Whether [host] is out on the public internet (every address it resolves to). Looks the address up: call it off the main thread. */
    fun isPublic(host: String): Boolean {
        if (allowPrivate) return true
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local") || host.endsWith(".internal")) return false
        val addresses = runCatching { resolve(host) }.onFailure { log("Couldn't look up $host: ${it.javaClass.simpleName}") }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.none(::isPrivate)
    }

    private class Fetched(val bytes: ByteArray, val type: String?, val url: String, val cut: Boolean)

    /**
     * The body of [url], up to [limit] bytes: past it, the first [limit] bytes ([cutOff], for a
     * page, whose head is what a cutting needs) or nothing (a picture). With [untilHeadEnds] it
     * stops once the page's head is in. Redirects are followed by hand, at most five, each
     * checked, so a public page can't send the fetch somewhere private.
     */
    private fun fetch(url: String, limit: Int, cutOff: Boolean = true, untilHeadEnds: Boolean = false): Fetched? {
        val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var next = url.toHttpUrlOrNull() ?: return null
        repeat(6) {
            if (!isPublic(next.host)) return null
            val request = Request.Builder().url(next)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/json;q=0.9,image/*;q=0.9,*/*;q=0.5")
                .header("Accept-Language", acceptLanguage())
                .build()
            noRedirects.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    next = response.header("Location")?.let { next.resolve(it) } ?: return null
                    return@use
                }
                if (!response.isSuccessful) {
                    log("${next.host} answered ${response.code}")
                    return null
                }
                val body = response.body ?: return null
                if (!cutOff && body.contentLength() > limit) return null
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var cut = false
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        val before = out.size()
                        out.write(buffer, 0, minOf(n, limit - before + 1).coerceAtLeast(0))
                        if (out.size() > limit) {
                            if (!cutOff) return null
                            cut = true
                            break
                        }
                        if (untilHeadEnds && endsHead(out, before)) break
                    }
                }
                val bytes = out.toByteArray().let { if (it.size > limit) it.copyOf(limit) else it }
                return Fetched(bytes, response.header("Content-Type"), next.toString(), cut)
            }
        }
        return null
    }

    private fun plainCutting(url: String) =
        ContentPart.Attachment(id = newId(), kind = AttachmentKind.LINK, url = url, site = url.toHttpUrlOrNull()?.host?.let(::siteOf))

    companion object {
        private const val MAX_PAGE = 512 * 1024
        private const val MAX_READ = 2 * 1024 * 1024
        private const val MAX_PICTURE = 3 * 1024 * 1024
        private const val MAX_OEMBED = 64 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Android) Fumibako link preview"

        private val LINK = Regex("""https?://[^\s<>"']+""")
        private val META = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR = Regex("""(\w[\w:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
        private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val SEPARATORS = listOf(" | ", " - ", " – ", " — ", " · ", " :: ")

        /** The titles of pages sites send instead of theirs, to check for a robot or an old browser. */
        private val CHALLENGES = listOf(
            "Just a moment", "Client Challenge", "Attention Required", "Access denied", "Access Denied", "Your browser is deprecated",
            "Please enable JavaScript", "Are you a robot", "Verify you are human", "403 Forbidden", "Too Many Requests", "DDoS-Guard",
        )

        /** Query parameters that only say where a link was shared from. */
        private val TRACKING = setOf("si", "fbclid", "gclid", "igsh", "igshid", "feature", "ref_src", "mc_cid", "mc_eid")

        /** Links written in [text], without the punctuation that ends a sentence. */
        fun findLinks(text: String): List<String> =
            LINK.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'') }.distinct().toList()

        /** `property`/`name` to `content` for each meta tag, first one winning. */
        fun metaTags(html: String): Map<String, String> {
            val out = linkedMapOf<String, String>()
            META.findAll(html).forEach { tag ->
                val attrs = ATTR.findAll(tag.value).associate { m -> m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty()) }
                val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: return@forEach
                val content = attrs["content"] ?: return@forEach
                if (content.isNotBlank()) out.putIfAbsent(key, content)
            }
            return out
        }

        /** A site's name from its address, for a cutting's masthead: `m.youtube.com` is youtube.com. */
        fun siteOf(host: String): String = host.lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

        /**
         * A link written the way you'd say it, for a cutting with no headline: no `https://`, no
         * `www.`, no trailing slash, no tracking tags, and letters instead of `%20`s.
         */
        fun address(url: String): String {
            val u = url.toHttpUrlOrNull() ?: return url.substringAfter("://")
            val kept = u.newBuilder().query(null).apply {
                for (i in 0 until u.querySize) {
                    val name = u.queryParameterName(i)
                    if (!name.startsWith("utm_") && name !in TRACKING) addQueryParameter(name, u.queryParameterValue(i))
                }
            }.build()
            val path = runCatching { URLDecoder.decode(kept.encodedPath.replace("+", "%2B"), "UTF-8") }.getOrDefault(kept.encodedPath).removeSuffix("/")
            val query = kept.encodedQuery?.let { "?" + runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }.orEmpty()
            return siteOf(u.host) + path + query
        }

        /** A page title without its site's name tacked on: "*sshole · sshhole.com" is "*sshole". */
        fun withoutSiteName(title: String, site: String?, host: String): String {
            val labels = siteOf(host).split('.')
            val names = listOfNotNull(site, siteOf(host), siteOf(host).substringBeforeLast('.'), labels.getOrNull(labels.size - 2), labels.first().takeIf { labels.size >= 3 })
                .map { it.lowercase() }
            for (separator in SEPARATORS) {
                val head = title.substringBeforeLast(separator, "")
                val tail = title.substringAfterLast(separator, "").trim().lowercase()
                if (head.isNotBlank() && tail in names) return head.trim()
            }
            return title
        }

        /** Where a service answers what a link is called (oEmbed), for the services that do. */
        fun oEmbedAddress(u: HttpUrl): String? {
            val host = siteOf(u.host)
            val url = u.toString()
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            return when {
                MusicLinks.youtubeId(url) != null -> "https://www.youtube.com/oembed?format=json&url=" + enc("https://www.youtube.com/watch?v=" + MusicLinks.youtubeId(url))
                host == "open.spotify.com" -> "https://open.spotify.com/oembed?url=" + enc(url)
                host == "soundcloud.com" || host == "on.soundcloud.com" -> "https://soundcloud.com/oembed?format=json&url=" + enc(url)
                host == "vimeo.com" -> "https://vimeo.com/api/oembed.json?url=" + enc(url)
                else -> null
            }
        }

        private fun acceptLanguage(): String {
            val tag = Locale.getDefault().toLanguageTag()
            return if (tag.startsWith("en")) "$tag, en;q=0.8" else "$tag, en;q=0.6"
        }

        /** Whether `</head>` arrived in what was written after [from] (or just before it). */
        private fun endsHead(out: java.io.ByteArrayOutputStream, from: Int): Boolean {
            val bytes = out.toByteArray()
            val start = (from - 6).coerceAtLeast(0)
            return String(bytes, start, bytes.size - start, Charsets.ISO_8859_1).contains("</head", ignoreCase = true)
        }

        private fun titleOf(html: String) = TITLE.find(html)?.groupValues?.get(1)

        private fun clean(text: String) = text
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
            .replace(Regex("\\s+"), " ").trim()

        private fun extensionOf(path: String) = path.substringAfterLast('.', "").lowercase().takeIf { it in setOf("png", "jpg", "jpeg", "webp", "gif") } ?: "jpg"

        /** Private, local, link-local and carrier-grade addresses, in either family. */
        fun isPrivate(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return true
            val b = address.address.map { it.toInt() and 0xFF }
            return when (address) {
                is Inet4Address -> b[0] == 0 || (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) || (b[0] == 192 && b[1] == 0 && b[2] == 0)
                is Inet6Address -> (b[0] and 0xFE) == 0xFC || (b[0] == 0xFE && (b[1] and 0xC0) == 0x80)
                else -> true
            }
        }
    }
}
