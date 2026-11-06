package dev.aiharness.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aiharness.app.AppContainer
import dev.aiharness.app.engine.GenerationState
import dev.aiharness.core.media.LinkPreviews
import dev.aiharness.core.model.Track
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.MoodTags
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.stickers.StickResult
import dev.aiharness.core.model.UserPersona
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatHeader(
    val conversation: Conversation? = null,
    val persona: Persona? = null,
    val userPersona: UserPersona? = null,
    val profile: ModelProfile? = null,
)

class ChatViewModel(private val container: AppContainer, val conversationId: String) : ViewModel() {
    private val harness = container.harness

    /** Tickets punched by playing (marks kept apart from the letters). */
    val punched: StateFlow<Set<String>> = harness.marks.observeAll()
        .map { marks -> marks.filter { it.kind == dev.aiharness.core.model.MarkKind.PUNCHED }.map { it.attachmentId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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

    /** Stickers still stuck in this conversation, by what they're on (a message id, or this conversation's id for the draft). */
    val stickers: StateFlow<Map<String, List<Sticker>>> = harness.stickers.observeForConversation(conversationId)
        .map { list -> list.filterNot { it.isPeeled }.groupBy { it.target.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Stickers you've used lately, newest first. */
    val recentStickers: StateFlow<List<StickerRef>> = container.recentStickers

    /** Sticks [ref] on your draft, in the top corner of the field, a little crooked; refused when it's full. */
    fun stickOnDraft(ref: StickerRef) = viewModelScope.launch {
        val target = StickerTarget.draft(conversationId)
        val n = harness.stickerService.on(conversationId, target).size
        val slots = listOf(Triple(0.94f, 0.08f, 10f), Triple(0.82f, 0.02f, -9f), Triple(0.70f, 0.10f, 14f), Triple(0.58f, 0.03f, -6f), Triple(0.46f, 0.09f, 8f), Triple(0.34f, 0.02f, -12f))
        val (x, y, r) = slots[n % slots.size]
        if (harness.stickerService.stick(conversationId, target, ref, null, x, y, rotationDeg = r, scale = 0.7f) is StickResult.Stuck) container.usedSticker(ref)
    }

    /** Sticks [ref] on a letter, a note or a frame, where it was dropped. */
    fun stickOn(target: StickerTarget, ref: StickerRef, x: Float, y: Float) = viewModelScope.launch {
        val tilt = (Math.random() * 16 - 8).toFloat()
        if (harness.stickerService.stick(conversationId, target, ref, null, x, y, rotationDeg = tilt) is StickResult.Stuck) container.usedSticker(ref)
    }

    fun moveSticker(sticker: Sticker) = viewModelScope.launch {
        harness.stickerService.move(sticker, sticker.x, sticker.y, sticker.rotationDeg, sticker.scale)
    }

    fun peelSticker(sticker: Sticker) = viewModelScope.launch { harness.stickerService.peel(sticker, null) }

    /** Puts a sticker you just peeled back where it was. */
    fun unpeelSticker(sticker: Sticker) = viewModelScope.launch {
        harness.stickers.get(sticker.id)?.let { harness.stickers.upsert(it.copy(peeledAt = null, peeledByPersonaId = null)) }
    }

    /** Sticker pictures by id, for their `:name:`s. */
    val stickerArt: StateFlow<Map<String, StickerArt>> = harness.stickers.observeArt()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val imageGenReady: StateFlow<Boolean> = harness.settings.settings
        .map { it.imageGen.ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** What's waiting to go with your note. */
    val going = MutableStateFlow<List<Going>>(emptyList())

    /** Sends the note with what's waiting. A link on a line of its own leaves the text: its cutting carries it. */
    fun send(text: String) {
        val waiting = going.value
        if (waiting.any { it is Going.Preparing }) return
        val cut = waiting.mapNotNull { (it as? Going.Item)?.attachment?.url }.toSet()
        val body = text.lines().filterNot { it.trim() in cut }.joinToString("\n").trim()
        val images = waiting.filterIsInstance<Going.Picture>().map { it.image }
        val attachments = waiting.filterIsInstance<Going.Item>().map { it.attachment }
        if (body.isBlank() && images.isEmpty() && attachments.isEmpty()) return
        going.value = emptyList()
        container.generation.send(conversationId, body, images, attachments)
    }

    private fun prepare(label: String, work: suspend () -> List<Going>) {
        val key = "preparing-" + System.nanoTime()
        going.update { it + Going.Preparing(key, label) }
        viewModelScope.launch {
            val ready = runCatching { work() }.getOrDefault(emptyList())
            going.update { list -> list.flatMap { if (it.key == key) ready else listOf(it) } }
        }
    }

    fun addPhotos(uris: List<android.net.Uri>) = prepare("photos") { uris.mapNotNull { container.media.photo(it) }.map { Going.Picture(it) } }
    fun addFile(uri: android.net.Uri) = prepare("a file") { listOfNotNull(container.media.file(uri)?.let { Going.Item(it) }) }
    fun addVideo(uri: android.net.Uri) = prepare("a video") { listOfNotNull(container.media.video(uri)?.let { Going.Item(it) }) }

    /** Songs: one is a ticket, several a playlist (returned so the editor can open on it). */
    fun addSongs(uris: List<android.net.Uri>, onMix: (ContentPart.Attachment) -> Unit) = prepare("music") {
        val persona = header.value.persona?.name ?: "you"
        val you = header.value.userPersona?.name ?: "me"
        val made = container.media.songs(uris, persona, you)
        if (made?.kind == AttachmentKind.PLAYLIST) onMix(made)
        listOfNotNull(made?.let { Going.Item(it) })
    }

    /** More songs for the mix being edited. */
    suspend fun moreTracks(uris: List<android.net.Uri>): List<Track> = container.media.tracks(uris)

    /** A link written in your note becomes a cutting waiting with the rest, once. */
    fun noticeLinks(text: String) {
        val known = going.value.mapNotNull { (it as? Going.Item)?.attachment?.url } + going.value.mapNotNull { (it as? Going.Preparing)?.key?.removePrefix("link:") }
        LinkPreviews.findLinks(text).filter { it !in known }.forEach { url ->
            val key = "link:$url"
            going.update { it + Going.Preparing(key, url.substringAfter("://").substringBefore('/')) }
            viewModelScope.launch {
                val cutting = harness.links.cutting(url)
                going.update { list -> list.map { if (it.key == key) Going.Item(cutting) else it } }
            }
        }
    }

    fun replace(item: Going) = going.update { list -> list.map { if (it.key == item.key) item else it } }

    fun remove(item: Going) = going.update { list -> list.filterNot { it.key == item.key } }

    fun stop() = container.generation.cancel(conversationId)
    fun regenerate() = container.generation.regenerate(conversationId)
    fun dismissError() = container.generation.dismissError(conversationId)

    fun generateImage(prompt: String, parts: dev.aiharness.core.image.PicturePrompt.Parts, orientation: String, profileId: String?) =
        container.generation.generateImage(conversationId, prompt, parts, orientation, profileId)

    fun switchModel(profile: ModelProfile) = updateConversation { it.copy(modelProfileId = profile.id) }

    /** Changing the user persona starts a new context epoch automatically (the system prompt changes). */
    fun switchUserPersona(user: UserPersona) = updateConversation { it.copy(userPersonaId = user.id) }

    fun deleteMessages(messages: List<ChatMessage>) = viewModelScope.launch { harness.deleteMessages(conversationId, messages.map { it.id }) }
    /** Edits show the text without its mood tag; the tag is put back so the stored history keeps it. */
    fun editMessage(message: ChatMessage, text: String) = viewModelScope.launch {
        val tag = message.mood?.takeIf { message.role == MessageRole.ASSISTANT && MoodTags.parse(text) == null }
        harness.editMessage(message, if (tag != null) "$text [mood: $tag]" else text)
    }
    fun refreshContext() = viewModelScope.launch { harness.invalidateContext(conversationId) }
    fun deleteConversation(done: () -> Unit) = viewModelScope.launch {
        harness.conversations.delete(conversationId)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { done() }
    }

    fun markRead() = viewModelScope.launch { harness.markRead(conversationId) }

    private fun updateConversation(transform: (Conversation) -> Conversation) = viewModelScope.launch {
        harness.updateConversation(conversationId, transform)
    }
}
