package dev.aiharness.core.llm.openai

import dev.aiharness.core.llm.HarnessHttp
import dev.aiharness.core.llm.LlmEvent
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmProvider
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.llm.executeForString
import dev.aiharness.core.llm.sseFlow
import dev.aiharness.core.llm.toRequestBody
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.TokenUsage
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

/**
 * Chat Completions API (`POST {base}/chat/completions`). Covers OpenAI, OpenRouter, DeepSeek, Groq,
 * Ollama, LM Studio, KoboldCpp, vLLM and similar servers, and NovelAI's text endpoint.
 */
open class OpenAiCompatibleProvider(
    override val type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    private val defaultBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    private val http: OkHttpClient = HarnessHttp.client,
    /** NovelAI's endpoint only accepts plain-string message content. */
    private val stringContentOnly: Boolean = false,
) : LlmProvider {

    private val name get() = type.displayName

    private fun baseUrl(profile: ModelProfile) = (profile.baseUrl?.takeIf { it.isNotBlank() } ?: defaultBaseUrl).trimEnd('/')

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        val profile = request.profile
        val body = buildBody(request)
        val httpRequest = Request.Builder()
            .url("${baseUrl(profile)}/chat/completions")
            .post(body.toRequestBody())
            .header("Accept", "text/event-stream")
            .apply { request.apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()

        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = sortedMapOf<Int, ToolCallAccumulator>()
        var finishReason: String? = null
        var usage: TokenUsage? = null
        var modelId: String? = null

        http.sseFlow(httpRequest, name).collect { event ->
            val data = event.data.trim()
            if (data.isEmpty() || data == "[DONE]") return@collect
            val chunk = try {
                HarnessJson.parseToJsonElement(data).jsonObject
            } catch (e: Exception) {
                return@collect
            }
            chunk["error"]?.let { err ->
                val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: err.toString()
                throw LlmException("$name stream error: $msg")
            }
            chunk["model"]?.jsonPrimitive?.contentOrNull?.let { modelId = it }
            chunk["usage"]?.takeIf { it !is JsonNull }?.jsonObject?.let { usage = parseUsage(it) }
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@collect
            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
            val delta = choice["delta"] as? JsonObject ?: choice["message"] as? JsonObject ?: return@collect

            delta.stringOrNull("content")?.takeIf { it.isNotEmpty() }?.let {
                text.append(it)
                emit(LlmEvent.TextDelta(it))
            }
            (delta.stringOrNull("reasoning_content") ?: delta.stringOrNull("reasoning"))
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    reasoning.append(it)
                    emit(LlmEvent.ReasoningDelta(it))
                }
            (delta["tool_calls"] as? JsonArray)?.forEach { element ->
                val call = element.jsonObject
                val index = call["index"]?.jsonPrimitive?.intOrNull ?: calls.size
                val acc = calls.getOrPut(index) { ToolCallAccumulator() }
                call.stringOrNull("id")?.let { acc.id = it }
                (call["function"] as? JsonObject)?.let { fn ->
                    fn.stringOrNull("name")?.let { acc.name.append(it) }
                    fn.stringOrNull("arguments")?.let { acc.arguments.append(it) }
                }
            }
        }

        val toolCalls = calls.values.mapIndexedNotNull { i, acc ->
            val callName = acc.name.toString().ifBlank { return@mapIndexedNotNull null }
            LlmToolCall(
                id = acc.id ?: "call_$i",
                name = callName,
                argumentsJson = acc.arguments.toString().ifBlank { "{}" },
            )
        }
        val stop = when (finishReason) {
            "tool_calls", "function_call" -> StopReason.TOOL_USE
            "length" -> StopReason.MAX_TOKENS
            "content_filter" -> StopReason.REFUSAL
            null, "stop" -> if (toolCalls.isNotEmpty()) StopReason.TOOL_USE else StopReason.END_TURN
            else -> if (toolCalls.isNotEmpty()) StopReason.TOOL_USE else StopReason.OTHER
        }
        emit(
            LlmEvent.Completed(
                LlmTurnResult(
                    text = text.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    toolCalls = toolCalls,
                    stopReason = stop,
                    usage = usage,
                    modelId = modelId ?: profile.modelId,
                ),
            ),
        )
    }

    internal fun buildBody(request: LlmRequest): JsonObject {
        val profile = request.profile
        return buildJsonObject {
            put("model", profile.modelId)
            put("stream", true)
            put("messages", buildMessages(request))
            put(profile.options.openAiMaxTokensField.ifBlank { "max_tokens" }, profile.maxOutputTokens)
            profile.temperature?.let { put("temperature", it) }
            profile.topP?.let { put("top_p", it) }
            profile.reasoningEffort?.takeIf { it.isNotBlank() }?.let { put("reasoning_effort", it) }
            if (profile.options.openAiIncludeUsage) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        request.tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", tool.name)
                                            put("description", tool.description)
                                            put("parameters", tool.parameters)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
                put("tool_choice", "auto")
            }
            profile.extraBody?.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun buildMessages(request: LlmRequest): JsonArray = buildJsonArray {
        if (request.system.isNotBlank()) {
            add(buildJsonObject { put("role", "system"); put("content", request.system) })
        }
        request.messages.forEach { message ->
            when (message) {
                is LlmMessage.User -> add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userContent(message, request.profile.supportsVision))
                    },
                )
                is LlmMessage.Assistant -> add(
                    buildJsonObject {
                        put("role", "assistant")
                        if (message.text.isNotEmpty() || message.toolCalls.isEmpty()) {
                            put("content", message.text)
                        } else {
                            put("content", JsonNull)
                        }
                        if (message.toolCalls.isNotEmpty()) {
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    message.toolCalls.forEach { call ->
                                        add(
                                            buildJsonObject {
                                                put("id", call.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject {
                                                        put("name", call.name)
                                                        put("arguments", call.argumentsJson)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
                is LlmMessage.ToolResults -> message.results.forEach { result ->
                    add(
                        buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", result.callId)
                            put("content", if (result.isError) "Error: ${result.content}" else result.content)
                        },
                    )
                }
            }
        }
    }

    private fun userContent(message: LlmMessage.User, vision: Boolean): JsonElement {
        val hasImages = message.parts.any { it is LlmPart.Image }
        if (stringContentOnly || !vision || !hasImages) {
            return JsonPrimitive(
                message.parts.filterIsInstance<LlmPart.Text>().joinToString("\n\n") { it.text },
            )
        }
        return buildJsonArray {
            message.parts.forEach { part ->
                when (part) {
                    is LlmPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
                    // Servers differ too much in what else they take; the stand-in beside it says what it is.
                    is LlmPart.Media, is LlmPart.LinkedVideo -> Unit
                    is LlmPart.Image -> add(
                        buildJsonObject {
                            put("type", "image_url")
                            put(
                                "image_url",
                                buildJsonObject {
                                    val b64 = Base64.getEncoder().encodeToString(part.bytes)
                                    put("url", "data:${part.mimeType};base64,$b64")
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    override suspend fun listModels(profile: ModelProfile, apiKey: String?): List<String> {
        val request = Request.Builder()
            .url("${baseUrl(profile)}/models")
            .get()
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val raw = http.executeForString(request, name)
        val root = HarnessJson.parseToJsonElement(raw)
        val data = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: return emptyList()
        return data.mapNotNull { (it as? JsonObject)?.stringOrNull("id") }.sorted()
    }

    private class ToolCallAccumulator {
        var id: String? = null
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    companion object {
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val NOVELAI_TEXT_BASE_URL = "https://text.novelai.net/oa/v1"

        /** NovelAI's OpenAI-compatible text API (GLM and NovelAI's own models). */
        fun novelAi(http: OkHttpClient = HarnessHttp.client) = OpenAiCompatibleProvider(
            type = ProviderType.NOVELAI_TEXT,
            defaultBaseUrl = NOVELAI_TEXT_BASE_URL,
            http = http,
            stringContentOnly = true,
        )
    }
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun parseUsage(obj: JsonObject): TokenUsage = TokenUsage(
    inputTokens = obj["prompt_tokens"]?.jsonPrimitive?.longOrNull,
    outputTokens = obj["completion_tokens"]?.jsonPrimitive?.longOrNull,
    cacheReadTokens = (obj["prompt_tokens_details"] as? JsonObject)?.get("cached_tokens")?.jsonPrimitive?.longOrNull,
)
