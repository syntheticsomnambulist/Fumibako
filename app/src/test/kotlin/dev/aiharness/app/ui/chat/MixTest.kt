package dev.aiharness.app.ui.chat

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Songs added one by one (from the search, or the phone) make a mixtape, as picking several at once does. */
class MixTest {
    private fun song(id: String, title: String, ref: String? = "blob:$id.m4a") = ContentPart.Attachment(
        id, AttachmentKind.SONG, ref = ref, title = title, artist = "Pavement", lengthMs = 200_000,
        url = "https://music.apple.com/us/album/x/1?i=$id", previewOnly = true, pictureRef = "blob:$id.jpg",
    )

    @Test
    fun aSecondSongMakesAMixAndAThirdJoinsIt() {
        val first = song("s1", "Harness Your Hopes")
        val second = song("s2", "Alien Blues")
        val (two, mix) = joinedIntoMix(listOf(Going.Item(first), Going.Item(second)), second, "Pidge", "Ness") { "mix1" }
        val made = mix!!
        assertEquals(listOf("mix1"), two.map { it.key }, "the mix takes the first song's place; the second goes into it")
        assertEquals("A mix for Pidge", made.title)
        assertEquals("for Pidge, from Ness", made.subtitle)
        assertEquals(listOf("Harness Your Hopes", "Alien Blues"), made.tracks.map { it.title })
        assertEquals(400_000, made.lengthMs)
        // A searched song keeps its preview, cover and link on the cassette, and back on a ticket.
        assertEquals(first.copy(id = "t"), made.tracks.first().ticket("t"))

        val third = song("s3", "Alrighty Aphrodite")
        val (three, bigger) = joinedIntoMix(two + Going.Item(third), third, "Pidge", "Ness")
        assertEquals(listOf("mix1"), three.map { it.key })
        assertEquals(3, bigger!!.tracks.size)
    }

    @Test
    fun aLinksTicketIsntMixed() {
        val link = song("l1", "On Spotify", ref = null)
        val searched = song("s1", "Harness Your Hopes")
        val list = listOf(Going.Item(link), Going.Item(searched))
        val (after, mix) = joinedIntoMix(list, searched, "Pidge", "Ness")
        assertNull(mix)
        assertEquals(list, after)
    }
}
