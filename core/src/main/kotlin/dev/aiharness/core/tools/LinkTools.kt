package dev.aiharness.core.tools

import dev.aiharness.core.llm.ServerTool
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.media.LinkPreviews
import dev.aiharness.core.media.MusicLinks
import dev.aiharness.core.media.SongSource
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.MarkRepository
import dev.aiharness.core.util.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The links in a conversation, and the persona's read stamp on the ones it opens. A persona opens
 * only links already in its letters (one you shared, one it sent, one on a page it read), which is
 * also the rule Claude's web fetch keeps.
 */
class LinkReading(
    private val conversations: ConversationRepository,
    private val marks: MarkRepository,
    private val clock: Clock,
) {
    suspend fun known(conversationId: String): Set<String> = linksIn(conversations.getMessages(conversationId)).map(::same).toSet()

    /** Puts [personaId]'s read stamp on the cuttings in the conversation that are [url]. */
    suspend fun markRead(conversationId: String, url: String, personaId: String) {
        val key = same(url)
        conversations.getMessages(conversationId)
            .flatMap { it.attachments }
            .filter { it.kind == AttachmentKind.LINK && it.url?.let(::same) == key }
            .forEach { marks.add(AttachmentMark(it.id, MarkKind.READ, conversationId = conversationId, byPersonaId = personaId, at = clock.nowMillis())) }
    }

    companion object {
        fun linksIn(history: List<ChatMessage>): List<String> = history.flatMap { m ->
            m.attachments.mapNotNull { it.url } + LinkPreviews.findLinks(m.text) + m.toolResults.flatMap { LinkPreviews.findLinks(it.content) }
        }

        /** One address however it's written: no fragment, no trailing slash, the host in lowercase. */
        fun same(url: String): String {
            val u = url.trim().toHttpUrlOrNull() ?: return url.trim()
            return u.newBuilder().fragment(null).build().toString().removeSuffix("/").replaceFirst("://www.", "://")
        }
    }
}

/**
 * Opens a link from the conversation and reads the page: the app's own fetcher, on the phone (the
 * site sees your address), public addresses only. With Claude, Anthropic's web fetch runs in its
 * place ([ServerTool.WEB_FETCH]); either way, the persona's read stamp lands on the cutting.
 */
class OpenLinkTool(
    private val links: LinkPreviews,
    private val reading: LinkReading,
) : AgentTool {
    override val spec = ToolSpec(
        name = "open_link",
        description = "Open a link from your letters (one they shared, one you sent, or one on a page you read) and read the page. " +
            "Only links already in the conversation can be opened.",
        parameters = schema { string("url", "The link, exactly as it appears.", required = true) },
        server = ServerTool.WEB_FETCH,
    )

    override fun isAvailable(settings: AppSettings) = settings.media.personasOpenLinks

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (url.toHttpUrlOrNull() == null) return ToolOutcome("That isn't a web address.", isError = true)
        if (LinkReading.same(url) !in reading.known(context.conversation.id)) {
            return ToolOutcome("You can only open links that are already in your letters.", isError = true)
        }
        val page = links.read(url) ?: return ToolOutcome("The page didn't open: it may be private, gone, or not a web page.", isError = true)
        reading.markRead(context.conversation.id, url, context.persona.id)
        val title = page.title?.let { " title=\"${it.replace("\"", "'")}\"" }.orEmpty()
        val cut = if (page.cut) "\n[The rest of the page is cut off.]" else ""
        return ToolOutcome("<page url=\"${page.url}\"$title>\n${page.text}$cut\n</page>")
    }
}

/**
 * Puts a link into the persona's letter: a newspaper cutting of the page, or a ticket for a song.
 * The app opens the link first; one that doesn't open isn't sent, so a made-up address never
 * reaches you, and the persona hears what the page really is.
 */
class ShareLinkTool(private val links: LinkPreviews) : AgentTool {
    override val spec = ToolSpec(
        name = "share_link",
        description = "Put a link into your letter: it appears as a newspaper cutting of the page, or as a ticket for a song " +
            "(a music link from YouTube, SoundCloud, Bandcamp, Spotify or Apple Music, or an audio file). Share only real links " +
            "you've seen in the conversation or on a page you read; the app opens each one first and doesn't send one that doesn't open.",
        parameters = schema {
            string("url", "The link.", required = true)
            string("as", "\"song\" for a ticket, \"cutting\" for a page (by default, music services are songs, other links cuttings).", enum = listOf("song", "cutting"))
        },
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (url.toHttpUrlOrNull() == null) return ToolOutcome("That isn't a web address.", isError = true)
        val source = MusicLinks.sourceOf(url)
        val asSong = when (args["as"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "song" -> source != null
            "cutting" -> false
            else -> MusicLinks.isMusicOnly(url)
        }
        val shared = if (asSong && source == SongSource.AUDIO) {
            val type = links.probe(url) ?: return notSent()
            if (!type.startsWith("audio/") && type.isNotEmpty()) return ToolOutcome("That link isn't an audio file, so it wasn't sent.", isError = true)
            val (title, artist) = MusicLinks.titleAndArtist(SongSource.AUDIO, null, null, url)
            ContentPart.Attachment(dev.aiharness.core.util.newId(), AttachmentKind.SONG, url = url, title = title, artist = artist, mimeType = type.ifEmpty { null }, site = url.toHttpUrlOrNull()?.host)
        } else {
            val page = links.fetched(url) ?: return notSent()
            if (asSong && source != null) {
                val (title, artist) = MusicLinks.titleAndArtist(source, page.title, page.snippet, url)
                page.copy(kind = AttachmentKind.SONG, title = title, artist = artist, site = source.label, snippet = null)
            } else {
                page
            }
        }
        val where = if (shared.kind == AttachmentKind.SONG) "a ticket" else "a cutting"
        return ToolOutcome("In your letter now, as $where: ${shared.standIn}", attachments = listOf(shared))
    }

    private fun notSent() = ToolOutcome("That link didn't open, so it wasn't sent. Share only links you've seen.", isError = true)
}
