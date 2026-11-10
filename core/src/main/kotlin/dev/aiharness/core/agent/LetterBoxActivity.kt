package dev.aiharness.core.agent

import dev.aiharness.core.model.LetterBoxes
import dev.aiharness.core.tools.ChooseLetterBoxTool

/**
 * Now and then, a persona may choose a new box for its letters (docs/plan-desktop.md, "Building
 * them"). A small offer: it joins a private moment that's happening anyway and never brings one
 * about ([optional]), takes no slot, and comes at most every [EVERY] days, and not within
 * [SETTLED] days of the box last being chosen (by the persona or by you).
 */
class LetterBoxActivity : HeartbeatActivity {
    override val name = "letter_box"
    override val optional = true

    override suspend fun invite(beat: Beat): String? {
        val persona = beat.persona
        if (beat.h.tools.available(beat.setup.settings, persona).none { it.spec.name == ChooseLetterBoxTool.NAME }) return null
        if ((beat.lastInvited(name) ?: 0L) > beat.now - EVERY * DAY) return null
        val chosenAt = persona.letterBox?.chosenAt ?: 0L
        if (chosenAt > beat.now - SETTLED * DAY) return null
        val box = LetterBoxes.of(persona)
        return "Your letters to ${beat.userName} are kept in a little box of your own (a fumibako) in their letters list: " +
            "${LetterBoxes.describe(box)}. If you'd like a different one, you can choose it with ${ChooseLetterBoxTool.NAME}: " +
            "lacquered in your ink, covered in washi in your ink and pattern, or pale paulownia wood; your name on a plate, your seal, " +
            "your name engraved, your name in gold, or your ink's kanji on the lid; tied with a bow, crosswise with a flat knot, " +
            "with mizuhiki, or with a tasseled cord. There's no need to change it."
    }

    companion object {
        const val EVERY = 12
        const val SETTLED = 30
        private const val DAY = 86_400_000L
    }
}
