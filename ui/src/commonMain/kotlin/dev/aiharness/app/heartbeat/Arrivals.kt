package dev.aiharness.app.heartbeat

import dev.aiharness.core.Harness
import dev.aiharness.core.agent.HeartbeatOutcome
import dev.aiharness.core.model.MessageRole
import kotlinx.serialization.Serializable

/** A reply in the post: the letters it's in, and when it arrives (kept by a computer until then). */
@Serializable
data class OnItsWay(val conversationId: String, val at: Long)

/**
 * Letters in the post, the same on the phone and the computer: a reply that reaches you gets its
 * notification then, as a heartbeat letter's (held the same way while your planner says you're
 * busy). Not when you've read it in the app as it arrived.
 */
object Arrivals {
    /** The reply that arrived [at] in [conversationId], to tell as a heartbeat letter; null once it's been read (or it's gone). */
    suspend fun letter(harness: Harness, conversationId: String, at: Long): HeartbeatOutcome.Sent? {
        val conversation = harness.conversations.get(conversationId) ?: return null
        if ((conversation.lastReadAt ?: 0L) >= at) return null
        val persona = harness.personas.get(conversation.personaId) ?: return null
        val reply = harness.conversations.getMessages(conversationId)
            .lastOrNull { it.deliverAt == at && it.role == MessageRole.ASSISTANT && !it.hidden && it.displayText.isNotBlank() }
            ?: return null
        return HeartbeatOutcome.Sent(conversation, persona, reply)
    }
}
