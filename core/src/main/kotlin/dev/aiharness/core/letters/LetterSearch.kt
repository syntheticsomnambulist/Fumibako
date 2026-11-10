package dev.aiharness.core.letters

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.util.Clock

/** What a search looks through: everything, the persona's letters, your notes, or pictures. */
enum class LetterKind { EVERYTHING, THEIRS, YOURS, PICTURES }

/** Whose letters a persona looks through: yours, its own, or both. */
enum class LetterFrom { YOU, ME, EITHER }

/** A letter that matched, with the passage around the first match. */
data class LetterHit(
    val message: ChatMessage,
    val conversation: Conversation,
    /** You wrote it (one of your personas); otherwise the persona did. */
    val byYou: Boolean,
    /** A few sentences around the first match, trimmed with "…" where they were cut. */
    val passage: String,
    /** Where the search's words are in [passage] (whole words). */
    val marks: List<IntRange>,
    /** The match is in a picture's caption or description, not in the letter's words. */
    val inPicture: Boolean,
    /** How many of the search's words it has (searches by personas rank by this). */
    val wordsFound: Int,
) {
    /** When it reached you (a reply in the post, when it arrived). */
    val at: Long get() = message.arrivedAt
}

/**
 * Searching past letters, by their words (see [LetterWords]): for you, newest first; for a persona,
 * its own letters with you, the ones with more of the words first, then newer ones.
 */
class LetterSearch(private val conversations: ConversationRepository, private val clock: Clock) {

    /** Your search: letters with every word of [query], in [personaId]'s letters or everyone's. */
    suspend fun search(query: String, personaId: String? = null, kind: LetterKind = LetterKind.EVERYTHING, limit: Int = 200): List<LetterHit> {
        val terms = LetterWords.terms(query)
        if (terms.isEmpty()) return emptyList()
        return hits(terms, matchAll = true, personaId = personaId)
            .filter { hit ->
                when (kind) {
                    LetterKind.EVERYTHING -> true
                    LetterKind.THEIRS -> !hit.byYou
                    LetterKind.YOURS -> hit.byYou
                    LetterKind.PICTURES -> hit.inPicture
                }
            }
            .sortedByDescending { it.at }
            .take(limit)
    }

    /**
     * A persona looking back through its letters with you: any of the words of [query] (small words
     * left out), those with more of them first, then newer ones.
     */
    suspend fun lookUp(personaId: String, query: String, from: LetterFrom = LetterFrom.EITHER, limit: Int = 5): List<LetterHit> {
        val terms = LetterWords.terms(query).filter { it.length > 1 && it !in SMALL_WORDS }.ifEmpty { LetterWords.terms(query) }
        if (terms.isEmpty()) return emptyList()
        return hits(terms, matchAll = false, personaId = personaId)
            .filter { hit ->
                when (from) {
                    LetterFrom.YOU -> hit.byYou
                    LetterFrom.ME -> !hit.byYou
                    LetterFrom.EITHER -> true
                }
            }
            .sortedWith(compareByDescending<LetterHit> { it.wordsFound }.thenByDescending { it.at })
            .take(limit)
    }

    private suspend fun hits(terms: List<String>, matchAll: Boolean, personaId: String?): List<LetterHit> {
        val now = clock.nowMillis()
        val conversationsById = mutableMapOf<String, Conversation?>()
        return conversations.searchLetters(terms, matchAll, personaId, CANDIDATES)
            // Not one still in the post, and not what was never sent (a refusal, a failed turn).
            .filter { it.arrived(now) && !it.excludedFromContext && (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT || it.images.isNotEmpty()) }
            .mapNotNull { message ->
                val conversation = conversationsById.getOrPut(message.conversationId) { conversations.get(message.conversationId) } ?: return@mapNotNull null
                if (personaId != null && conversation.personaId != personaId) return@mapNotNull null
                hitIn(message, conversation, terms, matchAll)
            }
    }

    private fun hitIn(message: ChatMessage, conversation: Conversation, terms: List<String>, matchAll: Boolean): LetterHit? {
        val pieces = LetterWords.pieces(message)
        val found = pieces.flatMap { LetterWords.termsIn(it.text, terms) }.toSet()
        if (found.isEmpty() || (matchAll && found.size < terms.size)) return null
        val piece = pieces.first { LetterWords.termsIn(it.text, terms).isNotEmpty() }
        val passage = Passages.around(piece.text, LetterWords.matches(piece.text, terms).first())
        return LetterHit(
            message = message,
            conversation = conversation,
            byYou = message.role == MessageRole.USER,
            passage = passage,
            marks = LetterWords.matches(passage, terms),
            inPicture = piece.inPicture,
            wordsFound = found.size,
        )
    }

    private companion object {
        /** How many letters the word index hands over at most, newest first, before they're ranked. */
        const val CANDIDATES = 500

        /** Words too common to look for when a persona searches by any of its words. */
        val SMALL_WORDS = setOf(
            "the", "and", "or", "of", "to", "in", "on", "at", "for", "with", "about", "from", "is", "it", "its", "was", "we",
            "you", "your", "me", "my", "i", "that", "this", "what", "when", "where", "who", "how", "did", "do", "an", "be", "by",
        )
    }
}

/** A few sentences around a match, for a search result. */
object Passages {
    private val SENTENCE_END = Regex("(?<=[.!?。！？…])\\s+|\\n+")

    /** The sentence holding [match], with one either side, at most about [maxLength] characters. */
    fun around(text: String, match: IntRange, maxLength: Int = 320): String {
        val bounds = sentences(text)
        val at = bounds.indexOfFirst { match.first in it }.coerceAtLeast(0)
        var from = bounds[(at - 1).coerceAtLeast(0)].first
        var to = bounds[(at + 1).coerceAtMost(bounds.lastIndex)].last
        // Too long: only the matching sentence, then a window around the word.
        if (to - from + 1 > maxLength) {
            from = bounds[at].first
            to = bounds[at].last
        }
        if (to - from + 1 > maxLength) {
            val half = maxLength / 2
            from = (match.first - half).coerceAtLeast(from)
            to = (from + maxLength - 1).coerceAtMost(to)
            // Don't cut words in half at either end.
            while (from > 0 && from < text.length && !text[from - 1].isWhitespace() && from < match.first) from++
            while (to < text.lastIndex && to > match.last && !text[to + 1].isWhitespace()) to--
        }
        val core = text.substring(from, to + 1).trim().replace(Regex("\\s*\\n+\\s*"), " ")
        val cutBefore = text.substring(0, from).isNotBlank()
        val cutAfter = text.substring(to + 1).isNotBlank()
        return (if (cutBefore) "…" else "") + core + (if (cutAfter) "…" else "")
    }

    /** Each sentence's range in [text], separators left out. */
    private fun sentences(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        SENTENCE_END.findAll(text).forEach { gap ->
            if (gap.range.first > start) ranges += start until gap.range.first
            start = gap.range.last + 1
        }
        if (start < text.length) ranges += start until text.length
        return ranges.ifEmpty { listOf(text.indices) }
    }
}
