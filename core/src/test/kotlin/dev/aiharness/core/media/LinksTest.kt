package dev.aiharness.core.media

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.ServerTool
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.llm.anthropic.AnthropicProvider
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import dev.aiharness.core.tools.LinkReading
import dev.aiharness.core.tools.OpenLinkTool
import dev.aiharness.core.tools.ShareLinkTool
import dev.aiharness.core.tools.ToolContext
import dev.aiharness.core.tools.schema
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.3.5 step 5: personas open links you share (read stamps) and share links and songs themselves. */
class LinksTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private val article = """
        <html><head><title>Tea</title><meta property="og:title" content="Why the second steeping is the good one"></head>
        <body><nav>Home | Shop</nav><article><h1>The second steeping</h1><p>The first pour wakes the leaves.</p>
        <p>See <a href="https://example.com/oolong">oolong</a> too.</p><script>track()</script></article><footer>(c) Kettle</footer></body></html>
    """.trimIndent()

    @Test
    fun aPageReadsAsItsArticleWithLinksKept() {
        val (text, cut) = Readable.of(article, 10_000)
        assertFalse(cut)
        assertEquals("The second steeping\nThe first pour wakes the leaves.\nSee oolong (https://example.com/oolong) too.", text)
        assertFalse("Home" in text || "track" in text || "Kettle" in text)
    }

    @Test
    fun musicLinksAreKnownByTheirService() {
        assertEquals(SongSource.YOUTUBE, MusicLinks.sourceOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", MusicLinks.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3"))
        assertEquals(SongSource.SPOTIFY, MusicLinks.sourceOf("https://open.spotify.com/track/abc"))
        assertEquals(SongSource.BANDCAMP, MusicLinks.sourceOf("https://hanasato.bandcamp.com/track/october-walk"))
        assertEquals(SongSource.AUDIO, MusicLinks.sourceOf("https://example.com/files/walk.mp3"))
        assertNull(MusicLinks.sourceOf("https://example.com/blog"))
        assertTrue(MusicLinks.isMusicOnly("https://open.spotify.com/track/abc"))
        assertFalse(MusicLinks.isMusicOnly("https://youtu.be/dQw4w9WgXcQ"), "a YouTube video isn't always a song")
        assertEquals("October Walk" to "Hana Sato", MusicLinks.titleAndArtist(SongSource.YOUTUBE, "Hana Sato - October Walk - YouTube", null, ""))
        assertEquals("October Walk" to "Hana Sato", MusicLinks.titleAndArtist(SongSource.BANDCAMP, "October Walk, by Hana Sato", null, ""))
        assertEquals("October Walk" to "Hana Sato", MusicLinks.titleAndArtist(SongSource.SPOTIFY, "October Walk", "Listen to October Walk on Spotify. Song · Hana Sato · 2024", ""))
    }

    private suspend fun context(env: TestEnv, conversationId: String) =
        ToolContext(env.conversations.get(conversationId)!!, env.persona, env.user, env.settingsRepo.current(), background = false)

    @Test
    fun aPersonaOpensOnlyLinksInTheConversationAndStampsTheCutting() = runTest {
        server.enqueue(MockResponse().setBody(article).setHeader("Content-Type", "text/html"))
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        val url = server.url("/tea").toString()
        val cutting = ContentPart.Attachment("cut1", AttachmentKind.LINK, url = url, title = "Tea")
        env.harness.agent.send(conversation.id, "Read this?", attachments = listOf(cutting)).toList()
        val tool = OpenLinkTool(LinkPreviews(InMemoryBlobStore(), allowPrivate = true), LinkReading(env.conversations, env.marks, env.clock))

        val elsewhere = tool.execute(buildJsonObject { put("url", "https://example.com/elsewhere") }, context(env, conversation.id))
        assertTrue(elsewhere.isError)
        val read = tool.execute(buildJsonObject { put("url", url) }, context(env, conversation.id))
        assertFalse(read.isError, read.content)
        assertContains(read.content, "The first pour wakes the leaves.")
        assertEquals(listOf(MarkKind.READ to env.persona.id), env.marks.forAttachment("cut1").map { it.kind to it.byPersonaId })
    }

    @Test
    fun claudesOwnFetchStampsTheCuttingToo() = runTest {
        val env = TestEnv(ScriptedProvider(ProviderType.ANTHROPIC)).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        val url = "https://kettleandleaf.example/second-steeping"
        env.provider.then(LlmTurnResult(text = "I read it.", opened = listOf("$url/")))
        env.harness.agent.send(conversation.id, "", attachments = listOf(ContentPart.Attachment("cut2", AttachmentKind.LINK, url = url))).toList()
        assertEquals(listOf(MarkKind.READ), env.marks.forAttachment("cut2").map { it.kind })
    }

    @Test
    fun sharedLinksBecomeCuttingsAndDeadOnesArentSent() = runTest {
        server.enqueue(MockResponse().setBody(article).setHeader("Content-Type", "text/html"))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg"))
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        val tool = ShareLinkTool(LinkPreviews(InMemoryBlobStore(), allowPrivate = true))

        val shared = tool.execute(buildJsonObject { put("url", server.url("/tea").toString()) }, context(env, conversation.id))
        val cutting = shared.attachments.single() as ContentPart.Attachment
        assertEquals(AttachmentKind.LINK, cutting.kind)
        assertEquals("Why the second steeping is the good one", cutting.title)

        val dead = tool.execute(buildJsonObject { put("url", server.url("/made-up").toString()) }, context(env, conversation.id))
        assertTrue(dead.isError)
        assertTrue(dead.attachments.isEmpty())

        val song = tool.execute(buildJsonObject { put("url", server.url("/music/october_walk.mp3").toString()); put("as", "song") }, context(env, conversation.id))
        val ticket = song.attachments.single() as ContentPart.Attachment
        assertEquals(AttachmentKind.SONG, ticket.kind)
        assertEquals("october walk", ticket.title)
    }

    @Test
    fun openLinkGoesToClaudeAsWebFetchOnAnthropicsOwnApi() {
        val spec = ToolSpec("open_link", "Open a link", schema { string("url", "The link", required = true) }, server = ServerTool.WEB_FETCH)
        fun toolsFor(profile: ModelProfile) = AnthropicProvider().buildParams(
            LlmRequest(profile, "k", "sys", listOf(LlmMessage.User(listOf(LlmPart.Text("hi")))), listOf(spec)),
        ).tools().get()
        val claude = ModelProfile(id = "a", name = "a", provider = ProviderType.ANTHROPIC, modelId = "claude-opus-5-5")

        val first = toolsFor(claude).single()
        assertTrue(first.isWebFetchTool20260209(), "the dynamic-filtering fetch on current models")
        assertTrue(toolsFor(claude.copy(modelId = "claude-haiku-5-5")).single().isWebFetchTool20250910())
        assertTrue(toolsFor(claude.copy(baseUrl = "https://proxy.example")).single().isBetaTool(), "a proxy gets the app's own fetcher")
    }
}
