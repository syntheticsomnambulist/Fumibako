package dev.aiharness.core.memory

import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.pow

data class ScoredMemory(val entry: MemoryEntry, val score: Double, val relevance: Double)

data class SaveResult(val entry: MemoryEntry, val mergedWithExisting: Boolean)

/**
 * Persona memory: storage, de-duplication, optional embeddings, and hybrid recall.
 *
 * Recall score = 0.65 * relevance + 0.20 * importance + 0.15 * recency, where relevance mixes
 * BM25 keyword overlap with embedding cosine similarity when an embedder is configured.
 */
class MemoryService(
    private val repo: MemoryRepository,
    private val settings: SettingsRepository,
    private val embedderFactory: EmbedderFactory?,
    private val clock: Clock,
    private val log: (String) -> Unit = {},
) {
    private suspend fun embedder(): Embedder? {
        val config = settings.current().memory.embedding ?: return null
        return try {
            embedderFactory?.create(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Embedder unavailable: ${e.message}")
            null
        }
    }

    private fun inScope(entry: MemoryEntry, userPersonaId: String?) =
        entry.aboutUserPersonaId == null || entry.aboutUserPersonaId == userPersonaId

    suspend fun all(personaId: String): List<MemoryEntry> = repo.listForPersona(personaId)

    suspend fun get(id: String): MemoryEntry? = repo.get(id)

    /** Pinned / core memories that belong in the system prompt. */
    suspend fun core(personaId: String, userPersonaId: String?): List<MemoryEntry> =
        repo.listForPersona(personaId)
            .filter { it.isCore && inScope(it, userPersonaId) }
            .sortedWith(compareByDescending<MemoryEntry> { it.importance }.thenBy { it.createdAt })

    suspend fun save(
        personaId: String,
        content: String,
        kind: MemoryKind = MemoryKind.FACT,
        importance: Int = 5,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        aboutUserPersonaId: String? = null,
        sourceConversationId: String? = null,
    ): SaveResult {
        val now = clock.nowMillis()
        val clean = content.trim()
        require(clean.isNotEmpty()) { "Memory content is empty" }
        val existing = findNearDuplicate(personaId, clean, aboutUserPersonaId)
        val entry = if (existing != null) {
            existing.copy(
                content = clean,
                importance = max(existing.importance, importance.coerceIn(1, 10)),
                tags = (existing.tags + tags).distinct(),
                pinned = existing.pinned || pinned,
                updatedAt = now,
            )
        } else {
            MemoryEntry(
                id = newId(),
                personaId = personaId,
                content = clean,
                kind = if (pinned && kind == MemoryKind.FACT) MemoryKind.CORE else kind,
                importance = importance.coerceIn(1, 10),
                tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
                pinned = pinned,
                aboutUserPersonaId = aboutUserPersonaId,
                sourceConversationId = sourceConversationId,
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now,
            )
        }
        repo.upsert(entry)
        embedQuietly(listOf(entry))
        return SaveResult(entry, mergedWithExisting = existing != null)
    }

    suspend fun update(
        id: String,
        content: String? = null,
        importance: Int? = null,
        pinned: Boolean? = null,
        tags: List<String>? = null,
    ): MemoryEntry? {
        val current = repo.get(id) ?: return null
        val updated = current.copy(
            content = content?.trim()?.takeIf { it.isNotEmpty() } ?: current.content,
            importance = importance?.coerceIn(1, 10) ?: current.importance,
            pinned = pinned ?: current.pinned,
            tags = tags ?: current.tags,
            updatedAt = clock.nowMillis(),
        )
        repo.upsert(updated)
        if (updated.content != current.content) embedQuietly(listOf(updated))
        return updated
    }

    suspend fun delete(id: String) = repo.delete(id)

    /**
     * Hybrid recall for one moment of conversation.
     * @param excludeIds memories already in the prompt (e.g. core memories).
     */
    suspend fun retrieve(
        personaId: String,
        userPersonaId: String?,
        query: String,
        topK: Int,
        minRelevance: Double,
        excludeIds: Set<String> = emptySet(),
        markAccessed: Boolean = true,
    ): List<ScoredMemory> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val candidates = repo.listForPersona(personaId).filter { inScope(it, userPersonaId) && it.id !in excludeIds }
        if (candidates.isEmpty()) return emptyList()

        val keyword = KeywordIndex(candidates.associate { it.id to it.content + " " + it.tags.joinToString(" ") })
            .normalizedScore(query)
        val semantic = semanticScores(personaId, query, candidates)
        val now = clock.nowMillis()

        val scored = candidates.mapNotNull { entry ->
            val kw = keyword[entry.id] ?: 0.0
            val sem = semantic[entry.id]
            val relevance = if (sem != null) 0.7 * sem + 0.3 * kw else kw
            if (relevance < minRelevance) return@mapNotNull null
            val ageDays = (now - max(entry.lastAccessedAt, entry.updatedAt)).coerceAtLeast(0) / 86_400_000.0
            val recency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)
            val score = 0.65 * relevance + 0.20 * (entry.importance / 10.0) + 0.15 * recency
            ScoredMemory(entry, score, relevance)
        }.sortedByDescending { it.score }.take(topK)

        if (markAccessed && scored.isNotEmpty()) repo.markAccessed(scored.map { it.entry.id }, now)
        return scored
    }

    /** Computes embeddings for memories that don't have one yet. Returns how many were embedded. */
    suspend fun backfillEmbeddings(personaId: String, limit: Int = 64): Int {
        val embedder = embedder() ?: return 0
        val existing = repo.getVectors(personaId, embedder.modelKey)
        val missing = repo.listForPersona(personaId).filter { it.id !in existing }.take(limit)
        if (missing.isEmpty()) return 0
        embedQuietly(missing, embedder)
        return missing.size
    }

    private suspend fun semanticScores(personaId: String, query: String, candidates: List<MemoryEntry>): Map<String, Double> {
        val embedder = embedder() ?: return emptyMap()
        return try {
            val vectors = repo.getVectors(personaId, embedder.modelKey)
            if (vectors.isEmpty()) return emptyMap()
            val queryVector = embedder.embed(listOf(query)).firstOrNull() ?: return emptyMap()
            candidates.mapNotNull { entry ->
                val v = vectors[entry.id] ?: return@mapNotNull null
                // Rescale: unrelated texts typically still score ~0.2 cosine with modern embedders.
                entry.id to ((cosine(queryVector, v) - 0.2) / 0.8).coerceIn(0.0, 1.0)
            }.toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Semantic recall failed, using keywords only: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun embedQuietly(entries: List<MemoryEntry>, given: Embedder? = null) {
        val embedder = given ?: embedder() ?: return
        try {
            val vectors = embedder.embed(entries.map { it.content })
            entries.zip(vectors).forEach { (entry, vector) ->
                repo.putVector(entry.id, entry.personaId, embedder.modelKey, vector)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Embedding failed (will retry during upkeep): ${e.message}")
        }
    }

    private suspend fun findNearDuplicate(personaId: String, content: String, aboutUserPersonaId: String?): MemoryEntry? {
        val tokens = TextTokens.tokenize(content).toSet()
        if (tokens.isEmpty()) return null
        return repo.listForPersona(personaId)
            .filter { it.aboutUserPersonaId == aboutUserPersonaId }
            .map { it to jaccard(tokens, TextTokens.tokenize(it.content).toSet()) }
            .filter { it.second >= DUPLICATE_THRESHOLD }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        return intersection.toDouble() / (a.size + b.size - intersection)
    }

    companion object {
        private const val RECENCY_HALF_LIFE_DAYS = 14.0
        private const val DUPLICATE_THRESHOLD = 0.8
    }
}
