package dev.aiharness.core.llm

import dev.aiharness.core.llm.openai.OpenAiCompatibleProvider
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.tools.schema
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiCompatibleProviderTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun profile() = ModelProfile(
        id = "p",
        name = "p",
        provider = ProviderType.OPENAI_COMPATIBLE,
        modelId = "gpt-test",
        baseUrl = server.url("/v1").toString(),
        maxOutputTokens = 321,
        temperature = 0.7,
        options = dev.aiharness.core.model.ProviderOptions(openAiMaxTokensField = "max_completion_tokens"),
    )

    private fun sse(vararg chunks: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n")

    @Test
    fun streamsTextAndAssemblesToolCallsAcrossChunks() = runTest {
        server.enqueue(
            sse(
                """{"model":"gpt-test-1","choices":[{"delta":{"content":"Hel"}}]}""",
                """{"choices":[{"delta":{"content":"lo"}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"memory_save","arguments":"{\"con"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"tent\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
                """{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":4}}}""",
            ),
        )
        val tools = listOf(ToolSpec("memory_save", "save", schema { string("content", "c", required = true) }))
        val request = LlmRequest(
            profile = profile(),
            apiKey = "sk-test",
            system = "sys",
            messages = listOf(
                LlmMessage.User(listOf(LlmPart.Text("hi"))),
                LlmMessage.Assistant("", listOf(LlmToolCall("c0", "memory_search", "{}"))),
                LlmMessage.ToolResults(listOf(LlmToolResult("c0", "memory_search", "none"))),
            ),
            tools = tools,
        )
        val events = OpenAiCompatibleProvider().stream(request).toList()

        assertEquals("Hello", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })
        val result = (events.last() as LlmEvent.Completed).result
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals(listOf(LlmToolCall("call_1", "memory_save", """{"content":"x"}""")), result.toolCalls)
        assertEquals(10L, result.usage?.inputTokens)
        assertEquals(4L, result.usage?.cacheReadTokens)
        assertEquals("gpt-test-1", result.modelId)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = HarnessJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(321, body["max_completion_tokens"]!!.jsonPrimitive.content.toInt())
        assertNull(body["max_tokens"])
        val roles = body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user", "assistant", "tool"), roles)
        assertEquals("function", (body["tools"] as JsonArray)[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun novelAiSendsStringContentToItsEndpoint() = runTest {
        server.enqueue(sse("""{"choices":[{"delta":{"content":"ok"},"finish_reason":"stop"}]}"""))
        val profile = profile().copy(provider = ProviderType.NOVELAI_TEXT, modelId = "glm-4-6")
        val result = OpenAiCompatibleProvider.novelAi().complete(
            LlmRequest(profile, "pst-x", "", listOf(LlmMessage.User(listOf(LlmPart.Text("a"), LlmPart.Text("b"))))),
        )
        assertEquals("ok", result.text)
        val body = HarnessJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive
        assertTrue(content.isString)
        assertEquals("a\n\nb", content.content)
    }

    @Test
    fun httpErrorsBecomeLlmExceptionsWithRetryability() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"slow down"}}"""))
        val error = runCatching {
            OpenAiCompatibleProvider().complete(LlmRequest(profile(), "k", "", listOf(LlmMessage.User(listOf(LlmPart.Text("x"))))))
        }.exceptionOrNull() as LlmException
        assertEquals(429, error.statusCode)
        assertTrue(error.retryable)
        assertTrue(error.message!!.contains("slow down"))
    }

    @Test
    fun sseParserHandlesMultilineDataAndComments() {
        val buffer = Buffer().writeUtf8(": keepalive\n\nevent: delta\ndata: a\ndata: b\n\ndata: c\n")
        assertEquals(SseEvent("delta", "a\nb"), readSseEvent(buffer))
        assertEquals(SseEvent(null, "c"), readSseEvent(buffer))
        assertNull(readSseEvent(buffer))
    }
}
