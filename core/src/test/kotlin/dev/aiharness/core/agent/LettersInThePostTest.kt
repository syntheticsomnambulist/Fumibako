package dev.aiharness.core.agent

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.LetterSettings
import dev.aiharness.core.model.LetterTravel
import dev.aiharness.core.model.MessageRole
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Letters in the post: a reply is written at once and reaches you a little later. */
class LettersInThePostTest {
    private suspend fun env(travel: LetterTravel = LetterTravel.MINUTES): Pair<TestEnv, ScriptedProvider> {
        val provider = ScriptedProvider()
        val env = TestEnv(provider).apply { seed() }
        env.settingsRepo.update { it.copy(letters = LetterSettings(travel = travel)) }
        return env to provider
    }

    @Test
    fun aReplyIsWrittenNowAndArrivesInAFewMinutes() = runTest {
        val (env, provider) = env()
        val conversation = env.harness.createConversation(env.persona.id)
        env.clock.advanceMinutes(1)
        val sent = env.clock.millis
        provider.then(LlmTurnResult(text = "A fern! Where did you find it?"))
        env.harness.agent.send(conversation.id, "Look what I found").toList()

        val reply = env.conversations.getMessages(conversation.id).last()
        assertEquals(MessageRole.ASSISTANT, reply.role)
        val travel = reply.deliverAt!! - sent
        assertTrue(travel in 2 * 60_000L..3 * 60_000L, "took ${travel / 1000} s")
        assertFalse(reply.arrived(env.clock.millis))
        assertTrue(reply.arrived(reply.deliverAt!!))

        // The letters list: on its way, not unread, and reading the thread meanwhile doesn't count.
        var c = env.conversations.get(conversation.id)!!
        assertEquals(reply.deliverAt, c.lastAgentMessageAt)
        assertTrue(c.onItsWay(env.clock.millis))
        assertFalse(c.unreadAt(env.clock.millis))
        env.harness.markRead(conversation.id)
        env.clock.advanceMinutes(4)
        c = env.conversations.get(conversation.id)!!
        assertFalse(c.onItsWay(env.clock.millis))
        assertTrue(c.unreadAt(env.clock.millis), "arrived and not yet read")
    }

    @Test
    fun aPersonasOwnTravelTimeComesFirst() = runTest {
        val (env, provider) = env()
        env.personas.upsert(env.persona.copy(letterTravel = LetterTravel.DAY))
        val conversation = env.harness.createConversation(env.persona.id)
        val sent = env.clock.millis
        provider.then(LlmTurnResult(text = "Your letter came all this way."))
        env.harness.agent.send(conversation.id, "Hello from far away").toList()
        val travel = env.conversations.getMessages(conversation.id).last().deliverAt!! - sent
        assertTrue(travel in 20 * 3_600_000L..28 * 3_600_000L, "took ${travel / 3_600_000} h")
    }

    @Test
    fun aSecondReplyNeverArrivesBeforeTheFirst() = runTest {
        val (env, provider) = env(LetterTravel.HOURS)
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "First."))
        env.harness.agent.send(conversation.id, "One").toList()
        val first = env.conversations.getMessages(conversation.id).last().deliverAt!!
        // Persona travel shortened meanwhile: the second still comes after the first.
        env.personas.upsert(env.persona.copy(letterTravel = LetterTravel.MINUTES))
        env.clock.advanceMinutes(1)
        provider.then(LlmTurnResult(text = "Second."))
        env.harness.agent.send(conversation.id, "Two").toList()
        val second = env.conversations.getMessages(conversation.id).last().deliverAt!!
        assertTrue(second > first)
    }

    @Test
    fun straightAwayAndHeartbeatLettersArentHeld() = runTest {
        val (env, provider) = env(LetterTravel.STRAIGHT_AWAY)
        val conversation = env.harness.createConversation(env.persona.id)
        provider.then(LlmTurnResult(text = "Right here."))
        env.harness.agent.send(conversation.id, "Hi").toList()
        assertNull(env.conversations.getMessages(conversation.id).last().deliverAt)

        // A letter it writes on its own comes when it comes, whatever the travel time.
        env.settingsRepo.update { it.copy(letters = LetterSettings(travel = LetterTravel.DAYS)) }
        env.harness.markRead(conversation.id)
        env.clock.advanceMinutes(240)
        provider.then(LlmTurnResult(text = "Thinking of you."))
        val outcome = env.harness.heartbeat.run().single()
        assertTrue(outcome is dev.aiharness.core.agent.HeartbeatOutcome.Sent, "got $outcome")
        assertNull(outcome.message.deliverAt)
    }
}
