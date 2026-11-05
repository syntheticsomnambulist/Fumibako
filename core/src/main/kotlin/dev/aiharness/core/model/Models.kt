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
    /** The start of the latest visible persona letter, for the letters list. */
    val preview: String? = null,
    /** Whether that letter came from a heartbeat ("left for you while you were away"). */
    val previewFromHeartbeat: Boolean = false,
    /** Frozen prompt state for the current context epoch (see ContextBuilder). */
    val context: ContextState? = null,
    /** Last message already folded into an episode memory by background upkeep. */
    val lastEpisodeMessageId: String? = null,
    val archived: Boolean = false,
) {
    val hasUnread: Boolean
        get() = lastAgentMessageAt != null && lastAgentMessageAt > (lastReadAt ?: 0L)

    companion object {
        /** One line of [text] for a preview. */
        fun previewOf(text: String): String =
            text.replace(Regex("[*`_]{1,2}"), "").replace(Regex("\\s+"), " ").trim().take(PREVIEW_LENGTH)

        private const val PREVIEW_LENGTH = 220
    }
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

/** How much a memory matters, chosen by the persona in words. */
@Serializable
enum class MemoryLevel(val label: String, val toolValue: String) {
    IN_PASSING("In passing", "in_passing"),
    WORTH_KEEPING("Worth keeping", "worth_keeping"),
    MATTERS_DEEPLY("Matters deeply", "matters_deeply"),
    ;

    companion object {
        /** Lenient parsing for tool input: the three names, common synonyms, or an old 1 to 10 score. */
        fun parse(raw: String?): MemoryLevel? {
            val value = raw?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            value.toIntOrNull()?.let { n ->
                return when {
                    n <= 3 -> IN_PASSING
                    n <= 7 -> WORTH_KEEPING
                    else -> MATTERS_DEEPLY
                }
            }
            return when (value) {
                "in_passing", "passing", "minor", "low", "small", "trivial", "light" -> IN_PASSING
                "worth_keeping", "keeping", "normal", "medium", "default", "moderate" -> WORTH_KEEPING
                "matters_deeply", "deeply", "major", "high", "deep", "important", "critical", "defining" -> MATTERS_DEEPLY
                else -> null
            }
        }
    }
}

@Serializable
data class MemoryEntry(
    val id: String,
    val personaId: String,
    val content: String,
    val kind: MemoryKind = MemoryKind.FACT,
    val level: MemoryLevel = MemoryLevel.WORTH_KEEPING,
    val tags: List<String> = emptyList(),
    /** Always in mind: kept in the system prompt (different from "matters deeply"). */
    val pinned: Boolean = false,
    /** Memories about the user can be scoped to one user persona; null means "always relevant". */
    val aboutUserPersonaId: String? = null,
    val sourceConversationId: String? = null,
    /** The persona's message the memory was kept in, for "open the letter it came from". */
    val sourceMessageId: String? = null,
    /** The persona's mood word when the memory was kept. Never changes once set. */
    val feltMood: String? = null,
    /** Whether the persona chose [feltMood] itself (rather than it coming from the letter's mood). */
    val feltMoodChosen: Boolean = false,
    /** How the persona feels about it now, when that has changed; null means "as it felt". */
    val mood: String? = null,
    /** The persona's one-line reason for the change of feeling. */
    val moodReason: String? = null,
    val moodRevisedAt: Long? = null,
    val moodRevisions: Int = 0,
    /** A newer memory that replaced this one; replaced memories stay as history but aren't recalled. */
    val replacedById: String? = null,
    val replacedAt: Long? = null,
    /** The user wrote, edited or confirmed this memory. */
    val userTouched: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** Last time it came up at all, including automatic recall into a letter. */
    val lastAccessedAt: Long,
    val accessCount: Int = 0,
    /** Last real use (searched for, cited in a reflection, kept again, edited): this keeps it strong. */
    val lastUsedAt: Long = createdAt,
    val useCount: Int = 0,
) {
    val isCore: Boolean get() = pinned || kind == MemoryKind.CORE
    val isReplaced: Boolean get() = replacedById != null

    /** How it feels now: the revised feeling, or the one it was kept with. */
    val currentMood: String? get() = mood ?: feltMood

    /** Whether the persona picked the feeling deliberately (when kept, or by revising it). */
    val feelingChosen: Boolean get() = feltMoodChosen || mood != null
}
