package dev.aiharness.core.llm

import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.TokenUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.last
import kotlinx.serialization.json.JsonObject

/**
 * A function tool offered to the model. [parameters] is a JSON Schema object. A tool with a
 * [server] counterpart is sent as that provider-hosted tool where the provider has one (Claude's
 * web fetch for `open_link`), and as this function tool elsewhere.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val server: ServerTool? = null,
)

/** Tools a provider can run on its own servers in place of ours. */
enum class ServerTool { WEB_FETCH }

/** Provider-neutral conversation message. Providers translate these to their wire format. */
sealed interface LlmMessage {
    data class User(val parts: List<LlmPart>) : LlmMessage

    /**
     * @param replay provider-native serialization of this turn, produced by the same provider in the
     *   same context epoch. When present, providers send it verbatim instead of rebuilding from fields.
     */
    data class Assistant(
        val text: String,
        val toolCalls: List<LlmToolCall> = emptyList(),
        val replay: String? = null,
    ) : LlmMessage

    data class ToolResults(val results: List<LlmToolResult>) : LlmMessage
}

sealed interface LlmPart {
    data class Text(val text: String) : LlmPart
    data class Image(val bytes: ByteArray, val mimeType: String) : LlmPart

    /**
     * A PDF, a song or a video, sent whole to a model that takes it in (see `MediaSupport`). The
     * context builder only makes these for providers that can send them; others drop them.
     */
    data class Media(val bytes: ByteArray, val mimeType: String, val name: String? = null) : LlmPart
}

data class LlmToolCall(val id: String, val name: String, val argumentsJson: String)

data class LlmToolResult(val callId: String, val name: String, val content: String, val isError: Boolean = false)

data class LlmRequest(
    val profile: ModelProfile,
    val apiKey: String?,
    val system: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec> = emptyList(),
)

enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL, OTHER }

data class RefusalInfo(val category: String?, val explanation: String?)

data class LlmTurnResult(
    val text: String,
    val reasoning: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val stopReason: StopReason = StopReason.END_TURN,
    val usage: TokenUsage? = null,
    /** Provider-native serialization of this assistant turn for faithful replay. */
    val replay: String? = null,
    val refusal: RefusalInfo? = null,
    val modelId: String? = null,
    /** Pages the provider opened itself this turn (Claude's web fetch), by address. */
    val opened: List<String> = emptyList(),
)

sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent
    data class ReasoningDelta(val text: String) : LlmEvent
    data class Completed(val result: LlmTurnResult) : LlmEvent
}

class LlmException(
    message: String,
    val statusCode: Int? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

/** One chat-completion backend. Implementations must be safe to call concurrently. */
interface LlmProvider {
    val type: ProviderType

    /** Streams a single assistant turn. The final event is always [LlmEvent.Completed]. */
    fun stream(request: LlmRequest): Flow<LlmEvent>

    /** Model ids available to this account, for the model picker. */
    suspend fun listModels(profile: ModelProfile, apiKey: String?): List<String>
}

suspend fun LlmProvider.complete(request: LlmRequest): LlmTurnResult =
    stream(request).filterIsInstance<LlmEvent.Completed>().last().result
