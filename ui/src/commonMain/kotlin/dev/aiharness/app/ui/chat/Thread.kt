package dev.aiharness.app.ui.chat

import dev.aiharness.core.agent.ContextSlip
import dev.aiharness.core.agent.ModelChanges
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.FoundLetter
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
     * The persona's model changed: from [ModelChanges.Notice.messageId]'s letter on, it writes with
     * the new one. [after] is when it last wrote with the old one, [at] when the new one first
     * wrote, and [told] whether that letter's context told it so.
     */
    data class ModelChange(val notice: ModelChanges.Notice, val after: Long?, val at: Long, val told: Boolean) : ThreadRow {
        override val key get() = "model-${notice.messageId}"
    }

    /**
     * Everything the persona wrote in answer to one note: its text across tool rounds, pictures,
     * thoughts, and postscripts for what it did with its tools (memories kept, and so on). [prompt]
     * is the note it answers (or a heartbeat's hidden prompt), which holds what it had in mind.
     */
    data class Letter(val messages: List<ChatMessage>, val prompt: ChatMessage? = null) : ThreadRow {
        override val key get() = "letter-${messages.first().id}"
        val createdAt: Long get() = messages.first().createdAt

        /** When it reached you: a letter in the post, at its arrival. */
        val arrivedAt: Long get() = messages.first().arrivedAt
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

        /** What it had in mind as it wrote (the index card behind its first tab). */
        val slip: ContextSlip get() = ContextSlip.of(prompt, messages)
    }
}

sealed interface LetterBlock {
    data class Words(val text: String) : LetterBlock
    data class Picture(val image: ContentPart.Image) : LetterBlock
    data class Attached(val attachment: ContentPart.Attachment) : LetterBlock
}

/** A P.S. line: [label] in the persona's ink, then [text]; [found] is a letter it looked back at, which opens. */
data class Postscript(val label: String, val text: String?, val isError: Boolean = false, val found: FoundLetter? = null)

/**
 * Groups messages (oldest first) into thread rows, oldest first, with a day heading where the date
 * changes. [prompts] are the notes and hidden heartbeat prompts the letters answer, by when they
 * were written (a reply in the post can arrive after a later note); [messages]' own notes by default.
 */
fun buildThread(
    messages: List<ChatMessage>,
    zone: ZoneId = ZoneId.systemDefault(),
    prompts: List<ChatMessage> = messages.filter { it.role == MessageRole.USER },
): List<ThreadRow> {
    val rows = mutableListOf<ThreadRow>()
    var day: LocalDate? = null
    var letter: MutableList<ChatMessage>? = null
    val notesInOrder = prompts.sortedBy { it.createdAt }
    // The note (or hidden prompt) a letter answers: the last one written before it.
    fun promptFor(first: ChatMessage) = notesInOrder.lastOrNull { it.createdAt <= first.createdAt && it.id != first.id }
    val changes = ModelChanges.notices(messages).associateBy { it.messageId }

    fun closeLetter() {
        letter?.takeIf { it.isNotEmpty() }?.let { rows += ThreadRow.Letter(it.toList(), promptFor(it.first())) }
        letter = null
    }

    fun startRow(message: ChatMessage) {
        // A letter in the post goes under the day it arrived.
        val date = Instant.ofEpochMilli(message.arrivedAt).atZone(zone).toLocalDate()
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
                if (message.hidden || (message.displayText.isBlank() && message.images.isEmpty() && message.attachments.isEmpty())) continue
                startRow(message)
                rows += ThreadRow.Note(message)
            }
            MessageRole.ASSISTANT, MessageRole.TOOL -> {
                if (message.hidden) continue
                // A letter written while the user was away is its own letter.
                if (letter?.lastOrNull()?.let { it.origin.isHeartbeat() != message.origin.isHeartbeat() } == true) closeLetter()
                // Its model changed with this one: the notice goes before the letter (a new letter if need be).
                val change = changes[message.id]
                if (change != null && letter != null) closeLetter()
                val current = letter ?: mutableListOf<ChatMessage>().also {
                    startRow(message)
                    letter = it
                }
                if (change != null) {
                    val after = messages.takeWhile { it.id != message.id }.lastOrNull { it.writtenWith?.sameModel(change.was) == true }?.createdAt
                    val told = promptFor(message)?.turnContext?.lines()?.any { it.startsWith("Your model changed:") } == true
                    rows += ThreadRow.ModelChange(change, after, message.createdAt, told)
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
            // A change shows only before a letter that does.
            is ThreadRow.ModelChange -> (rows.getOrNull(i + 1) as? ThreadRow.Letter)?.isVisible() == true
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
                "memory_search", "letter_search" -> null
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
            // The page it read, by its title (its read stamp is on the cutting too).
            "open_link" -> Postscript(
                "Read the page:",
                Regex("""<page url="([^"]*)"(?: title="([^"]*)")?""").find(result.content)?.let { m -> m.groupValues[2].ifBlank { m.groupValues[1] } },
            )
            // The letter it found first, which opens (the index card has the rest).
            "letter_search" -> result.foundLetters.firstOrNull()?.let { Postscript("Looked back at:", null, found = it) }
            // What it shared is in the letter, as a cutting or a ticket.
            "memory_search", "generate_image", "share_link" -> null
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
    "open_link" -> "open the page"
    "share_link" -> "share a link"
    "draw_sticker" -> "draw a sticker"
    "letter_search" -> "look through old letters"
    else -> "use $name"
}
