package dev.aiharness.core.tools

import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.storage.PersonaRepository
import kotlinx.serialization.json.JsonObject

/**
 * Lets a persona change what it's wearing: for its pictures and for how it describes itself. The
 * change is persona-wide and lasts until the next one; "default" puts its usual clothes back on.
 */
class ChangeOutfitTool(
    private val personas: PersonaRepository,
    private val log: suspend (personaId: String, kind: ActivityKind, note: String) -> Unit,
    private val now: () -> Long,
) : AgentTool {
    override val spec = ToolSpec(
        name = "change_outfit",
        description = "Change what you're wearing, e.g. getting ready to go out, or into something comfortable for the night. " +
            "It stays until you change again, in every conversation, and your pictures show it. Use \"default\" to go back to your usual clothes.",
        parameters = schema {
            string("outfit", "What you're wearing now, as short image tags (e.g. \"yellow raincoat, rain boots\"), or \"default\".", required = true)
        },
    )

    override fun isAvailable(settings: AppSettings) = true

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        val requested = args.requireString("outfit").trim()
        val persona = personas.get(context.persona.id) ?: context.persona
        val backToDefault = requested.isEmpty() || requested.equals("default", ignoreCase = true)
        val outfit = if (backToDefault) null else requested.take(300)
        if (outfit == persona.outfit) return ToolOutcome(if (outfit == null) "You're already in your usual clothes." else "You're already wearing that.")
        personas.upsert(persona.copy(outfit = outfit, updatedAt = now()))
        log(persona.id, ActivityKind.CHANGED_OUTFIT, outfit ?: "")
        return ToolOutcome(
            when {
                outfit != null -> "You're now wearing: $outfit."
                persona.defaultOutfit.isNotBlank() -> "You're back in your usual clothes: ${persona.defaultOutfit}."
                else -> "You're back in your usual clothes."
            },
        )
    }
}
