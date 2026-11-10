package dev.aiharness.core.transfer

import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.Persona
import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * A persona as a character card: V3, with a V2 copy for older apps, in her portrait (a PNG). Her
 * lore goes as the card's lorebook; her other memories can go too, as plain entries. What she came
 * with from a card and Fumibako doesn't use (fields, extensions, the lorebook's settings, entries
 * the card had switched off) goes back as it came, and Fumibako's own fields go under
 * `extensions.fumibako`.
 */
object CardExport {
    /** What goes in besides who she is. */
    data class Choices(val lore: Boolean = true, val otherMemories: Boolean = false)

    /**
     * Her card's V3 JSON. [original] is the card she came from (null for one made here), [memories]
     * all of hers; [now] stamps it (seconds, as the spec has them).
     */
    fun v3(persona: Persona, memories: List<MemoryEntry>, original: JsonObject?, choices: Choices, now: Long, moodAssets: List<String> = emptyList()): JsonObject {
        val came = original?.let { CardReader.parseCard(it) }
        val base: Map<String, JsonElement> = came?.data.orEmpty()
        val ext = FumibakoCardExtension(
            ink = persona.ink,
            tapePattern = persona.tapePattern,
            pronouns = persona.pronouns,
            alsoPronouns = persona.alsoPronouns,
            pronounsMix = persona.pronounsMix,
            selfPrompt = persona.selfPrompt.ifBlank { null },
            defaultOutfit = persona.defaultOutfit.ifBlank { null },
            stylePrompt = persona.stylePrompt,
        )
        val extensions = JsonObject((came?.extensions.orEmpty()) + (FumibakoCardExtension.KEY to HarnessJson.encodeToJsonElement(ext)))
        val book = lorebook(persona, memories, came?.book, choices)
        val data = LinkedHashMap(base)
        data["name"] = JsonPrimitive(persona.name)
        data["description"] = JsonPrimitive(persona.description)
        data["personality"] = JsonPrimitive(persona.personality)
        data["scenario"] = JsonPrimitive(persona.scenario)
        data["first_mes"] = JsonPrimitive(persona.greeting)
        data["mes_example"] = JsonPrimitive(persona.exampleDialogue)
        data["creator_notes"] = JsonPrimitive(persona.fromCard?.creatorNotes ?: came?.creatorNotes.orEmpty())
        data["system_prompt"] = JsonPrimitive(persona.systemPromptOverride.orEmpty())
        data["post_history_instructions"] = JsonPrimitive(persona.postHistoryInstructions.orEmpty())
        data["alternate_greetings"] = JsonArray(persona.alternateGreetings.map(::JsonPrimitive))
        data["tags"] = JsonArray((persona.fromCard?.tags ?: came?.tags.orEmpty()).map(::JsonPrimitive))
        data["creator"] = JsonPrimitive(persona.fromCard?.creator ?: came?.creator.orEmpty())
        data["character_version"] = JsonPrimitive(persona.fromCard?.version ?: came?.version.orEmpty())
        data["extensions"] = extensions
        if (book != null) data["character_book"] = book else data.remove("character_book")
        if ("group_only_greetings" !in data) data["group_only_greetings"] = JsonArray(emptyList())
        data["assets"] = buildJsonArray {
            add(buildJsonObject { put("type", "icon"); put("uri", "ccdefault:"); put("name", "main"); put("ext", "png") })
            persona.moodPortraits.zip(moodAssets).forEach { (mood, path) ->
                add(buildJsonObject { put("type", "emotion"); put("uri", "__asset:$path"); put("name", mood.name); put("ext", mood.ref.substringAfterLast('.', "png")) })
            }
        }
        if ("creation_date" !in data) data["creation_date"] = JsonPrimitive(persona.createdAt / 1000)
        data["modification_date"] = JsonPrimitive(now / 1000)
        return buildJsonObject {
            put("spec", "chara_card_v3")
            put("spec_version", "3.0")
            put("data", JsonObject(data))
        }
    }

    /** The V2 copy of a V3 card, for apps that only read `chara`. */
    fun v2(v3: JsonObject): JsonObject {
        val data = LinkedHashMap(v3["data"] as JsonObject)
        V3_ONLY.forEach { data.remove(it) }
        (data["character_book"] as? JsonObject)?.let { book ->
            val entries = (book["entries"] as? JsonArray).orEmpty().map { e -> JsonObject((e as JsonObject) - "use_regex") }
            data["character_book"] = JsonObject(book + ("entries" to JsonArray(entries)))
        }
        return buildJsonObject {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", JsonObject(data))
        }
    }

    /**
     * Her portrait ([portraitPng], or a plain one in [inkRgb] when she has none) with [v3] inside as
     * `ccv3` and its V2 copy as `chara`; [moods] are her mood portraits, by the paths [v3] gives them.
     */
    fun png(portraitPng: ByteArray?, v3: JsonObject, inkRgb: Int, moods: List<Pair<String, ByteArray>> = emptyList()): ByteArray {
        val picture = portraitPng?.takeIf { Png.isPng(it) } ?: Png.plain(400, 600, inkRgb)
        val text = { o: JsonObject -> Base64.getEncoder().encodeToString(HarnessJson.encodeToString(JsonObject.serializer(), o).toByteArray()) }
        val chunks = listOf("chara" to text(v2(v3)), "ccv3" to text(v3)) +
            moods.map { (path, bytes) -> "chara-ext-asset_:$path" to Base64.getEncoder().encodeToString(bytes) }
        return Png.withTexts(picture, chunks) { it == "chara" || it == "ccv3" || it.startsWith("chara-ext-asset_") }
    }

    /** The lorebook her card carries: her lore, her other memories when chosen, and entries the card had switched off. */
    private fun lorebook(persona: Persona, memories: List<MemoryEntry>, came: Lorebook?, choices: Choices): JsonObject? {
        val live = memories.filter { it.personaId == persona.id && !it.isReplaced }
        val lore = if (choices.lore) live.filter { it.kind == MemoryKind.LORE }.sortedBy { it.createdAt } else emptyList()
        val others = if (choices.otherMemories) live.filter { it.kind != MemoryKind.LORE }.sortedBy { it.createdAt } else emptyList()
        val off = came?.entries.orEmpty().filter { !it.enabled }
        if (lore.isEmpty() && others.isEmpty() && off.isEmpty() && came == null) return null
        val byId = came?.entries.orEmpty().filter { it.id != null }.associateBy { it.id }
        var next = 0
        val entries = buildList {
            (lore + others).forEach { m ->
                val from = m.loreEntryId?.let { byId[it] }
                val keys = (m.recallWords + if (m.kind == MemoryKind.LORE) emptyList() else m.tags).distinct()
                val entry = LinkedHashMap<String, JsonElement>(from?.raw?.let(::asCardEntry).orEmpty())
                entry["keys"] = JsonArray(keys.map(::JsonPrimitive))
                entry["content"] = JsonPrimitive(m.content)
                entry["enabled"] = JsonPrimitive(true)
                entry["constant"] = JsonPrimitive(m.isCore)
                entry.putIfAbsent("extensions", JsonObject(emptyMap()))
                entry.putIfAbsent("insertion_order", JsonPrimitive(next))
                entry.putIfAbsent("use_regex", JsonPrimitive(false))
                entry.putIfAbsent("id", JsonPrimitive(next))
                add(JsonObject(entry))
                next++
            }
            off.forEach { add(asCardEntry(it.raw)) }
        }
        val settings = came?.raw.orEmpty().filterKeys { it != "entries" }
        return JsonObject(
            LinkedHashMap<String, JsonElement>(settings).apply {
                putIfAbsent("name", JsonPrimitive("${persona.name}'s lore"))
                putIfAbsent("extensions", JsonObject(emptyMap()))
                put("entries", JsonArray(entries))
            },
        )
    }

    /** An entry in a card's shape (from World Info's, when that's how it came). */
    private fun asCardEntry(raw: JsonObject): JsonObject {
        if ("keys" in raw || "key" !in raw) return raw
        val e = LinkedHashMap<String, JsonElement>(raw)
        e.remove("key")?.let { e["keys"] = it }
        e.remove("keysecondary")?.let { e["secondary_keys"] = it }
        e.remove("disable")?.let { e["enabled"] = JsonPrimitive(!((it as? JsonPrimitive)?.content == "true")) }
        e.remove("order")?.let { e["insertion_order"] = it }
        e.remove("uid")?.let { e.putIfAbsent("id", it) }
        return JsonObject(e)
    }

    private val V3_ONLY = listOf("assets", "nickname", "creator_notes_multilingual", "source", "group_only_greetings", "creation_date", "modification_date")
}
