package dev.aiharness.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Models & providers
// ---------------------------------------------------------------------------

@Serializable
enum class ProviderType(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI_COMPATIBLE("OpenAI-compatible"),
    GEMINI("Google Gemini"),
    NOVELAI_TEXT("NovelAI (text)"),
}

/** How tools are offered to a model. */
@Serializable
enum class ToolMode {
    /** Provider-native function calling. */
    NATIVE,

    /** Tools described in the prompt; calls parsed from `<tool_call>` tags in the reply. */
    EMULATED,

    /** No tools. Memory is formed by background upkeep instead. */
    NONE,
}

/** A named API credential. The secret itself lives in a [dev.aiharness.core.storage.SecretStore]. */
@Serializable
data class Credential(
    val id: String,
    val label: String,
    val kind: CredentialKind,
)

@Serializable
enum class CredentialKind { ANTHROPIC, OPENAI_COMPATIBLE, GEMINI, NOVELAI, OTHER }

/** A swappable model configuration. Conversations reference one by id. */
@Serializable
data class ModelProfile(
    val id: String,
    val name: String,
    val provider: ProviderType,
    val modelId: String,
    /** Overrides the provider's default endpoint (required for most OpenAI-compatible servers). */
    val baseUrl: String? = null,
    val credentialId: String? = null,
    val maxOutputTokens: Int = 4096,
    /** Hard context limit of the model. */
    val contextWindowTokens: Int = 128_000,
    /** Soft budget for the prompt; history beyond it is compacted into a summary. */
    val contextBudgetTokens: Int = 32_000,
    /** Ignored by Anthropic (current Claude models reject sampling parameters). */
    val temperature: Double? = null,
    val topP: Double? = null,
    /** Anthropic `effort`, OpenAI `reasoning_effort`, Gemini thinking level. */
    val reasoningEffort: String? = null,
    val toolMode: ToolMode = ToolMode.NATIVE,
    val supportsVision: Boolean = false,
    /** Extra fields merged into the JSON body for HTTP providers (e.g. `min_p`). */
    val extraBody: JsonObject? = null,
    val options: ProviderOptions = ProviderOptions(),
)

@Serializable
data class ProviderOptions(
    val anthropicAdaptiveThinking: Boolean = true,
    /** `summarized` streams readable reasoning summaries; `omitted` hides them. */
    val anthropicThinkingDisplay: String = "summarized",
    /** Server-side refusal fallback (`fallbacks: "default"`) on models that support it. */
    val anthropicServerFallback: Boolean = true,
    val anthropicPromptCaching: Boolean = true,
    /** Newer OpenAI models want `max_completion_tokens`; most compatible servers use `max_tokens`. */
    val openAiMaxTokensField: String = "max_tokens",
    val openAiIncludeUsage: Boolean = true,
)

// ---------------------------------------------------------------------------
// Personas
// ---------------------------------------------------------------------------

/** Washi tape patterns, part of a persona's (and the user's) look alongside its ink. */
@Serializable
enum class TapePattern { PLAIN, DOTS, STRIPES, GINGHAM }

/** An agent persona. Each persona owns its own memories. */
@Serializable
data class Persona(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val greeting: String = "",
    val exampleDialogue: String = "",
    /** Replaces the generated role section of the system prompt. Supports {{char}} / {{user}}. */
    val systemPromptOverride: String? = null,
    /** Image-model tags describing how this persona looks (used by image generation). */
    val appearanceTags: String = "",
    val avatarRef: String? = null,
    val defaultModelProfileId: String? = null,
    val heartbeatEnabled: Boolean = true,
    /** Signature ink: one of the madoromi accent ink names (see `docs/design`). */
    val ink: String = "sakura",
    val tapePattern: TapePattern = TapePattern.DOTS,
    /** What an unread letter from this persona wears; null is the default seal. Stickers come in v0.2. */
    val unreadMark: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Who the user is presenting as. Swappable per conversation. */
@Serializable
data class UserPersona(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarRef: String? = null,
    val ink: String = "sora",
    val tapePattern: TapePattern = TapePattern.STRIPES,
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------------------
// Conversations
// ---------------------------------------------------------------------------

@Serializable
data class Conversation(
    val id: String,
    val personaId: String,
    val userPersonaId: String? = null,
    val modelProfileId: String? = null,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUserMessageAt: Long? = null,
    /** When the latest visible persona message arrived, for unread marks and the letters list. */
    val lastAgentMessageAt: Long? = null,
    /** When the user last had this conversation open. */
    val lastReadAt: Long? = null,
    /** The persona's latest self-chosen mood in this conversation (see [MoodTags]). */
    val mood: String? = null,
    /** Frozen prompt state for the current context epoch (see ContextBuilder). */
    val context: ContextState? = null,
    /** Last message already folded into an episode memory by background upkeep. */
    val lastEpisodeMessageId: String? = null,
    val archived: Boolean = false,
) {
    val hasUnread: Boolean
        get() = lastAgentMessageAt != null && lastAgentMessageAt > (lastReadAt ?: 0L)
}

/**
 * The prompt state of one context "epoch". Within an epoch the system prompt and tool set are frozen
 * and history is append-only, so provider replay data (Claude thinking signatures, Gemini thought
 * signatures) stays valid and prompt caches keep hitting. A new epoch starts on compaction or when
 * the persona / user persona / tool configuration changes.
 */
@Serializable
data class ContextState(
    val epochId: String,
    val systemPrompt: String,
    val configFingerprint: String,
    val toolFingerprint: String,
    val windowStartMessageId: String? = null,
    val summary: String? = null,
    val startedAt: Long,
)

@Serializable
enum class MessageRole { USER, ASSISTANT, TOOL }

@Serializable
enum class MessageOrigin { USER, AGENT, HEARTBEAT, SYSTEM }

@Serializable
sealed interface ContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart

    @Serializable
    @SerialName("image")
    data class Image(val ref: String, val mimeType: String = "image/png", val caption: String? = null) : ContentPart

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : ContentPart

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val callId: String,
        val name: String,
        val content: String,
        val isError: Boolean = false,
    ) : ContentPart

    /** Displayable reasoning summary. Never sent back to providers as text. */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(val text: String) : ContentPart
}

@Serializable
data class TokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val parts: List<ContentPart>,
    val createdAt: Long,
    val origin: MessageOrigin = MessageOrigin.USER,
    /** App-written context (time, recalled memories) attached to a user turn when it was sent. */
    val turnContext: String? = null,
    val epochId: String? = null,
    val providerType: ProviderType? = null,
    val modelProfileId: String? = null,
    val modelId: String? = null,
    /** Opaque provider-native serialization of an assistant turn, replayed verbatim when valid. */
    val replay: String? = null,
    /** Not shown in the chat UI (e.g. heartbeat prompts). */
    val hidden: Boolean = false,
    /** Kept for the record but never sent to a model (e.g. refusals, failed turns). */
    val excludedFromContext: Boolean = false,
    val errorMessage: String? = null,
    val usage: TokenUsage? = null,
    /** Mood the persona chose for this message, parsed from its `[mood: ...]` tag. */
    val mood: String? = null,
) {
    /** The text as stored and replayed to the model, including any mood tag. */
    val text: String
        get() = parts.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text }

    /** The text as shown to the user: mood tags removed. */
    val displayText: String
        get() = MoodTags.strip(text)

    val toolCalls: List<ContentPart.ToolCall>
        get() = parts.filterIsInstance<ContentPart.ToolCall>()

    val toolResults: List<ContentPart.ToolResult>
        get() = parts.filterIsInstance<ContentPart.ToolResult>()

    val images: List<ContentPart.Image>
        get() = parts.filterIsInstance<ContentPart.Image>()

    val reasoning: String?
        get() = parts.filterIsInstance<ContentPart.Reasoning>().joinToString("\n") { it.text }.ifBlank { null }
}

// ---------------------------------------------------------------------------
// Memory
// ---------------------------------------------------------------------------

@Serializable
enum class MemoryKind {
    /** Always-in-mind memories, included in the system prompt. */
    CORE,

    /** A discrete fact or preference. */
    FACT,

    /** A summary of something that happened (from compaction or background upkeep). */
    EPISODE,

    /** The persona's own thoughts and conclusions (often written during heartbeats). */
    REFLECTION,
}

@Serializable
data class MemoryEntry(
    val id: String,
    val personaId: String,
    val content: String,
    val kind: MemoryKind = MemoryKind.FACT,
    /** 1 (trivial) .. 10 (defining). */
    val importance: Int = 5,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    /** Memories about the user can be scoped to one user persona; null means "always relevant". */
    val aboutUserPersonaId: String? = null,
    val sourceConversationId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessedAt: Long,
    val accessCount: Int = 0,
) {
    val isCore: Boolean get() = pinned || kind == MemoryKind.CORE
}
