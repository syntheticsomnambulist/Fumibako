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
    fun linksAreFoundWithoutTheirSentencePunctuation() {
        assertEquals(
            listOf("https://example.com/a?b=1", "http://tea.example/second"),
            LinkPreviews.findLinks("Look: https://example.com/a?b=1. And (http://tea.example/second)!"),
        )
    }
}
