package dev.aiharness.core

import dev.aiharness.core.image.ImageRequest
import dev.aiharness.core.image.NovelAiImageGenerator
import dev.aiharness.core.llm.LlmMessage
import dev.aiharness.core.llm.LlmPart
import dev.aiharness.core.llm.LlmRequest
import dev.aiharness.core.llm.LlmToolResult
import dev.aiharness.core.llm.StopReason
import dev.aiharness.core.llm.ToolSpec
import dev.aiharness.core.llm.anthropic.AnthropicProvider
import dev.aiharness.core.llm.complete
import dev.aiharness.core.llm.openai.OpenAiCompatibleProvider
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.tools.schema
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real API calls, skipped unless HARNESS_LIVE_TESTS=1 and the matching key is set:
 *   ANTHROPIC_API_KEY, NOVELAI_API_KEY (a NovelAI persistent API token).
 * Each test makes one or two small requests.
 */
class LiveSmokeTest {
    private fun key(name: String): String {
        assumeTrue("live tests disabled", System.getenv("HARNESS_LIVE_TESTS") == "1")
        val value = System.getenv(name)
        assumeTrue("$name not set", !value.isNullOrBlank())
        return value!!
    }

    @Test
    fun anthropicToolRoundTripWithThinkingReplay() = runBlocking {
        val apiKey = key("ANTHROPIC_API_KEY")
        val profile = ModelProfile(
            id = "live",
            name = "live",
            provider = ProviderType.ANTHROPIC,
            modelId = System.getenv("ANTHROPIC_MODEL") ?: "claude-opus-5-5",
            maxOutputTokens = 4000,
            reasoningEffort = "low",
        )
        val tools = listOf(ToolSpec("memory_save", "Save a memory about the user.", schema { string("content", "The memory", required = true) }))
        val provider = AnthropicProvider()
        val first = LlmRequest(
            profile,
            apiKey,
            "You are a friendly companion. When the user shares a personal fact, save it with memory_save, then reply briefly.",
            listOf(LlmMessage.User(listOf(LlmPart.Text("My favourite tea is genmaicha.")))),
            tools,
        )
        val turn1 = provider.complete(first)
        assertEquals(StopReason.TOOL_USE, turn1.stopReason, "expected a memory_save call, got: ${turn1.text}")
        val call = turn1.toolCalls.single()
        val turn2 = provider.complete(
            first.copy(
                messages = first.messages + LlmMessage.Assistant(turn1.text, turn1.toolCalls, turn1.replay) +
                    LlmMessage.ToolResults(listOf(LlmToolResult(call.id, call.name, "Saved."))),
            ),
        )
        assertEquals(StopReason.END_TURN, turn2.stopReason)
        assertTrue(turn2.text.isNotBlank())
        println("Anthropic live: ${turn2.text.take(120)} | usage=${turn2.usage}")
    }

    @Test
    fun novelAiTextModelList() = runBlocking {
        val apiKey = key("NOVELAI_API_KEY")
        val profile = ModelProfile(id = "nai", name = "nai", provider = ProviderType.NOVELAI_TEXT, modelId = "glm-4-6")
        val models = OpenAiCompatibleProvider.novelAi().listModels(profile, apiKey)
        println("NovelAI text models: $models")
        assertTrue(models.isNotEmpty())
    }

    @Test
    fun novelAiGeneratesOneImage() = runBlocking {
        val apiKey = key("NOVELAI_API_KEY")
        val images = NovelAiImageGenerator().generate(
            ImageRequest(
                prompt = "1girl, reading a book, cozy cafe, warm lighting",
                model = System.getenv("NOVELAI_IMAGE_MODEL") ?: "nai-diffusion-4-5-full",
                width = 832,
                height = 1216,
                steps = 23,
                scale = 5.0,
                sampler = "k_euler_ancestral",
                noiseSchedule = "karras",
                qualityToggle = true,
                ucPreset = "heavy",
            ),
            apiKey,
        )
        assertEquals("image/png", images.single().mimeType)
        System.getenv("NOVELAI_OUTPUT")?.let { java.io.File(it).writeBytes(images.single().bytes) }
        println("NovelAI live: ${images.single().bytes.size} bytes, seed ${images.single().seed}")
    }
}
