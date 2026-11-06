package dev.aiharness.core.model

import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmTurnResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PronounsTest {
    private fun sentence(w: Words) = "However many ${w.they} ${w.verb("draws", "draw")}, ${w.they} ${w.be} on ${w.their} own."

    @Test
    fun theAppsSentencesFollowThePersonasPronouns() {
        assertEquals("However many she draws, she is on her own.", sentence(Words("Aria", Pronouns(PronounKind.SHE))))
        assertEquals("However many he draws, he is on his own.", sentence(Words("Kestrel", Pronouns(PronounKind.HE))))
        assertEquals("However many they draw, they are on their own.", sentence(Words("Juno", Pronouns(PronounKind.THEY))))
        assertEquals("However many it draws, it is on its own.", sentence(Words("Unit", Pronouns(PronounKind.IT))))
        assertEquals("However many Wren draws, Wren is on Wren's own.", sentence(Words("Wren", Pronouns(PronounKind.NAME_ONLY))))
        assertEquals("However many Wren draws, Wren is on Wren's own.", sentence(Words("Wren", null)), "not chosen yet: the name")
        val xe = Pronouns(PronounKind.CUSTOM, listOf("xe", "xem", "xyr", "xyrs", "xemself"), customPlural = false)
        assertEquals("However many xe draws, xe is on xyr own.", sentence(Words("Sol", xe)))
        assertEquals("xe/xem", xe.label)
        assertEquals("However many xe draw, xe are on xyr own.", sentence(Words("Sol", xe.copy(customPlural = true))))
        assertEquals("She", Words("Aria", Pronouns(PronounKind.SHE)).theyCapital)
    }

    @Test
    fun thePersonaIsToldItsPronounsOnceChosen() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hello").toList()
        assertFalse("pronouns" in env.provider.requests.last().system, "not chosen: nothing said")

        env.personas.upsert(env.persona.copy(pronouns = Pronouns(PronounKind.SHE)))
        env.provider.then(LlmTurnResult(text = "Hi again"))
        env.harness.agent.send(conversation.id, "Hello again").toList()
        assertContains(env.provider.requests.last().system, "You are Mira, talking with Sam. Your pronouns are she/her.")

        env.personas.upsert(env.persona.copy(pronouns = Pronouns(PronounKind.NAME_ONLY)))
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "And now?").toList()
        assertContains(env.provider.requests.last().system, "You don't use pronouns: others refer to you by your name.")
    }
}
