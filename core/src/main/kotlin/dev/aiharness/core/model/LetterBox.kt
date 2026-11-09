package dev.aiharness.core.model

import kotlinx.serialization.Serializable

/**
 * The little box (fumibako) a persona's letters are kept in, drawn in the letters list
 * (docs/design/fumibako-studies.html; chosen with the user in docs/plan-desktop.md). Every box is
 * the same size; what differs is its finish, what's on the lid and how it's tied. A persona picks
 * its own (`choose_letter_box`, now and then in a heartbeat), or you choose for it on its page.
 */
@Serializable
data class LetterBox(
    val finish: BoxFinish = BoxFinish.LACQUER,
    val lid: BoxLid = BoxLid.NAME,
    val tie: BoxTie = BoxTie.BOW,
    /** True when you chose it for them; false when they chose it themselves. */
    val chosenByYou: Boolean = false,
    /** When it was last chosen, by either of you (0: never). */
    val chosenAt: Long = 0L,
)

@Serializable
enum class BoxFinish {
    /** Their ink, deep, under clear lacquer, with a thin line round the lid. */
    LACQUER,

    /** Covered in washi in their ink and tape pattern. */
    WASHI,

    /** Pale paulownia (kiri) wood, the wood keepsake boxes are made of. */
    PAULOWNIA,
}

@Serializable
enum class BoxLid {
    /** Their name on a paper plate. */
    NAME,

    /** Their seal: their ink's kanji in a ring, on a paper disc. */
    SEAL,

    /** Their name cut into the lid. */
    ENGRAVED,

    /** Their name cut in and filled with gold. */
    GILDED,

    /** Their ink's kanji, large. */
    KANJI,
}

@Serializable
enum class BoxTie {
    /** A cord in their ink, tied in a bow. */
    BOW,

    /** A cord round both ways, tied flat where it crosses. */
    CROSSWISE,

    /** Paper cords in their ink and the paper, in an awaji knot. */
    MIZUHIKI,

    /** A cord with a tassel hanging over the front. */
    TASSEL,
}

object LetterBoxes {
    /**
     * Their box: the one chosen, or until one is, a pick of their own made from their id, the same
     * every time (so nothing needs writing for a persona that hasn't chosen).
     */
    fun of(persona: Persona): LetterBox = persona.letterBox ?: pick(persona.id)

    fun pick(seed: String): LetterBox {
        val random = java.util.Random(seed.hashCode().toLong() * 0x5DEECE66DL + 11)
        return LetterBox(
            finish = BoxFinish.entries[random.nextInt(BoxFinish.entries.size)],
            lid = BoxLid.entries[random.nextInt(BoxLid.entries.size)],
            tie = BoxTie.entries[random.nextInt(BoxTie.entries.size)],
        )
    }

    /** The box in words, for the persona: "lacquered in your ink, your name in gold on the lid, tied with mizuhiki". */
    fun describe(box: LetterBox): String = listOf(finishWords(box.finish), lidWords(box.lid), tieWords(box.tie)).joinToString(", ")

    fun finishWords(finish: BoxFinish) = when (finish) {
        BoxFinish.LACQUER -> "lacquered in your ink"
        BoxFinish.WASHI -> "covered in washi in your ink and pattern"
        BoxFinish.PAULOWNIA -> "pale paulownia wood"
    }

    fun lidWords(lid: BoxLid) = when (lid) {
        BoxLid.NAME -> "your name on a plate on the lid"
        BoxLid.SEAL -> "your seal on the lid"
        BoxLid.ENGRAVED -> "your name engraved on the lid"
        BoxLid.GILDED -> "your name in gold on the lid"
        BoxLid.KANJI -> "your ink's kanji on the lid"
    }

    fun tieWords(tie: BoxTie) = when (tie) {
        BoxTie.BOW -> "tied with a bow"
        BoxTie.CROSSWISE -> "tied crosswise with a flat knot"
        BoxTie.MIZUHIKI -> "tied with mizuhiki"
        BoxTie.TASSEL -> "tied with a tasseled cord"
    }
}
