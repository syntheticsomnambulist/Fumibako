package dev.aiharness.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aiharness.app.AppContainer
import dev.aiharness.app.engine.GenerationState
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatHeader(
    val conversation: Conversation? = null,
    val persona: Persona? = null,
    val userPersona: UserPersona? = null,
    val profile: ModelProfile? = null,
)

class ChatViewModel(private val container: AppContainer, val conversationId: String) : ViewModel() {
    private val harness = container.harness

    val messages: StateFlow<List<ChatMessage>> = harness.conversations.observeMessages(conversationId)
        .map { list -> list.filter { !it.hidden } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profiles: StateFlow<List<ModelProfile>> = harness.profiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userPersonas: StateFlow<List<UserPersona>> = harness.userPersonas.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val header: StateFlow<ChatHeader> = combine(
        harness.conversations.observe(conversationId),
        harness.personas.observeAll(),
        userPersonas,
        profiles,
        harness.settings.settings,
    ) { conversation, personas, users, profiles, settings ->
        val persona = personas.firstOrNull { it.id == conversation?.personaId }
        val profileId = conversation?.modelProfileId ?: persona?.defaultModelProfileId ?: settings.defaultModelProfileId
        ChatHeader(
            conversation = conversation,
            persona = persona,
            userPersona = users.firstOrNull { it.id == (conversation?.userPersonaId ?: settings.activeUserPersonaId) },
            profile = profiles.firstOrNull { it.id == profileId } ?: profiles.minByOrNull { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatHeader())

    val generation: StateFlow<GenerationState> = container.generation.state(conversationId)

    val imageGenReady: StateFlow<Boolean> = harness.settings.settings
        .map { it.imageGen.enabled && it.imageGen.credentialId != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun send(text: String) {
        if (text.isNotBlank()) container.generation.send(conversationId, text.trim())
    }

    fun stop() = container.generation.cancel(conversationId)
    fun regenerate() = container.generation.regenerate(conversationId)
    fun dismissError() = container.generation.dismissError(conversationId)

    fun generateImage(prompt: String, includePersona: Boolean, orientation: String) =
        container.generation.generateImage(conversationId, prompt, includePersona, orientation)

    fun switchModel(profile: ModelProfile) = updateConversation { it.copy(modelProfileId = profile.id) }

    /** Changing the user persona starts a new context epoch automatically (the system prompt changes). */
    fun switchUserPersona(user: UserPersona) = updateConversation { it.copy(userPersonaId = user.id) }

    fun deleteMessage(message: ChatMessage) = viewModelScope.launch { harness.deleteMessages(conversationId, listOf(message.id)) }
    fun editMessage(message: ChatMessage, text: String) = viewModelScope.launch { harness.editMessage(message, text) }
    fun refreshContext() = viewModelScope.launch { harness.invalidateContext(conversationId) }
    fun deleteConversation(done: () -> Unit) = viewModelScope.launch {
        harness.conversations.delete(conversationId)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { done() }
    }

    private fun updateConversation(transform: (Conversation) -> Conversation) = viewModelScope.launch {
        harness.conversations.get(conversationId)?.let { harness.conversations.upsert(transform(it)) }
    }
}
