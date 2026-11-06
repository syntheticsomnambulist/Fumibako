package dev.aiharness.core.model

import kotlinx.serialization.Serializable

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

/** How the app's text refers to this persona. */
val Persona.words: Words get() = Words(name, pronouns)
