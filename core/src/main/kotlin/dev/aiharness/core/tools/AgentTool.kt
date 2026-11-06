package dev.aiharness.core.tools

import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** What a tool sees when it runs. */
data class ToolContext(
    val conversation: Conversation,
    val persona: Persona,
    val userPersona: UserPersona?,
    val settings: AppSettings,
    /** True during background heartbeats (the user is not watching). */
    val background: Boolean,
    /** The persona's message that made this tool call (memories link back to it). */
    val messageId: String? = null,
)

data class ToolOutcome(
    /** Text returned to the model. */
    val content: String,
    val isError: Boolean = false,
    /** Media shown to the user alongside the tool result (e.g. generated images). */
    val attachments: List<ContentPart.Image> = emptyList(),
)

/**
 * An agent-callable tool. To add one: implement this, then register it with [ToolRegistry.register].
 * Keep [spec] deterministic (no timestamps or per-user text) so the prompt prefix stays stable.
 */
interface AgentTool {
    val spec: ToolSpec

    /** Whether the tool is offered under the current settings. */
    fun isAvailable(settings: AppSettings): Boolean = true

    /** Whether it's offered to this persona (its own switches); by default, whenever [isAvailable]. */
    fun isAvailableFor(settings: AppSettings, persona: Persona): Boolean = isAvailable(settings)

    suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome
}

class ToolRegistry(tools: List<AgentTool> = emptyList()) {
    private val tools = linkedMapOf<String, AgentTool>().apply { tools.forEach { put(it.spec.name, it) } }

    fun register(tool: AgentTool) {
        tools[tool.spec.name] = tool
    }

    /** Available tools, sorted by name for a stable request prefix; with a [persona], its own switches count too. */
    fun available(settings: AppSettings, persona: Persona? = null): List<AgentTool> =
        tools.values.filter { it.isAvailable(settings) && (persona == null || it.isAvailableFor(settings, persona)) }.sortedBy { it.spec.name }

    suspend fun execute(call: LlmToolCall, context: ToolContext): ToolOutcome {
        val tool = tools[call.name] ?: return ToolOutcome("Unknown tool '${call.name}'.", isError = true)
        if (!tool.isAvailable(context.settings) || !tool.isAvailableFor(context.settings, context.persona)) {
            return ToolOutcome("Tool '${call.name}' is disabled.", isError = true)
        }
        val args = try {
            HarnessJson.parseToJsonElement(call.argumentsJson.ifBlank { "{}" }).jsonObject
        } catch (e: Exception) {
            return ToolOutcome("INVALID_JSON: the arguments were not a valid JSON object. Try again.", isError = true)
        }
        // Streamed tool input may be truncated; never run a tool without its required arguments.
        val missing = (tool.spec.parameters["required"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { key -> args[key] == null || args[key] is kotlinx.serialization.json.JsonNull }
        if (missing.isNotEmpty()) {
            return ToolOutcome("INVALID_ARGUMENTS: missing required ${missing.joinToString { "'$it'" }}.", isError = true)
        }
        return try {
            tool.execute(args, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            ToolOutcome("Invalid arguments: ${e.message}", isError = true)
        } catch (e: Exception) {
            ToolOutcome("Tool failed: ${e.message ?: e::class.simpleName}", isError = true)
        }
    }
}

// ---------------------------------------------------------------------------
// Small JSON Schema DSL and argument helpers
// ---------------------------------------------------------------------------

class SchemaBuilder {
    private val properties = linkedMapOf<String, JsonObject>()
    private val required = mutableListOf<String>()

    private fun prop(name: String, isRequired: Boolean, block: JsonObjectBuilder.() -> Unit) {
        properties[name] = buildJsonObject(block)
        if (isRequired) required += name
    }

    fun string(name: String, description: String, required: Boolean = false, enum: List<String>? = null) =
        prop(name, required) {
            put("type", "string")
            put("description", description)
            enum?.let { values -> put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) }
        }

    fun integer(name: String, description: String, required: Boolean = false, min: Int? = null, max: Int? = null) =
        prop(name, required) {
            put("type", "integer")
            put("description", description)
            min?.let { put("minimum", it) }
            max?.let { put("maximum", it) }
        }

    fun boolean(name: String, description: String, required: Boolean = false) =
        prop(name, required) {
            put("type", "boolean")
            put("description", description)
        }

    fun stringArray(name: String, description: String, required: Boolean = false) =
        prop(name, required) {
            put("type", "array")
            put("description", description)
            put("items", buildJsonObject { put("type", "string") })
        }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(properties))
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
}

fun schema(block: SchemaBuilder.() -> Unit): JsonObject = SchemaBuilder().apply(block).build()

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.requireString(key: String): String =
    string(key) ?: throw IllegalArgumentException("'$key' is required")

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> value.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    else -> emptyList()
}
