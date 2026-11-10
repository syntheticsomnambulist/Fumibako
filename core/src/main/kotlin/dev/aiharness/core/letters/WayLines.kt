package dev.aiharness.core.letters

import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.WayLine
import dev.aiharness.core.model.words

/**
 * A persona's lines for the 〒 postmark: while a reply of its is in the post, the postmark reads
 * "Aria's reply is on its way · she's looking for a stamp". One line per letter, the same in the
 * thread and the letters list, never changing while you watch. A persona starts with plain ones;
 * you can write your own (they stay "yours"), and it can write its own, on its page or, at most
 * weekly, when it reflects in a heartbeat.
 */
object WayLines {
    /** At most this many lines. */
    const val MOST = 6

    /** Its own rewrites in a heartbeat come at most this often. */
    const val WEEK = 7L * 24 * 60 * 60 * 1000

    /** The plain lines a persona starts with: "she's addressing the envelope". */
    fun plain(persona: Persona): List<String> {
        val w = persona.words
        val subject = w.they + w.verb("'s", "'re")
        return listOf("$subject addressing the envelope", "$subject looking for a stamp", "$subject on the way to the post box")
    }

    /** Its lines, or the plain ones when it has none. */
    fun of(persona: Persona): List<String> = persona.wayLines.map { it.text.trim() }.filter { it.isNotEmpty() }.ifEmpty { plain(persona) }

    /**
     * The line on the postmark for the reply to a note: [noteAt] is when the note it answers was
     * sent, so the thread and the letters list pick the same one, and it holds until it arrives.
     */
    fun pick(persona: Persona, conversationId: String, noteAt: Long?): String {
        val lines = of(persona)
        return lines[Math.floorMod("$conversationId:${noteAt ?: 0}".hashCode(), lines.size)]
    }

    /** Lines from what the persona wrote: one a line, without bullets, numbers, quotes or the postmark's own words. */
    fun parse(text: String, name: String): List<String> =
        text.lines().mapNotNull { clean(it, name) }.distinct().take(MOST)

    /** One line, tidied; null when there's nothing left. */
    fun clean(raw: String, name: String): String? {
        var line = raw.trim()
            .removePrefix("-").removePrefix("*").removePrefix("•").trim()
            .replace(Regex("^\\d+[.)]\\s*"), "")
        val lead = "$name's reply is on its way"
        if (line.startsWith(lead, ignoreCase = true)) line = line.substring(lead.length).trim().removePrefix("·").removePrefix(",").trim()
        line = line.trim('"', '“', '”', '\'', ' ').removeSuffix(".").trim()
        if (line.isEmpty() || line.length > 90 || line.endsWith(":")) return null
        return line
    }

    /**
     * [persona] with [theirs] as its own lines: yours stay as they are, then its new ones, up to
     * [MOST] in all. [at] is when it wrote them.
     */
    fun withTheirs(persona: Persona, theirs: List<String>, at: Long): Persona {
        val yours = persona.wayLines.filter { it.yours }
        val room = (MOST - yours.size).coerceAtLeast(0)
        val fresh = theirs.filter { t -> yours.none { it.text.equals(t, ignoreCase = true) } }.take(room).map { WayLine(it) }
        return persona.copy(wayLines = yours + fresh, wayLinesWrittenAt = at)
    }
}
