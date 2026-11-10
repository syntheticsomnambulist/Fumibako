package dev.aiharness.core.transfer

import dev.aiharness.core.FakeClock
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.storage.inmemory.InMemoryBlobStore
import dev.aiharness.core.storage.inmemory.InMemoryMemoryRepository
import dev.aiharness.core.storage.inmemory.InMemoryPersonaRepository
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Character cards in and out (v0.5): PNG chunks, V1 to V3, `.charx`, lorebooks, and the round trip. */
class CardsTest {
    private val clock = FakeClock()
    private val personas = InMemoryPersonaRepository()
    private val memories = InMemoryMemoryRepository()
    private val blobs = InMemoryBlobStore()
    private val import = CardImport(personas, memories, blobs, clock)

    @Test
    fun textChunksGoInBeforeTheEndAndThePictureStillDecodes() {
        val plain = Png.plain(4, 3, 0x2973B0)
        val withText = Png.withTexts(plain, listOf("chara" to "aGk=", "note" to "kept")) { false }
        assertEquals(mapOf("chara" to "aGk=", "note" to "kept"), Png.texts(withText))
        assertEquals(listOf("IHDR", "IDAT", "tEXt", "tEXt", "IEND"), Png.chunks(withText).map { it.type })

        // Java's own decoder checks the chunks' CRCs and the pixels.
        val image = assertNotNull(ImageIO.read(withText.inputStream()))
        assertEquals(4, image.width)
        assertEquals(0x2973B0, image.getRGB(3, 2) and 0xFFFFFF)

        val replaced = Png.withTexts(withText, listOf("chara" to "Ynll")) { it == "chara" }
        assertEquals(mapOf("note" to "kept", "chara" to "Ynll"), Png.texts(replaced))
    }

    @Test
    fun aV2PictureCardIsReadWithItsLorebook() {
        val read = CardReader.read(pngCard(chara = V2))
        val file = assertIs<CardRead.OfCard>(read).file
        val card = file.card
        assertEquals(CardSpec.V2, card.spec)
        assertEquals(CardFormat.PNG, file.format)
        assertEquals("Mira", card.name)
        assertEquals("Keeps a lighthouse nobody asked her to keep. Writes long letters on the night shift.", card.description)
        assertEquals(listOf("Hello from the light.", "The ferry's late again."), listOf(card.firstMessage) + card.alternateGreetings.take(1))
        assertEquals(listOf("cozy", "seaside"), card.tags)
        assertEquals("tidepool", card.creator)
        val book = assertNotNull(card.book)
        assertEquals(3, book.entries.size)
        assertEquals(2, book.enabled.size, "the switched-off entry is left out")
        assertEquals(listOf("lighthouse", "Gull Point"), book.enabled.first().keys)
        assertTrue(book.enabled.first().constant)
        assertNotNull(file.portrait, "the picture is her portrait")
    }

    @Test
    fun aV3CardWinsOverItsV2CopyAndItsEmbeddedMoodsComeWithIt() {
        val happy = Png.plain(2, 2, 0xE3940B)
        val v3 = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"Mira v3","description":"d","first_mes":"hi",
            "assets":[{"type":"icon","uri":"ccdefault:","name":"main","ext":"png"},{"type":"emotion","uri":"__asset:happy","name":"happy","ext":"png"}],
            "extensions":{}}}"""
        val bytes = pngCard(chara = V2, ccv3 = v3, extra = listOf("chara-ext-asset_:happy" to Base64.getEncoder().encodeToString(happy)))
        val file = assertIs<CardRead.OfCard>(CardReader.read(bytes)).file
        assertEquals(CardSpec.V3, file.card.spec)
        assertEquals("Mira v3", file.card.name)
        assertEquals(listOf("happy"), file.moods.map { it.name })
        assertTrue(file.moods.single().picture.bytes.contentEquals(happy))
    }

    @Test
    fun aCharxCarriesItsPortraitAndMoodsAsFiles() {
        val icon = Png.plain(3, 3, 0x178D9D)
        val sad = Png.plain(1, 1, 0x384DAD)
        val card = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"Juno","description":"A cartographer.",
            "assets":[{"type":"icon","uri":"embeded://assets/icon/main.png","name":"main","ext":"png"},
                      {"type":"emotion","uri":"embeded://assets/emotion/sad.png","name":"sad","ext":"png"}]}}"""
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("card.json")); z.write(card.toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("assets/icon/main.png")); z.write(icon); z.closeEntry()
                z.putNextEntry(ZipEntry("assets/emotion/sad.png")); z.write(sad); z.closeEntry()
            }
        }.toByteArray()
        val file = assertIs<CardRead.OfCard>(CardReader.read(zip)).file
        assertEquals(CardFormat.CHARX, file.format)
        assertEquals("Juno", file.card.name)
        assertTrue(assertNotNull(file.portrait).bytes.contentEquals(icon))
        assertEquals(listOf("sad"), file.moods.map { it.name })
    }

    @Test
    fun aWorldInfoFileIsALorebookAndAPlainPictureIsNotACard() {
        val worldInfo = """{"name":"Gull Island","entries":{
            "0":{"uid":0,"key":["ferry","Tomas"],"keysecondary":[],"comment":"The ferry","content":"Old Tomas runs the ferry on Tuesdays.","constant":false,"disable":false,"order":100},
            "1":{"uid":1,"key":["storm"],"content":"An old storm entry.","disable":true}}}"""
        val book = assertIs<CardRead.OfLorebook>(CardReader.read(worldInfo.toByteArray())).book
        assertEquals("Gull Island", book.name)
        assertEquals(listOf("Old Tomas runs the ferry on Tuesdays."), book.enabled.map { it.content })
        assertEquals(listOf("ferry", "Tomas"), book.enabled.single().keys)
        assertEquals("The ferry", book.enabled.single().name)

        assertIs<CardRead.Not>(CardReader.read(Png.plain(2, 2, 0)))
        assertIs<CardRead.Not>(CardReader.read("""{"hello":"there"}""".toByteArray()))
    }

    @Test
    fun aCardComesInWithWhatsTicked() = runTest {
        val file = assertIs<CardRead.OfCard>(CardReader.read(pngCard(chara = V2))).file
        val preview = CardPreview(file)
        assertEquals(3, preview.greetings)
        assertEquals(1, preview.loreSwitchedOff)
        assertEquals(1, preview.loreAlwaysInMind)
        assertTrue(preview.tokens(CardChoices(systemPrompt = true)) > preview.tokens(CardChoices()), "its system prompt adds to every letter")

        val mira = import.bring(file, CardChoices(exampleDialogue = false, ink = "ruri"))
        assertEquals("Mira", mira.name)
        assertEquals("Hello from the light.", mira.greeting)
        assertEquals(2, mira.alternateGreetings.size)
        assertEquals("", mira.exampleDialogue, "example dialogue was unticked")
        assertNull(mira.systemPromptOverride, "the card's own prompts start off")
        assertNull(mira.postHistoryInstructions)
        assertNull(mira.pronouns, "other cards don't carry pronouns")
        assertEquals("ruri", mira.ink)
        assertEquals("tidepool", mira.fromCard?.creator)
        assertEquals("Mira starts out not knowing you.", mira.fromCard?.creatorNotes)
        assertNotNull(blobs.get(assertNotNull(mira.fromCard?.cardRef)), "the card is kept as it came")
        assertNotNull(mira.avatarRef)

        val lore = memories.listForPersona(mira.id).sortedBy { it.createdAt }
        assertEquals(2, lore.size)
        assertTrue(lore.all { it.kind == MemoryKind.LORE && it.wontFade })
        assertTrue(lore[0].pinned, "a constant entry is always in mind")
        assertEquals(listOf("lighthouse", "Gull Point"), lore[0].recallWords)
        assertEquals("0", lore[0].loreEntryId)
        assertFalse(lore[1].pinned)
    }

    @Test
    fun exportingGivesTheCardBackWithHerChangesAndWhatFumibakoDoesntUse() = runTest {
        val file = assertIs<CardRead.OfCard>(CardReader.read(pngCard(chara = V2))).file
        val mira = import.bring(file).copy(personality = "Wry, patient.", pronouns = Pronouns(PronounKind.SHE), tapePattern = TapePattern.GINGHAM, ink = "ruri")
        val lore = memories.listForPersona(mira.id).sortedBy { it.createdAt }
        memories.upsert(lore[1].copy(content = "Old Tomas runs the ferry on Tuesdays and Fridays.", recallWords = listOf("Tomas", "ferry")))
        val original = HarnessJson.parseToJsonElement(blobs.get(mira.fromCard!!.cardRef!!)!!.decodeToString()).jsonObject

        val v3 = CardExport.v3(mira, memories.listForPersona(mira.id), original, CardExport.Choices(), now = clock.millis)
        val png = CardExport.png(null, v3, 0x2973B0)
        val back = assertIs<CardRead.OfCard>(CardReader.read(png)).file.card
        assertEquals(CardSpec.V3, back.spec)
        assertEquals("Wry, patient.", back.personality)
        assertEquals("Hello from the light.", back.firstMessage)
        assertEquals("tidepool", back.creator)
        assertEquals("value", back.extensions["depth_prompt"]?.jsonPrimitive?.content, "other apps' extensions go back as they came")
        val ext = assertNotNull(FumibakoCardExtension.of(back))
        assertEquals("ruri", ext.ink)
        assertEquals(TapePattern.GINGHAM, ext.tapePattern)
        assertEquals(Pronouns(PronounKind.SHE), ext.pronouns)

        val book = assertNotNull(back.book)
        assertEquals(64, book.raw["scan_depth"]?.jsonPrimitive?.content?.toInt(), "the lorebook's settings go back")
        assertEquals(3, book.entries.size, "her two lore memories, and the entry the card had switched off")
        val ferry = book.entries.first { "ferry" in it.keys }
        assertEquals("Old Tomas runs the ferry on Tuesdays and Fridays.", ferry.content)
        assertEquals("before_char", ferry.raw["position"]?.jsonPrimitive?.content, "an entry's own settings go back")
        assertFalse(book.entries.single { it.content.startsWith("An old") }.enabled)

        // The V2 copy, for older apps.
        val v2 = CardReader.parseCard(String(Base64.getDecoder().decode(Png.texts(png).getValue("chara"))))
        assertEquals(CardSpec.V2, assertNotNull(v2).spec)
        assertEquals("Wry, patient.", v2.personality)
        assertFalse(v2.data.containsKey("assets"))
        assertTrue(v2.book!!.entries.none { "use_regex" in it.raw })

        // And back in, she comes whole.
        val again = import.bring(assertIs<CardRead.OfCard>(CardReader.read(png)).file)
        assertEquals(Pronouns(PronounKind.SHE), again.pronouns)
        assertEquals(TapePattern.GINGHAM, again.tapePattern)
        assertEquals("ruri", again.ink)
    }

    @Test
    fun aPersonaMadeHereBecomesACardWithHerMemoriesWhenAsked() = runTest {
        val file = assertIs<CardRead.OfCard>(CardReader.read(pngCard(chara = """{"spec":"chara_card_v2","data":{"name":"Kit","description":"A baker."}}"""))).file
        val kit = import.bring(file)
        memories.upsert(dev.aiharness.core.model.MemoryEntry(id = "m1", personaId = kit.id, content = "Sam takes their tea with honey", tags = listOf("tea"), createdAt = 1, updatedAt = 1, lastAccessedAt = 1))
        val without = CardExport.v3(kit, memories.listForPersona(kit.id), null, CardExport.Choices(), now = 0)
        assertNull(without["data"]!!.jsonObject["character_book"], "no lore, no lorebook")
        val with = CardExport.v3(kit, memories.listForPersona(kit.id), null, CardExport.Choices(otherMemories = true), now = 0)
        val entries = with["data"]!!.jsonObject["character_book"]!!.jsonObject["entries"]!!.jsonArray
        assertEquals("Sam takes their tea with honey", entries.single().jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("tea"))), entries.single().jsonObject["keys"])
    }

    private fun pngCard(chara: String? = null, ccv3: String? = null, extra: List<Pair<String, String>> = emptyList()): ByteArray {
        val chunks = listOfNotNull(
            chara?.let { "chara" to Base64.getEncoder().encodeToString(it.toByteArray()) },
            ccv3?.let { "ccv3" to Base64.getEncoder().encodeToString(it.toByteArray()) },
        ) + extra
        return Png.withTexts(Png.plain(8, 12, 0xA26480), chunks) { false }
    }

    private companion object {
        val V2 = """{"spec":"chara_card_v2","spec_version":"2.0","data":{
            "name":"Mira",
            "description":"Keeps a lighthouse nobody asked her to keep. Writes long letters on the night shift.",
            "personality":"Wry.", "scenario":"Gull Point, the off season.",
            "first_mes":"Hello from the light.",
            "alternate_greetings":["The ferry's late again.","Storm tonight."],
            "mes_example":"<START>\n{{char}}: The lamp's lit.",
            "creator_notes":"Mira starts out not knowing you.",
            "system_prompt":"You are {{char}} in a never-ending roleplay with {{user}}.",
            "post_history_instructions":"Stay in character.",
            "tags":["cozy","seaside"], "creator":"tidepool", "character_version":"1.2",
            "extensions":{"depth_prompt":"value"},
            "character_book":{"name":"Gull Point","scan_depth":64,"extensions":{},"entries":[
                {"id":0,"keys":["lighthouse","Gull Point"],"content":"Gull Point light was switched off in 1998.","enabled":true,"constant":true,"insertion_order":0,"extensions":{}},
                {"id":1,"keys":["Tomas","ferry"],"content":"Old Tomas runs the ferry on Tuesdays.","enabled":true,"insertion_order":1,"position":"before_char","extensions":{}},
                {"id":2,"keys":["storm"],"content":"An old storm entry.","enabled":false,"insertion_order":2,"extensions":{}}
            ]}
        }}"""
    }
}
