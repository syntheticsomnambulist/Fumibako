package dev.aiharness.core.letters

import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.storage.UserPersonaRepository
import dev.aiharness.core.tools.AgentTool
import dev.aiharness.core.tools.ToolContext
import dev.aiharness.core.tools.ToolOutcome
import dev.aiharness.core.tools.int
import dev.aiharness.core.tools.requireString
import dev.aiharness.core.tools.schema
import dev.aiharness.core.tools.string
import dev.aiharness.core.util.Clock
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `letter_search`: the persona looks back through its own letters with you (whichever of your
 * personas wrote them), never another persona's. Passages come back with who wrote them and when.
 */
class LetterSearchTool(
    private val search: LetterSearch,
    private val userPersonas: UserPersonaRepository,
    private val clock: Clock,
) : AgentTool {
    override val spec = ToolSpec(
        name = "letter_search",
        description = "Look back through your past letters with the user, by their words: what was said, and what's in pictures " +
            "you've sent each other. Use it when you want the exact words, a date, or a detail your memories don't hold. " +
            "Returns a few sentences around each match, with who wrote it and when; letters with more of the words come first.",
        parameters = schema {
            string("query", "Words to look for (a name, a place, a word they used). Each matches the start of a word.", required = true)
            string("from", "Whose letters: you (the user's), me (your own) or either (default).", enum = listOf("you", "me", "either"))
            integer("limit", "Most passages to return (default 5).", min = 1, max = 10)
        },
    )

    override fun isAvailable(settings: AppSettings) = settings.memory.letterSearch

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val query = args.requireString("query")
        val from = when (args.string("from")?.lowercase()) {
            "you" -> LetterFrom.YOU
            "me" -> LetterFrom.ME
            else -> LetterFrom.EITHER
        }
        val hits = search.lookUp(context.persona.id, query, from, (args.int("limit") ?: 5).coerceIn(1, 10))
        if (hits.isEmpty()) return ToolOutcome("No letters with those words.")
        val names = mutableMapOf<String?, String>()
        suspend fun nameOf(userPersonaId: String?): String = names.getOrPut(userPersonaId) {
            userPersonaId?.let { userPersonas.get(it)?.name?.takeIf(String::isNotBlank) }
                ?: context.userPersona?.name?.takeIf(String::isNotBlank) ?: "The user"
        }
        val lines = hits.map { hit ->
            val who = if (hit.byYou) "${nameOf(hit.conversation.userPersonaId)}'s note" else "Your letter"
            val where = if (hit.inPicture) " (in a picture)" else ""
            "- $who, ${DATE.format(Instant.ofEpochMilli(hit.at).atZone(clock.zone))}$where: \"${hit.passage}\""
        }
        val heading = if (hits.size == 1) "1 passage:" else "${hits.size} passages, best first:"
        return ToolOutcome((listOf(heading) + lines).joinToString("\n"), foundLetters = hits.map { it.message.id })
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH)
    }
}
