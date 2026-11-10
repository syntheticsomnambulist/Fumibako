package dev.aiharness.core.agent

import dev.aiharness.core.TestEnv
import dev.aiharness.core.image.ImageProfiles
import dev.aiharness.core.image.PicturePrompt
import dev.aiharness.core.model.ImageHost
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmToolCall
import dev.aiharness.core.llm.LlmTurnResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.storage.SecretStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PicturesTest {
    private val base = TestEnv().persona.copy(selfPrompt = "1girl, silver hair, green eyes", defaultOutfit = "cream sweater")

    @Test
    fun tagsPutTheSubjectFirstAndTheStyleLast() {
        assertEquals(
            "1girl, silver hair, green eyes, cream sweater, reading in a cafe, watercolor",
            PicturePrompt.build("reading in a cafe", base, defaultStyle = "watercolor"),
        )
    }

    @Test
    fun aPersonasOwnStyleAndCurrentOutfitWin() {
        val changed = base.copy(outfit = "yellow raincoat", stylePrompt = "ink wash")
        assertEquals("1girl, silver hair, green eyes, yellow raincoat, at the bus stop, ink wash", PicturePrompt.build("at the bus stop", changed, "watercolor"))
        assertEquals("cream sweater", changed.copy(outfit = null).currentOutfit)
    }

    @Test
    fun onlyTheStyleWhenThePersonaIsntInThePicture() {
        assertEquals("a rainy street, watercolor", PicturePrompt.build("a rainy street", base, "watercolor", PicturePrompt.Parts.STYLE_ONLY))
    }

    @Test
    fun sentencesForHostsThatWorkFromDescriptions() {
        assertEquals(
            "Drawn in the style of watercolor. Mira: 1girl, silver hair, green eyes. Wearing cream sweater. Reading in a cafe.",
            PicturePrompt.build("reading in a cafe", base, "watercolor", form = PicturePrompt.Form.SENTENCES),
        )
    }

    @Test
    fun youCanBeInThePictureToo() {
        val sam = dev.aiharness.core.model.UserPersona("u1", "Sam", selfPrompt = "1boy, messy brown hair", outfit = "green raincoat", createdAt = 1, updatedAt = 1)
        val together = PicturePrompt.Parts(you = true)
        assertEquals(
            "1girl, silver hair, green eyes, cream sweater, 1boy, messy brown hair, green raincoat, walking in the rain, watercolor",
            PicturePrompt.build("walking in the rain", base, "watercolor", together, you = sam),
        )
        assertEquals(
            "Drawn in the style of watercolor. Mira: 1girl, silver hair, green eyes. Wearing cream sweater. With Sam: 1boy, messy brown hair. Wearing green raincoat. Walking in the rain.",
            PicturePrompt.build("walking in the rain", base, "watercolor", together, PicturePrompt.Form.SENTENCES, you = sam),
        )
        // Your own portrait: you, in the default style, without the persona.
        assertEquals(
            "1boy, messy brown hair, green raincoat, portrait, watercolor",
            PicturePrompt.build("portrait", null, "watercolor", PicturePrompt.Parts.STYLE_ONLY.copy(you = true), you = sam),
        )
        assertEquals("Sam: 1boy, messy brown hair. Wearing green raincoat. Portrait.", PicturePrompt.build("portrait", null, "", PicturePrompt.Parts.STYLE_ONLY.copy(you = true), PicturePrompt.Form.SENTENCES, you = sam))
    }

    @Test
    fun aPersonaCanDrawYouInItsPicture() = runTest {
        val env = TestEnv().apply { seed() }
        env.credentials.upsert(Credential("nai", "NovelAI", CredentialKind.NOVELAI))
        env.secrets.put(SecretStore.credentialKey("nai"), "key")
        env.settingsRepo.update { it.copy(imageGen = it.imageGen.copy(profiles = listOf(ImageProfiles.preset(ImageHost.NOVELAI, "pic").copy(credentialId = "nai")), defaultStyle = "watercolor")) }
        env.personas.upsert(env.persona.copy(selfPrompt = base.selfPrompt, defaultOutfit = base.defaultOutfit))
        env.userPersonas.upsert(env.user.copy(selfPrompt = "1boy, messy brown hair", outfit = "green raincoat"))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "generate_image", """{"prompt":"holding hands, sunset","include_self":true,"include_user":true}"""))
        env.provider.then(LlmTurnResult(text = "Us!"))
        env.harness.agent.send(conversation.id, "Draw us together?").toList()
        assertEquals("1girl, silver hair, green eyes, cream sweater, 1boy, messy brown hair, green raincoat, holding hands, sunset, watercolor", env.images.requests.single().prompt)
    }

    @Test
    fun aNovelAiKeyTurnsPicturesOnUnlessTheyAreSetUp() {
        val nai = Credential("nai", "NovelAI", CredentialKind.NOVELAI)
        val off = dev.aiharness.core.model.ImageGenSettings(enabled = false, agentToolEnabled = false)
        val on = ImageProfiles.turnOnWith(off, nai)!!
        assertTrue(on.enabled && on.agentToolEnabled && on.ready)
        assertEquals(ImageHost.NOVELAI, on.profiles.single().host)
        assertEquals("nai", on.profiles.single().credentialId)
        assertEquals(on.profiles.single().id, on.defaultProfileId)
        assertNull(ImageProfiles.turnOnWith(on, nai), "already set up: left alone")
        assertNull(ImageProfiles.turnOnWith(off, Credential("a", "Anthropic", CredentialKind.ANTHROPIC)), "not a picture key")
    }

    private fun toolTurn(id: String, name: String, args: String) =
        LlmTurnResult(text = "", toolCalls = listOf(LlmToolCall(id, name, args)), stopReason = StopReason.TOOL_USE)

    @Test
    fun aPersonaChangesClothesForEveryConversationAndItIsLogged() = runTest {
        val env = TestEnv().apply { seed() }
        env.personas.upsert(env.persona.copy(selfPrompt = base.selfPrompt, defaultOutfit = base.defaultOutfit))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "change_outfit", """{"outfit":"yellow raincoat, rain boots"}"""))
        env.provider.then(LlmTurnResult(text = "Ready for the rain!"))
        env.harness.agent.send(conversation.id, "Shall we go for a walk?").toList()

        assertEquals("yellow raincoat, rain boots", env.personas.get(env.persona.id)!!.outfit)
        val logged = env.activity.forPersona(env.persona.id, 0).last()
        assertEquals(ActivityKind.CHANGED_OUTFIT, logged.kind)
        assertEquals("yellow raincoat, rain boots", logged.note)

        // A new conversation knows what it's wearing.
        val other = env.harness.createConversation(env.persona.id)
        env.provider.then(LlmTurnResult(text = "Still damp."))
        env.harness.agent.send(other.id, "Hello again").toList()
        val context = (env.provider.requests.last().messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
        assertContains(context, "You're wearing: yellow raincoat, rain boots")

        // "default" puts the usual clothes back on, and the line goes away.
        env.provider.then(toolTurn("c2", "change_outfit", """{"outfit":"default"}"""))
        env.provider.then(LlmTurnResult(text = "Dry again."))
        env.harness.agent.send(other.id, "Home now").toList()
        assertNull(env.personas.get(env.persona.id)!!.outfit)
        env.provider.then(LlmTurnResult(text = "Cosy."))
        env.harness.agent.send(other.id, "Tea?").toList()
        val later = (env.provider.requests.last().messages.last() as LlmMessage.User).parts.filterIsInstance<LlmPart.Text>().joinToString { it.text }
        assertFalse("You're wearing" in later)
    }

    @Test
    fun aPictureRightAfterChangingShowsTheNewOutfitInTheirStyle() = runTest {
        val env = TestEnv().apply { seed() }
        env.credentials.upsert(Credential("nai", "NovelAI", CredentialKind.NOVELAI))
        env.secrets.put(SecretStore.credentialKey("nai"), "key")
        env.settingsRepo.update { it.copy(imageGen = it.imageGen.copy(profiles = listOf(ImageProfiles.preset(ImageHost.NOVELAI, "pic").copy(credentialId = "nai")), defaultStyle = "watercolor")) }
        env.personas.upsert(env.persona.copy(selfPrompt = base.selfPrompt, defaultOutfit = base.defaultOutfit))
        val conversation = env.harness.createConversation(env.persona.id)
        env.provider.then(toolTurn("c1", "change_outfit", """{"outfit":"yellow raincoat"}"""))
        env.provider.then(toolTurn("c2", "generate_image", """{"prompt":"standing in the rain, smiling","include_self":true}"""))
        env.provider.then(LlmTurnResult(text = "Here I am!"))
        env.harness.agent.send(conversation.id, "Show me your raincoat").toList()

        assertEquals("1girl, silver hair, green eyes, yellow raincoat, standing in the rain, smiling, watercolor", env.images.requests.single().prompt)
    }
}
