package dev.aiharness.core.letters

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.MessageRole
import java.text.Normalizer

/**
 * The words searching letters looks through: what a letter or note says, and what's in its
 * pictures (captions, and the descriptions the app keeps of drawings and photos). Never the
 * persona's thoughts or its context.
 *
 * Words are runs of letters and digits, folded (lower case, accents dropped) the way the word index
 * folds them (SQLite's unicode61), and a search's words match the start of a word, so "cat" finds
 * "cats". Japanese has no spaces, so a run of it is one word, found by its start.
 */
object LetterWords {
    private val WORD = Regex("[\\p{L}\\p{N}\\p{M}]+")
    private val MARKS = Regex("\\p{M}+")

    /** One searchable piece of a message: its words, or a picture's caption or description. */
    data class Piece(val text: String, val inPicture: Boolean)

    /** What a message has to search, in order: its words, then its pictures'. Empty for hidden ones. */
    fun pieces(message: ChatMessage): List<Piece> {
        if (message.hidden) return emptyList()
        return buildList {
            if (message.role == MessageRole.USER || message.role == MessageRole.ASSISTANT) {
                message.displayText.takeIf { it.isNotBlank() }?.let { add(Piece(it, inPicture = false)) }
            }
            message.images.forEach { image ->
                image.caption?.takeIf { it.isNotBlank() }?.let { add(Piece(it, inPicture = true)) }
                image.description?.takeIf { it.isNotBlank() }?.let { add(Piece(it, inPicture = true)) }
            }
        }
    }

    /** The text the word index keeps for a message (empty when there's nothing to search). */
    fun of(message: ChatMessage): String = pieces(message).joinToString("\n") { it.text }

    /** A search's words, folded and without repeats: "Cats' rain!" is ["cats", "rain"]. */
    fun terms(query: String): List<String> = WORD.findAll(query).map { fold(it.value) }.filter { it.isNotEmpty() }.distinct().toList()

    /** The words of [text] that start with one of [terms], as ranges of [text] (whole words, for marking). */
    fun matches(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty()) return emptyList()
        return WORD.findAll(text).filter { word -> fold(word.value).let { w -> terms.any { w.startsWith(it) } } }.map { it.range }.toList()
    }

    /** Which of [terms] start a word in [text]. */
    fun termsIn(text: String, terms: List<String>): Set<String> {
        val words = WORD.findAll(text).map { fold(it.value) }.toList()
        return terms.filter { term -> words.any { it.startsWith(term) } }.toSet()
    }

    /** Lower case without accents, as unicode61 folds a word. */
    fun fold(word: String): String = MARKS.replace(Normalizer.normalize(word, Normalizer.Form.NFD), "").lowercase()

    /** The word index's query for [terms]: each as the start of a word, all of them or any. */
    fun matchQuery(terms: List<String>, matchAll: Boolean): String =
        terms.joinToString(if (matchAll) " " else " OR ") { "$it*" }
}
