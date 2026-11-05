package dev.aiharness.core.memory

import kotlin.math.ln

/** Minimal tokenizer + light stemmer for keyword recall. Works without any embedding endpoint. */
object TextTokens {
    private val STOPWORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "could", "did", "do", "does",
        "for", "from", "had", "has", "have", "he", "her", "hers", "him", "his", "how", "i", "if", "in", "into",
        "is", "it", "its", "just", "me", "my", "no", "not", "of", "on", "or", "our", "she", "so", "than", "that",
        "the", "their", "them", "then", "there", "they", "this", "to", "too", "us", "was", "we", "were", "what",
        "when", "where", "which", "who", "why", "will", "with", "would", "you", "your", "yours", "im", "ive",
        "dont", "about", "also", "really", "very", "some", "any", "all", "get", "got", "like", "yeah", "okay", "ok",
    )

    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    fun tokenize(text: String): List<String> = text.lowercase()
        .replace("'", "")
        .split(splitter)
        .asSequence()
        .filter { it.length > 1 && it !in STOPWORDS }
        .map(::stem)
        .toList()

    private fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 3 && word.endsWith("es") && !word.endsWith("ses") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }
}

/**
 * BM25 over a small in-memory corpus (one persona's memories). Rebuilt per query; persona memory
 * counts are small enough (hundreds to low thousands) that this stays fast on a phone.
 */
class KeywordIndex(documents: Map<String, String>, private val k1: Double = 1.2, private val b: Double = 0.75) {
    private val docTokens: Map<String, Map<String, Int>> =
        documents.mapValues { (_, text) -> TextTokens.tokenize(text).groupingBy { it }.eachCount() }
    private val docLengths = docTokens.mapValues { it.value.values.sum() }
    private val avgLength = docLengths.values.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0
    private val documentFrequency: Map<String, Int> = buildMap {
        docTokens.values.forEach { tf -> tf.keys.forEach { term -> merge(term, 1, Int::plus) } }
    }

    /** Raw BM25 scores for documents matching at least one query term. */
    fun score(query: String): Map<String, Double> {
        val terms = TextTokens.tokenize(query).distinct()
        if (terms.isEmpty() || docTokens.isEmpty()) return emptyMap()
        val n = docTokens.size.toDouble()
        val scores = HashMap<String, Double>()
        terms.forEach { term ->
            val df = documentFrequency[term] ?: return@forEach
            val idf = ln(1 + (n - df + 0.5) / (df + 0.5))
            docTokens.forEach { (id, tf) ->
                val f = tf[term] ?: return@forEach
                val len = docLengths[id] ?: 0
                val s = idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * len / avgLength))
                scores.merge(id, s, Double::plus)
            }
        }
        return scores
    }

    /** Scores squashed into 0..1 so they can be mixed with cosine similarity. */
    fun normalizedScore(query: String): Map<String, Double> = score(query).mapValues { (_, s) -> s / (s + SATURATION) }

    companion object {
        private const val SATURATION = 2.0
    }
}
