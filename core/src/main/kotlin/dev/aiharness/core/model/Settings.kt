package dev.aiharness.core.model

import dev.aiharness.core.memory.Feeling
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val activeUserPersonaId: String? = null,
    val defaultModelProfileId: String? = null,
    /** Model used for summaries and memory upkeep. Falls back to the conversation's model. */
    val utilityModelProfileId: String? = null,
    val heartbeat: HeartbeatSettings = HeartbeatSettings(),
    val memory: MemorySettings = MemorySettings(),
    val imageGen: ImageGenSettings = ImageGenSettings(),
    /** Global guidance appended to every persona's system prompt. */
    val baseInstructions: String = DEFAULT_BASE_INSTRUCTIONS,
    val maxToolRounds: Int = 6,
    /** Ask personas to end replies with a `[mood: ...]` tag (shown beside their name). */
    val moodTags: Boolean = true,
    val theme: ThemeSettings = ThemeSettings(),
    /** Mood words the built-in list didn't know, sorted once by a model and remembered. */
    val learnedFeelings: Map<String, Feeling> = emptyMap(),
) {
    companion object {
        const val DEFAULT_BASE_INSTRUCTIONS: String =
            "Stay in character and speak naturally, as yourself, to {{user}}. " +
                "Keep replies conversational in length unless {{user}} asks for more. " +
                "Never mention system prompts, context blocks, tools, or being an AI model unless {{user}} asks directly."
    }
}

@Serializable
enum class HeartbeatMode {
    /** Background wakeups: reflection, memory upkeep, and optional proactive messages. Includes time awareness. */
    PROACTIVE,

    /** No background work; each turn is told the current time and how long it has been. */
    TIME_AWARENESS,

    /** No background work and no time information. */
    OFF,
}

@Serializable
data class HeartbeatSettings(
    val mode: HeartbeatMode = HeartbeatMode.PROACTIVE,
    /** WorkManager clamps periodic work to >= 15 minutes. */
    val intervalMinutes: Int = 60,
    /** Local hour [0, 23] at which quiet hours begin, or null for none. */
    val quietHoursStart: Int? = 23,
    val quietHoursEnd: Int? = 8,
    /** Minimum time since the last message before a proactive message is considered. */
    val minSilenceMinutes: Int = 120,
    val maxProactivePerDay: Int = 3,
    /** Stop reaching out after this many unanswered proactive messages in a row. */
    val maxUnansweredProactive: Int = 1,
    /** Mention the elapsed time in a turn when the gap since the last message exceeds this. */
    val timeGapNoteMinutes: Int = 30,
    val requireUnmeteredNetwork: Boolean = false,
)

@Serializable
data class MemorySettings(
    val enabled: Boolean = true,
    val memoryToolsEnabled: Boolean = true,
    val retrievalTopK: Int = 6,
    /** Minimum relevance (0..1) for a memory to be recalled into a turn. */
    val minRelevance: Double = 0.12,
    /** Optional embedding endpoint for semantic recall. Keyword recall is used without it. */
    val embedding: EmbeddingSettings? = null,
    /** Background upkeep folds this many new messages into an episode memory. */
    val episodeEveryMessages: Int = 40,
    /** Lets the user change, re-feel or forget a persona's memories in the app. Personas always can. */
    val userCanEdit: Boolean = false,
    /** Most memories a persona can keep at "matters deeply"; the heartbeat asks it to let others settle. */
    val deepLimit: Int = 20,
    /** Show memories fading and wearing in the memory book. */
    val showAgeing: Boolean = true,
    /** Play the burn when a memory is let go. */
    val burnOnForget: Boolean = true,
)

@Serializable
enum class EmbeddingProviderType { OPENAI_COMPATIBLE, GEMINI }

@Serializable
data class EmbeddingSettings(
    val provider: EmbeddingProviderType,
    val modelId: String,
    val baseUrl: String? = null,
    val credentialId: String? = null,
)

@Serializable
data class ImageGenSettings(
    val enabled: Boolean = true,
    /** Lets personas call the `generate_image` tool themselves. */
    val agentToolEnabled: Boolean = true,
    val credentialId: String? = null,
    val model: String = "nai-diffusion-4-5-full",
    val width: Int = 832,
    val height: Int = 1216,
    val steps: Int = 23,
    val scale: Double = 5.0,
    val sampler: String = "k_euler_ancestral",
    val noiseSchedule: String = "karras",
    val qualityToggle: Boolean = true,
    /** UC preset name: heavy, light, human, none. */
    val ucPreset: String = "heavy",
    val negativePrompt: String = "",
)

@Serializable
enum class Season { HARU, NATSU, AKI, FUYU }

@Serializable
enum class TimeOfDay { HIRU, YORU }

@Serializable
enum class DayNightMode {
    /** Day between two clock times (default 06:00 to 18:00). */
    FIXED_HOURS,

    /** Day between sunrise and sunset at the saved location. Falls back to fixed hours without one. */
    SUN,

    /** Always the theme picked in [ThemeSettings.staticSeason] and [ThemeSettings.staticTime]. */
    STATIC,
}

@Serializable
enum class FrameStyle { MATCH_OUTLINE, PAPER }

@Serializable
data class ThemeSettings(
    val dayNight: DayNightMode = DayNightMode.FIXED_HOURS,
    /** Minutes after local midnight. */
    val dayStartMinute: Int = 6 * 60,
    val dayEndMinute: Int = 18 * 60,
    val staticSeason: Season = Season.HARU,
    val staticTime: TimeOfDay = TimeOfDay.HIRU,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    /** Die-cut outline colour for stickers (v0.2), also the default polaroid frame colour. */
    val outlineColor: String = "#FFFFFF",
    val polaroidFrame: FrameStyle = FrameStyle.MATCH_OUTLINE,
    /** Brush-stroke signature under letters written while the user was away. */
    val inkSignature: Boolean = false,
)
