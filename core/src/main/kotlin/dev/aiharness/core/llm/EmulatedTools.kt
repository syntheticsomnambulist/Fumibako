package dev.aiharness.core.llm

import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Prompt-based tool calling for models without native function calling. Tools are described in the
 * system prompt; the model writes `<tool_call>{"name": ..., "arguments": {...}}</tool_call>` and gets
 * results back as `<tool_result>` text in the next user turn. The rendering is deterministic, so a
 * replayed conversation produces the same prompt prefix every time.
 */
object EmulatedToolProtocol {
    const val CALL_OPEN = "<tool_call>"
    const val CALL_CLOSE = "</tool_call>"

    fun systemAppendix(tools: List<ToolSpec>): String = buildString {
        appendLine()
        appendLine()
        appendLine("# Tools")
        appendLine("You can use tools. To call one, write exactly one block per call, on its own line:")
        appendLine("$CALL_OPEN{\"name\": \"tool_name\", \"arguments\": {...}}$CALL_CLOSE")
        appendLine("After writing tool calls, stop and wait: results arrive in <tool_result> blocks. ")
        appendLine("Never write <tool_result> blocks yourself. Available tools:")
        tools.forEach { tool ->
            appendLine("- ${tool.name}: ${tool.description}")
            appendLine("  arguments schema: ${tool.parameters}")
        }
    }.trimEnd()

    fun renderCall(call: LlmToolCall): String =
        CALL_OPEN + buildJsonObject {
            put("name", call.name)
            put("arguments", runCatching { HarnessJson.parseToJsonElement(call.argumentsJson) }.getOrElse { JsonObject(emptyMap()) })
        } + CALL_CLOSE

    fun renderResult(result: LlmToolResult): String {
        val status = if (result.isError) " error=\"true\"" else ""
        return "<tool_result name=\"${result.name}\"$status>\n${result.content}\n</tool_result>"
    }

    private val callRegex = Regex(Regex.escape(CALL_OPEN) + "(.*?)" + Regex.escape(CALL_CLOSE), RegexOption.DOT_MATCHES_ALL)

    /** Splits a reply into visible text and parsed tool calls. Malformed blocks are left as text. */
    fun parse(text: String, idPrefix: String): Pair<String, List<LlmToolCall>> {
        val calls = mutableListOf<LlmToolCall>()
        val visible = callRegex.replace(text) { match ->
            val parsed = runCatching { HarnessJson.parseToJsonElement(match.groupValues[1].trim()).jsonObject }.getOrNull()
            val name = parsed?.get("name")?.jsonPrimitive?.contentOrNull
            if (parsed == null || name.isNullOrBlank()) {
                match.value
            } else {
                val args = parsed["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                calls += LlmToolCall(id = "$idPrefix-${calls.size}", name = name, argumentsJson = args.toString())
                ""
            }
        }
        return visible.trim() to calls
    }
}

/** Hides `<tool_call>` blocks from a stream of text deltas, holding back possible partial tags. */
class ToolCallStreamFilter {
    private val pending = StringBuilder()
    private var insideCall = false

    fun accept(delta: String): String {
        pending.append(delta)
        val out = StringBuilder()
        while (true) {
            if (insideCall) {
                val end = pending.indexOf(EmulatedToolProtocol.CALL_CLOSE)
                if (end == -1) return out.toString()
                pending.delete(0, end + EmulatedToolProtocol.CALL_CLOSE.length)
                insideCall = false
            } else {
                val start = pending.indexOf(EmulatedToolProtocol.CALL_OPEN)
                if (start != -1) {
                    out.append(pending, 0, start)
                    pending.delete(0, start + EmulatedToolProtocol.CALL_OPEN.length)
                    insideCall = true
                    continue
                }
                // Keep a tail that might be the beginning of "<tool_call>".
                val keep = longestTagPrefixSuffix(pending)
                out.append(pending, 0, pending.length - keep)
                pending.delete(0, pending.length - keep)
                return out.toString()
            }
        }
    }

    fun flush(): String = if (insideCall) "" else pending.toString().also { pending.setLength(0) }

    private fun longestTagPrefixSuffix(sb: CharSequence): Int {
        val tag = EmulatedToolProtocol.CALL_OPEN
        for (len in minOf(tag.length - 1, sb.length) downTo 1) {
            if (sb.subSequence(sb.length - len, sb.length) == tag.substring(0, len)) return len
        }
        return 0
    }
}

/** Wraps a provider so tools are offered through [EmulatedToolProtocol] instead of native calling. */
class EmulatedToolsProvider(private val delegate: LlmProvider) : LlmProvider by delegate {
    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        if (request.tools.isEmpty()) {
            delegate.stream(request.copy(messages = flatten(request.messages))).collect { emit(it) }
            return@flow
        }
        val adapted = request.copy(
            system = request.system + EmulatedToolProtocol.systemAppendix(request.tools),
            messages = flatten(request.messages),
            tools = emptyList(),
        )
        val filter = ToolCallStreamFilter()
        delegate.stream(adapted).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> filter.accept(event.text).takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.TextDelta(it)) }
                is LlmEvent.ReasoningDelta -> emit(event)
                is LlmEvent.Completed -> {
                    filter.flush().takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.TextDelta(it)) }
                    val (visible, calls) = EmulatedToolProtocol.parse(event.result.text, "emu-${System.nanoTime()}")
                    emit(
                        LlmEvent.Completed(
                            event.result.copy(
                                text = visible,
                                toolCalls = calls,
                                stopReason = if (calls.isNotEmpty()) StopReason.TOOL_USE else event.result.stopReason,
                                replay = null,
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** Rewrites native tool turns as plain text so text-only endpoints can read them. */
    private fun flatten(messages: List<LlmMessage>): List<LlmMessage> = messages.map { message ->
        when (message) {
            is LlmMessage.Assistant -> if (message.toolCalls.isEmpty()) {
                message.copy(replay = null)
            } else {
                LlmMessage.Assistant(
                    text = (listOf(message.text) + message.toolCalls.map(EmulatedToolProtocol::renderCall))
                        .filter { it.isNotBlank() }
                        .joinToString("\n"),
                )
            }
            is LlmMessage.ToolResults -> LlmMessage.User(
                listOf(LlmPart.Text(message.results.joinToString("\n") { EmulatedToolProtocol.renderResult(it) })),
            )
            is LlmMessage.User -> message
        }
    }
}

/** Strips tools entirely (ToolMode.NONE). */
class NoToolsProvider(private val delegate: LlmProvider) : LlmProvider by delegate {
    override fun stream(request: LlmRequest): Flow<LlmEvent> = delegate.stream(request.copy(tools = emptyList()))
}

/** Looks up providers by type and applies the profile's tool mode. New backends register here. */
class ProviderRegistry(providers: List<LlmProvider>) {
    private val byType = providers.associateBy { it.type }.toMutableMap()

    fun register(provider: LlmProvider) {
        byType[provider.type] = provider
    }

    fun raw(profile: ModelProfile): LlmProvider =
        byType[profile.provider] ?: throw LlmException("No provider registered for ${profile.provider}")

    fun forProfile(profile: ModelProfile): LlmProvider {
        val provider = raw(profile)
        return when (profile.toolMode) {
            ToolMode.NATIVE -> provider
            ToolMode.EMULATED -> EmulatedToolsProvider(provider)
            ToolMode.NONE -> NoToolsProvider(provider)
        }
    }
}
