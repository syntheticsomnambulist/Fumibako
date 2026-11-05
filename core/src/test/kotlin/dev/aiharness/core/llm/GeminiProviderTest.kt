package dev.aiharness.core.llm

import dev.aiharness.core.llm.gemini.GeminiProvider
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.tools.schema
import dev.aiharness.core.util.HarnessJson
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
import kotlin.test.assertTrue

class GeminiProviderTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun profile() = ModelProfile(
        id = "g",
        name = "g",
        provider = ProviderType.GEMINI,
        modelId = "gemini-test",
        baseUrl = server.url("/v1beta").toString(),
        reasoningEffort = "low",
    )

    @Test
    fun preservesThoughtSignaturesForReplay() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Let me \"}]}}]}\n\n" +
                    "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"check.\"}]}}]}\n\n" +
                    "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"memory_search\",\"args\":{\"query\":\"cats\"}},\"thoughtSignature\":\"SIG123\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":7,\"candidatesTokenCount\":3}}\n\n",
            ),
        )
        val tools = listOf(ToolSpec("memory_search", "search", schema { string("query", "q", required = true) }))
        val result = GeminiProvider().complete(
            LlmRequest(profile(), "gkey", "be nice", listOf(LlmMessage.User(listOf(LlmPart.Text("hi")))), tools),
        )
        assertEquals("Let me check.", result.text)
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals("memory_search", result.toolCalls.single().name)
        assertEquals("""{"query":"cats"}""", result.toolCalls.single().argumentsJson)
        // Text fragments merge; the signed function-call part is kept verbatim.
        val replay = HarnessJson.parseToJsonElement(result.replay!!).jsonArray
        assertEquals(2, replay.size)
        assertEquals("SIG123", replay[1].jsonObject["thoughtSignature"]!!.jsonPrimitive.content)

        val first = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:streamGenerateContent?alt=sse", first.path)
        assertEquals("gkey", first.getHeader("x-goog-api-key"))
        val body = HarnessJson.parseToJsonElement(first.body.readUtf8()).jsonObject
        val declaration = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("OBJECT", declaration["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("low", body["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)

        // Second request replays the model turn with its signature, then the function response.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Found it.\"}]},\"finishReason\":\"STOP\"}]}\n\n"),
        )
        GeminiProvider().complete(
            LlmRequest(
                profile(),
                "gkey",
                "be nice",
                listOf(
                    LlmMessage.User(listOf(LlmPart.Text("hi"))),
                    LlmMessage.Assistant(result.text, result.toolCalls, result.replay),
                    LlmMessage.ToolResults(listOf(LlmToolResult(result.toolCalls.single().id, "memory_search", "cats are great"))),
                ),
                tools,
            ),
        )
        val second = HarnessJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val contents = second["contents"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "model", "user"), contents.map { it["role"]!!.jsonPrimitive.content })
        val modelParts = contents[1]["parts"]!!.jsonArray
        assertEquals("SIG123", modelParts[1].jsonObject["thoughtSignature"]!!.jsonPrimitive.content)
        val response = contents[2]["parts"]!!.jsonArray[0].jsonObject["functionResponse"] as JsonObject
        assertEquals("memory_search", response["name"]!!.jsonPrimitive.content)
        assertTrue("id" !in response, "synthetic ids are not sent to Gemini")
    }
}
