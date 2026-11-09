package dev.aiharness.core.memory

import dev.aiharness.core.letters.LetterWords
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import kotlin.math.max

data class ScoredMemory(val entry: MemoryEntry, val score: Double, val relevance: Double)

data class SaveResult(val entry: MemoryEntry, val mergedWithExisting: Boolean, val replaced: MemoryEntry? = null)

/** What recall is for. */
enum class RecallMode {
    /** Brought into a letter on its own: skips dormant and replaced memories, and isn't "real use". */
    AUTOMATIC,

    /** The persona searched on purpose: everything is findable, and finding a memory strengthens it. */
    SEARCH,
}

/** Result of asking to change how a memory feels. */
sealed interface FeelingChange {
    data class Changed(val entry: MemoryEntry) : FeelingChange
    data object NotFound : FeelingChange
    data object NeedsReason : FeelingChange

    /** Feelings are revisited at most once a week per memory. */
    data class TooSoon(val nextAllowedAt: Long) : FeelingChange

    /** Only memories that have just come up can be re-felt. */
    data object NotFresh : FeelingChange
}

/**
 * Persona memory: storage, de-duplication, optional embeddings, recall, and how memories feel and
 * age. Ranking, fading and dormancy come from [MemoryWeights]; recall score is
 * 0.65 * relevance + 0.20 * salience + 0.15 * strength, where relevance mixes BM25 keyword overlap
 * with embedding cosine similarity when an embedder is configured.
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

    /** How a memory's current feeling sorts into a family and intensity (null when unknown). */
    suspend fun feelingOf(entry: MemoryEntry): Feeling? =
        FeelingWords.lookup(entry.currentMood, settings.current().learnedFeelings)

    /** Always-in-mind memories, for the system prompt. Replaced ones are left out. */
    suspend fun core(personaId: String, userPersonaId: String?): List<MemoryEntry> =
        repo.listForPersona(personaId)
            .filter { it.isCore && !it.isReplaced && inScope(it, userPersonaId) }
            .sortedWith(compareByDescending<MemoryEntry> { it.level.ordinal }.thenBy { it.createdAt })

    /** You mark a memory "Won't fade", or take the mark off (the persona can't). */
    suspend fun setWontFade(id: String, on: Boolean): MemoryEntry? {
        val entry = repo.get(id) ?: return null
        if (entry.wontFade == on) return entry
        // Coming off, it fades from now, not from when it was last used.
        val now = clock.nowMillis()
        val updated = entry.copy(wontFade = on, userTouched = true, updatedAt = now, lastUsedAt = if (on) entry.lastUsedAt else now)
        repo.upsert(updated)
        return updated
    }

    /** How many memories the persona keeps at "matters deeply". */
    suspend fun deepCount(personaId: String): Int =
        repo.listForPersona(personaId).count { it.level == MemoryLevel.MATTERS_DEEPLY && !it.isReplaced && it.kind != MemoryKind.LORE }

    /**
     * Keeps a memory. A near-duplicate of an existing memory is merged into it (which counts as the
     * memory coming up again). [replaces] marks an older memory as replaced by this one.
     */
    suspend fun save(
        personaId: String,
        content: String,
        kind: MemoryKind = MemoryKind.FACT,
        level: MemoryLevel = MemoryLevel.WORTH_KEEPING,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        aboutUserPersonaId: String? = null,
        sourceConversationId: String? = null,
        sourceMessageId: String? = null,
        feltMood: String? = null,
        feltMoodChosen: Boolean = false,
        replaces: String? = null,
        userTouched: Boolean = false,
        keepsakes: List<Keepsake> = emptyList(),
        wontFade: Boolean = false,
        recallWords: List<String> = emptyList(),
    ): SaveResult {
        val now = clock.nowMillis()
        val clean = content.trim()
        require(clean.isNotEmpty()) { "Memory content is empty" }
        val mood = feltMood?.let { FeelingWords.normalize(it) }?.takeIf { it.isNotEmpty() && it != "none" }
        val existing = findNearDuplicate(personaId, clean, aboutUserPersonaId)?.takeIf { it.id != replaces }
        val entry = if (existing != null) {
            existing.copy(
                content = clean,
                // Merging only raises a memory to "matters deeply" when the new save says so; the
                // heartbeat lets levels settle back down, so they don't only ever creep upward.
                level = if (level == MemoryLevel.MATTERS_DEEPLY) level else existing.level,
                tags = (existing.tags + tags).distinct(),
                pinned = existing.pinned || pinned,
                userTouched = existing.userTouched || userTouched,
                updatedAt = now,
                lastUsedAt = now,
                useCount = existing.useCount + 1,
                keepsakes = (existing.keepsakes + keepsakes).distinctBy { it.key },
            )
        } else {
            MemoryEntry(
                id = newId(),
                personaId = personaId,
                content = clean,
                kind = if (pinned && kind == MemoryKind.FACT) MemoryKind.CORE else kind,
                level = level,
                tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
                pinned = pinned,
                aboutUserPersonaId = aboutUserPersonaId,
                sourceConversationId = sourceConversationId,
                sourceMessageId = sourceMessageId,
                feltMood = mood,
                feltMoodChosen = mood != null && feltMoodChosen,
                userTouched = userTouched,
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now,
                lastUsedAt = now,
                keepsakes = keepsakes.distinctBy { it.key },
                wontFade = wontFade,
                recallWords = recallWords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            )
        }
        repo.upsert(entry)
        embedQuietly(listOf(entry))
        val replaced = replaces?.let { replaceWith(personaId, it, entry.id, now) }
        return SaveResult(entry, mergedWithExisting = existing != null, replaced = replaced)
    }

    private suspend fun replaceWith(personaId: String, oldId: String, newId: String, now: Long): MemoryEntry? {
        val old = repo.get(oldId)?.takeIf { it.personaId == personaId && it.id != newId } ?: return null
        val updated = old.copy(replacedById = newId, replacedAt = now, updatedAt = now)
        repo.upsert(updated)
        return updated
    }

    /** Changes a memory's text, level, pin or tags. [byUser] edits count as the user confirming it. */
    suspend fun update(
        id: String,
        content: String? = null,
        level: MemoryLevel? = null,
        pinned: Boolean? = null,
        tags: List<String>? = null,
        byUser: Boolean = false,
        /** Keepsakes to add (ones already kept stay). */
        keep: List<Keepsake> = emptyList(),
        /** Keepsakes to take out, by key. */
        unkeep: Set<String> = emptySet(),
    ): MemoryEntry? {
        val current = repo.get(id) ?: return null
        val now = clock.nowMillis()
        val updated = current.copy(
            content = content?.trim()?.takeIf { it.isNotEmpty() } ?: current.content,
            level = level ?: current.level,
            pinned = pinned ?: current.pinned,
            tags = tags ?: current.tags,
            keepsakes = (current.keepsakes + keep).distinctBy { it.key }.filter { it.key !in unkeep },
            userTouched = current.userTouched || byUser,
            updatedAt = now,
            lastUsedAt = if (byUser) now else current.lastUsedAt,
            useCount = if (byUser) current.useCount + 1 else current.useCount,
        )
        repo.upsert(updated)
        if (updated.content != current.content) embedQuietly(listOf(updated))
        return updated
    }

    /**
     * Changes how a memory feels now; the feeling it was kept with is never touched. A persona needs
     * a reason, may only re-feel a memory that has just come up (it was recalled, searched for or
     * revisited in the last few hours), and at most once a week per memory. The user (with editing
     * allowed) isn't limited. Setting the feeling back to the original clears the revision.
     */
    suspend fun reviseFeeling(id: String, mood: String, reason: String?, byUser: Boolean = false): FeelingChange {
        val current = repo.get(id) ?: return FeelingChange.NotFound
        val now = clock.nowMillis()
        val word = FeelingWords.normalize(mood)
        if (word.isEmpty()) return FeelingChange.NeedsReason
        if (!byUser) {
            if (reason.isNullOrBlank()) return FeelingChange.NeedsReason
            val lastRevision = current.moodRevisedAt
            if (lastRevision != null && now - lastRevision < REVISION_GAP_MILLIS) {
                return FeelingChange.TooSoon(lastRevision + REVISION_GAP_MILLIS)
            }
            if (now - max(current.lastAccessedAt, current.lastUsedAt) > FRESH_WINDOW_MILLIS) return FeelingChange.NotFresh
        }
        val backToOriginal = word == current.feltMood
        val updated = current.copy(
            mood = if (backToOriginal) null else word,
            moodReason = if (backToOriginal) null else reason?.trim()?.take(240),
            moodRevisedAt = now,
            moodRevisions = current.moodRevisions + 1,
            userTouched = current.userTouched || byUser,
            updatedAt = now,
        )
        repo.upsert(updated)
        return FeelingChange.Changed(updated)
    }

    /** Puts a memory's feeling back to how it felt when kept (the user's "reset"). */
    suspend fun resetFeeling(id: String): MemoryEntry? {
        val current = repo.get(id) ?: return null
        val updated = current.copy(mood = null, moodReason = null, updatedAt = clock.nowMillis())
        repo.upsert(updated)
        return updated
    }

    /**
     * After a turn: memories kept in [messageIds] that have no feeling yet take the letter's [mood]
     * (its mood tag is only known once the reply is finished). With [keepSource] false (a heartbeat
     * that stayed quiet, so the message is discarded) the link to the message is dropped.
     */
    suspend fun settleTurn(personaId: String, messageIds: Collection<String>, mood: String?, keepSource: Boolean = true) {
        if (messageIds.isEmpty()) return
        val word = mood?.let { FeelingWords.normalize(it) }?.takeIf { it.isNotEmpty() }
        repo.listForPersona(personaId).filter { it.sourceMessageId != null && it.sourceMessageId in messageIds }.forEach { entry ->
            val updated = entry.copy(
                feltMood = entry.feltMood ?: word,
                sourceMessageId = if (keepSource) entry.sourceMessageId else null,
            )
            if (updated != entry) repo.upsert(updated)
        }
    }

    suspend fun delete(id: String) = repo.delete(id)

    /** Marks memories as really used (searched for, cited in a reflection): it keeps them strong. */
    suspend fun markUsed(ids: Collection<String>) {
        if (ids.isNotEmpty()) repo.markUsed(ids, clock.nowMillis())
    }

    /**
     * Recall for one moment of conversation.
     * @param excludeIds memories already in the prompt (e.g. core memories).
     */
    suspend fun retrieve(
        personaId: String,
        userPersonaId: String?,
        query: String,
        topK: Int,
        minRelevance: Double,
        excludeIds: Set<String> = emptySet(),
        mode: RecallMode = RecallMode.AUTOMATIC,
    ): List<ScoredMemory> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val now = clock.nowMillis()
        val learned = settings.current().learnedFeelings
        val feelings = mutableMapOf<String, Feeling?>()
        fun feeling(e: MemoryEntry) = feelings.getOrPut(e.id) { FeelingWords.lookup(e.currentMood, learned) }

        val candidates = repo.listForPersona(personaId).filter { entry ->
            inScope(entry, userPersonaId) && entry.id !in excludeIds &&
                (mode == RecallMode.SEARCH || (!entry.isReplaced && !MemoryWeights.isDormant(entry, now, feeling(entry))))
        }
        if (candidates.isEmpty()) return emptyList()

        val keyword = KeywordIndex(candidates.associate { it.id to it.content + " " + it.tags.joinToString(" ") + " " + it.recallWords.joinToString(" ") })
            .normalizedScore(query)
        val semantic = semanticScores(personaId, query, candidates)
        val queryWords = " " + LetterWords.terms(query).joinToString(" ") + " "

        val scored = candidates.mapNotNull { entry ->
            val kw = keyword[entry.id] ?: 0.0
            val sem = semantic[entry.id]
            var relevance = if (sem != null) 0.7 * sem + 0.3 * kw else kw
            // A recall word in the letter brings it to mind, as a lorebook's keys do.
            if (entry.recallWords.any { word -> LetterWords.terms(word).joinToString(" ").let { it.isNotEmpty() && " $it " in queryWords } }) {
                relevance = maxOf(relevance, RECALL_WORD_RELEVANCE)
            }
            if (relevance < minRelevance) return@mapNotNull null
            val f = feeling(entry)
            val score = 0.65 * relevance + 0.20 * MemoryWeights.salience(entry, f) + 0.15 * MemoryWeights.strength(entry, now, f)
            ScoredMemory(entry, score, relevance)
        }.sortedByDescending { it.score }.take(topK)

        if (scored.isNotEmpty()) {
            val ids = scored.map { it.entry.id }
            // Automatic recall shows when a memory last came up, but doesn't keep it strong;
            // otherwise whatever is recalled gets recalled again and again.
            if (mode == RecallMode.SEARCH) repo.markUsed(ids, now) else repo.markAccessed(ids, now)
        }
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
            .filter { it.aboutUserPersonaId == aboutUserPersonaId && !it.isReplaced }
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
        private const val DUPLICATE_THRESHOLD = 0.8

        /** How relevant a memory is when one of its recall words is in the letter. */
        private const val RECALL_WORD_RELEVANCE = 0.9
        private const val REVISION_GAP_MILLIS = 7L * 86_400_000L
        private const val FRESH_WINDOW_MILLIS = 6L * 3_600_000L

        /**
         * The feeling of a stretch of conversation, by the peak-end rule: the strongest feeling the
         * persona had in it, or else how it ended.
         */
        fun peakEndMood(messages: List<ChatMessage>, learned: Map<String, Feeling> = emptyMap()): String? {
            val moods = messages.filter { it.role == MessageRole.ASSISTANT && !it.hidden }.mapNotNull { it.mood }
            if (moods.isEmpty()) return null
            return moods.lastOrNull { FeelingWords.lookup(it, learned)?.intensity == Intensity.STRONG } ?: moods.last()
        }
    }
}
