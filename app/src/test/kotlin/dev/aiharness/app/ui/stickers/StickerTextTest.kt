package dev.aiharness.app.ui.stickers

import androidx.compose.ui.graphics.Color
import dev.aiharness.app.ui.STICKER_PREFIX
import dev.aiharness.app.ui.chat.partialStickerName
import dev.aiharness.app.ui.chat.stickerSuggestions
import dev.aiharness.app.ui.formatChatText
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `:name:` in letters: stickers inline, emoji by name, everything else left as written. */
class StickerTextTest {
    @Test
    fun namesBecomeInlineStickersOrEmojiAndTimesStayTimes() {
        val text = formatChatText("Night :aria-sleepy: :tea: at 10:30:45 :nope:", Color.Red, setOf("aria-sleepy"))
        assertEquals("Night :aria-sleepy: 🍵 at 10:30:45 :nope:", text.text)
        val inline = text.getStringAnnotations(0, text.length).filter { it.tag.startsWith("androidx.compose.foundation.text.inlineContent") }
        assertEquals(listOf(STICKER_PREFIX + "aria-sleepy"), inline.map { it.item })
    }

    @Test
    fun aHalfTypedNameIsFinishedFromStickersThenEmoji() {
        assertEquals("ari", partialStickerName("Night, :ari"))
        assertNull(partialStickerName("Night, :a"), "two letters at least")
        assertNull(partialStickerName("Night, :aria: ok"))
        val art = listOf(StickerArt("a1", "aria-wave", "p1", imageRef = "r", createdAt = 1), StickerArt("a2", "mochi", imageRef = "r", createdAt = 2))
        assertEquals(listOf("aria-wave"), stickerSuggestions("ari", art).map { it.first })
        val tea = stickerSuggestions("tea", art)
        assertEquals("tea", tea.first().first)
        assertEquals(StickerRef.Emoji("🍵", "tea"), tea.first().second)
    }
}
