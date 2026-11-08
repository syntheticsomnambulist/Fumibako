package dev.aiharness.core.agent

import dev.aiharness.core.Harness
import dev.aiharness.core.llm.LlmEvent
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.llm.complete
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.LetterTravel
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.MoodTags
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.tools.SlotKeeper
import dev.aiharness.core.tools.ToolContext
import dev.aiharness.core.tools.ToolOutcome
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

sealed interface AgentEvent {
    data class UserMessageSaved(val message: ChatMessage) : AgentEvent
    data class TextDelta(val text: String) : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent
    data class MessageSaved(val message: ChatMessage) : AgentEvent
    data class ToolStarted(val name: String, val argumentsJson: String) : AgentEvent
    data class ToolFinished(val name: String, val outcome: ToolOutcome) : AgentEvent
    data class Failed(val message: String, val retryable: Boolean) : AgentEvent
    data class Finished(val finalText: String, val refused: Boolean) : AgentEvent
}

/** Everything needed to run a turn, resolved from ids. */
data class TurnSetup(
    val conversation: Conversation,
    val persona: Persona,
    val userPersona: UserPersona?,
    val profile: ModelProfile,
    val apiKey: String?,
    val settings: AppSettings,
)

data class TurnOutcome(val messages: List<ChatMessage>, val finalText: String, val refused: Boolean)

/** Where new messages go: straight to storage (chat) or held back (heartbeat, until it decides to speak). */
fun interface MessageSink {
    suspend fun append(message: ChatMessage)
}

/**
 * The agent loop: prepare context, stream a model turn, run requested tools, repeat until the
 * model answers without tools (or [AppSettings.maxToolRounds] is reached).
 */
class AgentRunner(private val h: Harness) {

    /** Sends a user message and streams the agent's reply. */
    fun send(
        conversationId: String,
        text: String,
        images: List<ContentPart.Image> = emptyList(),
        attachments: List<ContentPart.Attachment> = emptyList(),
    ): Flow<AgentEvent> = channelFlow {
        h.locks.withLock(conversationId) {
            val setup = resolveOrFail(conversationId) { send(it) } ?: return@withLock
            val history = h.conversations.getMessages(conversationId)
            val now = h.clock.nowMillis()
            val messageId = newId()
            // Stickers on the draft go with the note, and the persona hears about them with it.
            h.stickerService.sendDraft(conversationId, messageId)
            val turnContext = h.turnContext.build(
                persona = setup.persona,
                userPersona = setup.userPersona,
                settings = setup.settings,
                history = history,
                query = text,
                epoch = setup.conversation.context,
                conversationId = conversationId,
                currentMessageId = messageId,
            )
            val userMessage = ChatMessage(
                id = messageId,
                conversationId = conversationId,
                role = MessageRole.USER,
                parts = buildList {
                    if (text.isNotBlank()) add(ContentPart.Text(text))
                    addAll(images)
                    addAll(attachments)
                },
                createdAt = now,
                origin = MessageOrigin.USER,
                turnContext = turnContext,
            )
            h.conversations.appendMessage(userMessage)
            val conversation = h.updateConversation(conversationId) { it.copy(updatedAt = now, lastUserMessageAt = now) }
                ?: setup.conversation
            send(AgentEvent.UserMessageSaved(userMessage))
            // What the persona's model can't take in, the utility model may describe before it reads the note.
            val arrived = if (userMessage.images.isEmpty() && userMessage.attachments.isEmpty()) userMessage
            else runCatching { h.describer.onArrival(userMessage, setup.profile, setup.settings) }.getOrDefault(userMessage)

            runGuarded(setup.copy(conversation = conversation), history + arrived, arrivalFor(setup, history)) { send(it) }
        }
    }

    /** Drops the agent's reply to the latest user turn and generates it again. */
    fun regenerate(conversationId: String): Flow<AgentEvent> = channelFlow {
        h.locks.withLock(conversationId) {
            val setup = resolveOrFail(conversationId) { send(it) } ?: return@withLock
            val history = h.conversations.getMessages(conversationId)
            val lastUser = history.indexOfLast { it.role == MessageRole.USER }
            if (lastUser < 0) {
                send(AgentEvent.Failed("Nothing to regenerate yet.", retryable = false))
                return@withLock
            }
            // Removing only the tail keeps the earlier prefix intact, so replay data stays valid.
            h.conversations.deleteMessages(history.drop(lastUser + 1).map { it.id })
            runGuarded(setup, history.take(lastUser + 1), arrivalFor(setup, history.take(lastUser + 1))) { send(it) }
        }
    }

    /** Resolves the turn, reporting setup problems (no model, deleted persona) as a [AgentEvent.Failed]. */
    private suspend fun resolveOrFail(conversationId: String, emit: suspend (AgentEvent) -> Unit): TurnSetup? = try {
        h.resolveTurn(conversationId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emit(AgentEvent.Failed(e.message ?: "Couldn't start the turn", retryable = false))
        null
    }

    /**
     * When a reply written now reaches you (letters in the post): after the persona's travel time,
     * or Settings', and never before a reply already on its way. Null when it comes straight away.
     */
    internal fun arrivalFor(setup: TurnSetup, history: List<ChatMessage>): Long? {
        val travel = setup.persona.letterTravel ?: setup.settings.letters.travel
        if (travel == LetterTravel.STRAIGHT_AWAY) return null
        val now = h.clock.nowMillis()
        val onItsWay = history.mapNotNull { it.deliverAt }.filter { it > now }.maxOrNull()
        return maxOf(now + travel.pick(), (onItsWay ?: 0L) + 1_000L)
    }

    private suspend fun runGuarded(setup: TurnSetup, history: List<ChatMessage>, deliverAt: Long?, emit: suspend (AgentEvent) -> Unit) {
        try {
            val outcome = runTurn(
                setup = setup,
                baseHistory = history,
                sink = { h.conversations.appendMessage(it) },
                origin = MessageOrigin.AGENT,
                background = false,
                allowCompaction = true,
                emit = emit,
                deliverAt = deliverAt,
            )
            emit(AgentEvent.Finished(outcome.finalText, outcome.refused))
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            h.log("Turn failed: ${e.message}")
            emit(AgentEvent.Failed(e.message ?: "Request failed", e.retryable))
        } catch (e: Exception) {
            h.log("Turn failed: $e")
            emit(AgentEvent.Failed(e.message ?: e::class.simpleName.orEmpty(), retryable = false))
        }
    }

    internal suspend fun runTurn(
        setup: TurnSetup,
        baseHistory: List<ChatMessage>,
        sink: MessageSink,
        origin: MessageOrigin,
        background: Boolean,
        allowCompaction: Boolean,
        emit: suspend (AgentEvent) -> Unit,
        slots: SlotKeeper? = null,
        /** A reply in the post: when it reaches you (its messages are kept back till then). */
        deliverAt: Long? = null,
    ): TurnOutcome {
        var conversation = setup.conversation
        val transcript = baseHistory.toMutableList()
        val produced = mutableListOf<ChatMessage>()
        val provider = h.providers.forProfile(setup.profile)
        val specs = if (setup.profile.toolMode == ToolMode.NONE) emptyList() else h.tools.available(setup.settings, setup.persona).map { it.spec }
        val stickerShelf = if (specs.any { it.name == "place_sticker" }) h.stickerService.shelf(setup.persona.id) else null
        var finalText = ""

        for (round in 0 until setup.settings.maxToolRounds.coerceAtLeast(1)) {
            val prepared = h.contextBuilder.prepare(
                ContextInput(
                    conversation = conversation,
                    persona = setup.persona,
                    userPersona = setup.userPersona,
                    settings = setup.settings,
                    profile = setup.profile,
                    tools = specs,
                    stickers = stickerShelf,
                    history = transcript,
                    allowCompaction = allowCompaction && round == 0,
                    summarize = { request -> h.summarizer.summarize(setup.profile, setup.settings, request) },
                ),
            )
            if (prepared.state != conversation.context || prepared.episodeThroughMessageId != null) {
                val update: (Conversation) -> Conversation = {
                    it.copy(context = prepared.state, lastEpisodeMessageId = prepared.episodeThroughMessageId ?: it.lastEpisodeMessageId)
                }
                conversation = h.updateConversation(conversation.id, update) ?: update(conversation)
            }

            val request = LlmRequest(setup.profile, setup.apiKey, prepared.state.systemPrompt, prepared.messages, specs)
            var result: LlmTurnResult? = null
            provider.stream(request).collect { event ->
                when (event) {
                    is LlmEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))
                    is LlmEvent.ReasoningDelta -> emit(AgentEvent.ReasoningDelta(event.text))
                    is LlmEvent.Completed -> result = event.result
                }
            }
            val turn = result ?: throw LlmException("The model stream ended without a result", retryable = true)
            // Pages the provider opened itself (Claude's web fetch): the persona's read stamp lands on their cuttings.
            turn.opened.forEach { url -> runCatching { h.linkReading.markRead(conversation.id, url, setup.persona.id) } }
            val refused = turn.stopReason == StopReason.REFUSAL

            val assistant = ChatMessage(
                id = newId(),
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                parts = buildList {
                    turn.reasoning?.let { add(ContentPart.Reasoning(it)) }
                    if (turn.text.isNotBlank()) add(ContentPart.Text(turn.text))
                    turn.toolCalls.forEach { add(ContentPart.ToolCall(it.id, it.name, it.argumentsJson)) }
                },
                createdAt = h.clock.nowMillis(),
                origin = origin,
                epochId = prepared.state.epochId,
                providerType = setup.profile.provider,
                modelProfileId = setup.profile.id,
                modelId = turn.modelId,
                replay = turn.replay,
                excludedFromContext = refused,
                errorMessage = if (refused) refusalMessage(turn) else null,
                usage = turn.usage,
                mood = MoodTags.parse(turn.text),
                // A refusal is said straight away, not after the wait.
                deliverAt = deliverAt.takeUnless { refused },
            )
            sink.append(assistant)
            transcript += assistant
            produced += assistant
            emit(AgentEvent.MessageSaved(assistant))

            if (refused) return TurnOutcome(produced, "", refused = true)
            finalText = turn.text
            // Don't run tools from a truncated turn: their arguments may be incomplete.
            if (turn.toolCalls.isEmpty() || turn.stopReason == StopReason.MAX_TOKENS) break

            val toolContext = ToolContext(conversation, setup.persona, setup.userPersona, setup.settings, background, messageId = assistant.id, slots = slots)
            val resultParts = mutableListOf<ContentPart>()
            turn.toolCalls.forEach { call ->
                emit(AgentEvent.ToolStarted(call.name, call.argumentsJson))
                val outcome = h.tools.execute(LlmToolCall(call.id, call.name, call.argumentsJson), toolContext)
                resultParts += ContentPart.ToolResult(call.id, call.name, outcome.content, outcome.isError)
                resultParts += outcome.attachments
                emit(AgentEvent.ToolFinished(call.name, outcome))
            }
            val toolMessage = ChatMessage(
                id = newId(),
                conversationId = conversation.id,
                role = MessageRole.TOOL,
                parts = resultParts,
                createdAt = h.clock.nowMillis(),
                origin = origin,
                epochId = prepared.state.epochId,
                deliverAt = deliverAt,
            )
            sink.append(toolMessage)
            transcript += toolMessage
            produced += toolMessage
            emit(AgentEvent.MessageSaved(toolMessage))
        }

        if (!background) {
            recordReply(conversation.id, produced)
            settleMemories(setup.persona.id, produced, finalText, conversation, keepSource = true)
        }
        return TurnOutcome(produced, finalText, refused = false)
    }

    /**
     * Memories kept during a turn take the letter's mood once the reply is finished (its mood tag
     * comes at the end). The heartbeat calls this too, dropping the message link when it stays quiet.
     */
    internal suspend fun settleMemories(personaId: String, produced: List<ChatMessage>, finalText: String, conversation: Conversation, keepSource: Boolean) {
        val ids = produced.filter { it.role == MessageRole.ASSISTANT }.map { it.id }
        val mood = MoodTags.parse(finalText) ?: h.conversations.get(conversation.id)?.mood ?: conversation.mood
        runCatching { h.memory.settleTurn(personaId, ids, mood, keepSource) }.onFailure { h.log("Couldn't settle memory moods: ${it.message}") }
    }

    /** Bumps the conversation and records the latest visible reply's time and mood (for the letters list). */
    internal suspend fun recordReply(conversationId: String, produced: List<ChatMessage>) {
        val now = h.clock.nowMillis()
        val reply = produced.lastOrNull { it.isVisibleReply() }
        h.updateConversation(conversationId) {
            it.copy(
                updatedAt = now,
                // A reply in the post counts from when it arrives (the letters list says it's on its way till then).
                lastAgentMessageAt = reply?.arrivedAt ?: it.lastAgentMessageAt,
                mood = reply?.mood ?: it.mood,
                preview = reply?.let { r -> Conversation.previewOf(r.displayText) } ?: it.preview,
                previewFromHeartbeat = reply?.let { r -> r.origin == MessageOrigin.HEARTBEAT } ?: it.previewFromHeartbeat,
            )
        }
    }

    private fun refusalMessage(turn: LlmTurnResult): String {
        val category = turn.refusal?.category?.let { " ($it)" }.orEmpty()
        return "The model declined to respond$category." + (turn.refusal?.explanation?.let { " $it" } ?: "")
    }
}

/** Summaries for compaction and episode memories, using the utility model when one is set. */
class LlmSummarizer(private val h: Harness) {
    suspend fun summarize(fallbackProfile: ModelProfile, settings: AppSettings, request: SummaryRequest): String? {
        val profile = settings.utilityModelProfileId?.let { h.profiles.get(it) } ?: fallbackProfile
        val key = h.apiKey(profile.credentialId)
        val instruction = if (request.condense) {
            "Condense this running summary of a conversation between ${request.personaName} and ${request.userName}. " +
                "Keep names, facts, promises, feelings and unresolved threads; drop filler. Under 250 words."
        } else {
            "Summarize this part of a conversation between ${request.personaName} and ${request.userName} so " +
                "${request.personaName} can remember it later. Keep facts learned, decisions, promises, emotional moments, " +
                "names, dates and anything left unresolved. Write in past tense, third person, under 200 words. " +
                "Output only the summary."
        }
        val result = h.providers.forProfile(profile.copy(toolMode = ToolMode.NONE)).complete(
            LlmRequest(
                profile = profile.copy(maxOutputTokens = profile.maxOutputTokens.coerceAtMost(8_000)),
                apiKey = key,
                system = "You write concise, faithful memory summaries.",
                messages = listOf(LlmMessage.User(listOf(LlmPart.Text("$instruction\n\n<transcript>\n${request.transcript}\n</transcript>")))),
            ),
        )
        return result.text.takeIf { result.stopReason != StopReason.REFUSAL }
    }

    /** A short behind-the-scenes question for the utility model (e.g. sorting mood words). */
    suspend fun ask(fallbackProfile: ModelProfile, settings: AppSettings, system: String, prompt: String): String? {
        val profile = settings.utilityModelProfileId?.let { h.profiles.get(it) } ?: fallbackProfile
        val result = h.providers.forProfile(profile.copy(toolMode = ToolMode.NONE)).complete(
            LlmRequest(
                profile = profile.copy(maxOutputTokens = profile.maxOutputTokens.coerceAtMost(2_000)),
                apiKey = h.apiKey(profile.credentialId),
                system = system,
                messages = listOf(LlmMessage.User(listOf(LlmPart.Text(prompt)))),
            ),
        )
        return result.text.takeIf { result.stopReason != StopReason.REFUSAL }
    }
}

internal fun ChatMessage.isVisibleReply() =
    role == MessageRole.ASSISTANT && !hidden && !excludedFromContext && displayText.isNotBlank()
