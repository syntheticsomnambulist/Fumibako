package dev.aiharness.core.memory

import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** How a memory looks in the memory book as it ages. */
enum class MemoryAge { FRESH, FADING, WORN }

/**
 * The arithmetic of remembering, kept pure so it is easy to test. Personas only choose a level and a
 * mood word; everything here is derived:
 *
 * - **Salience** (how much a memory stands out): its level, plus a little for a strong feeling, plus
 *   a little when the user wrote, edited or confirmed it.
 * - **Strength** (how present it still is): halves over a time set by its level, lengthened by a
 *   strong feeling and by real use. Memories that matter deeply, and ones always in mind, don't fade.
 * - **Dormant**: weak and old. Out of automatic recall, still searchable, never deleted.
 */
object MemoryWeights {
    const val DORMANT_STRENGTH = 0.15
    const val DORMANT_MIN_AGE_DAYS = 60.0
    const val STRENGTH_FLOOR = 0.1
    private const val MAX_HALF_LIFE_DAYS = 365.0
    private const val DAY = 86_400_000.0

    fun salience(entry: MemoryEntry, feeling: Feeling?): Double {
        var s = when (entry.level) {
            MemoryLevel.IN_PASSING -> 0.2
            MemoryLevel.WORTH_KEEPING -> 0.5
            MemoryLevel.MATTERS_DEEPLY -> 0.9
        }
        if (feelingCounts(entry, feeling)) s += 0.1
        if (entry.userTouched) s += 0.1
        return min(1.0, s)
    }

    /** Days for a memory's strength to halve, or null when it doesn't fade. */
    fun halfLifeDays(entry: MemoryEntry, feeling: Feeling?): Double? {
        if (entry.isCore || entry.level == MemoryLevel.MATTERS_DEEPLY) return null
        var days = when (entry.level) {
            MemoryLevel.IN_PASSING -> 7.0
            else -> 30.0
        }
        if (feelingCounts(entry, feeling)) days *= 2
        days *= 1.5.pow(min(entry.useCount, 6))
        return min(days, MAX_HALF_LIFE_DAYS)
    }

    /** 1.0 when fresh, falling towards [STRENGTH_FLOOR] (never below, so a strong match still surfaces). */
    fun strength(entry: MemoryEntry, now: Long, feeling: Feeling?): Double {
        val halfLife = halfLifeDays(entry, feeling) ?: return 1.0
        val days = max(0L, now - entry.lastUsedAt) / DAY
        return max(STRENGTH_FLOOR, 0.5.pow(days / halfLife))
    }

    fun isDormant(entry: MemoryEntry, now: Long, feeling: Feeling?): Boolean {
        if (halfLifeDays(entry, feeling) == null) return false
        val days = max(0L, now - entry.lastUsedAt) / DAY
        return days >= DORMANT_MIN_AGE_DAYS && strength(entry, now, feeling) <= DORMANT_STRENGTH
    }

    fun age(entry: MemoryEntry, now: Long, feeling: Feeling?): MemoryAge = when {
        isDormant(entry, now, feeling) -> MemoryAge.WORN
        strength(entry, now, feeling) < 0.5 -> MemoryAge.FADING
        else -> MemoryAge.FRESH
    }

    /**
     * A feeling only weighs in when it's strong, and for facts only when the persona chose it: a
     * mundane fact kept while the persona happened to be elated shouldn't stand out for it.
     */
    private fun feelingCounts(entry: MemoryEntry, feeling: Feeling?): Boolean =
        feeling?.intensity == Intensity.STRONG && (entry.kind != MemoryKind.FACT || entry.feelingChosen)
}
