package dev.aiharness.app.engine

import dev.aiharness.core.Harness
import dev.aiharness.core.agent.AgentEvent
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class GenerationState(
    val active: Boolean = false,
    val streamingText: String = "",
    val reasoning: String = "",
    val activeTool: String? = null,
    val error: String? = null,
    val retryable: Boolean = false,
)

/**
 * Runs agent turns in an app-wide scope so a reply keeps streaming when the user leaves the chat
 * screen. Exposes per-conversation progress for the UI; finished messages come from the database.
 */
class GenerationManager(private val harness: Harness, private val scope: CoroutineScope) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<GenerationState>>()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun state(conversationId: String): StateFlow<GenerationState> = flow(conversationId)

    private fun flow(id: String) = states.getOrPut(id) { MutableStateFlow(GenerationState()) }

    fun send(conversationId: String, text: String) = run(conversationId) { harness.agent.send(conversationId, text) }

    fun regenerate(conversationId: String) = run(conversationId) { harness.agent.regenerate(conversationId) }

    fun cancel(conversationId: String) {
        jobs[conversationId]?.cancel()
    }

    fun dismissError(conversationId: String) = flow(conversationId).update { it.copy(error = null) }

    /** User-requested image, posted into the chat as the user's own message. */
    /** Draws a picture you asked for in a letter: [parts] of the persona, with [profileId] (null: its usual one). */
    fun generateImage(conversationId: String, prompt: String, parts: dev.aiharness.core.image.PicturePrompt.Parts, orientation: String, profileId: String?) {
        val state = flow(conversationId)
        if (jobs[conversationId]?.isActive == true) return
        state.value = GenerationState(active = true, activeTool = "generate_image")
        jobs[conversationId] = scope.launch {
            try {
                val conversation = harness.conversations.get(conversationId) ?: return@launch
                val persona = harness.personas.get(conversation.personaId)
                val settings = harness.settings.current()
                val image = harness.images.generate(
                    settings = settings.imageGen,
                    prompt = prompt,
                    orientation = orientation,
                    persona = persona,
                    parts = parts,
                    profile = settings.imageGen.profiles.firstOrNull { it.id == profileId },
                )
                harness.conversations.appendMessage(
                    ChatMessage(
                        id = newId(),
                        conversationId = conversationId,
                        role = MessageRole.USER,
                        parts = listOf(ContentPart.Text("(shared an image)"), image),
                        createdAt = System.currentTimeMillis(),
                        origin = MessageOrigin.USER,
                    ),
                )
                state.update { it.copy(active = false, activeTool = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e.message ?: "Image generation failed", retryable = false) }
            } finally {
                state.update { it.copy(active = false, activeTool = null) }
            }
        }
    }

    private fun run(conversationId: String, start: () -> Flow<AgentEvent>) {
        val state = flow(conversationId)
        if (jobs[conversationId]?.isActive == true) return
        state.value = GenerationState(active = true)
        jobs[conversationId] = scope.launch {
            try {
                start().collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> state.update { it.copy(streamingText = it.streamingText + event.text) }
                        is AgentEvent.ReasoningDelta -> state.update { it.copy(reasoning = it.reasoning + event.text) }
                        is AgentEvent.MessageSaved -> state.update { it.copy(streamingText = "", reasoning = "") }
                        is AgentEvent.ToolStarted -> state.update { it.copy(activeTool = event.name) }
                        is AgentEvent.ToolFinished -> state.update { it.copy(activeTool = null) }
                        is AgentEvent.Failed -> state.update { it.copy(error = event.message, retryable = event.retryable) }
                        is AgentEvent.UserMessageSaved, is AgentEvent.Finished -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never let a failed turn crash the app scope.
                state.update { it.copy(error = e.message ?: e::class.simpleName, retryable = false) }
            } finally {
                state.update { it.copy(active = false, streamingText = "", reasoning = "", activeTool = null) }
            }
        }
    }

    companion object {
        fun describeTool(name: String?): String = when (name) {
            null -> "Thinking"
            "memory_save" -> "Saving a memory"
            "memory_search" -> "Searching memories"
            "memory_update" -> "Updating a memory"
            "memory_forget" -> "Forgetting a memory"
            "generate_image" -> "Generating an image"
            else -> "Using $name"
        }

        fun describeToolDone(name: String): String = when (name) {
            "memory_save" -> "Saved a memory"
            "memory_search" -> "Searched memories"
            "memory_update" -> "Updated a memory"
            "memory_forget" -> "Forgot a memory"
            "generate_image" -> "Generated an image"
            else -> "Used $name"
        }
    }
}
