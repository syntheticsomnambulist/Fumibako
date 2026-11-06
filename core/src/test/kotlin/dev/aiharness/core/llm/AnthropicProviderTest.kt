package dev.aiharness.core.llm

import dev.aiharness.core.llm.anthropic.AnthropicProvider
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.tools.schema
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnthropicProviderTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun profile(baseUrl: String? = server.url("/").toString().trimEnd('/')) = ModelProfile(
        id = "a",
        name = "a",
        provider = ProviderType.ANTHROPIC,
        modelId = "claude-opus-5-5",
        baseUrl = baseUrl,
        maxOutputTokens = 2048,
        temperature = 0.9, // must never be sent to Claude
        reasoningEffort = "low",
    )

    private fun event(type: String, json: String) = "event: $type\ndata: $json\n\n"

    private val toolTurnStream = listOf(
        event("message_start", """{"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":1,"cache_read_input_tokens":3,"cache_creation_input_tokens":0}}}"""),
        event("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Worth remembering."}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"SIGabc"}}"""),
        event("content_block_stop", """{"type":"content_block_stop","index":0}"""),
        event("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Noted!"}}"""),
        event("content_block_stop", """{"type":"content_block_stop","index":1}"""),
        event("content_block_start", """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"memory_save","input":{}}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"content\":"}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"Sam likes tea\"}"}}"""),
        event("content_block_stop", """{"type":"content_block_stop","index":2}"""),
        event("message_delta", """{"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":30}}"""),
        event("message_stop", """{"type":"message_stop"}"""),
    ).joinToString("")

    private val textTurnStream = listOf(
        event("message_start", """{"type":"message_start","message":{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":40,"output_tokens":1}}}"""),
        event("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""),
        event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Done."}}"""),
        event("content_block_stop", """{"type":"content_block_stop","index":0}"""),
        event("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":3}}"""),
        event("message_stop", """{"type":"message_stop"}"""),
    ).joinToString("")

    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    private val tools = listOf(ToolSpec("memory_save", "Save a memory", schema { string("content", "text", required = true) }))

    @Test
    fun streamsThinkingTextAndToolUseThenReplaysSignatureVerbatim() = runTest {
        server.enqueue(sse(toolTurnStream))
        val provider = AnthropicProvider()
        val firstRequest = LlmRequest(profile(), "sk-ant-test", "You are Mira.", listOf(LlmMessage.User(listOf(LlmPart.Text("I like tea")))), tools)
        val events = provider.stream(firstRequest).toList()

        assertEquals("Noted!", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })
        assertEquals("Worth remembering.", events.filterIsInstance<LlmEvent.ReasoningDelta>().joinToString("") { it.text })
        val result = (events.last() as LlmEvent.Completed).result
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals("toolu_1", result.toolCalls.single().id)
        assertEquals("Sam likes tea", HarnessJson.parseToJsonElement(result.toolCalls.single().argumentsJson).jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(3L, result.usage?.cacheReadTokens)
        assertTrue(result.replay!!.contains("SIGabc"))

        val first = server.takeRequest()
        // The SDK decides the auth header form (x-api-key or Authorization: Bearer).
        val sentKey = first.getHeader("x-api-key") ?: first.getHeader("Authorization")?.removePrefix("Bearer ")
        assertEquals("sk-ant-test", sentKey)
        val firstBody = HarnessJson.parseToJsonElement(first.body.readUtf8()).jsonObject
        assertNull(firstBody["temperature"], "sampling parameters are not sent to Claude")
        assertEquals("adaptive", firstBody["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("summarized", firstBody["thinking"]!!.jsonObject["display"]!!.jsonPrimitive.content)
        assertEquals("low", firstBody["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val system = firstBody["system"]!!.jsonArray.single().jsonObject
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("memory_save", firstBody["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertNull(firstBody["fallbacks"], "server fallback only applies to the first-party API")

        // Second turn: the assistant turn is replayed from the stored SDK param, signature intact.
        server.enqueue(sse(textTurnStream))
        val second = provider.complete(
            firstRequest.copy(
                messages = firstRequest.messages + listOf(
                    LlmMessage.Assistant(result.text, result.toolCalls, result.replay),
                    LlmMessage.ToolResults(listOf(LlmToolResult("toolu_1", "memory_save", "Saved."))),
                ),
            ),
        )
        assertEquals("Done.", second.text)
        val secondBody = HarnessJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val messages = secondBody["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        val assistantBlocks = messages[1]["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("thinking", "text", "tool_use"), assistantBlocks.map { it["type"]!!.jsonPrimitive.content })
        assertEquals("SIGabc", assistantBlocks[0]["signature"]!!.jsonPrimitive.content)
        val toolResult = messages[2]["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", toolResult["type"]!!.jsonPrimitive.content)
        assertEquals("toolu_1", toolResult["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun aPdfGoesAsADocumentAndOtherMediaStayBehind() = runTest {
        server.enqueue(sse(textTurnStream))
        val parts = listOf(
            LlmPart.Text("[tag: map.pdf, PDF, 1 page, 3 KB]"),
            LlmPart.Media(byteArrayOf(37, 80, 68, 70), "application/pdf", "map.pdf"),
            LlmPart.Media(byteArrayOf(1, 2, 3), "audio/mp3", "song.mp3"),
        )
        AnthropicProvider().complete(LlmRequest(profile(), "k", "sys", listOf(LlmMessage.User(parts))))
        val body = HarnessJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val blocks = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "document"), blocks.map { it["type"]!!.jsonPrimitive.content })
        val source = blocks[1]["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("application/pdf", source["media_type"]!!.jsonPrimitive.content)
        assertEquals("JVBERg==", source["data"]!!.jsonPrimitive.content)
        assertEquals("map.pdf", blocks[1]["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun serverFallbackIsEnabledForFirstPartySupportedModels() {
        val firstParty = profile(baseUrl = null)
        assertTrue(AnthropicProvider.supportsServerFallback(firstParty))
        assertFalse(AnthropicProvider.supportsServerFallback(firstParty.copy(modelId = "claude-haiku-4-5")))
        assertFalse(AnthropicProvider.supportsServerFallback(profile()))

        val params = AnthropicProvider().buildParams(
            LlmRequest(firstParty, "k", "sys", listOf(LlmMessage.User(listOf(LlmPart.Text("hi"))))),
        )
        assertTrue(params.fallbacks().isPresent)
        assertTrue(params.betas().get().any { it.asString() == AnthropicProvider.SERVER_FALLBACK_BETA })
    }

    @Test
    fun apiErrorsMapToLlmException() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
        )
        val error = runCatching {
            AnthropicProvider().complete(LlmRequest(profile(), "bad", "", listOf(LlmMessage.User(listOf(LlmPart.Text("x"))))))
        }.exceptionOrNull()
        assertTrue(error is LlmException, "got $error")
        assertEquals(401, error.statusCode)
        assertFalse(error.retryable)
    }

    @Suppress("unused")
    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
}
