package dev.aiharness.core.agent

import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmToolResult
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContextState
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId
import dev.aiharness.core.util.sha256Hex
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.format.DateTimeFormatter

data class SummaryRequest(
    val personaName: String,
    val userName: String,
    val transcript: String,
    /** True to condense an existing summary rather than summarize new messages. */
    val condense: Boolean = false,
)

data class ContextInput(
    val conversation: Conversation,
    val persona: Persona,
    val userPersona: UserPersona?,
    val settings: AppSettings,
    val profile: ModelProfile,
    val tools: List<ToolSpec>,
    /** Full conversation history in order, including the newest user turn. */
    val history: List<ChatMessage>,
    /** Compaction only happens at the start of a turn, never between tool rounds. */
    val allowCompaction: Boolean,
    val summarize: suspend (SummaryRequest) -> String?,
)

data class PreparedContext(
    val state: ContextState,
    val messages: List<LlmMessage>,
    /** Set when compaction recorded an episode memory through this message. */
    val episodeThroughMessageId: String? = null,
    val compacted: Boolean = false,
)

/**
 * Turns persisted chat history into a provider request while keeping the history append-only.
 *
 * Within one context epoch the system prompt and tools are frozen and earlier turns are never
 * rewritten, so provider replay data (Claude thinking-block signatures, Gemini thought signatures)
 * remains valid and prompt caches keep hitting. A new epoch starts when:
 *  - the persona, user persona, base instructions or heartbeat mode change (config fingerprint),
 *  - the tool set or tool mode changes (tool fingerprint),
 *  - history outgrows the budget and is compacted into a summary.
 * Messages from earlier epochs are sent as plain text/tool calls without replay data, which is the
 * documented recovery for providers that bind reasoning to the exact conversation prefix.
 */
class ContextBuilder(
    private val prompts: PromptAssembler,
    private val memory: MemoryService,
    private val blobs: BlobStore,
    private val clock: Clock,
    private val log: (String) -> Unit = {},
) {
    suspend fun prepare(input: ContextInput): PreparedContext {
        val previous = input.conversation.context
        val userName = input.userPersona?.name?.takeIf { it.isNotBlank() } ?: "User"
        val configFp = configFingerprint(input)
        val toolFp = toolFingerprint(input)
        val eligible = input.history.filter { !it.excludedFromContext }

        var windowStart = previous?.windowStartMessageId?.let { id -> eligible.indexOfFirst { it.id == id } } ?: 0
        var needNewEpoch = previous == null || previous.configFingerprint != configFp || previous.toolFingerprint != toolFp
        if (windowStart < 0) {
            windowStart = 0
            needNewEpoch = true
        }
        var summary = previous?.summary
        var episodeThrough: String? = null
        var compacted = false

        if (input.allowCompaction) {
            val window = eligible.subList(windowStart, eligible.size)
            val budget = budgetFor(input.profile)
            val systemEstimate = TokenEstimator.estimate(previous?.systemPrompt ?: provisionalPrompt(input, summary))
            val total = systemEstimate + window.sumOf(TokenEstimator::estimate)
            if (total > budget) {
                val keepFrom = chooseCut(window, (budget * TAIL_FRACTION).toInt())
                if (keepFrom > 0) {
                    val chunk = window.subList(0, keepFrom)
                    val chunkSummary = summarizeChunk(input, chunk, userName)
                    summary = mergeSummaries(input, summary, chunkSummary, budget, userName)
                    if (chunkSummary != null && shouldRecordEpisode(input, chunk)) {
                        recordEpisode(input, chunk, chunkSummary)
                        episodeThrough = chunk.last().id
                    }
                    windowStart += keepFrom
                    needNewEpoch = true
                    compacted = true
                }
            }
        }

        val state = if (needNewEpoch) {
            ContextState(
                epochId = newId(),
                systemPrompt = prompts.buildSystemPrompt(
                    persona = input.persona,
                    userPersona = input.userPersona,
                    settings = input.settings,
                    coreMemories = if (input.settings.memory.enabled) memory.core(input.persona.id, input.userPersona?.id) else emptyList(),
                    summary = summary,
                    toolNames = input.tools.map { it.name }.toSet(),
                ),
                configFingerprint = configFp,
                toolFingerprint = toolFp,
                windowStartMessageId = eligible.getOrNull(windowStart)?.id,
                summary = summary,
                startedAt = clock.nowMillis(),
            )
        } else {
            previous!!
        }

        val window = eligible.subList(windowStart.coerceAtMost(eligible.size), eligible.size)
        return PreparedContext(
            state = state,
            messages = toLlmMessages(window, state, input.profile),
            episodeThroughMessageId = episodeThrough,
            compacted = compacted,
        )
    }

    private fun budgetFor(profile: ModelProfile): Int =
        minOf(profile.contextBudgetTokens, profile.contextWindowTokens - profile.maxOutputTokens).coerceAtLeast(MIN_BUDGET)

    /**
     * Picks the first index to keep verbatim: the tail fits in [tailTokens] and starts at a user turn,
     * so a tool round (assistant tool call + results) is never split.
     */
    internal fun chooseCut(window: List<ChatMessage>, tailTokens: Int): Int {
        var acc = 0
        var cut = window.size
        for (j in window.indices.reversed()) {
            acc += TokenEstimator.estimate(window[j])
            if (acc > tailTokens) break
            cut = j
        }
        val lastUser = window.indexOfLast { it.role == MessageRole.USER }
        val boundary = (cut until window.size).firstOrNull { window[it].role == MessageRole.USER } ?: lastUser
        return boundary.coerceAtLeast(0)
    }

    private suspend fun summarizeChunk(input: ContextInput, chunk: List<ChatMessage>, userName: String): String? =
        safeSummarize(input, SummaryRequest(input.persona.name, userName, renderTranscript(chunk, input.persona.name, userName)))

    private suspend fun mergeSummaries(
        input: ContextInput,
        previous: String?,
        chunk: String?,
        budget: Int,
        userName: String,
    ): String? {
        val merged = listOfNotNull(previous?.takeIf { it.isNotBlank() }, chunk?.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
            .ifBlank { return previous }
        if (TokenEstimator.estimate(merged) <= budget * SUMMARY_FRACTION) return merged
        return safeSummarize(input, SummaryRequest(input.persona.name, userName, merged, condense = true)) ?: merged
    }

    private suspend fun safeSummarize(input: ContextInput, request: SummaryRequest): String? = try {
        input.summarize(request)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Summarization failed: ${e.message}")
        null
    }

    private fun shouldRecordEpisode(input: ContextInput, chunk: List<ChatMessage>): Boolean {
        if (!input.settings.memory.enabled) return false
        val lastEpisodeId = input.conversation.lastEpisodeMessageId ?: return true
        if (chunk.any { it.id == lastEpisodeId }) return false
        val lastEpisode = input.history.firstOrNull { it.id == lastEpisodeId } ?: return true
        return lastEpisode.createdAt < chunk.first().createdAt
    }

    private suspend fun recordEpisode(input: ContextInput, chunk: List<ChatMessage>, summary: String) {
        val from = DAY.format(Instant.ofEpochMilli(chunk.first().createdAt).atZone(clock.zone))
        val to = DAY.format(Instant.ofEpochMilli(chunk.last().createdAt).atZone(clock.zone))
        val span = if (from == to) from else "$from to $to"
        memory.save(
            personaId = input.persona.id,
            content = "Conversation recap ($span): $summary",
            kind = MemoryKind.EPISODE,
            importance = 6,
            tags = listOf("episode"),
            sourceConversationId = input.conversation.id,
        )
    }

    private suspend fun provisionalPrompt(input: ContextInput, summary: String?) = prompts.buildSystemPrompt(
        input.persona, input.userPersona, input.settings, emptyList(), summary, input.tools.map { it.name }.toSet(),
    )

    private suspend fun toLlmMessages(window: List<ChatMessage>, state: ContextState, profile: ModelProfile): List<LlmMessage> {
        val out = mutableListOf<LlmMessage>()
        window.forEachIndexed { index, message ->
            when (message.role) {
                MessageRole.USER -> {
                    val parts = mutableListOf<LlmPart>()
                    message.turnContext?.takeIf { it.isNotBlank() }?.let { parts += LlmPart.Text(TurnContextBuilder.wrap(it)) }
                    if (message.text.isNotBlank()) parts += LlmPart.Text(message.text)
                    message.images.forEach { image ->
                        val bytes = if (profile.supportsVision) blobs.get(image.ref) else null
                        parts += if (bytes != null) {
                            LlmPart.Image(bytes, image.mimeType)
                        } else {
                            LlmPart.Text("[Image${image.caption?.let { ": $it" } ?: ""}]")
                        }
                    }
                    if (parts.isNotEmpty()) out += LlmMessage.User(parts)
                }
                MessageRole.ASSISTANT -> {
                    val replayValid = message.replay != null &&
                        message.epochId == state.epochId &&
                        message.providerType == profile.provider
                    val calls = message.toolCalls.map { LlmToolCall(it.id, it.name, it.argumentsJson) }
                    val imageNotes = message.images.joinToString("\n") { "[Image you sent: ${it.caption.orEmpty()}]" }
                    val text = listOf(message.text, imageNotes).filter { it.isNotBlank() }.joinToString("\n")
                    if (text.isBlank() && calls.isEmpty() && !replayValid) return@forEachIndexed
                    out += LlmMessage.Assistant(text, calls, if (replayValid) message.replay else null)
                    if (calls.isNotEmpty() && window.getOrNull(index + 1)?.role != MessageRole.TOOL) {
                        // Interrupted tool round (e.g. the app was killed): close it so the history stays valid.
                        out += LlmMessage.ToolResults(calls.map { LlmToolResult(it.id, it.name, "The tool call was interrupted.", true) })
                    }
                }
                MessageRole.TOOL -> {
                    val previous = out.lastOrNull() as? LlmMessage.Assistant
                    if (previous == null || previous.toolCalls.isEmpty()) return@forEachIndexed
                    val results = message.toolResults.associateBy { it.callId }
                    out += LlmMessage.ToolResults(
                        previous.toolCalls.map { call ->
                            results[call.id]?.let { LlmToolResult(it.callId, it.name, it.content, it.isError) }
                                ?: LlmToolResult(call.id, call.name, "No result.", true)
                        },
                    )
                }
            }
        }
        if (out.firstOrNull() !is LlmMessage.User) out.add(0, LlmMessage.User(listOf(LlmPart.Text(CONVERSATION_START))))
        return out
    }

    private fun configFingerprint(input: ContextInput) = sha256Hex(
        input.persona.id,
        input.persona.updatedAt.toString(),
        input.userPersona?.id,
        input.userPersona?.updatedAt?.toString(),
        input.settings.baseInstructions,
        input.settings.heartbeat.mode.name,
        input.settings.memory.enabled.toString(),
    )

    private fun toolFingerprint(input: ContextInput) = sha256Hex(
        input.profile.toolMode.name,
        *input.tools.map { "${it.name}\u0001${it.description}\u0001${it.parameters}" }.toTypedArray(),
    )

    companion object {
        const val CONVERSATION_START = "(The conversation begins.)"
        private const val MIN_BUDGET = 2_000
        private const val TAIL_FRACTION = 0.45
        private const val SUMMARY_FRACTION = 0.15
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

        fun renderTranscript(messages: List<ChatMessage>, charName: String, userName: String): String = buildString {
            messages.forEach { m ->
                if (m.hidden) return@forEach
                when (m.role) {
                    MessageRole.USER -> if (m.text.isNotBlank()) append("$userName: ${m.text}\n")
                    MessageRole.ASSISTANT -> {
                        if (m.text.isNotBlank()) append("$charName: ${m.text}\n")
                        m.toolCalls.forEach { append("[$charName used ${it.name}]\n") }
                    }
                    MessageRole.TOOL -> m.images.forEach { append("[$charName showed an image: ${it.caption.orEmpty()}]\n") }
                }
            }
        }.trim()
    }
}

object TokenEstimator {
    fun estimate(text: String): Int = (text.length / 3.6).toInt() + 1

    fun estimate(message: ChatMessage): Int =
        6 + estimate(message.text) +
            (message.turnContext?.let(::estimate) ?: 0) +
            message.toolCalls.sumOf { estimate(it.argumentsJson) + 10 } +
            message.toolResults.sumOf { estimate(it.content) + 10 } +
            message.images.size * 800
}
