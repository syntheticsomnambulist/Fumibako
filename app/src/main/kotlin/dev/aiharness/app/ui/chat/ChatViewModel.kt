package dev.aiharness.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aiharness.app.AppContainer
import dev.aiharness.app.engine.GenerationState
import dev.aiharness.core.media.LinkPreviews
import dev.aiharness.core.media.MusicLinks
import dev.aiharness.core.media.SongSource
import dev.aiharness.core.util.newId
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /** Cuttings the persona has opened: attachment id to whose read stamp is on it. */
    val reads: StateFlow<Map<String, String>?> = harness.marks.observeAll()
        .map { marks -> marks.filter { it.kind == dev.aiharness.core.model.MarkKind.READ && it.byPersonaId != null }.associate { it.attachmentId to it.byPersonaId!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** Your unsent note here, kept by the app: it stays when you leave the letters, and what's being made ready carries on. */
    private val draft = container.drafts.of(conversationId)

    /** What's waiting to go with your note. */
    val going: MutableStateFlow<List<Going>> = draft.going

    /** The words of your unsent note, as you left them. */
    val draftText: String get() = draft.text.value

    /** Keeps what's in the box as the draft. */
    fun keepText(text: String) {
        draft.text.value = text
    }

    /** Work that outlasts this screen: preparing what you picked, a link's cutting, a note on its way. */
    private val work get() = container.appScope

    /**
     * Sends the note with what's waiting, and says whether it went (so the box is only cleared
     * then). A link on a line of its own leaves the text: its cutting carries it. Cuttings still
     * being made are waited for, a few seconds at most (then the link goes as a plain cutting);
     * photos, files and videos being prepared hold the note back.
     */
    fun send(text: String): Boolean {
        if (draft.sending?.isActive == true) return false
        linkPause?.cancel()
        noticeLinksNow(text)
        if (going.value.any { it is Going.Preparing && !it.key.startsWith(LINK_KEY) }) return false
        if (text.isBlank() && going.value.isEmpty()) return false
        draft.sending = work.launch {
            withTimeoutOrNull(LINK_WAIT_MS) { going.first { list -> list.none { it is Going.Preparing } } }
            // Anything picked while this waited, and still being prepared, stays for the next note.
            val current = going.value.filterNot { it is Going.Preparing && !it.key.startsWith(LINK_KEY) }
            val waiting = current.map { item ->
                if (item is Going.Preparing) Going.Item(harness.links.cutting(item.key.removePrefix(LINK_KEY), fetch = false)) else item
            }
            val cut = waiting.mapNotNull { (it as? Going.Item)?.attachment?.url }.toSet()
            val body = text.lines().filterNot { it.trim() in cut }.joinToString("\n").trim()
            val images = waiting.filterIsInstance<Going.Picture>().map { it.image }
            val attachments = waiting.filterIsInstance<Going.Item>().map { it.attachment }
            if (body.isBlank() && images.isEmpty() && attachments.isEmpty()) return@launch
            // By what they were waiting under: a cutting that timed out goes plain, in its place.
            val sent = current.map { it.key }.toSet()
            going.update { list -> list.filterNot { it.key in sent } }
            draft.dismissedLinks.clear()
            container.generation.send(conversationId, body, images, attachments)
        }
        return true
    }

    /** Shows [label] waiting (with its [kind]'s drawing) while [work] makes it ready, and how far along that is when it says. */
    private fun prepare(label: String, kind: TrayItem?, work: suspend (progress: (Float) -> Unit) -> List<Going>) {
        val key = "preparing-" + System.nanoTime()
        going.update { it + Going.Preparing(key, label, kind) }
        val progress: (Float) -> Unit = { p ->
            going.update { list -> list.map { if (it.key == key && it is Going.Preparing) it.copy(progress = p.coerceIn(0f, 1f)) else it } }
        }
        this.work.launch {
            val ready = runCatching { work(progress) }.getOrDefault(emptyList())
            going.update { list -> list.flatMap { if (it.key == key) ready else listOf(it) } }
        }
    }

    fun addPhotos(uris: List<android.net.Uri>) = prepare("photos", TrayItem.PHOTOS) {
        val side = harness.settings.current().media.pictureSide
        uris.mapNotNull { container.media.photo(it, side) }.map { Going.Picture(it) }
    }
    fun addFile(uri: android.net.Uri) = prepare("a file", TrayItem.FILE) { listOfNotNull(container.media.file(uri)?.let { Going.Item(it) }) }
    fun addVideo(uri: android.net.Uri) = prepare("a video", TrayItem.VIDEO) { progress -> listOfNotNull(container.media.video(uri, progress)?.let { Going.Item(it) }) }

    /** Songs: one is a ticket, several a playlist (returned so the editor can open on it). */
    fun addSongs(uris: List<android.net.Uri>, onMix: (ContentPart.Attachment) -> Unit) = prepare("music", TrayItem.MUSIC) { progress ->
        val persona = header.value.persona?.name ?: "you"
        val you = header.value.userPersona?.name ?: "me"
        val made = container.media.songs(uris, persona, you, progress)
        if (made?.kind == AttachmentKind.PLAYLIST) onMix(made)
        listOfNotNull(made?.let { Going.Item(it) })
    }

    init {
        // A draft brought back may hold links not yet made into cuttings (it was left mid-word).
        noticeLinksNow(draft.text.value)
    }

    /** The tray's song search. */
    val songSearch = MutableStateFlow(SongSearch())
    private var searching: Job? = null

    /** Searches Apple's catalog for [query], once typing pauses. */
    fun searchSongs(query: String) {
        searching?.cancel()
        if (query.isBlank()) {
            songSearch.value = SongSearch()
            return
        }
        songSearch.update { it.copy(query = query, searching = true) }
        searching = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            val found = harness.music.songs(query)
            songSearch.update { it.copy(searching = false, searched = query, results = found.orEmpty(), failed = found == null) }
        }
    }

    /** A song picked from the search, as a ticket with its preview. */
    fun addFoundSong(song: dev.aiharness.core.media.FoundSong) = prepare("a song", TrayItem.MUSIC) { listOf(Going.Item(harness.music.ticket(song))) }

    /** More songs for the mix being edited. */
    suspend fun moreTracks(uris: List<android.net.Uri>): List<Track> = container.media.tracks(uris)

    private var linkPause: Job? = null

    /**
     * A link written in your note becomes a cutting waiting with the rest, once, when you pause
     * (so an address isn't fetched letter by letter as it's typed), or straight away when
     * something follows it.
     */
    fun noticeLinks(text: String) {
        linkPause?.cancel()
        noticeLinksNow(text, finishedOnly = true)
        linkPause = viewModelScope.launch {
            delay(LINK_PAUSE_MS)
            noticeLinksNow(text)
        }
    }

    private fun noticeLinksNow(text: String, finishedOnly: Boolean = false) {
        val known = going.value.mapNotNull { (it as? Going.Item)?.attachment?.url } + going.value.mapNotNull { (it as? Going.Preparing)?.key?.removePrefix(LINK_KEY) }
        val links = LinkPreviews.findLinks(text).filter { url -> !finishedOnly || text.contains("$url ") || text.contains("$url\n") }
        links.filter { it !in known && it !in draft.dismissedLinks }.forEach { url ->
            val key = LINK_KEY + url
            going.update { it + Going.Preparing(key, LinkPreviews.siteOf(url.substringAfter("://").substringBefore('/'))) }
            work.launch {
                val made = madeFrom(url)
                going.update { list -> list.map { if (it.key == key) Going.Item(made) else it } }
            }
        }
    }

    /**
     * What a link in your note goes as: a ticket for a music service's song (Spotify, Apple Music,
     * SoundCloud, Bandcamp, YouTube Music) or an audio file, a cutting for anything else (a
     * YouTube video too, which a tap can turn into a song).
     */
    private suspend fun madeFrom(url: String): ContentPart.Attachment {
        val source = MusicLinks.sourceOf(url)
        if (source == SongSource.AUDIO) {
            // An audio file isn't a page: it isn't fetched.
            val (title, artist) = MusicLinks.titleAndArtist(SongSource.AUDIO, null, null, url)
            return ContentPart.Attachment(newId(), AttachmentKind.SONG, url = url, title = title, artist = artist, site = LinkPreviews.siteOf(url.substringAfter("://").substringBefore('/')))
        }
        val cutting = harness.links.cutting(url, fetch = harness.settings.current().media.linkPreviews)
        if (source == null || !MusicLinks.isMusicOnly(url)) return cutting
        return MusicLinks.ticket(cutting, source).also { draft.cuttingsOf[it.id] = cutting }
    }

    /** Whether [item] can go either as a ticket or as a cutting (a music service's link), and what a tap makes it. */
    fun switchesTo(item: Going): String? {
        val a = (item as? Going.Item)?.attachment ?: return null
        if (a.ref != null || MusicLinks.sourceOf(a.url).let { it == null || it == SongSource.AUDIO }) return null
        return when (a.kind) {
            AttachmentKind.LINK -> "a song"
            AttachmentKind.SONG -> "a cutting"
            else -> null
        }
    }

    /** Turns a music link's cutting into a ticket, or back. */
    fun switchSongOrCutting(item: Going) {
        val a = (item as? Going.Item)?.attachment ?: return
        val source = MusicLinks.sourceOf(a.url) ?: return
        val switched = when (a.kind) {
            AttachmentKind.LINK -> MusicLinks.ticket(a, source).also { draft.cuttingsOf[it.id] = a }
            AttachmentKind.SONG -> draft.cuttingsOf.remove(a.id) ?: a.copy(kind = AttachmentKind.LINK)
            else -> return
        }
        replace(Going.Item(switched))
    }

    fun replace(item: Going) = going.update { list -> list.map { if (it.key == item.key) item else it } }

    fun remove(item: Going) {
        // A link taken out stays out, though it's still in the text.
        ((item as? Going.Item)?.attachment?.url ?: (item as? Going.Preparing)?.key?.takeIf { it.startsWith(LINK_KEY) }?.removePrefix(LINK_KEY))?.let { draft.dismissedLinks += it }
        going.update { list -> list.filterNot { it.key == item.key } }
    }

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
        container.drafts.discard(conversationId)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { done() }
    }

    fun markRead() = viewModelScope.launch { harness.markRead(conversationId) }

    private fun updateConversation(transform: (Conversation) -> Conversation) = viewModelScope.launch {
        harness.updateConversation(conversationId, transform)
    }
}

/** What a link's cutting waits under while it's made, before its address. */
private const val LINK_KEY = "link:"

/** How long typing pauses before a link at the end of the note is fetched. */
private const val LINK_PAUSE_MS = 900L

/** How long a note waits on its links' cuttings when you send it. */
private const val LINK_WAIT_MS = 8_000L

/** How long typing pauses before a song search runs. */
private const val SEARCH_PAUSE_MS = 450L
