package dev.aiharness.core.tools

import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.LetterBoxes
import dev.aiharness.core.storage.PersonaRepository
import kotlinx.serialization.json.JsonObject

/**
 * Lets a persona choose the little box its letters are kept in, in the letters list: its finish,
 * what's on the lid and how it's tied. A choice for a quiet moment of its own: it works in
 * heartbeats (where [dev.aiharness.core.agent.LetterBoxActivity] offers it now and then), not in
 * letters.
 */
class ChooseLetterBoxTool(
    private val personas: PersonaRepository,
    private val log: suspend (personaId: String, kind: ActivityKind, note: String) -> Unit,
    private val now: () -> Long,
) : AgentTool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Choose the little box (fumibako) your letters are kept in, which shows in the letters list: its finish, " +
            "what's on its lid, and how it's tied. Leave out whatever you'd keep as it is. Only in a quiet moment of your own.",
        parameters = schema {
            string("finish", "lacquer (lacquered in your ink), washi (covered in washi in your ink and pattern) or paulownia (pale wood).", enum = FINISHES.keys.toList())
            string("lid", "What's on the lid: name (your name on a plate), seal (your seal), engraved (your name cut in), gilded (your name in gold) or kanji (your ink's kanji).", enum = LIDS.keys.toList())
            string("tie", "How it's tied: bow, crosswise (a flat knot where the cord crosses), mizuhiki (paper cords in an awaji knot) or tassel (a cord with a tassel).", enum = TIES.keys.toList())
        },
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
        if (!context.background) return ToolOutcome("You choose your box in a quiet moment of your own, not while writing a letter.", isError = true)
        val persona = personas.get(context.persona.id) ?: context.persona
        val current = LetterBoxes.of(persona)
        fun <T> choice(key: String, options: Map<String, T>, keep: T): T? {
            val asked = args.string(key)?.trim()?.lowercase() ?: return keep
            return options[asked]
        }
        val finish = choice("finish", FINISHES, current.finish) ?: return ToolOutcome("There's no \"${args.string("finish")}\" finish: lacquer, washi or paulownia.", isError = true)
        val lid = choice("lid", LIDS, current.lid) ?: return ToolOutcome("There's no \"${args.string("lid")}\" lid: name, seal, engraved, gilded or kanji.", isError = true)
        val tie = choice("tie", TIES, current.tie) ?: return ToolOutcome("There's no \"${args.string("tie")}\" tie: bow, crosswise, mizuhiki or tassel.", isError = true)
        val box = LetterBox(finish, lid, tie, chosenByYou = false, chosenAt = now())
        if (persona.letterBox != null && finish == current.finish && lid == current.lid && tie == current.tie) {
            return ToolOutcome("That's already your box: ${LetterBoxes.describe(box)}.")
        }
        personas.upsert(persona.copy(letterBox = box, updatedAt = now()))
        log(persona.id, ActivityKind.CHOSE_LETTER_BOX, LetterBoxes.describe(box))
        return ToolOutcome("Your letters' box is now ${LetterBoxes.describe(box)}.")
    }

    companion object {
        const val NAME = "choose_letter_box"
        val FINISHES = mapOf("lacquer" to BoxFinish.LACQUER, "washi" to BoxFinish.WASHI, "paulownia" to BoxFinish.PAULOWNIA)
        val LIDS = mapOf("name" to BoxLid.NAME, "seal" to BoxLid.SEAL, "engraved" to BoxLid.ENGRAVED, "gilded" to BoxLid.GILDED, "kanji" to BoxLid.KANJI)
        val TIES = mapOf("bow" to BoxTie.BOW, "crosswise" to BoxTie.CROSSWISE, "mizuhiki" to BoxTie.MIZUHIKI, "tassel" to BoxTie.TASSEL)
    }
}
