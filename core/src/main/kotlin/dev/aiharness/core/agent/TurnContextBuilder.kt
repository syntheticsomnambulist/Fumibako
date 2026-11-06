package dev.aiharness.core.agent

import dev.aiharness.core.memory.keptNote
import dev.aiharness.core.memory.MemoryAge
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.memory.MemoryWeights
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContextState
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.stickers.StickerService
import dev.aiharness.core.tools.feelingNote
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.humanizeDuration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Builds the `<context>` block attached to a user turn: current time, elapsed time, memories
 * recalled for this moment, and stickers stuck or peeled since the persona's last turn. It is
 * computed once when the turn is sent and stored on the message, so replays are byte-identical.
 */
class TurnContextBuilder(
    private val memory: MemoryService,
    private val clock: Clock,
    private val stickers: StickerService? = null,
    private val planner: dev.aiharness.core.planner.Planner? = null,
) {
    private val timeFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

    suspend fun build(
        persona: Persona,
        userPersona: UserPersona?,
        settings: AppSettings,
        history: List<ChatMessage>,
        query: String,
        epoch: ContextState?,
        conversationId: String? = null,
        /** The note this context goes with, when it's a note being sent ("this note" in sticker notes). */
        currentMessageId: String? = null,
    ): String? {
        val lines = mutableListOf<String>()
        val now = clock.nowMillis()

        if (settings.heartbeat.mode != HeartbeatMode.OFF) {
            lines += "Current time: ${timeFormat.format(Instant.ofEpochMilli(now).atZone(clock.zone))}."
            val last = history.lastOrNull { !it.hidden && !it.excludedFromContext }
            if (last != null) {
                val gap = now - last.createdAt
                if (gap >= settings.heartbeat.timeGapNoteMinutes * 60_000L) {
                    lines += "Time since the previous message: ${humanizeDuration(gap)}."
                }
            }
        }

        // Out of its usual clothes: it should know, for how it describes itself and its pictures.
        persona.outfit?.takeIf { it.isNotBlank() }?.let { lines += "You're wearing: $it (not your usual clothes)." }

        // What's on in your planner, when the two of you share it (here, not the system prompt, so the cache holds).
        planner?.contextFor(persona, userPersona, settings)?.let { lines += it }

        if (stickers != null && conversationId != null) {
            val lastTurn = history.lastOrNull { it.role == MessageRole.ASSISTANT }?.createdAt ?: 0L
            val userName = userPersona?.name?.takeIf { it.isNotBlank() } ?: "The user"
            lines += stickers.notesSince(conversationId, lastTurn, history, currentMessageId, userName)
            if (persona.stickers.sticks) {
                // Stickers added since this epoch's system prompt named them.
                val fresh = stickers.newSince(persona.id, maxOf(epoch?.startedAt ?: Long.MAX_VALUE, lastTurn))
                if (fresh.isNotEmpty()) lines += "New stickers you can use: " + fresh.joinToString(", ") { ":${it.name}:" + (it.feeling?.let { f -> " ($f)" } ?: "") } + "."
            }
        }

        if (settings.memory.enabled) {
            val core = memory.core(persona.id, userPersona?.id)
            // Core memories created after this epoch's system prompt was frozen aren't in it yet.
            val sinceEpoch = epoch?.startedAt ?: Long.MAX_VALUE
            val previousUserTurn = history.lastOrNull { it.role == MessageRole.USER }?.createdAt ?: 0L
            val freshCore = core.filter { it.updatedAt > maxOf(sinceEpoch, previousUserTurn) }
            if (freshCore.isNotEmpty()) {
                lines += "Recently updated core memories:"
                freshCore.forEach { lines += "- ${it.content}${it.keptNote}" }
            }

            val lastAssistant = history.lastOrNull { it.role == MessageRole.ASSISTANT && !it.hidden }?.text.orEmpty()
            val recalled = memory.retrieve(
                personaId = persona.id,
                userPersonaId = userPersona?.id,
                query = (query + " " + lastAssistant.takeLast(400)).trim(),
                topK = settings.memory.retrievalTopK,
                minRelevance = settings.memory.minRelevance,
                excludeIds = core.map { it.id }.toSet(),
            )
            if (recalled.isNotEmpty()) {
                lines += "Memories that come to mind:"
                recalled.forEach { scored ->
                    val entry = scored.entry
                    val notes = buildList {
                        add(dateFormat.format(Instant.ofEpochMilli(entry.createdAt).atZone(clock.zone)))
                        feelingNote(entry)?.let { add(it) }
                        if (MemoryWeights.age(entry, now, memory.feelingOf(entry)) == MemoryAge.FADING) add("a little hazy")
                    }
                    lines += "- (${notes.joinToString(", ")}) ${entry.content}${entry.keptNote}"
                }
            }
        }

        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    companion object {
        fun wrap(context: String): String = "<context>\n$context\n</context>"
    }
}
