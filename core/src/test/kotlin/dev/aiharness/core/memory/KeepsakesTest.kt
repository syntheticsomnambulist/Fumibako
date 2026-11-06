package dev.aiharness.core.memory

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Track
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v0.3.5 step 7: a memory keeps things from its letter, chosen by the persona. */
class KeepsakesTest {
    private val mix = ContentPart.Attachment(
        "mix1", AttachmentKind.PLAYLIST, title = "rainy day mix", subtitle = "for Mira, from Sam",
        tracks = listOf(Track(title = "October Walk", artist = "Hana Sato")),
    )
    private val song = ContentPart.Attachment("song1", AttachmentKind.SONG, title = "Kettle Song", artist = "The Lanterns", lengthMs = 194_000)
    private val photo = ContentPart.Image("mem:cat.jpg", "image/jpeg", description = "A tabby asleep on a sill.")

    private fun save(id: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall(id, "memory_save", args)), stopReason = StopReason.TOOL_USE)

    @Test
    fun handlesNameWhatThePersonaSaw() {
        assertTrue(KeepsakeFinder.matches("[cassette: rainy day mix]", mix))
        assertTrue(KeepsakeFinder.matches("the cassette", mix))
        assertTrue(KeepsakeFinder.matches("rainy day mix", mix))
        assertFalse(KeepsakeFinder.matches("[ticket: rainy day mix]", mix), "the wrong frame")
        assertTrue(KeepsakeFinder.matches("[ticket: Kettle Song by The Lanterns, 3:14]", song))
        assertTrue(KeepsakeFinder.matches("the photo", photo))
        assertTrue(KeepsakeFinder.matches("[photo: A tabby asleep on a sill.]", photo))
    }

    @Test
    fun aMemoryKeepsWhatItNamesOrTheNotesFirstThing() = runTest {
        val provider = ScriptedProvider()
            .then(save("c1", """{"content":"Sam made me a rainy day mix"}"""))
            .then(save("c2", """{"content":"Kettle Song is Sam's favorite","keep":"[ticket: Kettle Song by The Lanterns, 3:14]"}"""))
            .then(save("c3", """{"content":"Sam likes rain","keep":"nothing"}"""))
            .then(LlmTurnResult(text = "Thank you!"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "For you.", attachments = listOf(mix, song)).toList()

        val memories = env.harness.memory.all(env.persona.id).associateBy { it.content }
        assertEquals(listOf("mix1"), memories.getValue("Sam made me a rainy day mix").keepsakes.map { it.key }, "named nothing: the note's first thing")
        assertEquals(listOf("song1"), memories.getValue("Kettle Song is Sam's favorite").keepsakes.map { it.key })
        assertTrue(memories.getValue("Sam likes rain").keepsakes.isEmpty())
        assertContains(provider.requests[1].messages.last().let { (it as LlmMessage.ToolResults).results.single().content }, "With it: [cassette: rainy day mix")
    }

    @Test
    fun aRecalledMemorySaysWhatWasKeptNotTheFile() = runTest {
        val provider = ScriptedProvider().then(LlmTurnResult(text = "ok"))
        val env = TestEnv(provider).apply { seed() }
        val keepsake = dev.aiharness.core.model.Keepsake("m0", song, keptAt = 0)
        env.harness.memory.save(env.persona.id, "Sam played Kettle Song on the walk home", keepsakes = listOf(keepsake))
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "Remember Kettle Song on the walk home?").toList()

        val context = (provider.requests.single().messages.last() as LlmMessage.User).parts
            .filterIsInstance<LlmPart.Text>().first { it.text.startsWith("<context>") }.text
        assertContains(context, "Sam played Kettle Song on the walk home (kept with [ticket: Kettle Song by The Lanterns, 3:14])")
    }
}
