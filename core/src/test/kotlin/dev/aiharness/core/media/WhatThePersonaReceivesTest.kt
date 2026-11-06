package dev.aiharness.core.media

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.TestEnv
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v0.3.5 step 3: pictures and files go whole where the model takes them, else as stand-ins or descriptions. */
class WhatThePersonaReceivesTest {
    private suspend fun env(type: ProviderType, modelId: String = "test-model", vision: Boolean = false): TestEnv {
        val provider = ScriptedProvider(type)
        provider.fallback = { request -> if (request.system == MediaDescriber.SYSTEM) LlmTurnResult(text = "A tabby cat asleep on a sill.") else LlmTurnResult(text = "Lovely.") }
        return TestEnv(provider).apply {
            seed()
            profiles.upsert(profile.copy(modelId = modelId, supportsVision = vision, contextBudgetTokens = 48_000))
        }
    }

    private fun LlmRequest.lastNote(): List<LlmPart> = (messages.last { it is LlmMessage.User } as LlmMessage.User).parts
    private fun List<LlmPart>.texts() = filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }
    private fun TestEnv.personaRequests() = provider.requests.filter { it.system != MediaDescriber.SYSTEM }

    private suspend fun TestEnv.photo() = ContentPart.Image(harness.blobs.put(ByteArray(2_000) { 7 }, "jpg"), "image/jpeg")
    private suspend fun TestEnv.pdf(pages: Int = 3) = ContentPart.Attachment(
        "pdf1", AttachmentKind.FILE, ref = harness.blobs.put(ByteArray(3_000) { 1 }, "pdf"), mimeType = "application/pdf",
        name = "market-map.pdf", sizeBytes = 3_000, pages = pages, pictureRef = harness.blobs.put(ByteArray(500) { 2 }, "jpg"),
    )
    private suspend fun TestEnv.song() = ContentPart.Attachment(
        "song1", AttachmentKind.SONG, ref = harness.blobs.put(ByteArray(4_000) { 3 }, "mp3"), mimeType = "audio/mpeg",
        title = "October Walk", artist = "Hana Sato", lengthMs = 222_000, sizeBytes = 4_000,
    )
    private suspend fun TestEnv.video() = ContentPart.Attachment(
        "vid1", AttachmentKind.VIDEO, ref = harness.blobs.put(ByteArray(5_000) { 4 }, "mp4"), mimeType = "video/mp4", name = "beach.mp4",
        title = "beach", lengthMs = 20_000, sizeBytes = 5_000,
        frameRefs = List(3) { harness.blobs.put(ByteArray(300) { 5 }, "jpg") },
    )

    @Test
    fun claudeSeesPhotosAndReadsPdfsButHearsNoSongs() = runTest {
        val env = env(ProviderType.ANTHROPIC)
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "For you.", images = listOf(env.photo()), attachments = listOf(env.pdf(), env.song(), env.video())).toList()

        val note = env.personaRequests().last().lastNote()
        assertEquals(1 + 3, note.filterIsInstance<LlmPart.Image>().size, "the photo, and the video's three frames")
        assertEquals(listOf("application/pdf"), note.filterIsInstance<LlmPart.Media>().map { it.mimeType })
        assertContains(note.texts(), "[ticket: October Walk by Hana Sato, 3:42]")
        assertContains(note.texts(), "(three frames from it:)")
    }

    @Test
    fun geminiHearsTheSongAndWatchesTheVideo() = runTest {
        val env = env(ProviderType.GEMINI, modelId = "gemini-3-flash")
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "", attachments = listOf(env.song(), env.video())).toList()

        val media = env.personaRequests().last().lastNote().filterIsInstance<LlmPart.Media>()
        assertEquals(listOf("audio/mp3", "video/mp4"), media.map { it.mimeType })
    }

    @Test
    fun aTextOnlyModelReadsStandInsAndTextFiles() = runTest {
        val env = env(ProviderType.OPENAI_COMPATIBLE)
        val conversation = env.harness.createConversation(env.persona.id)
        val notes = ContentPart.Attachment(
            "t1", AttachmentKind.FILE, ref = env.harness.blobs.put("Buy figs.\nCall Hana.".toByteArray(), "txt"),
            mimeType = "text/plain", name = "list.txt", sizeBytes = 20,
        )
        env.harness.agent.send(conversation.id, "", images = listOf(env.photo()), attachments = listOf(env.pdf(), notes)).toList()

        val note = env.personaRequests().last().lastNote()
        assertTrue(note.none { it is LlmPart.Image || it is LlmPart.Media })
        assertContains(note.texts(), "[photo]")
        assertContains(note.texts(), "[tag: market-map.pdf, PDF, 3 pages, 3 KB]")
        assertContains(note.texts(), "<file name=\"list.txt\">\nBuy figs.\nCall Hana.\n</file>")
    }

    @Test
    fun aLongTextFileIsCutToFit() {
        val a = ContentPart.Attachment("t", AttachmentKind.FILE, mimeType = "text/plain", name = "long.txt")
        val sent = MediaParts.textFile(a, "x".repeat(1_000).toByteArray(), maxChars = 250)
        assertContains(sent, "x".repeat(250) + "\n[The rest is cut off: 25% shown.]")
        assertFalse("x".repeat(251) in sent)
    }

    @Test
    fun laterEpochsCarryTheDescriptionInsteadOfThePicture() = runTest {
        val env = env(ProviderType.ANTHROPIC)
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "Look!", images = listOf(env.photo())).toList()
        env.clock.advanceMinutes(1)
        env.harness.describer.catchUp(conversation.id)

        val photo = env.conversations.getMessages(conversation.id).first { it.role == MessageRole.USER }.images.single()
        assertEquals("A tabby cat asleep on a sill.", photo.description)

        // The same epoch keeps sending the picture, so its history doesn't change.
        env.clock.advanceMinutes(1)
        env.harness.agent.send(conversation.id, "Did you see?").toList()
        val sameEpoch = env.personaRequests().last().messages.first { it is LlmMessage.User && it.parts.any { p -> p is LlmPart.Text && p.text == "Look!" } } as LlmMessage.User
        assertEquals(1, sameEpoch.parts.filterIsInstance<LlmPart.Image>().size)

        // A new epoch (the persona changed) reads the description.
        env.personas.upsert(env.persona.copy(description = "A curious botanist who keeps cats."))
        env.clock.advanceMinutes(1)
        env.harness.agent.send(conversation.id, "And now?").toList()
        val laterEpoch = env.personaRequests().last().messages.first { it is LlmMessage.User && it.parts.any { p -> p is LlmPart.Text && p.text == "Look!" } } as LlmMessage.User
        assertTrue(laterEpoch.parts.none { it is LlmPart.Image })
        assertContains(laterEpoch.parts.texts(), "[photo] A tabby cat asleep on a sill.")
    }

    @Test
    fun theUtilityModelDescribesWhatThePersonasModelCantSeeBeforeItReads() = runTest {
        val env = env(ProviderType.OPENAI_COMPATIBLE)
        val eyes = ModelProfile(id = "u", name = "Utility", provider = ProviderType.OPENAI_COMPATIBLE, modelId = "eyes", supportsVision = true)
        env.profiles.upsert(eyes)
        env.settingsRepo.update { it.copy(utilityModelProfileId = eyes.id) }
        val conversation = env.harness.createConversation(env.persona.id)
        env.harness.agent.send(conversation.id, "Our cat.", images = listOf(env.photo())).toList()

        val described = env.provider.requests.single { it.system == MediaDescriber.SYSTEM }
        assertEquals("eyes", described.profile.modelId)
        assertContains(env.personaRequests().last().lastNote().texts(), "[photo] A tabby cat asleep on a sill.")
    }

    @Test
    fun anythingTooBigForTheRequestGoesAsItsStandIn() = runTest {
        val env = env(ProviderType.GEMINI, modelId = "gemini-3-flash")
        val conversation = env.harness.createConversation(env.persona.id)
        val huge = env.video().copy(sizeBytes = 400L * 1024 * 1024, frameRefs = emptyList())
        env.harness.agent.send(conversation.id, "", attachments = listOf(huge)).toList()
        val note = env.personaRequests().last().lastNote()
        assertTrue(note.none { it is LlmPart.Media })
        assertContains(note.texts(), "[film: beach, 0:20]")
    }

    @Test
    fun aBigVideosSmallerCopyGoesWholeToGemini() = runTest {
        val env = env(ProviderType.GEMINI, modelId = "gemini-3-flash")
        val conversation = env.harness.createConversation(env.persona.id)
        val copy = env.harness.blobs.put(ByteArray(6_000) { 9 }, "mp4")
        // A two-and-a-half-minute phone video, far too big itself, with its 360p copy.
        val walk = env.video().copy(sizeBytes = 400L * 1024 * 1024, lengthMs = 150_000, modelRef = copy, modelMimeType = "video/mp4", modelSizeBytes = 6_000)
        env.harness.agent.send(conversation.id, "", attachments = listOf(walk)).toList()
        val media = env.personaRequests().last().lastNote().filterIsInstance<LlmPart.Media>().single()
        assertEquals("video/mp4", media.mimeType)
        assertEquals(6_000, media.bytes.size, "the copy, not the original")

        val older = env.profile.copy(provider = ProviderType.GEMINI, modelId = "gemini-2.5-flash", contextBudgetTokens = 48_000)
        assertFalse(MediaSupport.takesWhole(walk, older), "older Gemini models watch at higher cost, so it's too long for them")
    }

    @Test
    fun theLineUnderAFrameSaysWhatThePersonaCantTakeIn() = runTest {
        val env = env(ProviderType.ANTHROPIC)
        val claude = env.profile.copy(provider = ProviderType.ANTHROPIC, contextBudgetTokens = 48_000)
        val gemini = claude.copy(provider = ProviderType.GEMINI, modelId = "gemini-3-flash")
        val textOnly = claude.copy(provider = ProviderType.OPENAI_COMPATIBLE, supportsVision = false)
        val mix = ContentPart.Attachment("m", AttachmentKind.PLAYLIST, title = "rainy day mix")

        assertEquals("Aria can read the label, but can't hear the songs.", MediaSupport.cantTakeIn(mix, gemini, "Aria"))
        assertEquals("Aria knows its title and artist, but can't hear it.", MediaSupport.cantTakeIn(env.song(), claude, "Aria"))
        assertNull(MediaSupport.cantTakeIn(env.song(), gemini, "Aria"))
        // A song too long for a third of the budget goes by its ticket.
        assertEquals("Aria knows its title and artist, but can't hear it.", MediaSupport.cantTakeIn(env.song(), gemini.copy(contextBudgetTokens = 12_000), "Aria"))
        assertEquals("Aria sees three frames from the video.", MediaSupport.cantTakeIn(env.video(), claude, "Aria"))
        assertNull(MediaSupport.cantTakeIn(env.pdf(), claude, "Aria"))
        assertEquals("Aria can read the tag, but can't open the file.", MediaSupport.cantTakeIn(env.pdf(), textOnly, "Aria"))
        assertEquals("Aria sees the first page.", MediaSupport.cantTakeIn(env.pdf(), textOnly.copy(supportsVision = true), "Aria"))
        assertEquals("Aria can't see this photo.", MediaSupport.cantSee(textOnly, "Aria", several = false))
        assertNull(MediaSupport.cantSee(gemini.copy(supportsVision = false), "Aria", several = true))
    }
}
