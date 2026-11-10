package dev.aiharness.core.transfer

import dev.aiharness.core.agent.TokenEstimator
import dev.aiharness.core.model.CardOrigin
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MoodPortrait
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PronounMix
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * What a card Fumibako made carries of its own, under `extensions.fumibako`, so a persona comes
 * back whole: her ink and tape, pronouns, and picture prompts. Other cards have none of it.
 */
@Serializable
data class FumibakoCardExtension(
    val ink: String? = null,
    val tapePattern: TapePattern? = null,
    val pronouns: Pronouns? = null,
    val alsoPronouns: List<Pronouns> = emptyList(),
    val pronounsMix: PronounMix? = null,
    val selfPrompt: String? = null,
    val defaultOutfit: String? = null,
    val stylePrompt: String? = null,
) {
    companion object {
        const val KEY = "fumibako"

        fun of(card: Card): FumibakoCardExtension? =
            card.fumibako?.let { runCatching { HarnessJson.decodeFromJsonElement<FumibakoCardExtension>(it) }.getOrNull() }
    }
}

/** The ticks on the Import a card page: what comes in besides who she is. */
data class CardChoices(
    val greetings: Boolean = true,
    val exampleDialogue: Boolean = true,
    val lore: Boolean = true,
    val wontFade: Boolean = true,
    val moods: Boolean = true,
    /** Off by default: the card's `system_prompt` would replace Fumibako's role section. */
    val systemPrompt: Boolean = false,
    /** Off by default: the card's `post_history_instructions` would end each turn's context. */
    val postHistory: Boolean = false,
    /** Her ink; null keeps a Fumibako card's own, or the default. */
    val ink: String? = null,
)

/** What the Import a card page says of a card before anything comes in. */
class CardPreview(val file: CardFile) {
    val card: Card get() = file.card
    val fumibako: FumibakoCardExtension? = FumibakoCardExtension.of(file.card)

    /** A line under her name: the start of her description, to the end of a sentence. */
    val tagline: String = firstSentences(card.description, 160)

    val whoWords: Int = words(card.description) + words(card.personality) + words(card.scenario)
    val greetings: Int = (if (card.firstMessage.isNotBlank()) 1 else 0) + card.alternateGreetings.size
    val exampleWords: Int = words(card.exampleDialogue)
    val systemPromptWords: Int = words(card.systemPrompt)
    val postHistoryWords: Int = words(card.postHistoryInstructions)

    /** The lore that comes in: the book's entries that are on and say something. */
    val lore: List<LoreEntry> = card.book?.enabled.orEmpty()

    /** Entries the card switched off: left out, and counted. */
    val loreSwitchedOff: Int = card.book?.entries.orEmpty().count { !it.enabled && it.content.isNotBlank() }
    val loreAlwaysInMind: Int = lore.count { it.constant }
    val moods: List<String> = file.moods.map { it.name }

    /**
     * About what her card adds to every letter, with [choices]: who she is, her example dialogue,
     * the lore that's always in mind, and the card's own prompts when they're on.
     */
    fun tokens(choices: CardChoices): Int {
        var text = card.description.length + card.personality.length + card.scenario.length
        if (choices.exampleDialogue) text += card.exampleDialogue.length
        if (choices.lore) text += lore.filter { it.constant }.sumOf { it.content.length }
        if (choices.systemPrompt) text += card.systemPrompt.length
        if (choices.postHistory) text += card.postHistoryInstructions.length
        return TokenEstimator.estimate("x".repeat(text))
    }

    companion object {
        fun words(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

        fun firstSentences(text: String, max: Int): String {
            val flat = text.replace(Regex("\\s+"), " ").trim()
            if (flat.length <= max) return flat
            val cut = flat.take(max)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '。'))
            return if (end >= 40) cut.take(end + 1) else cut.substringBeforeLast(' ') + "…"
        }
    }
}

/**
 * Brings a character card in as a new persona: who she is, her greetings, example dialogue,
 * lore (as lore memories), mood portraits, and the card's own prompts when ticked. The card
 * itself is kept beside her ([CardOrigin.cardRef]) so exporting her gives back what Fumibako
 * doesn't use. `{{char}}` and `{{user}}` stay as written, and work as in SillyTavern.
 */
class CardImport(
    private val personas: PersonaRepository,
    private val memories: MemoryRepository,
    private val blobs: BlobStore,
    private val clock: Clock,
) {
    suspend fun bring(file: CardFile, choices: CardChoices = CardChoices()): Persona {
        val card = file.card
        val ext = FumibakoCardExtension.of(card)
        val now = clock.nowMillis()
        val cardRef = blobs.put(HarnessJson.encodeToString(JsonObject.serializer(), card.raw).toByteArray(), "json")
        val avatar = file.portrait?.let { blobs.put(it.bytes, it.ext) }
        val moods = if (!choices.moods) emptyList() else file.moods.map { MoodPortrait(it.name, blobs.put(it.picture.bytes, it.picture.ext)) }
        val defaults = Persona(id = "", name = "", createdAt = 0, updatedAt = 0)
        val persona = Persona(
            id = newId(),
            name = card.name.ifBlank { "Someone" },
            description = card.description,
            personality = card.personality,
            scenario = card.scenario,
            greeting = if (choices.greetings) card.firstMessage else "",
            alternateGreetings = if (choices.greetings) card.alternateGreetings else emptyList(),
            exampleDialogue = if (choices.exampleDialogue) card.exampleDialogue else "",
            systemPromptOverride = card.systemPrompt.takeIf { choices.systemPrompt && it.isNotBlank() },
            postHistoryInstructions = card.postHistoryInstructions.takeIf { choices.postHistory && it.isNotBlank() },
            fromCard = CardOrigin(
                creator = card.creator,
                version = card.version,
                tags = card.tags,
                creatorNotes = card.creatorNotes,
                cardRef = cardRef,
                importedAt = now,
            ),
            selfPrompt = ext?.selfPrompt ?: "",
            defaultOutfit = ext?.defaultOutfit ?: "",
            stylePrompt = ext?.stylePrompt,
            avatarRef = avatar,
            moodPortraits = moods,
            ink = choices.ink ?: ext?.ink ?: defaults.ink,
            tapePattern = ext?.tapePattern ?: defaults.tapePattern,
            pronouns = ext?.pronouns,
            alsoPronouns = ext?.alsoPronouns.orEmpty(),
            pronounsMix = ext?.pronounsMix ?: defaults.pronounsMix,
            createdAt = now,
            updatedAt = now,
        )
        personas.upsert(persona)
        if (choices.lore) card.book?.let { addLore(persona.id, it, choices.wontFade) }
        return persona
    }

    /**
     * A lorebook's entries as lore memories of [personaId]'s: the entries that are on (the others
     * are left out), `constant` ones always in mind, `keys` as recall words. Returns how many came in.
     */
    suspend fun addLore(personaId: String, book: Lorebook, wontFade: Boolean = true): Int {
        val now = clock.nowMillis()
        val entries = book.enabled.sortedBy { it.order }
        entries.forEachIndexed { i, entry ->
            // Their order is kept by a millisecond each, so the book reads as it was written.
            val at = now + i
            memories.upsert(
                MemoryEntry(
                    id = newId(),
                    personaId = personaId,
                    content = entry.content,
                    kind = MemoryKind.LORE,
                    pinned = entry.constant,
                    recallWords = entry.keys,
                    wontFade = wontFade,
                    loreEntryId = entry.id,
                    createdAt = at,
                    updatedAt = at,
                    lastAccessedAt = at,
                ),
            )
        }
        return entries.size
    }
}
