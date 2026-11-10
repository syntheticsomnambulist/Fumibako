package dev.aiharness.core.letters

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LetterSearchTest {
    private val env = TestEnv()
    private var made = 0L

    private suspend fun thread(personaId: String = env.persona.id, id: String = "c-$personaId"): Conversation {
        val conversation = Conversation(id, personaId, userPersonaId = env.user.id, title = "", createdAt = 1, updatedAt = 1)
        env.conversations.upsert(conversation)
        return conversation
    }

    private suspend fun letter(
        conversation: Conversation,
        role: MessageRole,
        text: String,
        vararg pictures: ContentPart.Image,
        hidden: Boolean = false,
        deliverAt: Long? = null,
    ): ChatMessage {
        made += 60_000
        val message = ChatMessage(
            id = "m${made / 60_000}",
            conversationId = conversation.id,
            role = role,
            parts = listOfNotNull(text.takeIf { it.isNotEmpty() }?.let { ContentPart.Text(it) }) + pictures,
            createdAt = env.clock.millis - 10_000_000 + made,
            hidden = hidden,
            deliverAt = deliverAt,
        )
        env.conversations.appendMessage(message)
        return message
    }

    @Test
    fun wordsAreFoldedAndMatchTheirStart() {
        assertEquals(listOf("cats", "rain", "cafe"), LetterWords.terms("Cats' RAIN! café cats"))
        val text = "Two cats sat in the Café by the concatenated rain."
        assertEquals(listOf("cats", "Café"), LetterWords.matches(text, listOf("cat", "cafe")).map { text.substring(it) })
        assertEquals(setOf("cat"), LetterWords.termsIn(text, listOf("cat", "dog")))
        assertEquals("cat* rain*", LetterWords.matchQuery(listOf("cat", "rain"), matchAll = true))
        assertEquals("cat* OR rain*", LetterWords.matchQuery(listOf("cat", "rain"), matchAll = false))
    }

    @Test
    fun aLetterIsSearchedByItsWordsAndPicturesButNotItsThoughtsOrMood() {
        val message = ChatMessage(
            "m", "c", MessageRole.ASSISTANT,
            listOf(
                ContentPart.Reasoning("I wonder about the lighthouse"),
                ContentPart.Text("Look at this! [mood: giddy]"),
                ContentPart.Image("blob:1", caption = "the harbor", description = "A red lighthouse at dusk."),
            ),
            createdAt = 1,
        )
        assertEquals("Look at this!\nthe harbor\nA red lighthouse at dusk.", LetterWords.of(message))
        assertEquals(emptyList(), LetterWords.pieces(message.copy(hidden = true)))
    }

    @Test
    fun yourSearchFindsEveryWordNewestFirst() = runTest {
        val mira = thread()
        val first = letter(mira, MessageRole.USER, "My cat knocked the plant over again.")
        letter(mira, MessageRole.ASSISTANT, "A cat with opinions! Does the plant survive?")
        val third = letter(mira, MessageRole.ASSISTANT, "Rain all day here. The cats are sulking.")
        letter(mira, MessageRole.USER, "No pets in this one.")

        val hits = env.harness.letterSearch.search("cat plant")
        assertEquals(listOf("m2", first.id), hits.map { it.message.id }, "both words, newest first")
        assertEquals(listOf(third.id, "m2", first.id), env.harness.letterSearch.search("cat").map { it.message.id })
        assertTrue(hits.last().byYou)
        assertFalse(hits.first().byYou)
        assertEquals(listOf("cat", "plant"), hits.last().marks.map { hits.last().passage.substring(it) })
    }

    @Test
    fun kindsAndPersonasNarrowIt() = runTest {
        val mira = thread()
        val juno = thread(personaId = "p2")
        letter(mira, MessageRole.USER, "The lighthouse was closed.")
        letter(mira, MessageRole.ASSISTANT, "Next time, the lighthouse!")
        letter(mira, MessageRole.ASSISTANT, "", ContentPart.Image("blob:1", caption = "lighthouse sketch"))
        letter(juno, MessageRole.ASSISTANT, "Juno's lighthouse story.")

        val search = env.harness.letterSearch
        assertEquals(4, search.search("lighthouse").size)
        assertEquals(3, search.search("lighthouse", personaId = env.persona.id).size)
        assertEquals(listOf("The lighthouse was closed."), search.search("lighthouse", kind = LetterKind.YOURS).map { it.passage })
        assertEquals(3, search.search("lighthouse", kind = LetterKind.THEIRS).size)
        val pictures = search.search("lighthouse", kind = LetterKind.PICTURES)
        assertEquals(listOf("lighthouse sketch"), pictures.map { it.passage })
        assertTrue(pictures.single().inPicture)
    }

    @Test
    fun hiddenLettersAndLettersStillInThePostAreNotFound() = runTest {
        val mira = thread()
        letter(mira, MessageRole.USER, "Write about the meteor shower.", hidden = true)
        letter(mira, MessageRole.ASSISTANT, "The meteor shower was lovely.", deliverAt = env.clock.millis + 60_000)
        assertEquals(emptyList(), env.harness.letterSearch.search("meteor"))
        env.clock.advanceMinutes(2)
        assertEquals(1, env.harness.letterSearch.search("meteor").size, "once it arrives, it can be found")
    }

    @Test
    fun aPersonaFindsItsOwnLettersWithMoreOfTheWordsFirst() = runTest {
        val mira = thread()
        val juno = thread(personaId = "p2")
        letter(mira, MessageRole.USER, "We should go back to the ramen place.")
        letter(mira, MessageRole.USER, "Rain again, so ramen again: the place by the station.")
        letter(mira, MessageRole.ASSISTANT, "I still think about that ramen.")
        letter(juno, MessageRole.USER, "Juno, the ramen place by the station closed.")

        val search = env.harness.letterSearch
        val found = search.lookUp(env.persona.id, "the ramen place by the station")
        assertEquals(listOf("m2", "m1", "m3"), found.map { it.message.id }, "more of the words first, then newer; never Juno's")
        assertEquals(listOf("m3"), search.lookUp(env.persona.id, "ramen", from = LetterFrom.ME).map { it.message.id })
        assertEquals(listOf("m2", "m1"), search.lookUp(env.persona.id, "ramen", from = LetterFrom.YOU).map { it.message.id })
    }

    @Test
    fun passagesAreTheSentencesAroundTheMatch() {
        val text = "First, the weather. It rained. Then we found the kinako stall! It was closed. We went home. The end."
        val match = Regex("kinako").find(text)!!.range
        assertEquals("…It rained. Then we found the kinako stall! It was closed.…", Passages.around(text, match))
        assertEquals("Only kinako.", Passages.around("Only kinako.", 5..10))
        val long = "word ".repeat(200) + "kinako " + "word ".repeat(200)
        val passage = Passages.around(long, long.indexOf("kinako").let { it until it + 6 }, maxLength = 60)
        assertTrue(passage.startsWith("…word") && passage.endsWith("word…") && "kinako" in passage && passage.length <= 62, passage)
    }

    @Test
    fun thePersonasToolReturnsPassagesAndKeepsWhatItFound() = runTest {
        val provider = ScriptedProvider()
            .then(LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall("t1", "letter_search", """{"query":"kinako"}""")), stopReason = StopReason.TOOL_USE))
            .then(LlmTurnResult(text = "You said the kinako stall was closed!"))
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.conversations.appendMessage(
            ChatMessage("old", conversation.id, MessageRole.USER, listOf(ContentPart.Text("The kinako stall was closed today.")), createdAt = env.clock.millis - 86_400_000),
        )

        env.harness.agent.send(conversation.id, "Remember that kinako stall?").toList()

        val result = env.conversations.getMessages(conversation.id).flatMap { it.toolResults }.single()
        assertEquals(listOf("old"), result.foundLetters.map { it.messageId }, "not the note it's answering")
        assertTrue(result.foundLetters.single().byYou)
        assertEquals("The kinako stall was closed today.", result.foundLetters.single().passage)
        assertContains(result.content, "Sam's note")
        assertContains(result.content, "\"The kinako stall was closed today.\"")
    }

    @Test
    fun theSwitchTakesTheToolAway() = runTest {
        val env = TestEnv().apply { seed() }
        suspend fun tools() = env.harness.tools.available(env.settingsRepo.current(), env.persona).map { it.spec.name }
        assertTrue("letter_search" in tools())
        env.settingsRepo.update { it.copy(memory = it.memory.copy(letterSearch = false)) }
        assertFalse("letter_search" in tools())
    }
}
