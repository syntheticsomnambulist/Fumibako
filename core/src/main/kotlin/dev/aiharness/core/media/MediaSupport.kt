package dev.aiharness.core.media

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.ToolMode

/** What a model takes in besides text. */
data class Senses(val pictures: Boolean, val pdfs: Boolean, val audio: Boolean, val video: Boolean) {
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
 * | Video | Three frames | Yes | Three frames |
 */
object MediaSupport {
    fun of(profile: ModelProfile): Senses = when (profile.provider) {
        // Every Claude model sees pictures and reads PDFs.
        ProviderType.ANTHROPIC -> Senses(pictures = true, pdfs = true, audio = false, video = false)
        // Gemini models take everything; Gemma through the same API takes pictures only.
        ProviderType.GEMINI -> {
            val gemini = profile.modelId.isBlank() || profile.modelId.lowercase().removePrefix("models/").startsWith("gemini")
            Senses(pictures = true, pdfs = gemini, audio = gemini, video = gemini)
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
        a.kind == AttachmentKind.FILE && isPdf(a) -> (a.pages ?: 10) * if (profile.provider == ProviderType.GEMINI) 300 else 2_000
        a.kind == AttachmentKind.FILE && isText(a) -> ((a.sizeBytes ?: 4_000L) / CHARS_PER_TOKEN).toInt()
        a.kind == AttachmentKind.SONG -> ((if (a.previewOnly) PREVIEW_MS else a.lengthMs ?: 240_000L) / 1000 * 32).toInt()
        // Gemini 3 and later watch video at about 70 tokens a frame by default (100 a second with sound); older ones at about 290.
        a.kind == AttachmentKind.VIDEO -> ((a.lengthMs ?: 60_000L) / 1000 * if (modernGemini(profile)) 100 else 300).toInt()
        else -> 0
    }

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
            AttachmentKind.SONG -> senses.audio && ref != null && geminiAudioType(sentType(a)) != null && fits
            AttachmentKind.VIDEO -> senses.video && ref != null && geminiVideoType(sentType(a)) != null && fits
            AttachmentKind.PLAYLIST, AttachmentKind.LINK -> false
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
     * The app's line under a frame in your note, on what [name] can't take in with its model, or
     * null when it takes it all in: "Aria can read the label, but can't hear the songs."
     */
    fun cantTakeIn(a: ContentPart.Attachment, profile: ModelProfile, name: String, opensLinks: Boolean = false): String? {
        val senses = of(profile)
        // A persona that can open links reads the page itself (open_link, or Claude's web fetch).
        if (a.kind == AttachmentKind.LINK && opensLinks && profile.toolMode != ToolMode.NONE) return null
        if (takesWhole(a, profile, senses)) {
            return when {
                a.kind == AttachmentKind.FILE && isText(a) && (a.sizeBytes ?: 0L) > textChars(profile) -> "$name reads the beginning of it."
                a.kind == AttachmentKind.SONG && a.previewOnly -> "$name hears its 30-second preview."
                else -> null
            }
        }
        return when (a.kind) {
            AttachmentKind.FILE -> when {
                isPdf(a) && picturesFor(a, senses).isNotEmpty() -> "$name sees the first page."
                else -> "$name can read the tag, but can't open the file."
            }
            AttachmentKind.SONG -> "$name knows its title and artist, but can't hear it."
            AttachmentKind.PLAYLIST -> "$name can read the label, but can't hear the songs."
            AttachmentKind.VIDEO ->
                if (picturesFor(a, senses).isNotEmpty()) "$name sees three frames from the video." else "$name can read its label, but can't watch it."
            AttachmentKind.LINK -> "$name can read the cutting, but can't open the page."
        }
    }

    /** The line under your photos when the persona's model can't see them. */
    fun cantSee(profile: ModelProfile, name: String, several: Boolean): String? =
        if (of(profile).pictures) null else if (several) "$name can't see these photos." else "$name can't see this photo."

    const val CHARS_PER_TOKEN = 3.6

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
