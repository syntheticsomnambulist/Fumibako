package dev.aiharness.core.model

/**
 * Personas end replies with a mood tag such as `[mood: curious]`. The tag stays in the stored text
 * (history is replayed to the model unchanged) and is removed only for display.
 */
object MoodTags {
    private const val OPEN = "[mood:"
    private val TAG = Regex("""\[\s*mood\s*:\s*([^\]\n]{1,40})\]""", RegexOption.IGNORE_CASE)

    /** The last mood tag in [text], normalised to lowercase, or null if there is none. */
    fun parse(text: String): String? =
        TAG.findAll(text).lastOrNull()?.groupValues?.get(1)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.take(32)

    /** [text] with every complete mood tag removed. */
    fun strip(text: String): String {
        if (!text.contains('[')) return text
        return TAG.replace(text, "").trimEnd().replace(Regex("[ \t]+\n"), "\n")
    }

    /**
     * For text still streaming in: strips complete tags and also hides a tag that has started but
     * not closed yet (`[mo`, `[mood: cur`), so it never flickers on screen.
     */
    fun stripStreaming(text: String): String {
        val stripped = strip(text)
        val open = stripped.lastIndexOf('[')
        if (open < 0) return stripped
        val tail = stripped.substring(open)
        if (tail.contains(']') || tail.contains('\n')) return stripped
        val compact = tail.lowercase().replace(" ", "")
        val partial = OPEN.startsWith(compact) || compact.startsWith(OPEN)
        return if (partial) stripped.substring(0, open).trimEnd() else stripped
    }
}
