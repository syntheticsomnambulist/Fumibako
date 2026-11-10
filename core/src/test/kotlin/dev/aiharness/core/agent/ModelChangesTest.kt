package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.WrittenWith
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ModelChangesTest {
    private fun LlmRequest.context(): String =
        (messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().firstOrNull { it.text.startsWith("<context>") }?.text.orEmpty()

    private val opus = WrittenWith("Claude Opus 5.5", ProviderType.ANTHROPIC, "claude-opus-5-5")
    private val sonnet = WrittenWith("Claude Sonnet 5.5", ProviderType.ANTHROPIC, "claude-sonnet-5-5")

    @Test
    fun aPersonaIsToldOnceWhenItsModelChanges() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val other = env.profile.copy(id = "m2", name = "Other model", modelId = "other-model")
        env.profiles.upsert(other)
        val conversation = env.harness.createConversation(env.persona.id)

        env.harness.agent.send(conversation.id, "Hello").toList()
        val first = env.conversations.getMessages(conversation.id).last { it.role == MessageRole.ASSISTANT }
        assertEquals(WrittenWith("Test model", env.profile.provider, "test-model"), first.writtenWith, "a reply records its model")
        assertFalse("Your model changed" in provider.requests.last().context())

        env.personas.upsert(env.persona.copy(defaultModelProfileId = other.id))
        env.harness.agent.send(conversation.id, "Still you?").toList()
        assertContains(
            provider.requests.last().context(),
            "Your model changed: you were Test model until now and are Other model from here. Your memories and letters are the same.",
        )

        env.harness.agent.send(conversation.id, "And now?").toList()
        assertFalse("Your model changed" in provider.requests.last().context(), "told once")
    }

    @Test
    fun theTickKeepsItQuietAndARenamedProfileIsNoChange() = runTest {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "Hello").toList()

        env.profiles.upsert(env.profile.copy(name = "My favorite"))
        env.harness.agent.send(conversation.id, "Renamed").toList()
        assertFalse("Your model changed" in provider.requests.last().context(), "the same model under a new name")

        env.personas.upsert(env.persona.copy(tellModelChanges = false))
        env.profiles.upsert(env.profile.copy(modelId = "newer-model"))
        env.harness.agent.send(conversation.id, "Quietly").toList()
        assertFalse("Your model changed" in provider.requests.last().context(), "not told when the tick is off")
        assertEquals(1, ModelChanges.notices(env.conversations.getMessages(conversation.id)).size, "the thread shows it anyway")
    }

    @Test
    fun theThreadMarksEachChangeAtTheFirstLetterWithTheNewModel() {
        fun reply(id: String, with: WrittenWith?, origin: MessageOrigin = MessageOrigin.AGENT) =
            ChatMessage(id, "c", MessageRole.ASSISTANT, listOf(ContentPart.Text(id)), createdAt = 1, origin = origin, writtenWith = with)
        val note = ChatMessage("n", "c", MessageRole.USER, listOf(ContentPart.Text("hi")), createdAt = 1)
        val thread = listOf(
            reply("old", null), // from before v0.5: no model recorded, nothing to compare
            reply("a", sonnet), note, reply("b", sonnet),
            reply("hb", opus, MessageOrigin.HEARTBEAT), // a heartbeat model of its own doesn't count
            note, reply("c", opus), reply("d", opus), note, reply("e", sonnet),
        )
        assertEquals(
            listOf(ModelChanges.Notice("c", sonnet, opus), ModelChanges.Notice("e", opus, sonnet)),
            ModelChanges.notices(thread),
        )
        assertEquals("Claude Sonnet 5.5" to "Claude Opus 5.5", ModelChanges.names(sonnet, opus))
        assertEquals("a" to "b", ModelChanges.names(sonnet.copy(modelId = "a"), sonnet.copy(modelId = "b")), "same names: the models'")
        assertNull(ModelChanges.lastWrittenWith(listOf(reply("old", null))))
    }
}
