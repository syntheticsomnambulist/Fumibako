package dev.aiharness.core.memory

import kotlinx.serialization.Serializable

/** The nine families mood words fall into (each has a stamp in the memory book). */
@Serializable
enum class FeelingFamily { BRIGHT, FOND, PROUD, CALM, SAD, UNEASY, CROSS, WONDER, WISTFUL }

@Serializable
enum class Intensity { MILD, STRONG }

@Serializable
data class Feeling(val family: FeelingFamily, val intensity: Intensity)

/**
 * Sorts a persona's mood words ("fond", "a bit anxious", "very proud") into a family and an
 * intensity, from a built-in list. Personas only ever choose words; no model is asked for numbers.
 * Words the list doesn't know can be sorted once by a model and passed in as `learned`.
 */
object FeelingWords {
    private val STRONGER = setOf("very", "so", "really", "deeply", "incredibly", "extremely", "utterly", "terribly", "awfully", "truly", "completely", "absolutely", "intensely", "hugely")
    private val SOFTER = setOf("a", "bit", "little", "slightly", "somewhat", "kind", "of", "kinda", "sort", "mildly", "quietly", "faintly", "rather", "fairly", "gently")

    /** Built-in list: family, then intensity, then words. */
    private val TABLE: Map<String, Feeling> = buildMap {
        fun add(family: FeelingFamily, intensity: Intensity, words: String) =
            words.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { put(it, Feeling(family, intensity)) }
        val mild = Intensity.MILD
        val strong = Intensity.STRONG
        add(FeelingFamily.BRIGHT, mild, "happy, glad, cheerful, pleased, amused, light, lighthearted, sunny, chipper, upbeat, hopeful, optimistic, playful, giggly, bubbly, giddy, good, fine, bright, merry, silly, cheeky")
        add(FeelingFamily.BRIGHT, strong, "delighted, elated, overjoyed, ecstatic, thrilled, joyful, joyous, gleeful, euphoric, excited, radiant, jubilant, exhilarated")
        add(FeelingFamily.FOND, mild, "fond, warm, affectionate, tender, caring, soft, sweet, friendly, grateful, thankful, appreciative, gentle, kind, sympathetic, reassuring, supportive")
        add(FeelingFamily.FOND, strong, "loving, adoring, smitten, devoted, besotted, touched, moved, heartwarmed, protective, cherished, treasured")
        add(FeelingFamily.PROUD, mild, "satisfied, accomplished, confident, capable, determined, encouraged, smug, assured, bold, brave, focused")
        add(FeelingFamily.PROUD, strong, "proud, triumphant, vindicated, victorious, fierce")
        add(FeelingFamily.CALM, mild, "calm, content, peaceful, relaxed, serene, settled, comfortable, cozy, cosy, mellow, quiet, steady, relieved, sleepy, drowsy, unbothered, patient, tranquil, watchful, easy, rested, grounded, still")
        add(FeelingFamily.CALM, strong, "blissful, at peace")
        add(FeelingFamily.SAD, mild, "sad, down, blue, low, glum, disappointed, sorry, regretful, weary, tired, lonely, hurt, deflated, mopey, sombre, somber, drained")
        add(FeelingFamily.SAD, strong, "heartbroken, devastated, grieving, miserable, despairing, crushed, bereft, gutted, anguished, desolate, hopeless")
        add(FeelingFamily.UNEASY, mild, "worried, nervous, uneasy, unsure, uncertain, apprehensive, restless, awkward, embarrassed, shy, flustered, concerned, tense, wary, doubtful, sheepish, self-conscious, jittery, antsy, bashful")
        add(FeelingFamily.UNEASY, strong, "anxious, scared, afraid, frightened, panicked, terrified, overwhelmed, distressed, ashamed, guilty, dreading, horrified, mortified")
        add(FeelingFamily.CROSS, mild, "annoyed, irritated, grumpy, cross, frustrated, impatient, sulky, miffed, peeved, jealous, defensive, stubborn, prickly, testy, huffy")
        add(FeelingFamily.CROSS, strong, "angry, furious, livid, resentful, indignant, outraged, bitter, betrayed, exasperated, enraged")
        add(FeelingFamily.WONDER, mild, "curious, interested, intrigued, surprised, puzzled, inquisitive, mischievous, teasing, impressed, alert, attentive, eager, keen, puckish")
        add(FeelingFamily.WONDER, strong, "fascinated, amazed, astonished, awed, inspired, stunned, shocked, captivated, enchanted, spellbound")
        add(FeelingFamily.WISTFUL, mild, "wistful, nostalgic, pensive, reflective, thoughtful, bittersweet, sentimental, dreamy, homesick, melancholy, contemplative, introspective, missing")
        add(FeelingFamily.WISTFUL, strong, "longing, yearning, aching, pining")
    }

    /** The family and intensity of [mood], or null when the list (and [learned]) don't know it. */
    fun lookup(mood: String?, learned: Map<String, Feeling> = emptyMap()): Feeling? {
        val phrase = normalize(mood ?: return null)
        if (phrase.isEmpty()) return null
        known(phrase, learned)?.let { return it }
        val words = phrase.split(' ').filter { it.isNotEmpty() }
        val stronger = words.any { it in STRONGER }
        val softer = words.any { it in SOFTER }
        val core = words.filterNot { it in STRONGER || it in SOFTER }
        val base = core.joinToString(" ").let { known(it, learned) } ?: core.firstNotNullOfOrNull { known(it, learned) } ?: return null
        return when {
            stronger -> base.copy(intensity = Intensity.STRONG)
            softer -> base.copy(intensity = Intensity.MILD)
            else -> base
        }
    }

    /** Lowercase, single-spaced, no punctuation: the key mood words are stored and learned under. */
    fun normalize(mood: String): String =
        mood.lowercase().replace(Regex("[^\\p{L}\\s-]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun known(word: String, learned: Map<String, Feeling>): Feeling? =
        TABLE[word] ?: learned[word] ?: word.removeSuffix("ly").takeIf { it != word }?.let { TABLE[it] }
}
