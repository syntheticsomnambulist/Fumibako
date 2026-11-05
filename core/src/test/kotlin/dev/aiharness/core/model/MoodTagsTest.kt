package dev.aiharness.core.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoodTagsTest {

    @Test
    fun parsesTheLastTagAndLowercasesIt() {
        assertEquals("curious", MoodTags.parse("Oh? Tell me more. [mood: Curious]"))
        assertEquals("fond", MoodTags.parse("[mood: shy] ... actually. [Mood:fond]"))
        assertEquals("a bit sleepy", MoodTags.parse("Night!\n[mood:  a bit sleepy ]"))
        assertNull(MoodTags.parse("No tag here [not a mood]"))
        assertNull(MoodTags.parse("[mood: ]"))
    }

    @Test
    fun stripsTagsForDisplay() {
        assertEquals("Oh? Tell me more.", MoodTags.strip("Oh? Tell me more. [mood: curious]"))
        assertEquals("Line one\nLine two", MoodTags.strip("Line one [mood: shy]\nLine two"))
        assertEquals("Keeps [brackets] alone", MoodTags.strip("Keeps [brackets] alone"))
    }

    @Test
    fun hidesATagThatIsStillStreamingIn() {
        assertEquals("Hello there.", MoodTags.stripStreaming("Hello there. ["))
        assertEquals("Hello there.", MoodTags.stripStreaming("Hello there. [mo"))
        assertEquals("Hello there.", MoodTags.stripStreaming("Hello there. [mood: cur"))
        assertEquals("Hello there.", MoodTags.stripStreaming("Hello there. [mood: curious]"))
        assertEquals("A list [1", MoodTags.stripStreaming("A list [1"))
        assertEquals("[note] then", MoodTags.stripStreaming("[note] then"))
    }

    @Test
    fun displayTextKeepsStoredTextIntact() {
        val message = ChatMessage(
            id = "m",
            conversationId = "c",
            role = MessageRole.ASSISTANT,
            parts = listOf(ContentPart.Text("Morning! [mood: bright]")),
            createdAt = 0,
        )
        assertEquals("Morning! [mood: bright]", message.text)
        assertEquals("Morning!", message.displayText)
    }
}
