package dev.aiharness.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.random.Random

@Serializable
enum class PronounKind(val label: String) {
    SHE("she/her"),
    HE("he/him"),
    THEY("they/them"),
    IT("it/its"),

    /** No pronouns: the name is used wherever one would be. */
    NAME_ONLY("name only"),
    CUSTOM("custom"),
}

/**
 * A persona's pronouns. [custom] holds, for [PronounKind.CUSTOM], the subject, object, possessive,
 * possessive pronoun and reflexive forms (e.g. xe, xem, xyr, xyrs, xemself), and [customPlural]
 * says whether verbs go as with "they" ("xe are") rather than "she" ("xe is").
 */
@Serializable
data class Pronouns(
    val kind: PronounKind,
    val custom: List<String> = emptyList(),
    val customPlural: Boolean = false,
) {
    private fun form(i: Int): String? = when (kind) {
        PronounKind.SHE -> listOf("she", "her", "her", "hers", "herself")[i]
        PronounKind.HE -> listOf("he", "him", "his", "his", "himself")[i]
        PronounKind.THEY -> listOf("they", "them", "their", "theirs", "themself")[i]
        PronounKind.IT -> listOf("it", "it", "its", "its", "itself")[i]
        PronounKind.NAME_ONLY -> null
        PronounKind.CUSTOM -> custom.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
    }

    val subject: String? get() = form(0)
    val objectForm: String? get() = form(1)
    val possessive: String? get() = form(2)
    val possessivePronoun: String? get() = form(3)
    val reflexive: String? get() = form(4)

    /** Verbs agree as with "they". */
    val plural: Boolean get() = kind == PronounKind.THEY || (kind == PronounKind.CUSTOM && customPlural)

    /** "she/her", "xe/xem", "name only". */
    val label: String
        get() = when (kind) {
            PronounKind.CUSTOM -> listOfNotNull(subject, objectForm).joinToString("/").ifEmpty { "custom" }
            else -> kind.label
        }

    /** What the persona is told about its own pronouns. */
    val forPrompt: String
        get() = when (kind) {
            PronounKind.NAME_ONLY -> "You don't use pronouns: others refer to you by your name."
            else -> "Your pronouns are " + listOfNotNull(subject, objectForm, possessive).distinct().joinToString("/") + "."
        }
}

/**
 * How the app's own sentences refer to someone: by pronoun when they have one, by name when they
 * don't (or haven't said). `words.they + " " + words.verb("draws", "draw")` reads "she draws",
 * "they draw" or "Aria draws".
 */
class Words(private val name: String, private val pronouns: Pronouns?) {
    private val p = pronouns?.takeIf { it.subject != null }

    /** "she", or the name. */
    val they: String get() = p?.subject ?: name

    /** "She", or the name (for the start of a sentence). */
    val theyCapital: String get() = they.replaceFirstChar { it.uppercase() }

    /** "her", or the name. */
    val them: String get() = p?.objectForm ?: name

    /** "her" (as in "her letters"), or "Aria's". */
    val their: String get() = p?.possessive ?: "$name's"

    /** "herself", or the name. */
    val themselves: String get() = p?.reflexive ?: name

    /** The verb as it goes with [they]: [single] ("draws") or [plural] ("draw"). */
    fun verb(single: String, plural: String): String = if (p?.plural == true) plural else single

    /** "is" or "are". */
    val be: String get() = verb("is", "are")
}

/** How the app's sentences choose between a persona's sets of pronouns. */
@Serializable
enum class PronounMix(val label: String) {
    /** Sentence by sentence, in turn: she, then they, then she. */
    TAKE_TURNS("Take turns"),

    /** Any set for any sentence, changing from day to day. */
    AT_RANDOM("At random"),
}

/** All of a persona's sets, the first one first. */
val Persona.pronounSets: List<Pronouns> get() = listOfNotNull(pronouns) + alsoPronouns

/** How the app's text refers to this persona (with several sets, the first). */
val Persona.words: Words get() = Words(name, pronouns)

/**
 * How the app's text refers to this persona in its [turn]-th sentence on a screen: with several
 * sets, they take turns sentence by sentence, or are picked at random (the same each day, so a
 * sentence doesn't change as you look at it).
 */
fun Persona.words(turn: Int, today: LocalDate = LocalDate.now()): Words {
    val sets = pronounSets
    if (sets.size <= 1) return Words(name, sets.firstOrNull())
    val i = when (pronounsMix) {
        PronounMix.TAKE_TURNS -> Math.floorMod(turn, sets.size)
        PronounMix.AT_RANDOM -> Random(id.hashCode() * 31L + turn * 7919L + today.toEpochDay()).nextInt(sets.size)
    }
    return Words(name, sets[i])
}

/** "she/they", "he/him", "xe/xem", or every set's label when they don't share a form. */
val Persona.pronounsLabel: String?
    get() {
        val sets = pronounSets.filter { it.kind != PronounKind.NAME_ONLY }
        return when {
            sets.isEmpty() -> null
            sets.size == 1 -> sets.single().label
            sets.all { it.subject != null } -> sets.mapNotNull { it.subject }.distinct().joinToString("/")
            else -> sets.joinToString(" · ") { it.label }
        }
    }

/** What the persona is told about its pronouns: every set, and that any of them is right. */
val Persona.pronounsForPrompt: String?
    get() {
        val sets = pronounSets
        if (sets.size <= 1) return sets.firstOrNull()?.forPrompt
        val named = sets.filter { it.kind != PronounKind.NAME_ONLY }.map { set ->
            listOfNotNull(set.subject, set.objectForm, set.possessive).distinct().joinToString("/")
        }.distinct()
        val byName = sets.any { it.kind == PronounKind.NAME_ONLY }
        val listed = when (named.size) {
            0 -> ""
            1 -> named.single()
            else -> named.dropLast(1).joinToString(", ") + " and " + named.last()
        }
        return when {
            named.isEmpty() -> PronounKind.NAME_ONLY.let { Pronouns(it).forPrompt }
            byName -> "Your pronouns are $listed, or just your name; others may use any of them."
            else -> "Your pronouns are $listed; others may use any of them."
        }
    }
