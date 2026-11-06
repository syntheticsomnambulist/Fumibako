package dev.aiharness.core.media

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Newspaper cuttings for links: the page's Open Graph data (site name, title, picture,
 * description), fetched once when the link is attached. Only public web addresses are fetched;
 * private and local ones (your router, your computer) are refused, and get a plain cutting with
 * just the address, as does any page that can't be read.
 */
class LinkPreviews(
    private val blobs: BlobStore,
    private val client: OkHttpClient = HarnessHttp.client,
    /** For tests against a local server only. */
    private val allowPrivate: Boolean = false,
    private val resolve: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() },
) {
    /** A cutting for [url]; with [fetch] off (the setting), or when the page doesn't open, just the address. */
    suspend fun cutting(url: String, fetch: Boolean = true): ContentPart.Attachment =
        (if (fetch) fetched(url) else null) ?: plainCutting(url)

    private fun plainCutting(url: String) =
        ContentPart.Attachment(id = newId(), kind = AttachmentKind.LINK, url = url, site = url.toHttpUrlOrNull()?.host?.removePrefix("www."))

    /** A cutting from the page at [url], or null when it doesn't open (private, gone, not a page). */
    suspend fun fetched(url: String): ContentPart.Attachment? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!isPublic(parsed.host)) return null
        val plain = plainCutting(url)
        return runCatching {
            withContext(Dispatchers.IO) {
                val html = get(url, MAX_PAGE)?.toString(Charsets.UTF_8) ?: return@withContext null
                val tags = metaTags(html)
                val title = tags["og:title"] ?: tags["twitter:title"] ?: titleOf(html)
                val picture = (tags["og:image"] ?: tags["twitter:image"])?.let { parsed.resolve(it)?.toString() }
                val pictureRef = picture?.toHttpUrlOrNull()?.takeIf { isPublic(it.host) }?.let { p ->
                    get(p.toString(), MAX_PICTURE)?.let { bytes -> blobs.put(bytes, extensionOf(p.encodedPath)) }
                }
                plain.copy(
                    title = title?.let(::clean)?.take(200),
                    site = tags["og:site_name"]?.let(::clean)?.take(60) ?: plain.site,
                    snippet = (tags["og:description"] ?: tags["description"])?.let(::clean)?.take(300),
                    pictureRef = pictureRef,
                    embed = (tags["og:video:secure_url"] ?: tags["og:video:url"] ?: tags["og:video"])?.takeIf { it.startsWith("https://") },
                )
            }
        }.getOrNull()
    }

    /** The type of what's at [url] when it opens (asked with HEAD, redirects checked), or null. */
    suspend fun probe(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!isPublic(parsed.host)) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
                var next = parsed
                repeat(6) {
                    if (!isPublic(next.host)) return@withContext null
                    val request = Request.Builder().url(next).head().header("User-Agent", USER_AGENT).build()
                    noRedirects.newCall(request).execute().use { response ->
                        if (response.isRedirect) {
                            next = response.header("Location")?.let { next.resolve(it) } ?: return@withContext null
                            return@use
                        }
                        return@withContext if (response.isSuccessful) response.header("Content-Type") ?: "" else null
                    }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * The words of the page at [url] for a persona to read, at most [maxChars] of them: an HTML
     * page's readable text, or a text file as it is. Null when it can't be read (a private
     * address, an error, too big, or not text).
     */
    suspend fun read(url: String, maxChars: Int = 12_000): PageText? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!isPublic(parsed.host)) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val page = fetch(url, MAX_READ) ?: return@withContext null
                val type = page.type?.lowercase().orEmpty()
                val raw = page.bytes.toString(Charsets.UTF_8)
                when {
                    type.startsWith("text/html") || type.contains("xhtml") || (type.isEmpty() && raw.contains("<html", ignoreCase = true)) -> {
                        val (text, cut) = Readable.of(raw, maxChars)
                        PageText(page.url, metaTags(raw)["og:title"]?.let(::clean) ?: titleOf(raw)?.let(::clean), text, cut)
                    }
                    type.startsWith("text/") || type.contains("json") -> PageText(page.url, null, raw.take(maxChars), raw.length > maxChars)
                    else -> null
                }
            }
        }.getOrNull()
    }

    /** Whether [host] is out on the public internet (every address it resolves to). */
    fun isPublic(host: String): Boolean {
        if (allowPrivate) return true
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local") || host.endsWith(".internal")) return false
        val addresses = runCatching { resolve(host) }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.none(::isPrivate)
    }

    /**
     * The body of [url], up to [limit] bytes (null past it, or on an error). Redirects are followed
     * by hand, at most five, each checked, so a public page can't send the fetch somewhere private.
     */
    private fun get(url: String, limit: Int): ByteArray? = fetch(url, limit)?.bytes

    private class Fetched(val bytes: ByteArray, val type: String?, val url: String)

    private fun fetch(url: String, limit: Int): Fetched? {
        val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var next = url.toHttpUrlOrNull() ?: return null
        repeat(6) {
            if (!isPublic(next.host)) return null
            val request = Request.Builder().url(next).header("User-Agent", USER_AGENT).header("Accept", "text/html,image/*;q=0.9,*/*;q=0.5").build()
            noRedirects.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    next = response.header("Location")?.let { next.resolve(it) } ?: return null
                    return@use
                }
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > limit) return null
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > limit) return null
                    }
                }
                return Fetched(out.toByteArray(), response.header("Content-Type"), next.toString())
            }
        }
        return null
    }

    companion object {
        private const val MAX_PAGE = 512 * 1024
        private const val MAX_READ = 2 * 1024 * 1024
        private const val MAX_PICTURE = 3 * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Android) Fumibako link preview"

        private val LINK = Regex("""https?://[^\s<>"']+""")
        private val META = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTR = Regex("""(\w[\w:-]*)\s*=\s*("([^"]*)"|'([^']*)')""")
        private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

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

        private fun titleOf(html: String) = TITLE.find(html)?.groupValues?.get(1)

        private fun clean(text: String) = text
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
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
