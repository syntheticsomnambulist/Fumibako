package dev.aiharness.core.llm.anthropic

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import dev.aiharness.core.llm.ServerTool
import com.anthropic.models.beta.messages.BetaWebFetchTool20260209
import com.anthropic.models.beta.messages.BetaWebFetchTool20250910
import com.anthropic.models.beta.messages.BetaRequestDocumentBlock
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.BetaToolUseBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import dev.aiharness.core.llm.LlmEvent
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmProvider
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.RefusalInfo
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.TokenUsage
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.toPlain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Claude via the official Anthropic Java SDK (beta Messages surface, so server-side refusal fallback
 * and other beta request fields are typed).
 *
 * Assistant turns are persisted as the SDK's own `BetaMessageParam` JSON and replayed verbatim, which
 * keeps thinking blocks (and their signatures) byte-identical. The [dev.aiharness.core.agent.ContextBuilder]
 * guarantees replay only happens inside an append-only context epoch, as preserved thinking requires.
 */
class AnthropicProvider : LlmProvider {
    override val type = ProviderType.ANTHROPIC

    private val clients = ConcurrentHashMap<String, AnthropicClient>()
    private val mapper by lazy { jsonMapper() }

    private fun client(profile: ModelProfile, apiKey: String?): AnthropicClient {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: throw LlmException("Anthropic: no API key configured")
        val baseUrl = profile.baseUrl?.takeIf { it.isNotBlank() }?.trimEnd('/')
        return clients.getOrPut("${baseUrl.orEmpty()}|$key") {
            AnthropicOkHttpClient.builder()
                .apiKey(key)
                .apply { if (baseUrl != null) baseUrl(baseUrl) }
                .maxRetries(2)
                .build()
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        val params = buildParams(request)
        val client = client(request.profile, request.apiKey)
        val accumulator = BetaMessageAccumulator.create()

        val response = try {
            client.beta().messages().createStreaming(params)
        } catch (e: Throwable) {
            throw mapError(e)
        }
        coroutineScope {
            // Closing the stream unblocks the SDK's blocking iterator when the collector cancels.
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    response.close()
                }
            }
            try {
                response.use { streamResponse ->
                    val iterator = streamResponse.stream().iterator()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val hasNext = try {
                            iterator.hasNext()
                        } catch (e: Throwable) {
                            currentCoroutineContext().ensureActive()
                            throw mapError(e)
                        }
                        if (!hasNext) break
                        val event = iterator.next()
                        accumulator.accumulate(event)
                        val delta = event.contentBlockDelta().orElse(null)?.delta() ?: continue
                        delta.text().orElse(null)?.text()?.takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.TextDelta(it)) }
                        delta.thinking().orElse(null)?.thinking()?.takeIf { it.isNotEmpty() }?.let {
                            emit(LlmEvent.ReasoningDelta(it))
                        }
                    }
                }
            } finally {
                watcher.cancel()
            }
        }
        emit(LlmEvent.Completed(toResult(accumulator.message(), request.profile)))
    }.flowOn(Dispatchers.IO)

    private fun toResult(message: BetaMessage, profile: ModelProfile): LlmTurnResult {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = mutableListOf<LlmToolCall>()
        val opened = mutableListOf<String>()
        message.content().forEach { block ->
            block.serverToolUse().ifPresent { use ->
                if (use.name().toString().contains("web_fetch")) {
                    runCatching { HarnessJson.parseToJsonElement(mapper.writeValueAsString(use._input())).jsonObject["url"]?.jsonPrimitive?.contentOrNull }
                        .getOrNull()?.let(opened::add)
                }
            }
            block.text().ifPresent { text.append(it.text()) }
            block.thinking().ifPresent { t -> if (t.thinking().isNotBlank()) reasoning.append(t.thinking()).append('\n') }
            block.toolUse().ifPresent { use ->
                calls += LlmToolCall(id = use.id(), name = use.name(), argumentsJson = mapper.writeValueAsString(use._input()))
            }
        }
        val stopReason = message.stopReason().orElse(null)
        val stop = when (stopReason) {
            BetaStopReason.END_TURN, BetaStopReason.STOP_SEQUENCE -> StopReason.END_TURN
            BetaStopReason.TOOL_USE -> StopReason.TOOL_USE
            BetaStopReason.MAX_TOKENS, BetaStopReason.MODEL_CONTEXT_WINDOW_EXCEEDED -> StopReason.MAX_TOKENS
            BetaStopReason.REFUSAL -> StopReason.REFUSAL
            else -> if (calls.isNotEmpty()) StopReason.TOOL_USE else StopReason.OTHER
        }
        val refusal = if (stop == StopReason.REFUSAL) {
            val details = message.stopDetails().orElse(null)
            RefusalInfo(
                category = details?.category()?.orElse(null)?.toString(),
                explanation = details?.explanation()?.orElse(null),
            )
        } else {
            null
        }
        val usage = message.usage()
        return LlmTurnResult(
            text = text.toString(),
            reasoning = reasoning.toString().trim().ifBlank { null },
            toolCalls = calls,
            stopReason = stop,
            usage = TokenUsage(
                inputTokens = usage.inputTokens(),
                outputTokens = usage.outputTokens(),
                cacheReadTokens = usage.cacheReadInputTokens().orElse(null),
                cacheWriteTokens = usage.cacheCreationInputTokens().orElse(null),
            ),
            replay = mapper.writeValueAsString(message.toParam()),
            refusal = refusal,
            modelId = message.model().asString(),
            opened = opened,
        )
    }

    internal fun buildParams(request: LlmRequest): MessageCreateParams {
        val profile = request.profile
        val options = profile.options
        val builder = MessageCreateParams.builder()
            .model(profile.modelId)
            .maxTokens(profile.maxOutputTokens.toLong())
            .messages(request.messages.map(::toParam))

        if (request.system.isNotBlank()) {
            val system = BetaTextBlockParam.builder().text(request.system)
            if (options.anthropicPromptCaching) system.cacheControl(BetaCacheControlEphemeral.builder().build())
            builder.systemOfBetaTextBlockParams(listOf(system.build()))
        }
        if (options.anthropicPromptCaching) {
            // Automatic caching of the growing conversation prefix.
            builder.cacheControl(BetaCacheControlEphemeral.builder().build())
        }
        request.tools.forEach { tool ->
            // Claude opens links itself, on Anthropic's servers, where the API is Anthropic's own.
            if (tool.server == ServerTool.WEB_FETCH && isFirstParty(profile)) {
                if (DYNAMIC_FETCH.containsMatchIn(profile.modelId)) {
                    builder.addTool(BetaWebFetchTool20260209.builder().maxUses(WEB_FETCH_USES).maxContentTokens(WEB_FETCH_TOKENS).build())
                } else {
                    builder.addTool(BetaWebFetchTool20250910.builder().maxUses(WEB_FETCH_USES).maxContentTokens(WEB_FETCH_TOKENS).build())
                }
                return@forEach
            }
            val props = (tool.parameters["properties"] as? JsonObject)?.toPlain() ?: emptyMap<String, Any?>()
            val required = (tool.parameters["required"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            builder.addTool(
                BetaTool.builder()
                    .name(tool.name)
                    .description(tool.description)
                    .inputSchema(
                        BetaTool.InputSchema.builder()
                            .properties(JsonValue.from(props))
                            .required(required)
                            .build(),
                    )
                    // Streams tool input as it is generated; proxies may reject the field, so first-party only.
                    .apply { if (isFirstParty(profile)) eagerInputStreaming(true) }
                    .build(),
            )
        }
        if (options.anthropicAdaptiveThinking) {
            val display = if (options.anthropicThinkingDisplay.equals("omitted", ignoreCase = true)) {
                BetaThinkingConfigAdaptive.Display.OMITTED
            } else {
                BetaThinkingConfigAdaptive.Display.SUMMARIZED
            }
            builder.thinking(BetaThinkingConfigAdaptive.builder().display(display).build())
        }
        effortOf(profile.reasoningEffort)?.let { builder.outputConfig(BetaOutputConfig.builder().effort(it).build()) }
        if (options.anthropicServerFallback && supportsServerFallback(profile)) {
            builder.addBeta(SERVER_FALLBACK_BETA).fallbacksDefault()
        }
        return builder.build()
    }

    private fun toParam(message: LlmMessage): BetaMessageParam = when (message) {
        is LlmMessage.User -> BetaMessageParam.ofUserOfContentBlockParams(
            message.parts.mapNotNull { part ->
                when (part) {
                    is LlmPart.Text -> BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(part.text).build())
                    is LlmPart.Image -> imageBlock(part)
                    is LlmPart.Media -> documentBlock(part)
                }
            }.ifEmpty { listOf(BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text("...").build())) },
        )

        is LlmMessage.Assistant -> message.replay
            ?.let { runCatching { mapper.readValue(it, BetaMessageParam::class.java) }.getOrNull() }
            ?: BetaMessageParam.ofAssistantOfContentBlockParams(
                    buildList {
                        if (message.text.isNotBlank()) {
                            add(BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(message.text).build()))
                        }
                        message.toolCalls.forEach { call ->
                            add(
                                BetaContentBlockParam.ofToolUse(
                                    BetaToolUseBlockParam.builder()
                                        .id(call.id)
                                        .name(call.name)
                                        .input(JsonValue.from(parseArgs(call.argumentsJson)))
                                        .build(),
                                ),
                            )
                        }
                        if (isEmpty()) add(BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text("...").build()))
                    },
                )

        is LlmMessage.ToolResults -> BetaMessageParam.ofUserOfContentBlockParams(
            message.results.map { result ->
                BetaContentBlockParam.ofToolResult(
                    BetaToolResultBlockParam.builder()
                        .toolUseId(result.callId)
                        .content(result.content)
                        .isError(result.isError)
                        .build(),
                )
            },
        )
    }

    private fun imageBlock(part: LlmPart.Image): BetaContentBlockParam? {
        val mediaType = when (part.mimeType.lowercase()) {
            "image/png" -> BetaBase64ImageSource.MediaType.IMAGE_PNG
            "image/jpeg", "image/jpg" -> BetaBase64ImageSource.MediaType.IMAGE_JPEG
            "image/webp" -> BetaBase64ImageSource.MediaType.IMAGE_WEBP
            "image/gif" -> BetaBase64ImageSource.MediaType.IMAGE_GIF
            else -> return null
        }
        return BetaContentBlockParam.ofImage(
            BetaImageBlockParam.builder()
                .source(
                    BetaBase64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(part.bytes))
                        .mediaType(mediaType)
                        .build(),
                )
                .build(),
        )
    }

    /** A PDF, as a document Claude reads page by page (text and pictures). Other media aren't sent. */
    private fun documentBlock(part: LlmPart.Media): BetaContentBlockParam? {
        if (part.mimeType.lowercase() != "application/pdf") return null
        return BetaContentBlockParam.ofDocument(
            BetaRequestDocumentBlock.builder()
                .base64Source(Base64.getEncoder().encodeToString(part.bytes))
                .apply { part.name?.takeIf { it.isNotBlank() }?.let { title(it) } }
                .build(),
        )
    }

    override suspend fun listModels(profile: ModelProfile, apiKey: String?): List<String> = withContext(Dispatchers.IO) {
        try {
            client(profile, apiKey).models().list().autoPager().map { it.id() }.sorted()
        } catch (e: Throwable) {
            throw mapError(e)
        }
    }

    private fun mapError(e: Throwable): Throwable = when (e) {
        is CancellationException, is LlmException -> e
        is AnthropicServiceException -> {
            val code = e.statusCode()
            LlmException(
                "Anthropic error $code: ${e.message}",
                statusCode = code,
                retryable = code == 408 || code == 409 || code == 429 || code >= 500,
                cause = e,
            )
        }
        is AnthropicIoException -> LlmException("Anthropic network error: ${e.message}", retryable = true, cause = e)
        else -> LlmException("Anthropic: ${e.message ?: e::class.simpleName}", cause = e)
    }

    companion object {
        /** Models with the dynamic-filtering web fetch; older ones take the basic one. */
        private val DYNAMIC_FETCH = Regex("claude-(opus-(4-[678]|5)|sonnet-(4-6|5))")
        private const val WEB_FETCH_USES = 3L
        private const val WEB_FETCH_TOKENS = 12_000L

        const val SERVER_FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Models that accept `fallbacks: "default"` on the Claude API. */
        private val SERVER_FALLBACK_MODELS = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

        fun isFirstParty(profile: ModelProfile): Boolean {
            val base = profile.baseUrl?.trim().orEmpty()
            return base.isEmpty() || base.trimEnd('/') == "https://api.anthropic.com"
        }

        fun supportsServerFallback(profile: ModelProfile): Boolean =
            isFirstParty(profile) && profile.modelId in SERVER_FALLBACK_MODELS

        private fun effortOf(value: String?): BetaOutputConfig.Effort? = when (value?.lowercase()) {
            "low" -> BetaOutputConfig.Effort.LOW
            "medium" -> BetaOutputConfig.Effort.MEDIUM
            "high" -> BetaOutputConfig.Effort.HIGH
            "xhigh" -> BetaOutputConfig.Effort.XHIGH
            "max" -> BetaOutputConfig.Effort.MAX
            else -> null
        }

        private fun parseArgs(json: String): Map<String, Any?> = runCatching {
            @Suppress("UNCHECKED_CAST")
            (HarnessJson.parseToJsonElement(json).jsonObject.toPlain() as Map<String, Any?>)
        }.getOrDefault(emptyMap())
    }
}
