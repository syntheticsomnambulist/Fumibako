package dev.aiharness.core.transfer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Base64
import java.util.zip.ZipInputStream

/** Which version of the character card spec a card follows (V1 has no `spec` at all). */
enum class CardSpec { V1, V2, V3 }

/** The file a card came in. */
enum class CardFormat { PNG, JSON, CHARX }

/**
 * One lorebook entry, read leniently from either shape it comes in: a card's `character_book`
 * (`keys`, `enabled`, `insertion_order`) or SillyTavern's World Info (`key`, `disable`, `order`).
 * [raw] is the entry as it came, kept so exporting gives back what Fumibako doesn't use.
 */
data class LoreEntry(
    /** Its `id` or `uid`, as text: how an exported entry finds the one it came from. */
    val id: String?,
    /** Its `comment` or `name`: a title for people, never sent to the persona. */
    val name: String?,
    val keys: List<String>,
    val secondaryKeys: List<String>,
    val content: String,
    val enabled: Boolean,
    /** Always in mind, whatever the letter says. */
    val constant: Boolean,
    val order: Int,
    val raw: JsonObject,
)

/** A lorebook: a card's `character_book`, or a World Info file on its own. */
data class Lorebook(
    val name: String?,
    val entries: List<LoreEntry>,
    /** The book as it came (its settings: scan depth, token budget, extensions). */
    val raw: JsonObject,
) {
    val enabled: List<LoreEntry> get() = entries.filter { it.enabled && it.content.isNotBlank() }
}

/** A V3 card's asset, as listed: `type` (icon, emotion, background…), where it is (`uri`), its name and extension. */
data class CardAssetInfo(val type: String, val uri: String, val name: String, val ext: String)

/** A picture from a card: its bytes and extension (png, webp…). */
class CardPicture(val bytes: ByteArray, val ext: String)

/** An asset found and read: a mood portrait (`emotion`), a background, another icon. */
class CardAsset(val type: String, val name: String, val picture: CardPicture)

/**
 * A character card's fields, the same whichever spec it follows. [raw] is the whole card as it
 * came (the V3 one when a PNG has both), kept beside the persona made from it.
 */
data class Card(
    val spec: CardSpec,
    val raw: JsonObject,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val exampleDialogue: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val version: String = "",
    val book: Lorebook? = null,
    val assets: List<CardAssetInfo> = emptyList(),
) {
    /** The card's `data` (V2 and V3), or the card itself (V1). */
    val data: JsonObject get() = (raw["data"] as? JsonObject).takeIf { spec != CardSpec.V1 } ?: raw

    /** `extensions`, where other apps keep their own fields. */
    val extensions: JsonObject get() = data["extensions"] as? JsonObject ?: JsonObject(emptyMap())

    /** What a card Fumibako made carries of its own (ink, tape, pronouns, picture prompts). */
    val fumibako: JsonObject? get() = extensions["fumibako"] as? JsonObject
}

/** A card read from its file, with its portrait and the assets it carries inside. */
class CardFile(val card: Card, val format: CardFormat, val portrait: CardPicture?, val assets: List<CardAsset>) {
    /** The V3 `emotion` assets: her moods' portraits. */
    val moods: List<CardAsset> get() = assets.filter { it.type == "emotion" }
}

/** What a file turned out to hold, read as a card. */
sealed interface CardRead {
    class OfCard(val file: CardFile) : CardRead
    class OfLorebook(val book: Lorebook) : CardRead

    /** Not a card: [why] says what it is instead, for the page. */
    class Not(val why: String) : CardRead
}

/** Reads character cards (V1, V2, V3) from PNG, JSON and `.charx`, and lorebooks on their own. */
object CardReader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun read(bytes: ByteArray): CardRead = when {
        Png.isPng(bytes) -> fromPng(bytes)
        isZip(bytes) -> fromCharx(bytes)
        else -> fromJsonText(bytes.toString(Charsets.UTF_8))
    }

    fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    private fun fromPng(bytes: ByteArray): CardRead {
        val texts = Png.texts(bytes)
        val v3 = texts["ccv3"]?.let(::decodeCardText)
        val v2 = texts["chara"]?.let(::decodeCardText)
        val card = v3 ?: v2 ?: return CardRead.Not("This picture has no character card inside.")
        val assets = card.assets.mapNotNull { info ->
            val picture = when {
                info.uri.startsWith("ccdefault:") -> if (info.type == "icon") CardPicture(bytes, "png") else null
                info.uri.startsWith("__asset:") -> {
                    val path = info.uri.removePrefix("__asset:")
                    (texts["chara-ext-asset_:$path"] ?: texts["chara-ext-asset_$path"])?.let { base64(it) }?.let { CardPicture(it, extOf(info)) }
                }
                else -> dataUri(info)
            } ?: return@mapNotNull null
            CardAsset(info.type, info.name, picture)
        }
        return CardRead.OfCard(CardFile(card, CardFormat.PNG, CardPicture(bytes, "png"), assets.filterNot { it.type == "icon" }))
    }

    private fun fromCharx(bytes: ByteArray): CardRead {
        val entries = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) entries[entry.name.trimStart('/')] = zip.readBytes()
                }
            }
        }.onFailure { return CardRead.Not("This file couldn't be opened.") }
        val cardJson = entries["card.json"] ?: return CardRead.Not("This file has no character card inside.")
        val card = parseCard(cardJson.toString(Charsets.UTF_8)) ?: return CardRead.Not("This file's card couldn't be read.")
        val assets = card.assets.mapNotNull { info ->
            val picture = when {
                info.uri.startsWith("embeded://") || info.uri.startsWith("embedded://") ->
                    entries[info.uri.substringAfter("://").trimStart('/')]?.let { CardPicture(it, extOf(info)) }
                else -> dataUri(info)
            } ?: return@mapNotNull null
            CardAsset(info.type, info.name, picture)
        }
        val icon = assets.filter { it.type == "icon" }.let { icons -> icons.firstOrNull { it.name == "main" } ?: icons.firstOrNull() }
        return CardRead.OfCard(CardFile(card, CardFormat.CHARX, icon?.picture, assets.filterNot { it.type == "icon" }))
    }

    private fun fromJsonText(text: String): CardRead {
        val obj = runCatching { json.parseToJsonElement(text.trimStart('﻿')) as? JsonObject }.getOrNull()
            ?: return CardRead.Not("This file isn't a character card or a lorebook.")
        parseCard(obj)?.let { card ->
            val assets = card.assets.mapNotNull { info -> dataUri(info)?.let { CardAsset(info.type, info.name, it) } }
            val icon = assets.firstOrNull { it.type == "icon" }
            return CardRead.OfCard(CardFile(card, CardFormat.JSON, icon?.picture, assets.filterNot { it.type == "icon" }))
        }
        return parseLorebook(obj)?.let { CardRead.OfLorebook(it) } ?: CardRead.Not("This file isn't a character card or a lorebook.")
    }

    private fun decodeCardText(text: String): Card? {
        val decoded = base64(text)?.toString(Charsets.UTF_8) ?: text
        return parseCard(decoded)
    }

    fun parseCard(text: String): Card? = runCatching { json.parseToJsonElement(text.trimStart('﻿')) as? JsonObject }.getOrNull()?.let(::parseCard)

    fun parseCard(raw: JsonObject): Card? {
        val spec = when (raw.str("spec")) {
            "chara_card_v3" -> CardSpec.V3
            "chara_card_v2" -> CardSpec.V2
            else -> CardSpec.V1
        }
        val d = if (spec == CardSpec.V1) raw else raw["data"] as? JsonObject ?: return null
        val name = d.str("name") ?: d.str("char_name") ?: return null
        if (spec == CardSpec.V1 && listOf("description", "first_mes", "personality", "char_persona", "char_greeting").none { d.containsKey(it) }) return null
        return Card(
            spec = spec,
            raw = raw,
            name = name.trim(),
            description = d.str("description") ?: d.str("char_persona").orEmpty(),
            personality = d.str("personality").orEmpty(),
            scenario = d.str("scenario") ?: d.str("world_scenario").orEmpty(),
            firstMessage = d.str("first_mes") ?: d.str("char_greeting").orEmpty(),
            alternateGreetings = d.strings("alternate_greetings").filter { it.isNotBlank() },
            exampleDialogue = d.str("mes_example") ?: d.str("example_dialogue").orEmpty(),
            creatorNotes = d.str("creator_notes") ?: d.str("creatorcomment").orEmpty(),
            systemPrompt = d.str("system_prompt").orEmpty(),
            postHistoryInstructions = d.str("post_history_instructions").orEmpty(),
            tags = d.strings("tags").map { it.trim() }.filter { it.isNotEmpty() },
            creator = d.str("creator").orEmpty(),
            version = d.str("character_version").orEmpty(),
            book = (d["character_book"] as? JsonObject)?.let(::parseLorebook),
            assets = (d["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
                val o = a as? JsonObject ?: return@mapNotNull null
                CardAssetInfo(o.str("type").orEmpty(), o.str("uri").orEmpty(), o.str("name").orEmpty(), o.str("ext").orEmpty())
            },
        )
    }

    /** A lorebook in a card's shape (entries as a list) or World Info's (entries by number). */
    fun parseLorebook(raw: JsonObject): Lorebook? {
        val entries: List<JsonObject> = when (val e = raw["entries"]) {
            is JsonArray -> e.mapNotNull { it as? JsonObject }
            is JsonObject -> e.values.mapNotNull { it as? JsonObject }
            else -> return null
        }
        return Lorebook(
            name = raw.str("name")?.takeIf { it.isNotBlank() },
            entries = entries.mapIndexed { i, o ->
                LoreEntry(
                    id = (o["id"] ?: o["uid"])?.let { (it as? JsonPrimitive)?.contentOrNull },
                    name = (o.str("comment") ?: o.str("name"))?.takeIf { it.isNotBlank() },
                    keys = (o.strings("keys").ifEmpty { o.strings("key") }).map { it.trim() }.filter { it.isNotEmpty() },
                    secondaryKeys = (o.strings("secondary_keys").ifEmpty { o.strings("keysecondary") }).map { it.trim() }.filter { it.isNotEmpty() },
                    content = o.str("content").orEmpty().trim(),
                    enabled = o.bool("enabled") ?: o.bool("disable")?.not() ?: true,
                    constant = o.bool("constant") ?: false,
                    order = (o["insertion_order"] ?: o["order"])?.let { (it as? JsonPrimitive)?.intOrNull } ?: i,
                    raw = o,
                )
            },
            raw = raw,
        )
    }

    private fun dataUri(info: CardAssetInfo): CardPicture? {
        if (!info.uri.startsWith("data:")) return null
        val comma = info.uri.indexOf(',')
        if (comma < 0 || !info.uri.substring(0, comma).endsWith(";base64")) return null
        val mime = info.uri.substring(5, comma).substringBefore(';')
        val bytes = base64(info.uri.substring(comma + 1)) ?: return null
        return CardPicture(bytes, info.ext.ifBlank { mime.substringAfter('/', "png") })
    }

    private fun extOf(info: CardAssetInfo): String = info.ext.ifBlank { "png" }.lowercase()

    private fun base64(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text.filterNot { it.isWhitespace() }) }.getOrNull()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.strings(key: String): List<String> = when (val v: JsonElement? = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        is JsonPrimitive -> if (v.isString) v.content.split(',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        else -> emptyList()
    }
}
