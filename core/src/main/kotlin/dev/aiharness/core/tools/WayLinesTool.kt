package dev.aiharness.core.tools

import dev.aiharness.core.letters.WayLines
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.storage.PersonaRepository
import kotlinx.serialization.json.JsonObject

/**
 * Lets a persona rewrite its own lines for the postmark, in a quiet moment of its own (where
 * [dev.aiharness.core.agent.WayLinesActivity] offers it, at most weekly), never in letters. Lines
 * you wrote stay as they are.
 */
class RewriteWayLinesTool(
    private val personas: PersonaRepository,
    private val log: suspend (personaId: String, kind: ActivityKind, count: Int) -> Unit,
    private val now: () -> Long,
) : AgentTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Rewrite your own lines for the postmark on your replies while they're on their way " +
            "(\"<your name>'s reply is on its way · she's looking for a stamp\"): a few short lines in the third person, " +
            "in your voice. Lines your correspondent wrote stay. Only in a quiet moment of your own, at most once a week.",
        parameters = schema {
            stringArray("lines", "Two to five short lines, each what follows \"<your name>'s reply is on its way ·\".", required = true)
        },
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        if (!context.background) return ToolOutcome("You rewrite your postmark lines in a quiet moment of your own, not while writing a letter.", isError = true)
        val persona = personas.get(context.persona.id) ?: context.persona
        val at = now()
        persona.wayLinesWrittenAt?.let { last ->
            if (at - last < WayLines.WEEK) return ToolOutcome("You rewrote them less than a week ago; they stay as they are for now.", isError = true)
        }
        val lines = args.stringList("lines").mapNotNull { WayLines.clean(it, persona.name) }.distinct()
        if (lines.isEmpty()) return ToolOutcome("No lines were given.", isError = true)
        val before = persona.wayLines.filter { !it.yours }.map { it.text }.ifEmpty { if (persona.wayLines.isEmpty()) WayLines.plain(persona) else emptyList() }
        val updated = WayLines.withTheirs(persona, lines, at)
        personas.upsert(updated.copy(updatedAt = at))
        val changed = updated.wayLines.filter { !it.yours }.count { it.text !in before }
        if (changed > 0) log(persona.id, ActivityKind.REWROTE_LINES, changed)
        return ToolOutcome("Your postmark lines are now:\n" + WayLines.of(updated).joinToString("\n") { "- $it" })
    }

    companion object {
        const val NAME = "rewrite_postmark_lines"
    }
}
