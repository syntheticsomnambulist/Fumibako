package dev.aiharness.core.agent

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.FoundLetter
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a persona had in mind when it wrote a letter (the index card behind its letter's first tab):
 * the context block stored on the note it answered, in sections, what it read of that note's
 * attachments, the letters it looked up, and the turn's tokens. Nothing is fetched to show it.
 */
data class ContextSlip(
    val time: List<String>,
    /** "Your model changed: ..." when its model had just changed. */
    val model: List<String>,
    val planner: List<String>,
    val memories: List<Recalled>,
    val stickers: List<String>,
    /** Anything else the context held (its outfit, say). */
    val also: List<String>,
    /** What it read of the note's pictures and attachments: their descriptions. */
    val read: List<String>,
    val searches: List<Search>,
    val tokensRead: Long,
    val tokensWritten: Long,
) {
    /** A memory that came up: its feeling ("always in mind" for core ones), and what it holds. */
    data class Recalled(val feeling: String?, val text: String, val core: Boolean = false)

    /** One `letter_search`: what it looked for, and what came back. */
    data class Search(val query: String, val found: List<FoundLetter>)

    val isEmpty: Boolean
        get() = time.isEmpty() && model.isEmpty() && planner.isEmpty() && memories.isEmpty() && stickers.isEmpty() &&
            also.isEmpty() && read.isEmpty() && searches.isEmpty() && tokensRead == 0L && tokensWritten == 0L

    companion object {
        private val TIME = listOf("Current time:", "Time since the previous message:", "Season:")
        private val ALSO = listOf("You're wearing:")
        private const val MODEL = "Your model changed:"
        private const val RECALLED = "Memories that come to mind:"
        private const val CORE = "Recently updated core memories:"
        private const val NEW_STICKERS = "New stickers you can use:"

        /**
         * The slip for a letter: [prompt] is the note (or hidden heartbeat prompt) it answers, with the
         * stored context; [letter] is what the persona wrote, across its tool rounds.
         */
        fun of(prompt: ChatMessage?, letter: List<ChatMessage>): ContextSlip {
            val time = mutableListOf<String>()
            val model = mutableListOf<String>()
            val planner = mutableListOf<String>()
            val memories = mutableListOf<Recalled>()
            val stickers = mutableListOf<String>()
            val also = mutableListOf<String>()
            var list: String? = null
            prompt?.turnContext?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { line ->
                when {
                    line == RECALLED || line == CORE -> list = line
                    line.startsWith("- ") && list != null -> memories += recalled(line.removePrefix("- "), core = list == CORE)
                    else -> {
                        list = null
                        when {
                            TIME.any { line.startsWith(it) } -> time += line
                            line.startsWith(MODEL) -> model += line
                            ALSO.any { line.startsWith(it) } -> also += line
                            line.startsWith(NEW_STICKERS) || (line.startsWith("[") && line.endsWith("]")) -> stickers += line.removeSurrounding("[", "]")
                            // The planner's lines have no common start; everything else in the block has one.
                            else -> planner += line
                        }
                    }
                }
            }
            val read = prompt?.parts.orEmpty().mapNotNull { part ->
                when (part) {
                    is ContentPart.Image -> part.description?.takeIf { it.isNotBlank() }?.let { "${part.caption?.takeIf(String::isNotBlank) ?: "A picture"}: $it" }
                    is ContentPart.Attachment -> part.description?.takeIf { it.isNotBlank() }?.let { "${part.title ?: part.name ?: part.kind.name.lowercase()}: $it" }
                    else -> null
                }
            }
            val calls = letter.flatMap { it.toolCalls }.associateBy { it.id }
            val searches = letter.flatMap { it.toolResults }.filter { it.name == "letter_search" && !it.isError }.map { result ->
                val query = calls[result.callId]?.argumentsJson
                    ?.let { runCatching { (HarnessJson.parseToJsonElement(it) as? JsonObject)?.get("query")?.jsonPrimitive?.contentOrNull }.getOrNull() }
                Search(query.orEmpty(), result.foundLetters)
            }
            val replies = letter.filter { it.role == MessageRole.ASSISTANT }
            return ContextSlip(
                time = time,
                model = model,
                planner = planner,
                memories = memories,
                stickers = stickers,
                also = also,
                read = read,
                searches = searches,
                tokensRead = replies.sumOf { it.usage?.read(it.providerType) ?: 0L },
                tokensWritten = replies.sumOf { it.usage?.outputTokens ?: 0L },
            )
        }

        /** "(12 Oct 2026, felt worried, now hopeful, a little hazy) Sam's interview" → its feeling now, and what it holds. */
        private fun recalled(line: String, core: Boolean): Recalled {
            if (core) return Recalled(null, line, core = true)
            if (!line.startsWith("(")) return Recalled(null, line)
            val close = line.indexOf(") ").takeIf { it > 0 } ?: return Recalled(null, line)
            val notes = line.substring(1, close).split(", ")
            val feeling = notes.firstNotNullOfOrNull { note ->
                when {
                    note.startsWith("now ") -> note.removePrefix("now ")
                    else -> null
                }
            } ?: notes.firstNotNullOfOrNull { note ->
                when {
                    note.startsWith("feels ") -> note.removePrefix("feels ")
                    note.startsWith("felt ") -> note.removePrefix("felt ")
                    else -> null
                }
            }
            return Recalled(feeling, line.substring(close + 2))
        }
    }
}
