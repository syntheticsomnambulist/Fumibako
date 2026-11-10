package dev.aiharness.core.memory

import dev.aiharness.core.FakeClock
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lore memories (from a card's lorebook) and "Won't fade" (v0.5). */
class LoreTest {
    private val clock = FakeClock()
    private val repo = InMemoryMemoryRepository()
    private val memory = MemoryService(repo, InMemorySettingsRepository(AppSettings()), null, clock)
    private val day = 86_400_000L

    @Test
    fun aMemoryThatWontFadeStaysAsStrongAsTheDayItWasKept() = runTest {
        val plain = memory.save("p", "Sam's favorite tea is genmaicha", level = MemoryLevel.IN_PASSING).entry
        val kept = memory.save("p", "The lighthouse keeper's name is Oren", level = MemoryLevel.IN_PASSING, wontFade = true).entry
        clock.millis += 400 * day

        assertTrue(MemoryWeights.isDormant(plain, clock.millis, null), "an ordinary small memory goes dormant")
        assertEquals(1.0, MemoryWeights.strength(kept, clock.millis, null))
        assertFalse(MemoryWeights.isDormant(kept, clock.millis, null))
        assertEquals(MemoryAge.FRESH, MemoryWeights.age(kept, clock.millis, null))
        assertEquals(
            listOf(kept.id),
            memory.retrieve("p", null, "Who keeps the lighthouse?", topK = 5, minRelevance = 0.05).map { it.entry.id },
            "still recalled only when it's relevant, but never too faded to be",
        )
        assertEquals(emptyList(), memory.retrieve("p", null, "What's for breakfast?", topK = 5, minRelevance = 0.12).map { it.entry.id })
    }

    @Test
    fun onlyYouSetItAndTakingItOffLetsItFadeFromNow() = runTest {
        val entry = memory.save("p", "Sam's cat is called Kinako").entry
        val marked = memory.setWontFade(entry.id, true)!!
        assertTrue(marked.wontFade && marked.userTouched)
        clock.millis += 200 * day
        val unmarked = memory.setWontFade(entry.id, false)!!
        assertFalse(unmarked.wontFade)
        assertEquals(clock.millis, unmarked.lastUsedAt, "it fades from when the mark came off")
        assertEquals(1.0, MemoryWeights.strength(unmarked, clock.millis, null))
    }

    @Test
    fun loreIsRecalledByItsRecallWords() = runTest {
        val lore = memory.save(
            "p", "Beyond the ridge stands a mill that has been silent for a hundred years.",
            kind = MemoryKind.LORE, wontFade = true, recallWords = listOf("old mill", "Hollowmere"),
        ).entry
        memory.save("p", "Sam walked the dog this morning")

        assertEquals(
            listOf(lore.id),
            memory.retrieve("p", null, "Shall we visit Hollowmere tomorrow?", topK = 1, minRelevance = 0.12).map { it.entry.id },
        )
        assertEquals(
            listOf(lore.id),
            memory.retrieve("p", null, "I dreamed about the old mill again", topK = 1, minRelevance = 0.12).map { it.entry.id },
        )
        assertEquals(
            emptyList(),
            memory.retrieve("p", null, "The mills of the city are loud", topK = 5, minRelevance = 0.5).map { it.entry.id }.filter { it == lore.id },
            "a recall word counts whole, not as part of another word",
        )
    }

    @Test
    fun loreDoesntCountAmongWhatMattersDeeply() = runTest {
        memory.save("p", "The river spirit is called Mizuha", kind = MemoryKind.LORE, level = MemoryLevel.MATTERS_DEEPLY)
        memory.save("p", "Sam's grandmother passed away in spring", level = MemoryLevel.MATTERS_DEEPLY)
        assertEquals(1, memory.deepCount("p"))
    }
}
