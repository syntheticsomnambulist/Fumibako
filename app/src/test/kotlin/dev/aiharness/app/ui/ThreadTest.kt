package dev.aiharness.app.ui

import dev.aiharness.app.ui.chat.LetterBlock
import dev.aiharness.app.ui.chat.ThreadRow
import dev.aiharness.app.ui.chat.buildThread
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.MoodTags
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThreadTest {
    private var t = 1_779_962_400_000L // 10:00 UTC
    private var n = 0

    private fun msg(role: MessageRole, vararg parts: ContentPart, origin: MessageOrigin = MessageOrigin.AGENT, hidden: Boolean = false, at: Long? = null) =
        ChatMessage(
            "m${n++}", "c", role, parts.toList(),
            createdAt = at ?: t.also { t += 60_000 },
            origin = origin,
            hidden = hidden,
            mood = parts.filterIsInstance<ContentPart.Text>().firstNotNullOfOrNull { MoodTags.parse(it.text) },
        )

    @Test
    fun groupsATurnIntoOneLetterWithPostscriptsAndPicturesInOrder() {
        val rows = buildThread(
            listOf(
                msg(MessageRole.USER, ContentPart.Text("I finished!"), origin = MessageOrigin.USER),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Huge! [mood: proud]"), ContentPart.ToolCall("a", "memory_save", """{"content":"Sam finished"}""")),
                msg(MessageRole.TOOL, ContentPart.ToolResult("a", "memory_save", "Saved.")),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Look:"), ContentPart.ToolCall("b", "generate_image", "{}")),
                msg(MessageRole.TOOL, ContentPart.ToolResult("b", "generate_image", "Shown."), ContentPart.Image("file:x.png")),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Ta-da.")),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf("Day", "Note", "Letter"), rows.map { it::class.simpleName })
        val letter = assertIs<ThreadRow.Letter>(rows[2])
        assertEquals("proud", letter.mood)
        assertEquals(listOf("Huge!", "Look:", "picture", "Ta-da."), letter.blocks.map { (it as? LetterBlock.Words)?.text ?: "picture" })
        assertEquals("Sam finished", letter.postscripts.single().text)
    }

    @Test
    fun heartbeatLettersStandAloneAndHiddenPromptsDisappear() {
        val rows = buildThread(
            listOf(
                msg(MessageRole.ASSISTANT, ContentPart.Text("Hi!")),
                msg(MessageRole.USER, ContentPart.Text("<heartbeat>"), origin = MessageOrigin.HEARTBEAT, hidden = true),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Thinking of you."), origin = MessageOrigin.HEARTBEAT),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Also, good luck."), origin = MessageOrigin.HEARTBEAT, at = t + 86_400_000L),
            ),
            ZoneOffset.UTC,
        )
        val letters = rows.filterIsInstance<ThreadRow.Letter>()
        assertEquals(2, letters.size)
        assertTrue(letters[1].fromHeartbeat)
        assertEquals(2, letters[1].paragraphs.size, "consecutive heartbeat messages share a letter")
        assertTrue(rows.none { it is ThreadRow.Note })
    }

    @Test
    fun aNoteOfOnlyThingsStillShows() {
        val song = ContentPart.Attachment("s1", dev.aiharness.core.model.AttachmentKind.SONG, title = "Harness Your Hopes", artist = "Pavement")
        val rows = buildThread(
            listOf(
                msg(MessageRole.USER, song, origin = MessageOrigin.USER),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Ooh.")),
            ),
            ZoneOffset.UTC,
        )
        assertTrue(rows.any { it is ThreadRow.Note && it.message.attachments.single().id == "s1" }, "a ticket sent without words is still a note")
    }

    @Test
    fun aNewDayGetsAHeading() {
        val rows = buildThread(
            listOf(
                msg(MessageRole.USER, ContentPart.Text("Night!"), origin = MessageOrigin.USER),
                msg(MessageRole.ASSISTANT, ContentPart.Text("Sleep well.")),
                msg(MessageRole.USER, ContentPart.Text("Morning!"), origin = MessageOrigin.USER, at = t + 86_400_000L),
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf("Day", "Note", "Letter", "Day", "Note"), rows.map { it::class.simpleName })
    }

    @Test
    fun stickerToolsLeavePostscriptsInYourTerms() {
        val messages = listOf(
            msg(MessageRole.USER, ContentPart.Text("I finished!"), origin = MessageOrigin.USER),
            msg(MessageRole.ASSISTANT, ContentPart.ToolCall("s1", "place_sticker", "{}"), ContentPart.ToolCall("s2", "draw_sticker", "{}")),
            msg(
                MessageRole.TOOL,
                ContentPart.ToolResult("s1", "place_sticker", "Stuck :aria-heart: on their note (top left)."),
                ContentPart.ToolResult("s2", "draw_sticker", "Drew :aria-proud: and put it on your sheet. You can stick it now."),
            ),
            msg(MessageRole.ASSISTANT, ContentPart.Text("You did it!")),
        )
        val letter = buildThread(messages, ZoneOffset.UTC).filterIsInstance<ThreadRow.Letter>().single()
        assertEquals(
            listOf("Stuck" to ":aria-heart: on your note (top left)", "Drew a new sticker:" to ":aria-proud:"),
            letter.postscripts.map { it.label to it.text },
        )
    }

    @Test
    fun aChangeOfModelGoesBeforeTheFirstLetterWithItAndLettersKnowTheirNote() {
        val sonnet = dev.aiharness.core.model.WrittenWith("Claude Sonnet 5.5", dev.aiharness.core.model.ProviderType.ANTHROPIC, "claude-sonnet-5-5")
        val opus = sonnet.copy(name = "Claude Opus 5.5", modelId = "claude-opus-5-5")
        val first = msg(MessageRole.USER, ContentPart.Text("Hello"), origin = MessageOrigin.USER).copy(turnContext = "Current time: Monday.")
        val reply = msg(MessageRole.ASSISTANT, ContentPart.Text("Hi!")).copy(writtenWith = sonnet)
        val second = msg(MessageRole.USER, ContentPart.Text("Still you?"), origin = MessageOrigin.USER)
            .copy(turnContext = "Your model changed: you were Claude Sonnet 5.5 until now and are Claude Opus 5.5 from here.")
        val found = dev.aiharness.core.model.FoundLetter(first.id, "c", byYou = true, at = first.createdAt, passage = "Hello")
        val searched = msg(MessageRole.ASSISTANT, ContentPart.ToolCall("s", "letter_search", """{"query":"hello"}""")).copy(writtenWith = opus)
        val result = msg(MessageRole.TOOL, ContentPart.ToolResult("s", "letter_search", "1 passage:", foundLetters = listOf(found)))
        val answer = msg(MessageRole.ASSISTANT, ContentPart.Text("Still me.")).copy(writtenWith = opus)
        val rows = buildThread(listOf(first, reply, second, searched, result, answer), ZoneOffset.UTC)

        assertEquals(listOf("Day", "Note", "Letter", "Note", "ModelChange", "Letter"), rows.map { it::class.simpleName })
        val change = assertIs<ThreadRow.ModelChange>(rows[4])
        assertEquals(sonnet, change.notice.was)
        assertEquals(opus, change.notice.now)
        assertEquals(reply.createdAt, change.after)
        assertTrue(change.told)
        assertEquals(first.id, assertIs<ThreadRow.Letter>(rows[2]).prompt?.id)
        val letter = assertIs<ThreadRow.Letter>(rows[5])
        assertEquals(second.id, letter.prompt?.id)
        assertEquals("hello", letter.slip.searches.single().query)
        val ps = letter.postscripts.single()
        assertEquals("Looked back at:", ps.label)
        assertEquals(found, ps.found)
    }

    @Test
    fun aHeartbeatLetterAnswersItsHiddenPromptNotTheNoteBefore() {
        val note = msg(MessageRole.USER, ContentPart.Text("Tired."), origin = MessageOrigin.USER).copy(turnContext = "Current time: Monday.")
        val reply = msg(MessageRole.ASSISTANT, ContentPart.Text("Rest!"))
        val prompt = msg(MessageRole.USER, ContentPart.Text("(heartbeat)"), origin = MessageOrigin.HEARTBEAT, hidden = true)
        val away = msg(MessageRole.ASSISTANT, ContentPart.Text("Good morning!"), origin = MessageOrigin.HEARTBEAT)
        val letters = buildThread(listOf(note, reply, prompt, away), ZoneOffset.UTC).filterIsInstance<ThreadRow.Letter>()
        assertEquals(listOf(note.id, prompt.id), letters.map { it.prompt?.id })
        assertTrue(letters.last().slip.isEmpty, "nothing in mind to show: no tab")

        // As the thread shows it: hidden prompts left out of the letters, but still each letter's prompt.
        val shown = buildThread(listOf(note, reply, away), ZoneOffset.UTC, prompts = listOf(note, prompt)).filterIsInstance<ThreadRow.Letter>()
        assertEquals(listOf(note.id, prompt.id), shown.map { it.prompt?.id })
    }

    @Test
    fun aReplyInThePostAnswersTheNoteBeforeItNotOneSentWhileItTravelled() {
        val asked = msg(MessageRole.USER, ContentPart.Text("How was it?"), origin = MessageOrigin.USER).copy(turnContext = "Current time: Monday.")
        val reply = msg(MessageRole.ASSISTANT, ContentPart.Text("Lovely.")).copy(deliverAt = t + 600_000)
        val later = msg(MessageRole.USER, ContentPart.Text("Hello?"), origin = MessageOrigin.USER)
        // Shown in the order they reached you: the reply after the later note.
        val letter = buildThread(listOf(asked, later, reply), ZoneOffset.UTC).filterIsInstance<ThreadRow.Letter>().single()
        assertEquals(asked.id, letter.prompt?.id)
    }
}
