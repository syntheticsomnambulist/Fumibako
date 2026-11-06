package dev.aiharness.core.media

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Test
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkPreviewsTest {
    @Test
    fun aPageBecomesACuttingWithItsPicture() = runTest {
        val server = MockWebServer()
        val page = """
            <html><head><title>Fallback title</title>
            <meta property="og:site_name" content="Kettle &amp; Leaf">
            <meta property="og:title" content="Why the second steeping is the good one">
            <meta name="description" content="The first pour wakes the leaves.">
            <meta property="og:image" content="/pictures/tea.png">
            </head><body>…</body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(page).setHeader("Content-Type", "text/html"))
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))).setHeader("Content-Type", "image/png"))
        server.start()
        val blobs = InMemoryBlobStore()
        val cutting = LinkPreviews(blobs, allowPrivate = true).cutting(server.url("/tea").toString())
        server.shutdown()

        assertEquals(AttachmentKind.LINK, cutting.kind)
        assertEquals("Kettle & Leaf", cutting.site)
        assertEquals("Why the second steeping is the good one", cutting.title)
        assertEquals("The first pour wakes the leaves.", cutting.snippet)
        assertNotNull(cutting.pictureRef?.let { blobs.get(it) })
        assertEquals("[cutting: Why the second steeping is the good one, Kettle & Leaf, ${cutting.url}]", cutting.standIn)
    }

    @Test
    fun privateAddressesAreNeverFetched() = runTest {
        val server = MockWebServer()
        server.start()
        val cutting = LinkPreviews(InMemoryBlobStore()).cutting(server.url("/").toString())
        assertEquals(0, server.requestCount, "localhost is refused")
        server.shutdown()
        assertNull(cutting.title)

        val previews = LinkPreviews(InMemoryBlobStore(), resolve = { host ->
            listOf(InetAddress.getByAddress(host, if (host == "router.example") byteArrayOf(192.toByte(), 168.toByte(), 1, 1) else byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
        })
        assertFalse(previews.isPublic("router.example"))
        assertTrue(previews.isPublic("example.com"))
        assertFalse(LinkPreviews(InMemoryBlobStore()).isPublic("10.0.0.2"))
        assertFalse(LinkPreviews(InMemoryBlobStore()).isPublic("100.100.1.1"), "carrier-grade NAT")
    }

    @Test
    fun aBigPageStillGivesItsHeadline() = runTest {
        val server = MockWebServer()
        val head = "<html><head><title>Welfare Regard in Multi-Agent Systems</title></head><body>"
        server.enqueue(MockResponse().setBody(head + "<p>words</p>".repeat(120_000) + "</body></html>").setHeader("Content-Type", "text/html"))
        server.start()
        val cutting = LinkPreviews(InMemoryBlobStore(), allowPrivate = true).cutting(server.url("/welfare").toString())
        server.shutdown()
        assertEquals("Welfare Regard in Multi-Agent Systems", cutting.title, "read from the top of a page of over a megabyte")
    }

    @Test
    fun aServicesOwnLookupGivesTitleAuthorAndPicture() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"title":"Harness Your Hopes (Official Music Video)","author_name":"Pavement","provider_name":"YouTube","thumbnail_url":"https://i.ytimg.com/vi/x/hqdefault.jpg"}""",
            ),
        )
        server.start()
        val lookups = mutableListOf<String>()
        val previews = LinkPreviews(InMemoryBlobStore(), allowPrivate = true, oEmbedFor = { u -> lookups += u.toString(); server.url("/oembed").toString() })
        val cutting = previews.cutting("https://m.youtube.com/watch?v=w2Ww_O3ceKU")
        assertEquals(1, server.requestCount, "the lookup is enough: the page isn't fetched")
        server.shutdown()
        assertEquals("Harness Your Hopes (Official Music Video)", cutting.title)
        assertEquals("Pavement", cutting.artist)
        assertEquals("YouTube", cutting.site)
        assertTrue(cutting.standIn.startsWith("[cutting: Harness Your Hopes (Official Music Video), by Pavement, YouTube, "))
        assertEquals("https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dw2Ww_O3ceKU", LinkPreviews.oEmbedAddress(okhttp3.HttpUrl.Builder().scheme("https").host("youtu.be").addPathSegment("w2Ww_O3ceKU").build()))
    }

    @Test
    fun aBotCheckIsNotTheHeadline() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<html><head><title>Client Challenge</title></head><body>…</body></html>").setHeader("Content-Type", "text/html"))
        server.start()
        val cutting = LinkPreviews(InMemoryBlobStore(), allowPrivate = true).cutting(server.url("/track/october-walk").toString())
        server.shutdown()
        assertNull(cutting.title, "a plain cutting, which shows its address")
    }

    @Test
    fun addressesAreLookedUpOffTheCallersThread() = runTest {
        // Android refuses network work on the main thread, looking up an address included.
        val caller = Thread.currentThread()
        var lookedUpOn: Thread? = null
        val previews = LinkPreviews(InMemoryBlobStore(), resolve = { host ->
            lookedUpOn = Thread.currentThread()
            listOf(InetAddress.getByAddress(host, byteArrayOf(192.toByte(), 168.toByte(), 1, 1)))
        })
        previews.cutting("https://router.example/")
        assertNotNull(lookedUpOn)
        assertTrue(lookedUpOn !== caller)
    }

    @Test
    fun aCuttingWithoutAHeadlineShowsItsAddressTidily() {
        assertEquals("sshhole.com", LinkPreviews.address("https://sshhole.com/"))
        assertEquals("youtube.com/watch?v=w2Ww_O3ceKU", LinkPreviews.address("https://m.youtube.com/watch?v=w2Ww_O3ceKU&si=abc123&feature=share"))
        assertEquals("example.com/two words/page", LinkPreviews.address("https://www.example.com/two%20words/page/?utm_source=x"))
        assertEquals("youtube.com", LinkPreviews.siteOf("m.youtube.com"))
        assertEquals("*sshole", LinkPreviews.withoutSiteName("*sshole · sshhole.com", null, "sshhole.com"))
        assertEquals("Welfare Regard", LinkPreviews.withoutSiteName("Welfare Regard | Alignment", null, "alignment.anthropic.com"))
        assertEquals("Why tea - a guide", LinkPreviews.withoutSiteName("Why tea - a guide", "Kettle", "kettle.example"))
    }

    @Test
    fun linksAreFoundWithoutTheirSentencePunctuation() {
        assertEquals(
            listOf("https://example.com/a?b=1", "http://tea.example/second"),
            LinkPreviews.findLinks("Look: https://example.com/a?b=1. And (http://tea.example/second)!"),
        )
    }
}
