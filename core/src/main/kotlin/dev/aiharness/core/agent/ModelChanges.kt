package dev.aiharness.core.agent

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.WrittenWith

/**
 * When a persona's model changes: it's told in its next letter's context, and the thread shows a
 * change-of-address notice before the first letter written with the new one. Only its replies to
 * your notes count, since a persona can write its heartbeat letters with a model of their own.
 */
object ModelChanges {
    /** The model of its latest reply to you that recorded one. */
    fun lastWrittenWith(history: List<ChatMessage>): WrittenWith? =
        history.lastOrNull { it.isReplyWithModel() }?.writtenWith

    /** The line for its context when [profile] isn't the model it last wrote with; null when it's the same. */
    fun contextLine(history: List<ChatMessage>, profile: ModelProfile): String? {
        val was = lastWrittenWith(history) ?: return null
        val now = WrittenWith.of(profile)
        if (was.sameModel(now)) return null
        val (before, after) = names(was, now)
        return "Your model changed: you were $before until now and are $after from here. Your memories and letters are the same."
    }

    /** A notice in the thread: from [messageId] on, the persona writes with [now] (it was [was]). */
    data class Notice(val messageId: String, val was: WrittenWith, val now: WrittenWith)

    /** Every change in a thread, at the first reply written with the new model. */
    fun notices(messages: List<ChatMessage>): List<Notice> {
        var last: WrittenWith? = null
        return messages.filter { it.isReplyWithModel() }.mapNotNull { message ->
            val now = message.writtenWith!!
            val was = last
            last = now
            if (was != null && !was.sameModel(now)) Notice(message.id, was, now) else null
        }
    }

    /** What to call the two: the profiles' names, or the models' when the names are the same. */
    fun names(was: WrittenWith, now: WrittenWith): Pair<String, String> =
        if (was.name != now.name) was.name to now.name else was.modelId to now.modelId

    private fun ChatMessage.isReplyWithModel() =
        role == MessageRole.ASSISTANT && origin == MessageOrigin.AGENT && writtenWith != null && !excludedFromContext
}
