package dev.aiharness.core.media

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What a video or song's own tags say, read as the phone writes them. */
object FileFacts {
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /**
     * When a video was recorded, from its date tag ("20261103T142233.000Z", in UTC). Null when it
     * has none, or only a placeholder (some phones write 1904, the first day QuickTime knows).
     */
    fun recordedAt(raw: String?): Long? = runCatching {
        val at = LocalDateTime.parse(raw!!.trim().take(15), STAMP).toInstant(ZoneOffset.UTC).toEpochMilli()
        at.takeIf { LocalDateTime.parse(raw.trim().take(15), STAMP).year >= 1990 }
    }.getOrNull()

    /** A song's year from its tag ("1987", or "1987-06-01"). */
    fun year(raw: String?): Int? = raw?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1000..2999 }

    /** A tag's text, or null when it's blank or only a placeholder. */
    fun text(raw: String?): String? = raw?.trim()?.takeUnless { it.isEmpty() || it.equals("<unknown>", ignoreCase = true) }
}
