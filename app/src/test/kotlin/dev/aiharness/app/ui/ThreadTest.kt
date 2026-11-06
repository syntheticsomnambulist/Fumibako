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
}

