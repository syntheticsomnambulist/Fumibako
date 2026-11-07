package dev.aiharness.core.media

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.ToolMode
import dev.aiharness.core.model.Track
import dev.aiharness.core.model.clock

/** What a model takes in besides text; [youtube]: it watches a YouTube link itself. */
data class Senses(val pictures: Boolean, val pdfs: Boolean, val audio: Boolean, val video: Boolean, val youtube: Boolean = false) {
    companion object {
        val TEXT_ONLY = Senses(pictures = false, pdfs = false, audio = false, video = false)
    }
}

/**
 * What each provider's models take in (docs/design/media.md, "What the persona receives"), what
 * that costs, and the line the app writes under a frame when the persona can't take it in.
 *
 * | | Claude | Gemini | OpenAI-compatible |
 * | --- | --- | --- | --- |
 * | Pictures | Yes | Yes | With "Sees pictures" |
 * | PDFs | Yes | Yes | The first page, as a picture |
 * | Text files | As text | As text | As text |
 * | Audio | No | Yes | No |
 * | Video | Three frames | Yes, its first part when long | Three frames |
 * | YouTube links | No | Yes, by their address | No |
 *
 * A video may take up to half of the persona's context budget ([videoCap]), more than other
 * things; a longer one is watched as its first part (Gemini clips it), and the note says so.
 */
object MediaSupport {
    fun of(profile: ModelProfile): Senses = when (profile.provider) {
        // Every Claude model sees pictures and reads PDFs.
        ProviderType.ANTHROPIC -> Senses(pictures = true, pdfs = true, audio = false, video = false)
        // Gemini models take everything; Gemma through the same API takes pictures only.
        ProviderType.GEMINI -> {
            val gemini = profile.modelId.isBlank() || profile.modelId.lowercase().removePrefix("models/").startsWith("gemini")
            Senses(pictures = true, pdfs = gemini, audio = gemini, video = gemini, youtube = gemini)
        }
        ProviderType.OPENAI_COMPATIBLE -> Senses(pictures = profile.supportsVision, pdfs = false, audio = false, video = false)
        ProviderType.NOVELAI_TEXT -> Senses.TEXT_ONLY
    }

    /**
     * The bytes of pictures and files one request may carry, under each provider's request size
     * (Claude 32 MB, Gemini 20 MB inline) once base64 has added its third.
     */
    fun inlineBudget(profile: ModelProfile): Long = when (profile.provider) {
        ProviderType.ANTHROPIC -> 20L * MB
        ProviderType.GEMINI -> 14L * MB
        ProviderType.OPENAI_COMPATIBLE -> 14L * MB
        ProviderType.NOVELAI_TEXT -> 0L
    }

    /** The most of its context budget one thing may take up whole; anything bigger goes as its stand-in. */
    fun tokenCap(profile: ModelProfile): Int = profile.contextBudgetTokens / 3

    /** The most a video (or YouTube link) may take: half the budget. A longer one is watched as its first part. */
    fun videoCap(profile: ModelProfile): Int = profile.contextBudgetTokens / 2

    /** About what a second of video costs: Gemini 3 and later, about 70 a frame plus its sound; older ones, about 290. */
    fun videoTokensPerSecond(profile: ModelProfile): Int = if (modernGemini(profile)) 100 else 300

    /** The seconds of video [profile]'s model watches within [videoCap]. */
    fun watchableSeconds(profile: ModelProfile): Int = videoCap(profile) / videoTokensPerSecond(profile)

    /** The most [a] may take of the context, sent whole: [videoCap] for a video or YouTube link, else [tokenCap]. */
    fun capFor(a: ContentPart.Attachment, profile: ModelProfile): Int =
        if (a.kind == AttachmentKind.VIDEO || youtubeUrl(a) != null && a.ref == null) videoCap(profile) else tokenCap(profile)

    /** A YouTube video's address, for a cutting or ticket of one (watched by models that take YouTube links). */
    fun youtubeUrl(a: ContentPart.Attachment): String? =
        if (a.kind == AttachmentKind.LINK || a.kind == AttachmentKind.SONG) MusicLinks.youtubeId(a.url)?.let { "https://www.youtube.com/watch?v=$it" } else null

    /** Whether [a] goes to [profile]'s model as a YouTube link it watches. */
    fun watchesOnYouTube(a: ContentPart.Attachment, senses: Senses, profile: ModelProfile): Boolean =
        senses.youtube && youtubeUrl(a) != null && a.ref == null && watchableSeconds(profile) >= MIN_WATCH_SECONDS

    /** The seconds of a video sent: its smaller copy's when that's only its first part, else its own (null when unknown). */
    private fun sentSeconds(a: ContentPart.Attachment): Int? =
        ((if (a.modelRef != null) a.modelLengthMs else null) ?: a.lengthMs)?.let { (it / 1000).toInt() }

    /** Where Gemini cuts a video it's sent: after [watchableSeconds], for one longer or of unknown length; null for one sent whole. */
    fun endSeconds(a: ContentPart.Attachment, profile: ModelProfile): Int? {
        val most = watchableSeconds(profile)
        val sent = sentSeconds(a)
        return if (sent == null || sent > most) most else null
    }

    /**
     * The seconds of [a] a model watches when that's not all of it: its first part. Null when it
     * watches all of it. For one of unknown length (a YouTube link), the most it watches.
     */
    fun watchedPart(a: ContentPart.Attachment, profile: ModelProfile): Int? {
        val full = a.lengthMs?.let { (it / 1000).toInt() }
        val watched = sentSeconds(a)?.let { minOf(it, watchableSeconds(profile)) } ?: watchableSeconds(profile)
        return watched.takeIf { full == null || it < full }
    }

    /** The characters of a text file sent as text, within [tokenCap]. */
    fun textChars(profile: ModelProfile): Int = (tokenCap(profile) * CHARS_PER_TOKEN).toInt()

    fun isPdf(a: ContentPart.Attachment): Boolean =
        a.mimeType?.lowercase() == "application/pdf" || a.name?.lowercase()?.endsWith(".pdf") == true

    fun isText(a: ContentPart.Attachment): Boolean {
        val mime = a.mimeType?.lowercase()?.substringBefore(';')?.trim()
        if (mime != null && (mime.startsWith("text/") || mime in TEXT_TYPES)) return true
        val ext = a.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in TEXT_EXTENSIONS
    }

    /** The audio type Gemini knows this song by, or null when it doesn't take that kind. */
    fun geminiAudioType(mime: String?): String? = when (val m = mime?.lowercase()?.substringBefore(';')?.trim()) {
        null -> null
        "audio/mpeg", "audio/mp3" -> "audio/mp3"
        "audio/x-wav", "audio/wav", "audio/wave" -> "audio/wav"
        "audio/x-flac", "audio/flac" -> "audio/flac"
        "audio/mp4", "audio/x-m4a", "audio/m4a" -> "audio/m4a"
        "audio/x-aiff", "audio/aiff" -> "audio/aiff"
        "audio/aac", "audio/ogg", "audio/opus", "audio/webm" -> m
        else -> null
    }

    /** The video type Gemini knows this video by, or null when it doesn't take that kind. */
    fun geminiVideoType(mime: String?): String? = when (val m = mime?.lowercase()?.substringBefore(';')?.trim()) {
        "video/mp4", "video/mpeg", "video/mpg", "video/quicktime", "video/mov", "video/avi",
        "video/x-flv", "video/webm", "video/wmv", "video/3gpp" -> m
        else -> null
    }

    /** About what a picture costs, at the usual 1280 px. */
    const val PICTURE_TOKENS = 1_200

    /** About what a thing costs sent whole (a PDF's pages, a song's seconds, a video's frames). */
    fun wholeTokens(a: ContentPart.Attachment, profile: ModelProfile): Int = when {
        // Gemini 3 and later watch video at about 70 tokens a frame by default (100 a second with sound); older ones at about 290.
        // As much as is watched: its first part, for a long one (a YouTube ticket is watched, sound and all).
        a.kind == AttachmentKind.VIDEO || youtubeUrl(a) != null && a.ref == null && profile.provider == ProviderType.GEMINI ->
            (sentSeconds(a)?.let { minOf(it, watchableSeconds(profile)) } ?: watchableSeconds(profile)) * videoTokensPerSecond(profile)
        a.kind == AttachmentKind.FILE && isPdf(a) -> (a.pages ?: 10) * if (profile.provider == ProviderType.GEMINI) 300 else 2_000
        a.kind == AttachmentKind.FILE && isText(a) -> ((a.sizeBytes ?: 4_000L) / CHARS_PER_TOKEN).toInt()
        a.kind == AttachmentKind.SONG -> ((if (a.previewOnly) PREVIEW_MS else a.lengthMs ?: 240_000L) / 1000 * 32).toInt()
        a.kind == AttachmentKind.PLAYLIST -> hearableTracks(a).sumOf { trackTokens(a.tracks[it]) }
        else -> 0
    }

    /** A mix's songs a model that hears could be sent (those with their file, in a kind it knows), by their places. */
    fun hearableTracks(a: ContentPart.Attachment): List<Int> =
        a.tracks.indices.filter { i -> a.tracks[i].ref != null && geminiAudioType(a.tracks[i].mimeType) != null }

    /** About what one of a mix's songs costs heard: its preview's thirty seconds, or its length. */
    fun trackTokens(t: Track): Int = ((if (t.previewOnly) PREVIEW_MS else t.lengthMs ?: 240_000L) / 1000 * 32).toInt()

    /** Gemini 3 or later, by its model id. */
    fun modernGemini(profile: ModelProfile): Boolean {
        if (profile.provider != ProviderType.GEMINI) return false
        val major = Regex("gemini-(\\d+)").find(profile.modelId.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: return profile.modelId.isBlank()
        return major >= 3
    }

    /** The file a model is sent for [a]: its smaller copy when there is one, else the original. */
    fun sentRef(a: ContentPart.Attachment): String? = a.modelRef ?: a.ref
    fun sentType(a: ContentPart.Attachment): String? = if (a.modelRef != null) a.modelMimeType else a.mimeType
    fun sentSize(a: ContentPart.Attachment): Long = (if (a.modelRef != null) a.modelSizeBytes else a.sizeBytes) ?: 0L

    /** Whether [profile]'s model takes [a] in whole: the right sense, a kind it knows, and small enough. */
    fun takesWhole(a: ContentPart.Attachment, profile: ModelProfile, senses: Senses = of(profile)): Boolean {
        val fits = sentSize(a) <= inlineBudget(profile) && wholeTokens(a, profile) <= tokenCap(profile)
        val ref = sentRef(a)
        return when (a.kind) {
            AttachmentKind.FILE -> when {
                isText(a) -> a.ref != null
                isPdf(a) -> senses.pdfs && a.ref != null && fits
                else -> false
            }
            AttachmentKind.SONG -> watchesOnYouTube(a, senses, profile) || senses.audio && ref != null && geminiAudioType(sentType(a)) != null && fits
            // Any length: one longer than its share is watched as its first part.
            AttachmentKind.VIDEO -> senses.video && ref != null && geminiVideoType(sentType(a)) != null &&
                sentSize(a) <= inlineBudget(profile) && watchableSeconds(profile) >= MIN_WATCH_SECONDS
            // A mix goes song by song, as many as fit (MediaParts): it's taken whole if one would.
            AttachmentKind.PLAYLIST -> senses.audio && hearableTracks(a).any { trackTokens(a.tracks[it]) <= tokenCap(profile) }
            AttachmentKind.LINK -> watchesOnYouTube(a, senses, profile)
        }
    }

    /**
     * The pictures that stand for [a] when it can't go whole: a video's three frames, a PDF's first
     * page. Empty when the model doesn't see pictures or there are none.
     */
    fun picturesFor(a: ContentPart.Attachment, senses: Senses): List<String> = if (!senses.pictures) emptyList() else when {
        a.kind == AttachmentKind.VIDEO -> a.frameRefs
        a.kind == AttachmentKind.FILE && isPdf(a) -> listOfNotNull(a.pictureRef)
        else -> emptyList()
    }

    /**
     * The app's line under a frame in your note, on what [name] can't take in with its model, and
     * why, or null when it takes it all in: "Aria sees three frames from the video; Claude can't
     * watch video."
     */
    fun cantTakeIn(a: ContentPart.Attachment, profile: ModelProfile, name: String, opensLinks: Boolean = false): String? {
        val senses = of(profile)
        if (takesWhole(a, profile, senses)) {
            val part = if (a.kind == AttachmentKind.VIDEO || watchesOnYouTube(a, senses, profile)) watchedPart(a, profile) else null
            val youtube = watchesOnYouTube(a, senses, profile)
            val verb = if (a.kind == AttachmentKind.SONG) "listens to" else "watches"
            return when {
                part != null && a.lengthMs == null -> "$name $verb up to its first ${clock(part * 1000L)}${if (youtube) ", from YouTube" else ""}."
                part != null -> "$name $verb its first ${clock(part * 1000L)}${if (youtube) ", from YouTube" else ""}; the whole of it is too much to send at once."
                a.kind == AttachmentKind.FILE && isText(a) && (a.sizeBytes ?: 0L) > textChars(profile) -> "$name reads the beginning of it."
                a.kind == AttachmentKind.SONG && a.previewOnly -> "$name hears its 30-second preview."
                a.kind == AttachmentKind.PLAYLIST && a.tracks.isNotEmpty() && a.tracks.all { it.previewOnly } -> "$name hears their 30-second previews."
                else -> null
            }
        }
        // A persona that can open links reads the page itself (open_link, or Claude's web fetch).
        if (a.kind == AttachmentKind.LINK && opensLinks && profile.toolMode != ToolMode.NONE) return null
        val model = modelWords(profile)
        return when (a.kind) {
            AttachmentKind.FILE -> when {
                isPdf(a) && picturesFor(a, senses).isNotEmpty() -> "$name sees the first page."
                else -> "$name can read the tag, but can't open the file."
            }
            AttachmentKind.SONG -> "$name knows its title and artist, but " + when {
                youtubeUrl(a) != null && a.ref == null -> "$model can't play YouTube links."
                a.ref == null -> "a link brings no sound to send."
                !senses.audio -> "$model can't hear music."
                geminiAudioType(sentType(a)) == null -> "it's a kind of sound file that can't be sent."
                else -> "it's too long to send."
            }
            AttachmentKind.PLAYLIST -> "$name can read the label, but " + if (!senses.audio) "$model can't hear music." else "can't hear the songs."
            AttachmentKind.VIDEO -> {
                val why = when {
                    !senses.video -> "$model can't watch video"
                    a.ref == null || geminiVideoType(sentType(a)) == null -> "it's a kind of video that can't be sent"
                    sentSize(a) > inlineBudget(profile) -> "it's too big to send"
                    else -> "there's no room for it in what $name reads"
                }
                if (picturesFor(a, senses).isNotEmpty()) "$name sees three frames from the video; $why." else "$name can read its label, but can't watch it: $why."
            }
            AttachmentKind.LINK ->
                if (youtubeUrl(a) != null) "$name can read the cutting, but $model can't play YouTube links." else "$name can read the cutting, but can't open the page."
        }
    }

    /** The model family, for a note on what it can't do: "Claude can't watch video." */
    private fun modelWords(profile: ModelProfile): String = when (profile.provider) {
        ProviderType.ANTHROPIC -> "Claude"
        ProviderType.GEMINI -> if (of(profile).video) "Gemini" else "Gemma"
        ProviderType.OPENAI_COMPATIBLE -> "this model"
        ProviderType.NOVELAI_TEXT -> "NovelAI's models"
    }

    /** The line under your photos when the persona's model can't see them. */
    fun cantSee(profile: ModelProfile, name: String, several: Boolean): String? =
        if (of(profile).pictures) null else if (several) "$name can't see these photos." else "$name can't see this photo."

    const val CHARS_PER_TOKEN = 3.6

    /** The least worth watching: a budget leaving less for a video goes by its frames. */
    const val MIN_WATCH_SECONDS = 10

    /** How long a music service's preview of a song runs. */
    const val PREVIEW_MS = 30_000L
    private const val MB = 1024L * 1024L
    private val TEXT_TYPES = setOf(
        "application/json", "application/xml", "application/x-yaml", "application/yaml", "application/javascript",
        "application/x-sh", "application/x-subrip", "application/toml", "application/x-tex", "application/rtf",
    )
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "xml", "yaml", "yml", "toml", "ini", "log", "html", "htm",
        "css", "js", "ts", "kt", "java", "py", "rb", "go", "rs", "c", "h", "cpp", "sh", "srt", "vtt", "tex", "org",
    )
}
