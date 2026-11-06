package dev.aiharness.core.media

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.SongsOpenIn
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MusicSearchTest {
    private val server = MockWebServer()

    private fun result(track: String, artist: String, preview: String) = """
        {"wrapperType":"track","kind":"song","artistName":"$artist","collectionName":"Brighten the Corners","trackName":"$track",
         "trackViewUrl":"https://music.apple.com/us/album/x/1589160766?i=1589161038&uo=4","previewUrl":"$preview",
         "artworkUrl100":"${server.url("/art/100x100bb.jpg")}","releaseDate":"1997-02-11T12:00:00Z","trackTimeMillis":206947}
    """.trimIndent()

    private fun serve() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/search") -> MockResponse().setBody(
                    "\n\n\n{\"resultCount\":2,\"results\":[" + result("Harness Your Hopes (B-side)", "Pavement", server.url("/preview.m4a").toString()) +
                        ",{\"wrapperType\":\"track\",\"kind\":\"feature-movie\",\"trackName\":\"Not a song\",\"artistName\":\"Someone\"}]}",
                )
                request.path!!.startsWith("/preview") -> MockResponse().setBody(Buffer().write(ByteArray(1200) { 7 }))
                request.path!!.startsWith("/art/600x600bb") -> MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3)))
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun songsAreFoundWithWhatATicketNeeds() = runTest {
        serve()
        val search = MusicSearch(InMemoryBlobStore(), base = server.url("/").toString().trimEnd('/'))
        val found = search.songs("harness your hopes", country = "US")!!
        assertEquals(1, found.size, "only songs")
        val song = found.single()
        assertEquals("Harness Your Hopes (B-side)", song.title)
        assertEquals("Pavement", song.artist)
        assertEquals(1997, song.year)
        assertEquals(206_947, song.lengthMs)
        assertTrue(song.artworkUrl!!.endsWith("/art/600x600bb.jpg"), "the bigger cover")
        assertEquals("https://music.apple.com/us/album/x/1589160766?i=1589161038", song.appleUrl)
        assertTrue(server.takeRequest().path!!.contains("term=harness+your+hopes"))
    }

    @Test
    fun aPickBecomesATicketWithItsPreview() = runTest {
        serve()
        val blobs = InMemoryBlobStore()
        val search = MusicSearch(blobs, base = server.url("/").toString().trimEnd('/'), allowHttp = true)
        val ticket = search.ticket(search.songs("harness", country = "US")!!.single())
        assertEquals(AttachmentKind.SONG, ticket.kind)
        assertTrue(ticket.previewOnly)
        assertEquals(1200, blobs.get(ticket.ref!!)?.size)
        assertNotNull(ticket.pictureRef?.let { blobs.get(it) })
        assertEquals("[ticket: Harness Your Hopes (B-side) by Pavement, from Brighten the Corners (1997), 3:26, with its 30-second preview]", ticket.standIn)
        val gemini = ModelProfile(id = "g", name = "Gemini", provider = ProviderType.GEMINI, modelId = "gemini-3-flash")
        assertEquals((MediaSupport.PREVIEW_MS / 1000 * 32).toInt(), MediaSupport.wholeTokens(ticket, gemini), "a model hears thirty seconds of it")
        assertEquals("Aria hears its 30-second preview.", MediaSupport.cantTakeIn(ticket, gemini, "Aria"))
    }

    @Test
    fun songsOpenWhereYouChose() = runTest {
        serve()
        val search = MusicSearch(InMemoryBlobStore(), base = server.url("/").toString().trimEnd('/'))
        val spotify = ContentPart.Attachment("s", AttachmentKind.SONG, title = "Harness Your Hopes", artist = "Pavement", url = "https://open.spotify.com/track/abc")
        val youtube = ContentPart.Attachment("y", AttachmentKind.SONG, title = "Harness Your Hopes", artist = "Pavement", url = "https://www.youtube.com/watch?v=w2Ww_O3ceKU")
        assertEquals(spotify.url, search.whereToHear(spotify, SongsOpenIn.AS_LINKED), "until it's set, a link opens where it's from")
        assertEquals(spotify.url, search.whereToHear(spotify, SongsOpenIn.SPOTIFY))
        assertEquals("https://music.apple.com/us/album/x/1589160766?i=1589161038", search.whereToHear(spotify, SongsOpenIn.APPLE_MUSIC), "the same song, found")
        assertEquals("https://music.youtube.com/watch?v=w2Ww_O3ceKU", search.whereToHear(youtube, SongsOpenIn.YOUTUBE_MUSIC), "the same video")
        assertEquals("https://open.spotify.com/search/Harness%20Your%20Hopes%20Pavement", search.whereToHear(youtube, SongsOpenIn.SPOTIFY), "otherwise a search there")
    }
}
