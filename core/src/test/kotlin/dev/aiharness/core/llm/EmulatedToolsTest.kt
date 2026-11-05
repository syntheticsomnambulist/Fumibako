package dev.aiharness.core.llm

import dev.aiharness.core.ScriptedProvider
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.tools.schema
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmulatedToolsTest {
    @Test
    fun parsesToolCallsAndKeepsVisibleText() {
        val (visible, calls) = EmulatedToolProtocol.parse(
            "Sure!\n<tool_call>{\"name\": \"memory_save\", \"arguments\": {\"content\": \"x\"}}</tool_call>\n<tool_call>broken</tool_call>",
            "t",
        )
        assertEquals(1, calls.size)
        assertEquals("memory_save", calls[0].name)
        assertEquals("""{"content":"x"}""", calls[0].argumentsJson)
        assertTrue(visible.startsWith("Sure!"))
        assertTrue(visible.contains("<tool_call>broken</tool_call>"), "malformed calls stay visible")
    }

    @Test
    fun streamFilterHidesCallsSplitAcrossChunks() {
        val filter = ToolCallStreamFilter()
        val chunks = listOf("Hello <to", "ol_call>{\"name\":", "\"x\"}</tool", "_call> world", " <")
        val out = chunks.joinToString("") { filter.accept(it) } + filter.flush()
        assertEquals("Hello  world <", out)
    }

    @Test
    fun wrapperRewritesToolTurnsAsTextAndParsesReply() = runTest {
        val inner = ScriptedProvider(type = ProviderType.NOVELAI_TEXT)
            .then(LlmTurnResult(text = "Okay <tool_call>{\"name\":\"memory_search\",\"arguments\":{\"query\":\"tea\"}}</tool_call>"))
        val wrapped = EmulatedToolsProvider(inner)
        val profile = ModelProfile(id = "n", name = "n", provider = ProviderType.NOVELAI_TEXT, modelId = "glm")
        val request = LlmRequest(
            profile = profile,
            apiKey = null,
            system = "base",
            messages = listOf(
                LlmMessage.User(listOf(LlmPart.Text("hi"))),
                LlmMessage.Assistant("", listOf(LlmToolCall("c1", "memory_save", """{"content":"a"}"""))),
                LlmMessage.ToolResults(listOf(LlmToolResult("c1", "memory_save", "Saved."))),
            ),
            tools = listOf(ToolSpec("memory_search", "Search", schema { string("query", "q", required = true) })),
        )
        val events = wrapped.stream(request).toList()
        val result = (events.last() as LlmEvent.Completed).result

        assertEquals("Okay", result.text)
        assertEquals("memory_search", result.toolCalls.single().name)
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals("Okay ", events.filterIsInstance<LlmEvent.TextDelta>().joinToString("") { it.text })

        val sent = inner.requests.single()
        assertTrue(sent.tools.isEmpty())
        assertTrue(sent.system.contains("# Tools") && sent.system.contains("memory_search"))
        val assistant = sent.messages[1] as LlmMessage.Assistant
        assertTrue(assistant.text.contains("<tool_call>"))
        val toolResult = sent.messages[2] as LlmMessage.User
        assertTrue((toolResult.parts.single() as LlmPart.Text).text.contains("<tool_result name=\"memory_save\">"))
    }
}
