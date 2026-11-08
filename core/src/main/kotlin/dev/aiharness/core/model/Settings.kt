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
    val stickers: StickerSettings = StickerSettings(),
    val media: MediaSettings = MediaSettings(),
    val planner: PlannerSettings = PlannerSettings(),
    /** How long a persona's reply takes to reach you. */
    val letters: LetterSettings = LetterSettings(),
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

/**
 * Letters in the post: a persona's reply is written as soon as you send your note, then kept back
 * until it arrives, a little while later. Letters a persona writes on its own (heartbeats) come
 * when they come.
 */
@Serializable
data class LetterSettings(
    /** How long replies take to reach you, unless a persona has its own. */
    val travel: LetterTravel = LetterTravel.MINUTES,
    /** The thread shows when a letter on its way arrives (on a little watch), not only that it's coming. */
    val showArrival: Boolean = false,
)

/** How long a reply takes to reach you: somewhere between [minMs] and [maxMs], so it never comes like clockwork. */
@Serializable
enum class LetterTravel(val label: String, val minMs: Long, val maxMs: Long) {
    STRAIGHT_AWAY("Straight away", 0L, 0L),
    MINUTES("A few minutes", 2 * MINUTE_MS, 3 * MINUTE_MS),
    HALF_HOUR("About half an hour", 20 * MINUTE_MS, 40 * MINUTE_MS),
    HOURS("A few hours", 2 * HOUR_MS, 4 * HOUR_MS),
    DAY("About a day", 20 * HOUR_MS, 28 * HOUR_MS),
    DAYS("Two or three days", 48 * HOUR_MS, 72 * HOUR_MS),
    ;

    /** A travel time in this one's range. */
    fun pick(random: kotlin.random.Random = kotlin.random.Random.Default): Long =
        if (maxMs <= minMs) minMs else minMs + random.nextLong(maxMs - minMs + 1)
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L

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

/** Which side of the equator you're on, which turns the year's seasons half a year round. */
@Serializable
enum class Hemisphere { NORTH, SOUTH }

/**
 * What the theme's seasons follow: the weather's seasons (spring from March, or September south
 * of the equator), or the solar terms the planner shows (spring from 立春, in early February).
 */
@Serializable
enum class SeasonsFollow(val label: String) {
    WEATHER("The weather"),
    SOLAR_TERMS("The solar terms"),
}

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
    /** How times are written across the app: as the phone has it, or always 12- or 24-hour. */
    val clock: ClockStyle = ClockStyle.PHONE,
    /** Minutes after local midnight. */
    val dayStartMinute: Int = 6 * 60,
    val dayEndMinute: Int = 18 * 60,
    val staticSeason: Season = Season.HARU,
    val staticTime: TimeOfDay = TimeOfDay.HIRU,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    /** What the seasons follow, for the theme (the planner's season band always shows the solar terms). */
    val seasonsFollow: SeasonsFollow = SeasonsFollow.WEATHER,
    /** Your side of the equator, for the theme and the planner; null works it out (from the saved location, else the time zone). */
    val hemisphere: Hemisphere? = null,
    /**
     * Die-cut outline colour for stickers, also the default polaroid frame colour: a hex colour, or
     * [OUTLINE_PAPER] or [OUTLINE_INK] (the ink of whoever stuck it).
     */
    val outlineColor: String = "#FFFFFF",
    val polaroidFrame: FrameStyle = FrameStyle.MATCH_OUTLINE,
    /** Brush-stroke signature under letters written while the user was away. */
    val inkSignature: Boolean = false,
    /** How things on the desk (stickers, and later photos and other media) look at night. */
    val lamplight: Lamplight = Lamplight.DIM_ALL,
) {
    companion object {
        const val OUTLINE_PAPER = "paper"
        const val OUTLINE_INK = "ink"
    }
}

/**
 * At night, stickers and media keep their day colours, lit as if by a desk lamp: dimmed a little
 * and warmed (see the design session's `media.md`).
 */
@Serializable
enum class Lamplight(val label: String) {
    DIM_ALL("Dim and warm everything"),
    KEEP_PHOTOS("Leave photos as they are"),
    NONE("Leave everything as it is"),
}

@Serializable
enum class StickerSize(val label: String, val dp: Int) { SMALL("Small", 40), MEDIUM("Medium", 52), LARGE("Large", 66) }

@Serializable
enum class OutlineWidth(val label: String, val dp: Float) { THIN("Thin", 1.5f), USUAL("Usual", 2.5f), THICK("Thick", 3.5f) }

/** Links, pictures and playback (the Media page). */
@Serializable
data class MediaSettings(
    /** Open links in the app's own browser (Custom Tabs); off, in the phone's default browser. */
    val linksInApp: Boolean = true,
    /** Fetch a page's title and picture for its cutting (which contacts the site); off, just the address. */
    val linkPreviews: Boolean = true,
    /** Personas can open links you share (Claude's web fetch, or the app's fetcher on the phone). */
    val personasOpenLinks: Boolean = true,
    /** Send pictures at full size rather than about 1280 px (more tokens each time a model sees them). */
    val fullSizePictures: Boolean = false,
    /** Write a short description of what arrives, which later context epochs carry instead of the file. */
    val describe: Boolean = true,
    /** Keep music playing after leaving the thread. */
    val keepPlaying: Boolean = true,
    /** Start videos with the sound off. */
    val videosMuted: Boolean = false,
    /** The frames' animations (apart from Android's "Remove animations", which turns them all off). */
    val paperMotion: Boolean = true,
    /** The light haptic ticks (a read stamp landing). */
    val haptics: Boolean = true,
    /** Which app a song opens in, to hear the whole of it. */
    val songsOpenIn: SongsOpenIn = SongsOpenIn.AS_LINKED,
) {
    /** The longest side pictures you send are scaled to. */
    val pictureSide: Int get() = if (fullSizePictures) 4096 else 1280
}

/** How times are written: as the phone has it, or always on a 12- or 24-hour clock. */
@Serializable
enum class ClockStyle { PHONE, H12, H24 }

/** The planner's page (docs/design/calendar.md on the design branch). */
@Serializable
data class PlannerSettings(
    /** The week page starts on Sunday instead of Monday. */
    val weekStartsSunday: Boolean = false,
    /** Hours you're asleep on every day of the week shown fold to a thin band (tap it to open them). */
    val foldSleep: Boolean = true,
    /** The season band: the solar terms and microseasons on the planner's pages, and a line to personas as each begins. */
    val seasonBand: Boolean = true,
    /** The day the ribbon (栞) marks, ISO; null marks today. A long press moves it to the page shown; a tap goes back to it. */
    val bookmark: String? = null,
    val holidays: HolidaySettings = HolidaySettings(),
    val phone: PhoneCalendarSettings = PhoneCalendarSettings(),
    /** What a persona's letter does while your planner says you're busy. */
    val duringBusy: DuringBusy = DuringBusy.HOLD,
    /** Your typical week's sleep is the heartbeat's quiet hours too (so they're set in one place). */
    val sleepQuietHours: Boolean = false,
)

/** A persona's letter during a busy stretch: its notification waits for the end, comes anyway, or doesn't come. */
@Serializable
enum class DuringBusy(val label: String) {
    HOLD("Hold notifications"),
    NOTIFY("Notify anyway"),
    SILENT("Don't notify"),
}

/**
 * The phone's calendars in your default persona's planner (docs/design/calendar.md, "Phone
 * calendars"): read live and never copied, each at the detail level you chose, which is also what
 * a persona that sees your planner sees. Nothing shows until you choose.
 */
@Serializable
data class PhoneCalendarSettings(
    /** Each calendar's detail level, by the phone's calendar id; one not listed is hidden. */
    val levels: Map<String, PhoneDetail> = emptyMap(),
    /** Your stamps on its events (done, not going), kept here since nothing is written back. */
    val stamps: List<PhoneStamp> = emptyList(),
) {
    fun level(calendarId: String): PhoneDetail = levels[calendarId] ?: PhoneDetail.HIDDEN
}

@Serializable
enum class PhoneDetail(val label: String) {
    HIDDEN("Hidden"),
    BUSY("Busy only"),
    TITLES("Titles"),
    PLACES("Titles and places"),
}

/** A stamp on one occurrence of a phone event (its event id and start), done or not going ([EntryState.CANCELLED]). */
@Serializable
data class PhoneStamp(val eventId: Long, val start: Long, val state: EntryState, val at: Long)

/**
 * Public holidays in the planner: off until you choose whose, then fetched from Nager.Date once a
 * year (only the country and the year are sent) and kept here. A holiday leaves your typical week
 * alone, since few are days off for everyone.
 */
@Serializable
data class HolidaySettings(
    val show: Boolean = false,
    /** ISO country code ("US"). */
    val country: String? = null,
    /** A region within it where holidays differ ("US-MA"); null shows only the nationwide ones. */
    val region: String? = null,
    /** The years fetched for [country], kept until they're out of date. */
    val years: List<HolidayYear> = emptyList(),
)

@Serializable
data class HolidayYear(val country: String, val year: Int, val fetchedAt: Long, val holidays: List<Holiday>)

/** A public holiday: its day (ISO), its English name and its own, and the regions it's kept in (none: everywhere). */
@Serializable
data class Holiday(
    val date: String,
    val name: String,
    val localName: String? = null,
    val regions: List<String> = emptyList(),
) {
    fun keptIn(region: String?): Boolean = regions.isEmpty() || (region != null && region in regions)
}

/** Where a song opens to be heard whole: the service its link is from, or always one app. */
@Serializable
enum class SongsOpenIn(val label: String) {
    AS_LINKED("Wherever its link is from"),
    SPOTIFY("Spotify"),
    APPLE_MUSIC("Apple Music"),
    YOUTUBE_MUSIC("YouTube Music"),
}

@Serializable
data class StickerSettings(
    /** How big a new sticker lands; it can be resized after. */
    val size: StickerSize = StickerSize.MEDIUM,
    val outlineWidth: OutlineWidth = OutlineWidth.USUAL,
    /** The most stickers on one letter or note, yours and the persona's together. */
    val maxPerLetter: Int = 6,
)
