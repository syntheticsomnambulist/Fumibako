package dev.aiharness.app.ui.chat

import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One row of the letters thread: a day heading, one of the user's notes, or one of the persona's letters. */
sealed interface ThreadRow {
    val key: String

    data class Day(val date: LocalDate) : ThreadRow {
        override val key get() = "day-$date"
    }

    data class Note(val message: ChatMessage) : ThreadRow {
        override val key get() = message.id
    }

    /**
     * Everything the persona wrote in answer to one note: its text across tool rounds, pictures,
     * thoughts, and postscripts for what it did with its tools (memories kept, and so on).
     */
    data class Letter(val messages: List<ChatMessage>) : ThreadRow {
        override val key get() = "letter-${messages.first().id}"
        val createdAt: Long get() = messages.first().createdAt
        val paragraphs: List<String> get() = messages.filter { it.role == MessageRole.ASSISTANT }.map { it.displayText }.filter { it.isNotBlank() }
        val mood: String? get() = messages.lastOrNull { it.mood != null }?.mood
        val fromHeartbeat: Boolean get() = messages.any { it.role == MessageRole.ASSISTANT && it.origin == MessageOrigin.HEARTBEAT }
        val reasoning: String? get() = messages.mapNotNull { it.reasoning }.joinToString("\n\n").ifBlank { null }
        val images: List<ContentPart.Image> get() = messages.flatMap { it.images }

        /** Text, pictures and attachments in the order they were written. */
        val blocks: List<LetterBlock>
            get() = messages.flatMap { m ->
                buildList {
                    if (m.role == MessageRole.ASSISTANT && m.displayText.isNotBlank()) add(LetterBlock.Words(m.displayText))
                    m.images.forEach { add(LetterBlock.Picture(it)) }
                    m.attachments.forEach { add(LetterBlock.Attached(it)) }
                }
            }
        val errors: List<String> get() = messages.mapNotNull { it.errorMessage }
        val postscripts: List<Postscript> get() = postscriptsOf(messages)

        /** The message an edit applies to: the last one with text. */
        val editable: ChatMessage? get() = messages.lastOrNull { it.role == MessageRole.ASSISTANT && it.displayText.isNotBlank() }
    }
}

sealed interface LetterBlock {
    data class Words(val text: String) : LetterBlock
    data class Picture(val image: ContentPart.Image) : LetterBlock
    data class Attached(val attachment: ContentPart.Attachment) : LetterBlock
}

data class Postscript(val label: String, val text: String?, val isError: Boolean = false)

/** Groups messages (oldest first) into thread rows, oldest first, with a day heading where the date changes. */
fun buildThread(messages: List<ChatMessage>, zone: ZoneId = ZoneId.systemDefault()): List<ThreadRow> {
    val rows = mutableListOf<ThreadRow>()
    var day: LocalDate? = null
    var letter: MutableList<ChatMessage>? = null

    fun closeLetter() {
        letter?.takeIf { it.isNotEmpty() }?.let { rows += ThreadRow.Letter(it.toList()) }
        letter = null
    }

    fun startRow(message: ChatMessage) {
        val date = Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
        if (date != day) {
            rows += ThreadRow.Day(date)
            day = date
        }
    }

    for (message in messages) {
        when (message.role) {
            MessageRole.USER -> {
                // Hidden user turns (heartbeat prompts) still end the letter before them.
                closeLetter()
                if (message.hidden || (message.displayText.isBlank() && message.images.isEmpty())) continue
                startRow(message)
                rows += ThreadRow.Note(message)
            }
            MessageRole.ASSISTANT, MessageRole.TOOL -> {
                if (message.hidden) continue
                // A letter written while the user was away is its own letter.
                if (letter?.lastOrNull()?.let { it.origin.isHeartbeat() != message.origin.isHeartbeat() } == true) closeLetter()
                val current = letter ?: mutableListOf<ChatMessage>().also {
                    startRow(message)
                    letter = it
                }
                current += message
            }
        }
    }
    closeLetter()
    // A letter with nothing to show (e.g. only a silent tool round) is dropped, and so is its heading if left alone.
    return rows.filterIndexed { i, row ->
        when (row) {
            is ThreadRow.Letter -> row.isVisible()
            is ThreadRow.Day -> rows.drop(i + 1).takeWhile { it !is ThreadRow.Day }.any { it !is ThreadRow.Letter || it.isVisible() }
            else -> true
        }
    }
}

private fun MessageOrigin.isHeartbeat() = this == MessageOrigin.HEARTBEAT

private fun ThreadRow.Letter.isVisible() =
    paragraphs.isNotEmpty() || images.isNotEmpty() || errors.isNotEmpty() || postscripts.isNotEmpty()

private fun postscriptsOf(messages: List<ChatMessage>): List<Postscript> {
    val calls = messages.flatMap { it.toolCalls }.associateBy { it.id }
    return messages.flatMap { it.toolResults }.mapNotNull { result ->
        val args = calls[result.callId]?.argumentsJson?.let { runCatching { HarnessJson.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val content = args?.get("content")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() }
        if (result.isError) {
            return@mapNotNull when (result.name) {
                "memory_search" -> null
                "generate_image" -> Postscript("The picture didn't come out:", result.content.take(160), isError = true)
                else -> Postscript("Couldn't ${toolVerb(result.name)}:", result.content.take(160), isError = true)
            }
        }
        when (result.name) {
            "memory_save" -> Postscript("Kept in memory:", content)
            "memory_update" -> Postscript("Updated a memory:", content)
            "memory_forget" -> Postscript("Let a memory go.", null)
            "change_outfit" -> {
                val outfit = args?.get("outfit")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.trim()
                if (outfit.isNullOrEmpty() || outfit.equals("default", ignoreCase = true)) Postscript("Changed back into the usual clothes.", null)
                else Postscript("Changed into:", outfit)
            }
            "place_sticker" -> Postscript(
                "Stuck",
                result.content.removePrefix("Stuck ").removeSuffix(".")
                    .replace(" on their note", " on your note").replace(" on your letter", " on an earlier letter"),
            )
            "peel_sticker" -> Postscript("Peeled", result.content.removePrefix("Peeled ").removeSuffix("."))
            "draw_sticker" -> Postscript("Drew a new sticker:", Regex(":[a-z0-9_-]+:").find(result.content)?.value)
            "memory_search", "generate_image" -> null
            else -> Postscript("Used ${result.name}.", null)
        }
    }
}

private fun toolVerb(name: String) = when (name) {
    "memory_save" -> "keep that in memory"
    "memory_update" -> "update a memory"
    "memory_forget" -> "let a memory go"
    "change_outfit" -> "change clothes"
    "place_sticker" -> "stick a sticker"
    "peel_sticker" -> "peel a sticker off"
    "draw_sticker" -> "draw a sticker"
    else -> "use $name"
}
