package dev.aiharness.core.agent

import dev.aiharness.core.TestEnv
import dev.aiharness.core.image.ImageProfiles
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.PersonaStickers
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.stickers.StickResult
import dev.aiharness.core.stickers.StickerSpot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StickersTest {
    private fun toolTurn(id: String, name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall(id, name, args)), stopReason = StopReason.TOOL_USE)

    private fun lastContext(env: TestEnv): String =
        (env.provider.requests.last().messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString("\n") { it.text }

    private val wave = StickerArt("a1", "mira-wave", ownerPersonaId = "p1", imageRef = "mem:wave.png", feeling = "hello", createdAt = 1)
    private val mochi = StickerArt("a2", "mochi", imageRef = "mem:mochi.png", createdAt = 2)

    @Test
    fun namesResolveToTheirOwnThenSharedThenEmoji() = runTest {
        val env = TestEnv().apply { seed() }
        env.stickers.upsertArt(wave)
        env.stickers.upsertArt(mochi)
        env.stickers.upsertArt(StickerArt("a3", "mira-wave", imageRef = "mem:shared-wave.png", createdAt = 3))
        val service = env.harness.stickerService

        assertEquals(StickerRef.Art("a1"), service.resolve("p1", ":mira-wave:"), "its own comes before a shared one")
        assertEquals(StickerRef.Art("a2"), service.resolve("p1", "mochi"))
        assertEquals(StickerRef.Emoji("🎉", "tada"), service.resolve("p1", ":tada:"))
        assertEquals(StickerRef.Emoji("❤️", "heart"), service.resolve("p1", "❤"), "with or without the variation selector")
        assertEquals(StickerRef.Emoji("🫎", "emoji"), service.resolve("p1", "🫎"), "an emoji with no name still sticks")
        assertNull(service.resolve("p1", ":nothing-called-this:"))
        assertEquals(":tada:", service.nameOf(StickerRef.Emoji("🎉", "tada")))
        assertEquals(":mochi:", service.nameOf(StickerRef.Art("a2")))
    }

    @Test
    fun aStickerStuckAndPeeledBeforeYourNextNoteIsNotNews() = runTest {
        val env = TestEnv().apply { seed() }
        env.stickers.upsertArt(mochi)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Morning!"))
        env.harness.agent.send(conversation.id, "Morning").toList()
        val herLetter = env.conversations.getMessages(conversation.id).last { it.role == MessageRole.ASSISTANT }
        val service = env.harness.stickerService

        // You stick one on her letter and one on your draft, then think better of both.
        env.clock.advanceMinutes(5)
        val onLetter = assertIs<StickResult.Stuck>(service.stick(conversation.id, StickerTarget.message(herLetter.id), StickerRef.Art(mochi.id), null, 0.8f, 0.6f))
        val onDraft = assertIs<StickResult.Stuck>(service.stick(conversation.id, StickerTarget.draft(conversation.id), StickerRef.Art(mochi.id), null, 0.9f, 0f))
        env.clock.advanceMinutes(1)
        service.peel(onLetter.sticker, null)
        service.peel(onDraft.sticker, null)
        env.clock.advanceMinutes(1)
        env.provider.then(LlmTurnResult(text = "Mm?"))
        env.harness.agent.send(conversation.id, "Never mind.").toList()

        assertFalse(":mochi:" in lastContext(env), "neither placing reaches the persona")
        val sent = env.conversations.getMessages(conversation.id).last { it.role == MessageRole.USER }
        assertTrue(service.on(conversation.id, StickerTarget.message(sent.id)).isEmpty(), "the peeled one didn't go with the note")
    }

    @Test
    fun thePersonaHearsWhatYouStuckAndPeeledOnce() = runTest {
        val env = TestEnv().apply { seed() }
        env.stickers.upsertArt(wave)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "place_sticker", """{"sticker":":mira-wave:","on":"their note","spot":"top left"}"""))
        env.provider.then(LlmTurnResult(text = "Morning!"))
        env.harness.agent.send(conversation.id, "Morning").toList()
        val messages = env.conversations.getMessages(conversation.id)
        val myNote = messages.first { it.role == MessageRole.USER }
        val herLetter = messages.last { it.role == MessageRole.ASSISTANT }
        val service = env.harness.stickerService

        // Mira's sticker landed on the note, near the top left, a little crooked.
        val hers = service.on(conversation.id, StickerTarget.message(myNote.id)).single()
        assertEquals("p1", hers.byPersonaId)
        assertTrue(hers.x in 0.04f..0.12f && hers.y in 0.08f..0.16f, "at ${hers.x}, ${hers.y}")
        assertTrue(hers.rotationDeg in -14f..14f)

        // You stick one on her letter and one on your draft, and peel hers off your note.
        env.clock.advanceMinutes(5)
        service.stick(conversation.id, StickerTarget.message(herLetter.id), StickerRef.Emoji("🎉", "tada"), null, 0.8f, 0.6f)
        service.stick(conversation.id, StickerTarget.draft(conversation.id), StickerRef.Emoji("🍵", "tea"), null, 0.9f, 0f)
        service.peel(hers, null)
        env.clock.advanceMinutes(1)
        env.provider.then(LlmTurnResult(text = "Ha!"))
        env.harness.agent.send(conversation.id, "Tea?").toList()

        val context = lastContext(env)
        assertContains(context, "[Sam stuck :tada: on your Thu 10:00 letter]")
        assertContains(context, "[Sam stuck :tea: on this note]")
        assertContains(context, "[Sam peeled your :mira-wave: off Sam's Thu 10:00 note]")
        val sent = env.conversations.getMessages(conversation.id).last { it.role == MessageRole.USER }
        assertEquals(listOf("tea"), service.on(conversation.id, StickerTarget.message(sent.id)).map { (it.ref as StickerRef.Emoji).name })
        assertTrue(service.on(conversation.id, StickerTarget.draft(conversation.id)).isEmpty(), "the draft's stickers went with the note")

        // Told once: the next turn has nothing new.
        env.clock.advanceMinutes(1)
        env.provider.then(LlmTurnResult(text = "Yes."))
        env.harness.agent.send(conversation.id, "Still there?").toList()
        assertFalse("stuck" in lastContext(env) || "peeled" in lastContext(env))
    }

    @Test
    fun theSystemPromptNamesItsStickersAndNewOnesArriveInContext() = runTest {
        val env = TestEnv().apply { seed() }
        env.stickers.upsertArt(wave)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hello").toList()
        val system = env.provider.requests.last().system
        assertContains(system, "## Stickers")
        assertContains(system, "Your stickers: :mira-wave: (hello)")

        env.clock.advanceMinutes(10)
        env.stickers.upsertArt(mochi.copy(createdAt = env.clock.millis))
        env.clock.advanceMinutes(1)
        env.provider.then(LlmTurnResult(text = "Cute"))
        env.harness.agent.send(conversation.id, "Look").toList()
        assertContains(lastContext(env), "New stickers you can use: :mochi:.")
    }

    @Test
    fun aLetterHoldsSixAndPeelingFollowsTheSettings() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hello").toList()
        val note = env.conversations.getMessages(conversation.id).first { it.role == MessageRole.USER }
        val service = env.harness.stickerService
        val target = StickerTarget.message(note.id)
        env.clock.advanceMinutes(5)
        repeat(6) { assertIs<StickResult.Stuck>(service.stickAt(conversation.id, target, StickerRef.Emoji("⭐", "star"), null, StickerSpot.MIDDLE)) }
        assertIs<StickResult.Refused>(service.stickAt(conversation.id, target, StickerRef.Emoji("⭐", "star"), null, StickerSpot.MIDDLE))

        // Mira can't peel yours by default, then can once you allow it.
        env.provider.then(toolTurn("c1", "peel_sticker", """{"sticker":":star:","on":"Thu 10:00 note"}"""))
        env.provider.then(LlmTurnResult(text = "Oops"))
        env.harness.agent.send(conversation.id, "Stars!").toList()
        assertEquals(6, service.on(conversation.id, target).size)
        assertContains(env.conversations.getMessages(conversation.id).flatMap { it.toolResults }.last().content, "isn't yours")

        env.personas.upsert(env.persona.copy(stickers = PersonaStickers(canPeelYours = true)))
        env.provider.then(toolTurn("c2", "peel_sticker", """{"sticker":"⭐","on":"Thu 10:00 note"}"""))
        env.provider.then(LlmTurnResult(text = "There"))
        env.harness.agent.send(conversation.id, "Go on").toList()
        assertEquals(5, service.on(conversation.id, target).size)
    }

    @Test
    fun withStickingOffThereAreNoStickerTools() = runTest {
        val env = TestEnv().apply { seed() }
        env.personas.upsert(env.persona.copy(stickers = PersonaStickers(sticks = false)))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Hi"))
        env.harness.agent.send(conversation.id, "Hello").toList()
        val request = env.provider.requests.last()
        assertFalse(request.tools.any { it.name == "place_sticker" || it.name == "peel_sticker" })
        assertFalse("## Stickers" in request.system)
    }

    @Test
    fun aStickerForALetterThatWasntSentGoesWithItButOneOnYourNoteStays() = runTest {
        val env = TestEnv().apply { seed() }
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Good luck!"))
        env.harness.agent.send(conversation.id, "Exam tomorrow").toList()
        env.clock.advanceMinutes(180)
        env.provider.then(toolTurn("c1", "place_sticker", """{"sticker":":crossed_fingers:","on":"their note","spot":"bottom right"}"""))
        env.provider.then(toolTurn("c2", "place_sticker", """{"sticker":":sparkles:"}"""))
        env.provider.then(LlmTurnResult(text = HeartbeatEngine.SILENT))

        assertIs<HeartbeatOutcome.Silent>(env.harness.heartbeat.run().single())

        val left = env.stickers.forConversation(conversation.id)
        assertEquals(listOf("crossed_fingers"), left.map { (it.ref as StickerRef.Emoji).name })
        val logged = env.activity.forPersona(env.persona.id, 0).single { it.kind == ActivityKind.STUCK_STICKER }
        assertEquals(":crossed_fingers:", logged.note)
    }

    private suspend fun readyToDraw(env: TestEnv) {
        env.credentials.upsert(Credential("nai", "NovelAI", CredentialKind.NOVELAI))
        env.secrets.put(SecretStore.credentialKey("nai"), "key")
        env.settingsRepo.update { it.copy(imageGen = it.imageGen.copy(profiles = listOf(ImageProfiles.preset(ImageHost.NOVELAI, "pic").copy(credentialId = "nai")), defaultStyle = "watercolor")) }
        env.personas.upsert(env.persona.copy(selfPrompt = "1girl, silver hair", defaultOutfit = "green cardigan"))
    }

    @Test
    fun aPersonaDrawsAStickerOfItselfOntoItsSheet() = runTest {
        val env = TestEnv().apply { seed() }
        readyToDraw(env)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "draw_sticker", """{"shows":"sleepy, hugging a pillow","name":":Mira Sleepy!:","feeling":"Sleepy"}"""))
        env.provider.then(LlmTurnResult(text = "Made you something."))
        env.harness.agent.send(conversation.id, "Draw yourself a sticker?").toList()

        val art = env.stickers.art(env.persona.id).single()
        assertEquals("mira-sleepy", art.name)
        assertEquals("sleepy", art.feeling)
        assertEquals("sleepy, hugging a pillow", art.prompt)
        val prompt = env.images.requests.single().prompt
        assertContains(prompt, "1girl, silver hair, green cardigan, chibi, sticker")
        assertContains(prompt, "sleepy, hugging a pillow, watercolor")
        assertTrue(env.harness.blobs.get(art.imageRef) != null, "the cut-out picture is kept")

        // A second one with the same name gets its own.
        env.provider.then(toolTurn("c2", "draw_sticker", """{"shows":"sleepy again","name":"mira-sleepy"}"""))
        env.provider.then(LlmTurnResult(text = "Another!"))
        env.harness.agent.send(conversation.id, "Again").toList()
        assertEquals(listOf("mira-sleepy", "mira-sleepy-2"), env.stickers.art(env.persona.id).map { it.name })
    }

    @Test
    fun drawingSeveralInAHeartbeatTakesOneSlot() = runTest {
        val env = TestEnv().apply { seed() }
        readyToDraw(env)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Night!"))
        env.harness.agent.send(conversation.id, "Night").toList()
        env.clock.advanceMinutes(240)
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(activityLimit = 2))) }
        env.provider.then { request ->
            val note = (request.messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
            assertContains(note, "draw_sticker")
            toolTurn("d1", "draw_sticker", """{"shows":"waving"}""")
        }
        env.provider.then(toolTurn("d2", "draw_sticker", """{"shows":"sleepy"}"""))
        env.provider.then(LlmTurnResult(text = "I drew some stickers while you slept."))

        assertIs<HeartbeatOutcome.Sent>(env.harness.heartbeat.run().single(), "two stickers took one slot, the letter the other")
        assertEquals(2, env.stickers.art(env.persona.id).size)
        assertEquals(2, env.activity.forPersona(env.persona.id, 0).single { it.kind == ActivityKind.DREW_STICKERS }.count)
    }

    @Test
    fun withOneSlotTheStickersHoldTheLetterBack() = runTest {
        val env = TestEnv().apply { seed() }
        readyToDraw(env)
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Night!"))
        env.harness.agent.send(conversation.id, "Night").toList()
        env.clock.advanceMinutes(240)
        env.settingsRepo.update { it.copy(heartbeat = it.heartbeat.copy(rhythm = it.heartbeat.rhythm.copy(activityLimit = 1))) }
        env.provider.then(toolTurn("d1", "draw_sticker", """{"shows":"waving"}"""))
        env.provider.then(LlmTurnResult(text = "And a letter too!"))

        assertIs<HeartbeatOutcome.Silent>(env.harness.heartbeat.run().single())
        assertEquals(1, env.stickers.art(env.persona.id).size, "the sticker stays on the sheet")
    }
}

