package dev.aiharness.core.memory

import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeelingsAndWeightsTest {

    @Test
    fun sortsMoodWordsIntoFamiliesAndIntensities() {
        assertEquals(Feeling(FeelingFamily.FOND, Intensity.MILD), FeelingWords.lookup("fond"))
        assertEquals(Feeling(FeelingFamily.PROUD, Intensity.STRONG), FeelingWords.lookup("Proud!"))
        assertEquals(Feeling(FeelingFamily.UNEASY, Intensity.MILD), FeelingWords.lookup("a bit anxious"), "softeners make it mild")
        assertEquals(Feeling(FeelingFamily.BRIGHT, Intensity.STRONG), FeelingWords.lookup("very happy"), "intensifiers make it strong")
        assertEquals(Feeling(FeelingFamily.CALM, Intensity.STRONG), FeelingWords.lookup("at peace"), "multi-word entries")
        assertEquals(FeelingFamily.SAD, FeelingWords.lookup("tired but happy")?.family, "first known word wins")
        assertNull(FeelingWords.lookup("bittersweetish"))
        assertNull(FeelingWords.lookup(null))
        val learned = mapOf("flummoxed" to Feeling(FeelingFamily.WONDER, Intensity.MILD))
        assertEquals(FeelingFamily.WONDER, FeelingWords.lookup("flummoxed", learned)?.family)
    }

    @Test
    fun parsesLevelsLeniently() {
        assertEquals(MemoryLevel.MATTERS_DEEPLY, MemoryLevel.parse("matters_deeply"))
        assertEquals(MemoryLevel.MATTERS_DEEPLY, MemoryLevel.parse("Matters deeply"))
        assertEquals(MemoryLevel.IN_PASSING, MemoryLevel.parse("minor"))
        assertEquals(MemoryLevel.WORTH_KEEPING, MemoryLevel.parse("5"))
        assertEquals(MemoryLevel.MATTERS_DEEPLY, MemoryLevel.parse("9"))
        assertEquals(MemoryLevel.IN_PASSING, MemoryLevel.parse("2"))
        assertNull(MemoryLevel.parse("whatever"))
    }

    private val day = 86_400_000L

    private fun memory(
        level: MemoryLevel = MemoryLevel.WORTH_KEEPING,
        kind: MemoryKind = MemoryKind.FACT,
        lastUsedAt: Long = 0,
        useCount: Int = 0,
        pinned: Boolean = false,
        chosen: Boolean = false,
        userTouched: Boolean = false,
    ) = MemoryEntry(
        id = "m", personaId = "p", content = "x", kind = kind, level = level, pinned = pinned,
        feltMood = "proud", feltMoodChosen = chosen, userTouched = userTouched,
        createdAt = 0, updatedAt = 0, lastAccessedAt = 0, lastUsedAt = lastUsedAt, useCount = useCount,
    )

    private val strong = Feeling(FeelingFamily.PROUD, Intensity.STRONG)
    private val mild = Feeling(FeelingFamily.FOND, Intensity.MILD)

    @Test
    fun salienceComesFromLevelFeelingAndTheUser() {
        assertEquals(0.2, MemoryWeights.salience(memory(MemoryLevel.IN_PASSING), mild), 1e-9)
        assertEquals(0.5, MemoryWeights.salience(memory(), mild), 1e-9)
        assertEquals(0.9, MemoryWeights.salience(memory(MemoryLevel.MATTERS_DEEPLY), mild), 1e-9)
        assertEquals(0.5, MemoryWeights.salience(memory(), strong), 1e-9, "a fact's borrowed mood doesn't count")
        assertEquals(0.6, MemoryWeights.salience(memory(chosen = true), strong), 1e-9)
        assertEquals(0.6, MemoryWeights.salience(memory(kind = MemoryKind.EPISODE), strong), 1e-9)
        assertEquals(1.0, MemoryWeights.salience(memory(MemoryLevel.MATTERS_DEEPLY, chosen = true, userTouched = true), strong), 1e-9)
    }

    @Test
    fun smallThingsFadeQuicklyAndUseKeepsThemLonger() {
        assertEquals(7.0, MemoryWeights.halfLifeDays(memory(MemoryLevel.IN_PASSING), mild))
        assertEquals(30.0, MemoryWeights.halfLifeDays(memory(), mild))
        assertEquals(60.0, MemoryWeights.halfLifeDays(memory(kind = MemoryKind.EPISODE), strong), "strong feelings fade slower")
        assertEquals(45.0, MemoryWeights.halfLifeDays(memory(useCount = 1), mild))
        assertNull(MemoryWeights.halfLifeDays(memory(MemoryLevel.MATTERS_DEEPLY), mild), "matters deeply doesn't fade")
        assertNull(MemoryWeights.halfLifeDays(memory(pinned = true), mild), "always in mind doesn't fade")

        val m = memory()
        assertEquals(1.0, MemoryWeights.strength(m, 0, mild), 1e-9)
        assertEquals(0.5, MemoryWeights.strength(m, 30 * day, mild), 1e-9)
        assertEquals(MemoryWeights.STRENGTH_FLOOR, MemoryWeights.strength(m, 400 * day, mild), 1e-9)
        assertEquals(MemoryAge.FRESH, MemoryWeights.age(m, 10 * day, mild))
        assertEquals(MemoryAge.FADING, MemoryWeights.age(m, 40 * day, mild))
    }

    @Test
    fun dormantMeansWeakAndOldButNeverDeepOrAlwaysInMind() {
        val trivia = memory(MemoryLevel.IN_PASSING)
        assertFalse(MemoryWeights.isDormant(trivia, 30 * day, mild), "too recent to be dormant, however weak")
        assertTrue(MemoryWeights.isDormant(trivia, 61 * day, mild))
        assertEquals(MemoryAge.WORN, MemoryWeights.age(trivia, 61 * day, mild))
        assertFalse(MemoryWeights.isDormant(memory(), 61 * day, mild), "worth keeping is still above the line at two months")
        assertTrue(MemoryWeights.isDormant(memory(), 90 * day, mild))
        assertFalse(MemoryWeights.isDormant(memory(MemoryLevel.MATTERS_DEEPLY), 1_000 * day, mild))
        assertFalse(MemoryWeights.isDormant(memory(pinned = true), 1_000 * day, mild))
    }
}
