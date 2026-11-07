package dev.aiharness.core.llm.gemini

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
import dev.aiharness.core.llm.openai.stringOrNull
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

/**
 * Google Gemini via the Generative Language API (`models/{model}:streamGenerateContent?alt=sse`).
 *
 * Model turns are replayed with their original parts, so `thoughtSignature`s on function calls are
 * returned exactly as received, which Gemini 3 requires during function calling.
 */
class GeminiProvider(
    private val http: OkHttpClient = HarnessHttp.client,
) : LlmProvider {
    override val type = ProviderType.GEMINI

    private fun baseUrl(profile: ModelProfile) =
        (profile.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL).trimEnd('/')

    /**
     * A request with a YouTube link Gemini won't take (a private or removed video, say) is sent
     * again once without its links, each said not to have come through, so the letter still comes.
     */
    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        var started = false
        try {
            streamOnce(request).collect { started = true; emit(it) }
        } catch (e: LlmException) {
            val code = e.statusCode
            if (started || code == null || code !in 400..499 || code == 401 || code == 429 || !hasLinkedVideos(request)) throw e
            streamOnce(withoutLinkedVideos(request)).collect { emit(it) }
        }
    }

    private fun streamOnce(request: LlmRequest): Flow<LlmEvent> = flow {
        val profile = request.profile
        val url = "${baseUrl(profile)}/models/${profile.modelId.removePrefix("models/")}:streamGenerateContent"
            .toHttpUrl().newBuilder().addQueryParameter("alt", "sse").build()
        val httpRequest = Request.Builder()
            .url(url)
            .post(buildBody(request).toRequestBody())
            .apply { request.apiKey?.takeIf { it.isNotBlank() }?.let { header("x-goog-api-key", it) } }
            .build()

        val text = StringBuilder()
        val reasoning = StringBuilder()
        val parts = mutableListOf<JsonObject>()
        val calls = mutableListOf<LlmToolCall>()
        var finishReason: String? = null
        var usage: TokenUsage? = null
        var modelVersion: String? = null

        http.sseFlow(httpRequest, "Gemini").collect { event ->
            val chunk = try {
                HarnessJson.parseToJsonElement(event.data).jsonObject
            } catch (e: Exception) {
                return@collect
            }
            chunk["error"]?.let { throw LlmException("Gemini stream error: $it") }
            (chunk["promptFeedback"] as? JsonObject)?.stringOrNull("blockReason")?.let {
                throw LlmException("Gemini blocked the prompt: $it")
            }
            chunk.stringOrNull("modelVersion")?.let { modelVersion = it }
            (chunk["usageMetadata"] as? JsonObject)?.let { usage = parseUsage(it) }
            val candidate = (chunk["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@collect
            candidate.stringOrNull("finishReason")?.let { finishReason = it }
            val content = candidate["content"] as? JsonObject ?: return@collect
            (content["parts"] as? JsonArray)?.forEach { element ->
                val part = element as? JsonObject ?: return@forEach
                val isThought = (part["thought"] as? JsonPrimitive)?.booleanOrNull == true
                val partText = part.stringOrNull("text")
                val fn = part["functionCall"] as? JsonObject
                when {
                    fn != null -> {
                        val name = fn.stringOrNull("name") ?: return@forEach
                        val args = fn["args"] as? JsonObject ?: JsonObject(emptyMap())
                        calls += LlmToolCall(
                            id = fn.stringOrNull("id") ?: "$SYNTHETIC_ID_PREFIX${calls.size}",
                            name = name,
                            argumentsJson = args.toString(),
                        )
                    }
                    partText != null && isThought -> {
                        reasoning.append(partText)
                        emit(LlmEvent.ReasoningDelta(partText))
                    }
                    partText != null -> {
                        text.append(partText)
                        if (partText.isNotEmpty()) emit(LlmEvent.TextDelta(partText))
                    }
                }
                appendPart(parts, part)
            }
        }

        val stop = when {
            calls.isNotEmpty() -> StopReason.TOOL_USE
            finishReason == "MAX_TOKENS" -> StopReason.MAX_TOKENS
            finishReason in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION") -> StopReason.REFUSAL
            finishReason == null || finishReason == "STOP" -> StopReason.END_TURN
            else -> StopReason.OTHER
        }
        val replay = buildJsonArray { parts.forEach { add(it) } }.toString()
        emit(
            LlmEvent.Completed(
                LlmTurnResult(
                    text = text.toString(),
                    reasoning = reasoning.toString().ifBlank { null },
                    toolCalls = calls,
                    stopReason = stop,
                    usage = usage,
                    replay = replay,
                    refusal = if (stop == StopReason.REFUSAL) {
                        dev.aiharness.core.llm.RefusalInfo(finishReason, null)
                    } else {
                        null
                    },
                    modelId = modelVersion ?: profile.modelId,
                ),
            ),
        )
    }

    /** Merges streamed text fragments into one part unless a part carries a signature or is a thought. */
    private fun appendPart(parts: MutableList<JsonObject>, part: JsonObject) {
        val last = parts.lastOrNull()
        val mergeable = { p: JsonObject ->
            p.keys == setOf("text")
        }
        if (last != null && mergeable(last) && mergeable(part)) {
            parts[parts.lastIndex] = buildJsonObject {
                put("text", last.stringOrNull("text").orEmpty() + part.stringOrNull("text").orEmpty())
            }
        } else {
            parts += part
        }
    }

    internal fun buildBody(request: LlmRequest): JsonObject {
        val profile = request.profile
        return buildJsonObject {
            if (request.system.isNotBlank()) {
                put("systemInstruction", buildJsonObject { put("parts", textParts(request.system)) })
            }
            put("contents", buildContents(request))
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "functionDeclarations",
                                    buildJsonArray {
                                        request.tools.forEach { tool ->
                                            add(
                                                buildJsonObject {
                                                    put("name", tool.name)
                                                    put("description", tool.description)
                                                    put("parameters", toGeminiSchema(tool.parameters))
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            }
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", profile.maxOutputTokens)
                    profile.temperature?.let { put("temperature", it) }
                    profile.topP?.let { put("topP", it) }
                    profile.reasoningEffort?.takeIf { it.isNotBlank() }?.let { level ->
                        put("thinkingConfig", buildJsonObject { put("thinkingLevel", level) })
                    }
                },
            )
            profile.extraBody?.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun inlineData(mimeType: String, bytes: ByteArray, endSeconds: Int? = null) = buildJsonObject {
        put(
            "inlineData",
            buildJsonObject {
                put("mimeType", mimeType)
                put("data", Base64.getEncoder().encodeToString(bytes))
            },
        )
        endSeconds?.let { put("videoMetadata", clip(it)) }
    }

    /** A YouTube video by its address, which Gemini fetches and watches. */
    private fun linkedVideo(part: LlmPart.LinkedVideo) = buildJsonObject {
        put("fileData", buildJsonObject { put("fileUri", part.uri) })
        part.endSeconds?.let { put("videoMetadata", clip(it)) }
    }

    /** Only the first [seconds] of a video. */
    private fun clip(seconds: Int) = buildJsonObject {
        put("startOffset", "0s")
        put("endOffset", "${seconds}s")
    }

    private fun hasLinkedVideos(request: LlmRequest): Boolean =
        request.messages.any { m -> m is LlmMessage.User && m.parts.any { it is LlmPart.LinkedVideo } }

    internal fun withoutLinkedVideos(request: LlmRequest): LlmRequest = request.copy(
        messages = request.messages.map { m ->
            if (m !is LlmMessage.User) m
            else m.copy(parts = m.parts.map { p -> if (p is LlmPart.LinkedVideo) LlmPart.Text("(The video didn't come through this time.)") else p })
        },
    )

    private fun buildContents(request: LlmRequest): JsonArray {
        val contents = mutableListOf<Pair<String, MutableList<JsonElement>>>()
        fun add(role: String, parts: List<JsonElement>) {
            if (parts.isEmpty()) return
            val last = contents.lastOrNull()
            if (last != null && last.first == role) last.second += parts else contents += role to parts.toMutableList()
        }
        request.messages.forEach { message ->
            when (message) {
                is LlmMessage.User -> add(
                    "user",
                    message.parts.mapNotNull { part ->
                        when (part) {
                            is LlmPart.Text -> buildJsonObject { put("text", part.text) }
                            // Every Gemini model sees pictures; the context builder decides what else goes (MediaSupport).
                            is LlmPart.Image -> inlineData(part.mimeType, part.bytes)
                            is LlmPart.Media -> inlineData(part.mimeType, part.bytes, part.endSeconds)
                            is LlmPart.LinkedVideo -> linkedVideo(part)
                        }
                    },
                )
                is LlmMessage.Assistant -> {
                    val replayed = message.replay?.let { runCatching { HarnessJson.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                    if (replayed != null && replayed.isNotEmpty()) {
                        add("model", replayed)
                    } else {
                        val parts = buildList<JsonElement> {
                            if (message.text.isNotEmpty()) add(buildJsonObject { put("text", message.text) })
                            message.toolCalls.forEach { call ->
                                add(
                                    buildJsonObject {
                                        put(
                                            "functionCall",
                                            buildJsonObject {
                                                put("name", call.name)
                                                put("args", parseArgs(call.argumentsJson))
                                                if (!call.id.startsWith(SYNTHETIC_ID_PREFIX)) put("id", call.id)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        add("model", parts.ifEmpty { listOf(buildJsonObject { put("text", "") }) })
                    }
                }
                is LlmMessage.ToolResults -> add(
                    "user",
                    message.results.map { result ->
                        buildJsonObject {
                            put(
                                "functionResponse",
                                buildJsonObject {
                                    put("name", result.name)
                                    if (!result.callId.startsWith(SYNTHETIC_ID_PREFIX)) put("id", result.callId)
                                    put(
                                        "response",
                                        buildJsonObject {
                                            put(if (result.isError) "error" else "result", result.content)
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
        return buildJsonArray {
            contents.forEach { (role, parts) ->
                add(
                    buildJsonObject {
                        put("role", role)
                        put("parts", JsonArray(parts))
                    },
                )
            }
        }
    }

    override suspend fun listModels(profile: ModelProfile, apiKey: String?): List<String> {
        val url = "${baseUrl(profile)}/models".toHttpUrl().newBuilder().addQueryParameter("pageSize", "1000").build()
        val request = Request.Builder().url(url).get()
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("x-goog-api-key", it) } }
            .build()
        val root = HarnessJson.parseToJsonElement(http.executeForString(request, "Gemini")).jsonObject
        return (root["models"] as? JsonArray).orEmpty().mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val methods = (model["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
            if (methods != null && "generateContent" !in methods) return@mapNotNull null
            model.stringOrNull("name")?.removePrefix("models/")
        }.sorted()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val SYNTHETIC_ID_PREFIX = "gemini-call-"

        private fun textParts(text: String) = buildJsonArray { add(buildJsonObject { put("text", text) }) }

        private fun parseArgs(json: String): JsonObject =
            runCatching { HarnessJson.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))

        private fun parseUsage(obj: JsonObject) = TokenUsage(
            inputTokens = obj["promptTokenCount"]?.jsonPrimitive?.longOrNull,
            outputTokens = obj["candidatesTokenCount"]?.jsonPrimitive?.longOrNull,
            cacheReadTokens = obj["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull,
        )

        private val SUPPORTED_SCHEMA_KEYS = setOf(
            "type", "description", "enum", "properties", "required", "items", "nullable", "format",
            "minimum", "maximum", "minItems", "maxItems",
        )

        /** Converts a JSON Schema object to the OpenAPI subset Gemini's `parameters` field accepts. */
        internal fun toGeminiSchema(schema: JsonElement): JsonElement {
            if (schema !is JsonObject) return schema
            return buildJsonObject {
                schema.forEach { (key, value) ->
                    if (key !in SUPPORTED_SCHEMA_KEYS) return@forEach
                    when (key) {
                        "type" -> put("type", (value as? JsonPrimitive)?.contentOrNull?.uppercase() ?: "STRING")
                        "properties" -> put(
                            "properties",
                            buildJsonObject {
                                (value as? JsonObject)?.forEach { (name, sub) -> put(name, toGeminiSchema(sub)) }
                            },
                        )
                        "items" -> put("items", toGeminiSchema(value))
                        else -> put(key, value)
                    }
                }
            }
        }
    }
}
