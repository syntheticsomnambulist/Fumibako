package dev.aiharness.core.memory

import dev.aiharness.core.FakeClock
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.EmbeddingProviderType
import dev.aiharness.core.model.EmbeddingSettings
import dev.aiharness.core.model.MemorySettings
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryServiceTest {
    private val clock = FakeClock()
    private val repo = InMemoryMemoryRepository()

    private fun service(settings: AppSettings = AppSettings(), embedder: Embedder? = null) =
        MemoryService(repo, InMemorySettingsRepository(settings), embedder?.let { e -> EmbedderFactory { e } }, clock)

    @Test
    fun mergesNearDuplicates() = runTest {
        val memory = service()
        val first = memory.save("p", "Sam's sister is called Ria", importance = 4)
        val second = memory.save("p", "Sam's sister is called Ria.", importance = 7)
        assertTrue(second.mergedWithExisting)
        assertEquals(first.entry.id, second.entry.id)
        assertEquals(7, memory.all("p").single().importance)
    }

    @Test
    fun recallsRelevantMemoriesFirstAndRespectsScope() = runTest {
        val memory = service()
        memory.save("p", "Sam loves hiking in the Alps every summer", importance = 6)
        memory.save("p", "Sam is allergic to cats", importance = 8)
        memory.save("p", "Alex (another persona) prefers chess", aboutUserPersonaId = "other-user")
        memory.save("q", "A different persona's memory about hiking")

        val results = memory.retrieve("p", userPersonaId = "u1", query = "Want to go hiking this summer?", topK = 5, minRelevance = 0.05)
        assertEquals("Sam loves hiking in the Alps every summer", results.first().entry.content)
        assertTrue(results.none { it.entry.content.contains("chess") }, "scoped memory of another user persona excluded")
        assertTrue(results.none { it.entry.personaId != "p" })
        // Accessing updates recency bookkeeping.
        assertEquals(1, repo.get(results.first().entry.id)!!.accessCount)
    }

    @Test
    fun coreMemoriesAreThoseMarkedCore() = runTest {
        val memory = service()
        memory.save("p", "Mira and Sam have been friends for years", pinned = true, importance = 9)
        memory.save("p", "Sam had pasta on Tuesday", importance = 2)
        assertEquals(listOf("Mira and Sam have been friends for years"), memory.core("p", null).map { it.content })
    }

    @Test
    fun usesEmbeddingsWhenConfigured() = runTest {
        // Toy embedder: dimension 0 = "pets", dimension 1 = "travel".
        val embedder = object : Embedder {
            override val modelKey = "toy"
            override suspend fun embed(texts: List<String>) = texts.map { t ->
                val l = t.lowercase()
                floatArrayOf(if ("cat" in l || "dog" in l || "pet" in l) 1f else 0f, if ("trip" in l || "travel" in l || "japan" in l) 1f else 0f)
            }
        }
        val settings = AppSettings(memory = MemorySettings(embedding = EmbeddingSettings(EmbeddingProviderType.OPENAI_COMPATIBLE, "toy")))
        val memory = service(settings, embedder)
        memory.save("p", "Sam has a dog named Biscuit")
        memory.save("p", "Sam is planning a trip to Japan")

        // No keyword overlap with either memory; only the vectors connect "pet" to the dog memory.
        val results = memory.retrieve("p", null, "How is your pet?", topK = 1, minRelevance = 0.1)
        assertEquals("Sam has a dog named Biscuit", results.single().entry.content)
    }
}
