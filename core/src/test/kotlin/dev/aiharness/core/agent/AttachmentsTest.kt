package dev.aiharness.core.agent

import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Track
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Attachments go with a note, and a model reads each by its stand-in (step 1, before per-provider handling). */
class AttachmentsTest {
    private val mix = ContentPart.Attachment(
        "a1", AttachmentKind.PLAYLIST, title = "rainy day mix", subtitle = "for Mira, from Sam",
        tracks = listOf(Track(title = "October Walk", artist = "Hana Sato", lengthMs = 222_000), Track(title = "Kettle", lengthMs = 180_000)),
        lengthMs = 402_000,
    )
    private val pdf = ContentPart.Attachment("a2", AttachmentKind.FILE, ref = "mem:x.pdf", mimeType = "application/pdf", name = "market-map.pdf", sizeBytes = 2_400_000, pages = 3)
    private val song = ContentPart.Attachment("a3", AttachmentKind.SONG, title = "October Walk", artist = "Hana Sato", lengthMs = 222_000)

    @Test
    fun standInsSayWhatEachIs() {
        assertEquals("[cassette: rainy day mix, for Mira, from Sam, 2 songs, October Walk by Hana Sato; Kettle]", mix.standIn)
        assertEquals("[tag: market-map.pdf, PDF, 3 pages, 2.4 MB]", pdf.standIn)
        assertEquals("[ticket: October Walk by Hana Sato, 3:42]", song.standIn)
    }

    @Test
    fun standInsCarryWhatTheFilesSayOfThemselves() {
        assertEquals(
            "[ticket: October Walk by Hana Sato, from Paper Seasons (2024), 3:42]",
            song.copy(album = "Paper Seasons", year = 2024).standIn,
        )
        assertEquals(
            "[tag: market-map.pdf, \"Night Market Stalls\" by Ana Ruiz, PDF, 3 pages, 2.4 MB]",
            pdf.copy(title = "Night Market Stalls", artist = "Ana Ruiz").standIn,
        )
        // Noon UTC is the same day in every zone from UTC-11 to UTC+11.
        val recorded = java.time.Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
        val film = ContentPart.Attachment(
            "a4", AttachmentKind.VIDEO, name = "VID_20261103.mp4", title = "Lantern walk", artist = "Sam", lengthMs = 227_000, recordedAt = recorded,
            width = 1080, height = 1920, hasSound = true,
        )
        assertEquals("[film: Lantern walk by Sam, VID_20261103.mp4, 3:47, portrait, with sound, recorded 3 Nov 2026]", film.standIn)
        // With no title of its own, its file's name; a picker's number is still a name, and better than nothing.
        assertEquals("[film: 1000004321.mp4, 0:12, landscape, silent]", ContentPart.Attachment("a5", AttachmentKind.VIDEO, name = "1000004321.mp4", lengthMs = 12_000, width = 1920, height = 1080, hasSound = false).standIn)
    }

    @Test
    fun aNoteCarriesItsAttachmentsToTheModel() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "I love the label."))
        env.harness.agent.send(conversation.id, "I made you something.", attachments = listOf(mix, pdf)).toList()

        val note = env.conversations.getMessages(conversation.id).first { it.role == MessageRole.USER }
        assertEquals(listOf("a1", "a2"), note.attachments.map { it.id })
        val sent = (env.provider.requests.last().messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }
        assertContains(sent, "I made you something.")
        assertContains(sent, mix.standIn)
        assertContains(sent, pdf.standIn)
    }

    @Test
    fun aNoteCanBeAttachmentsAlone() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Oh!"))
        env.harness.agent.send(conversation.id, "", attachments = listOf(song)).toList()
        val sent = (env.provider.requests.last().messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }
        assertContains(sent, song.standIn)
    }
}
