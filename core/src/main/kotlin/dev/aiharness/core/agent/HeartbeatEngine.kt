package dev.aiharness.core.agent

import dev.aiharness.core.Harness
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatSettings
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.util.humanizeDuration
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import java.time.Instant

sealed interface HeartbeatOutcome {
    val personaId: String

    data class Sent(val conversation: Conversation, val persona: Persona, val message: ChatMessage) : HeartbeatOutcome {
        override val personaId get() = persona.id
    }

    data class Silent(override val personaId: String, val conversationId: String) : HeartbeatOutcome
    data class Skipped(override val personaId: String, val reason: String) : HeartbeatOutcome
    data class Failed(override val personaId: String, val error: String) : HeartbeatOutcome
}

/**
 * The "persistent" half of the agent. On each wakeup (scheduled by the host app), for every persona
 * with heartbeats enabled it:
 *  1. does memory upkeep: folds new conversation into episode memories, backfills embeddings;
 *  2. if proactive messaging is allowed right now, gives the persona a private `<heartbeat>` turn in
 *     its latest conversation, with its memory tools. The persona may reflect and save memories, then
 *     either writes a message to the user or replies `[SILENT]`. Silent turns are discarded, so they
 *     never enter the conversation history.
 */
class HeartbeatEngine(private val h: Harness) {

    suspend fun run(): List<HeartbeatOutcome> {
        val settings = h.settings.current()
        if (settings.heartbeat.mode != HeartbeatMode.PROACTIVE) return emptyList()
        val outcomes = mutableListOf<HeartbeatOutcome>()
        val conversations = h.conversations.getAll().filter { !it.archived }
        h.personas.getAll().filter { it.heartbeatEnabled }.forEach { persona ->
            val conversation = conversations.filter { it.personaId == persona.id }.maxByOrNull { it.updatedAt }
            if (conversation == null) {
                outcomes += HeartbeatOutcome.Skipped(persona.id, "no conversation")
                return@forEach
            }
            outcomes += try {
                beat(persona, conversation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                h.log("Heartbeat failed for ${persona.name}: ${e.message}")
                HeartbeatOutcome.Failed(persona.id, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return outcomes
    }

    private suspend fun beat(persona: Persona, initial: Conversation): HeartbeatOutcome {
        val mutex = h.locks.mutex(initial.id)
        if (!mutex.tryLock()) return HeartbeatOutcome.Skipped(persona.id, "conversation busy")
        try {
            var setup = h.resolveTurn(initial.id)
            val history = h.conversations.getMessages(initial.id)

            // 1. Memory upkeep.
            setup = setup.copy(conversation = recordEpisodes(setup, history))
            runCatching { h.memory.backfillEmbeddings(persona.id) }.onFailure { h.log("Embedding backfill failed: ${it.message}") }

            // 2. Proactive turn, if allowed.
            val reason = blockedReason(setup.settings.heartbeat, history)
            if (reason != null) return HeartbeatOutcome.Skipped(persona.id, reason)

            val now = h.clock.nowMillis()
            val userName = setup.userPersona?.name ?: "the user"
            val lastUserMessage = history.lastOrNull { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }
            val unanswered = unansweredProactive(history)
            val note = buildString {
                append("<heartbeat>\n")
                append("This is a private heartbeat moment, not a message from $userName.")
                if (lastUserMessage != null) {
                    append(" $userName last wrote ${humanizeDuration(now - lastUserMessage.createdAt)} ago.")
                }
                if (unanswered > 0) append(" You have sent $unanswered message(s) since then without a reply.")
                append("\nTake a moment to reflect")
                if ("memory_save" in h.tools.available(setup.settings).map { it.spec.name }) {
                    append("; you may save reflections or tidy your memories with your memory tools")
                }
                append(".\nThen decide whether to message $userName now. Only reach out for a genuine reason: following up on ")
                append("something they mentioned, sharing a thought, or checking in after a long silence. They may be busy.\n")
                append("If you decide to write, reply with only the message itself, addressed to $userName. ")
                append("Otherwise reply with exactly $SILENT.\n")
                append("</heartbeat>")
            }
            val turnContext = h.turnContext.build(
                persona = setup.persona,
                userPersona = setup.userPersona,
                settings = setup.settings,
                history = history,
                query = history.takeLast(6).joinToString(" ") { it.text }.takeLast(800),
                epoch = setup.conversation.context,
            )
            val heartbeatMessage = ChatMessage(
                id = newId(),
                conversationId = initial.id,
                role = MessageRole.USER,
                parts = listOf(ContentPart.Text(note)),
                createdAt = now,
                origin = MessageOrigin.HEARTBEAT,
                turnContext = turnContext,
                hidden = true,
            )

            val buffered = mutableListOf<ChatMessage>()
            val outcome = h.agent.runTurn(
                setup = setup,
                baseHistory = history + heartbeatMessage,
                sink = { buffered += it },
                origin = MessageOrigin.HEARTBEAT,
                background = true,
                // Compaction waits for the next real turn so a discarded heartbeat never moves the window.
                allowCompaction = false,
                emit = {},
            )
            val text = outcome.finalText.trim()
            if (outcome.refused || text.isEmpty() || text.contains(SILENT)) {
                return HeartbeatOutcome.Silent(persona.id, initial.id)
            }

            h.conversations.appendMessage(heartbeatMessage)
            buffered.forEach { h.conversations.appendMessage(it) }
            val latest = h.conversations.get(initial.id) ?: setup.conversation
            h.conversations.upsert(latest.copy(updatedAt = h.clock.nowMillis()))
            val finalMessage = buffered.last { it.role == MessageRole.ASSISTANT }
            return HeartbeatOutcome.Sent(latest, persona, finalMessage)
        } finally {
            mutex.unlock()
        }
    }

    internal fun blockedReason(settings: HeartbeatSettings, history: List<ChatMessage>): String? {
        val now = h.clock.nowMillis()
        if (inQuietHours(settings, now)) return "quiet hours"
        val lastVisible = history.lastOrNull { !it.hidden } ?: return "empty conversation"
        if (now - lastVisible.createdAt < settings.minSilenceMinutes * 60_000L) return "conversation is recent"
        val dayAgo = now - 86_400_000L
        val sentToday = history.count { it.isProactive() && it.createdAt >= dayAgo }
        if (sentToday >= settings.maxProactivePerDay) return "daily proactive limit reached"
        if (unansweredProactive(history) >= settings.maxUnansweredProactive) return "waiting for a reply"
        return null
    }

    private fun unansweredProactive(history: List<ChatMessage>): Int {
        val lastUser = history.indexOfLast { it.role == MessageRole.USER && it.origin == MessageOrigin.USER }
        return history.drop(lastUser + 1).count { it.isProactive() }
    }

    private fun ChatMessage.isProactive() =
        role == MessageRole.ASSISTANT && origin == MessageOrigin.HEARTBEAT && !hidden && text.isNotBlank()

    private fun inQuietHours(settings: HeartbeatSettings, now: Long): Boolean {
        val start = settings.quietHoursStart ?: return false
        val end = settings.quietHoursEnd ?: return false
        if (start == end) return false
        val hour = Instant.ofEpochMilli(now).atZone(h.clock.zone).hour
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    /** Folds conversation since the last episode into an EPISODE memory once enough has accumulated. */
    private suspend fun recordEpisodes(setup: TurnSetup, history: List<ChatMessage>): Conversation {
        val conversation = setup.conversation
        val settings = setup.settings
        if (!settings.memory.enabled) return conversation
        val start = conversation.lastEpisodeMessageId?.let { id -> history.indexOfFirst { it.id == id } + 1 } ?: 0
        val pending = history.drop(start).filter { !it.hidden && !it.excludedFromContext }
        if (pending.size < settings.memory.episodeEveryMessages) return conversation
        val userName = setup.userPersona?.name ?: "User"
        val transcript = ContextBuilder.renderTranscript(pending, setup.persona.name, userName)
        val summary = h.summarizer.summarize(setup.profile, settings, SummaryRequest(setup.persona.name, userName, transcript))
            ?: return conversation
        h.memory.save(
            personaId = setup.persona.id,
            content = "Conversation recap: ${summary.trim()}",
            kind = MemoryKind.EPISODE,
            importance = 6,
            tags = listOf("episode"),
            sourceConversationId = conversation.id,
        )
        val updated = (h.conversations.get(conversation.id) ?: conversation).copy(lastEpisodeMessageId = pending.last().id)
        h.conversations.upsert(updated)
        return updated
    }

    companion object {
        const val SILENT = "[SILENT]"
    }
}
