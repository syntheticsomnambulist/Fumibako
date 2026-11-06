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
    /** The usual rhythm; a persona can have its own ([Persona.ownHeartbeat]). */
    val rhythm: HeartbeatRhythm = HeartbeatRhythm(),
    /** Mention the elapsed time in a turn when the gap since the last message exceeds this. */
    val timeGapNoteMinutes: Int = 30,
    val requireUnmeteredNetwork: Boolean = false,
) {
    /** The rhythm a persona's heartbeats follow. */
    fun rhythmFor(persona: Persona): HeartbeatRhythm = persona.ownHeartbeat ?: rhythm
}

/** How often a persona wakes, and when and how often it may write on its own. */
@Serializable
data class HeartbeatRhythm(
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
    /**
     * Most meaningful actions in the persona's own voice one heartbeat may take: writing to the user
     * now, writing to another persona later. Reflecting and tending memories are free.
     */
    val activityLimit: Int = 3,
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
    val showAging: Boolean = true,
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
    /** Where pictures are drawn; kept here rather than in the database (see plan-v0.3.md). */
    val profiles: List<ImageProfile> = emptyList(),
    /** The profile pictures use unless a persona has its own; null means the first one. */
    val defaultProfileId: String? = null,
    /** Artist and style tags every persona draws in, unless it has its own. */
    val defaultStyle: String = "",
) {
    /** The profile for a persona's pictures: its own, the default, or the first. */
    fun profileFor(personaProfileId: String?): ImageProfile? =
        profiles.firstOrNull { it.id == personaProfileId } ?: profiles.firstOrNull { it.id == defaultProfileId } ?: profiles.firstOrNull()

    /** Whether any picture can be drawn at all. */
    val ready: Boolean get() = enabled && profiles.any { it.isUsable }
}

/** Where a picture is drawn. */
@Serializable
enum class ImageHost(val label: String, val needsKey: Boolean, val needsServer: Boolean) {
    NOVELAI("NovelAI", needsKey = true, needsServer = false),
    STABLE_DIFFUSION("Forge or Automatic1111", needsKey = false, needsServer = true),
    COMFYUI("ComfyUI", needsKey = false, needsServer = true),
    OPENAI("OpenAI", needsKey = true, needsServer = false),
    GOOGLE("Google", needsKey = true, needsServer = false),
    ;

    /** Tag models take comma-separated tags; the others work from sentences. */
    val takesTags: Boolean get() = this == NOVELAI || this == STABLE_DIFFUSION || this == COMFYUI
}

/**
 * One way of drawing pictures: a host, its key or server, the model, the size, and the options
 * that host has. Fields a host doesn't use are ignored.
 */
@Serializable
data class ImageProfile(
    val id: String,
    val name: String,
    val host: ImageHost = ImageHost.NOVELAI,
    val credentialId: String? = null,
    /** A local server's address (Forge, Automatic1111, ComfyUI), or another endpoint for a hosted one. */
    val baseUrl: String? = null,
    /** NovelAI, OpenAI or Google model id; the checkpoint for Stable Diffusion and ComfyUI. */
    val model: String = "",
    val width: Int = 832,
    val height: Int = 1216,
    val steps: Int = 23,
    val scale: Double = 5.0,
    val sampler: String = "k_euler_ancestral",
    /** NovelAI's noise schedule; ComfyUI's and Forge's scheduler. */
    val noiseSchedule: String = "karras",
    /** NovelAI: add its quality tags. */
    val qualityToggle: Boolean = true,
    /** NovelAI's undesired content preset: heavy, light, human, none. */
    val ucPreset: String = "heavy",
    val negativePrompt: String = "",
    /** OpenAI: auto, low, medium or high. */
    val quality: String = "auto",
    /** ComfyUI: a workflow in its API format, with %prompt% and friends; null uses the default one. */
    val workflow: String? = null,
) {
    /** Has what its host needs to draw. */
    val isUsable: Boolean get() = (!host.needsKey || credentialId != null) && (!host.needsServer || !baseUrl.isNullOrBlank())
}

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
