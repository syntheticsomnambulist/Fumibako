package dev.aiharness.core

import dev.aiharness.core.agent.AgentRunner
import dev.aiharness.core.agent.ContextBuilder
import dev.aiharness.core.agent.HeartbeatEngine
import dev.aiharness.core.agent.LlmSummarizer
import dev.aiharness.core.agent.PromptAssembler
import dev.aiharness.core.agent.TurnContextBuilder
import dev.aiharness.core.agent.TurnSetup
import dev.aiharness.core.image.ImageGenerator
import dev.aiharness.core.image.ImageService
import dev.aiharness.core.image.HostImageGenerator
import dev.aiharness.core.llm.LlmException
import dev.aiharness.core.llm.LlmProvider
import dev.aiharness.core.llm.ProviderRegistry
import dev.aiharness.core.llm.anthropic.AnthropicProvider
import dev.aiharness.core.llm.gemini.GeminiProvider
import dev.aiharness.core.llm.openai.OpenAiCompatibleProvider
import dev.aiharness.core.media.LinkPreviews
import dev.aiharness.core.memory.EmbedderFactory
import dev.aiharness.core.memory.MemoryService
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.MoodTags
import dev.aiharness.core.storage.ActivityRepository
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.CredentialRepository
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.ModelProfileRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.storage.StickerRepository
import dev.aiharness.core.stickers.StickerCutter
import dev.aiharness.core.stickers.StickerMaker
import dev.aiharness.core.stickers.StickerService
import dev.aiharness.core.storage.UserPersonaRepository
import dev.aiharness.core.tools.AgentTool
import dev.aiharness.core.tools.ChangeOutfitTool
import dev.aiharness.core.tools.DrawStickerTool
import dev.aiharness.core.tools.GenerateImageTool
import dev.aiharness.core.tools.MemoryForgetTool
import dev.aiharness.core.tools.MemorySaveTool
import dev.aiharness.core.tools.MemorySearchTool
import dev.aiharness.core.tools.MemoryUpdateTool
import dev.aiharness.core.tools.PeelStickerTool
import dev.aiharness.core.tools.PlaceStickerTool
import dev.aiharness.core.tools.ToolRegistry
import dev.aiharness.core.util.Clock
import dev.aiharness.core.util.SystemClock
import dev.aiharness.core.util.newId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** One turn at a time per conversation, shared by chat and heartbeat. */
class ConversationLocks {
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    fun mutex(conversationId: String): Mutex = mutexes.getOrPut(conversationId) { Mutex() }
    suspend fun <T> withLock(conversationId: String, block: suspend () -> T): T = mutex(conversationId).withLock { block() }
    fun isBusy(conversationId: String): Boolean = mutexes[conversationId]?.isLocked == true
}

/**
 * Composition root for the agent harness. The host app supplies storage implementations; everything
 * else (providers, tools, memory, context, heartbeat, images) is built here and can be extended:
 * register extra [LlmProvider]s on [providers] and extra [AgentTool]s on [tools].
 */
class Harness(
    val personas: PersonaRepository,
    val userPersonas: UserPersonaRepository,
    val profiles: ModelProfileRepository,
    val credentials: CredentialRepository,
    val secrets: SecretStore,
    val conversations: ConversationRepository,
    val memories: MemoryRepository,
    val settings: SettingsRepository,
    val blobs: BlobStore,
    val activity: ActivityRepository,
    val stickers: StickerRepository,
    val clock: Clock = SystemClock,
    val providers: ProviderRegistry = defaultProviders(),
    imageGenerator: ImageGenerator = HostImageGenerator(),
    embedderFactory: EmbedderFactory? = null,
    /** Cuts drawn stickers out of their white background; the app supplies one (this default leaves them as drawn). */
    stickerCutter: StickerCutter = StickerCutter { it },
    val log: (String) -> Unit = {},
) {
    val memory = MemoryService(memories, settings, embedderFactory ?: EmbedderFactory.default(::apiKey), clock, log)
    val images = ImageService(imageGenerator, blobs, ::apiKey)
    val stickerService = StickerService(stickers, settings, clock)
    val stickerMaker = StickerMaker(images, blobs, stickers, stickerCutter, clock)
    val links = LinkPreviews(blobs)
    val tools = ToolRegistry(
        listOf(
            MemorySaveTool(memory),
            MemorySearchTool(memory),
            MemoryUpdateTool(memory),
            MemoryForgetTool(memory),
            GenerateImageTool(images, personas),
            ChangeOutfitTool(personas, { id, kind, note -> logActivity(id, kind, note = note) }, clock::nowMillis),
            PlaceStickerTool(stickerService, personas, conversations) { id, kind, conversationId, note -> logActivity(id, kind, conversationId, note = note) },
            PeelStickerTool(stickerService, personas, conversations),
            DrawStickerTool(stickerMaker, personas, stickers),
        ),
    )
    val prompts = PromptAssembler()
    val turnContext = TurnContextBuilder(memory, clock, stickerService)
    val contextBuilder = ContextBuilder(prompts, memory, blobs, clock, log)
    val summarizer = LlmSummarizer(this)
    val locks = ConversationLocks()
    val agent = AgentRunner(this)
    val heartbeat = HeartbeatEngine(this)
    private val conversationRows = Mutex()

    /**
     * Read-modify-write of a conversation row. Turns, heartbeats and the UI all update conversations
     * (context epoch, mood, read state, model); going through here keeps one writer from undoing
     * another's change with a stale copy.
     */
    suspend fun updateConversation(id: String, transform: (Conversation) -> Conversation): Conversation? =
        conversationRows.withLock {
            conversations.get(id)?.let(transform)?.also { conversations.upsert(it) }
        }

    /** The user has seen everything in this conversation up to now. */
    suspend fun markRead(conversationId: String) {
        val now = clock.nowMillis()
        updateConversation(conversationId) { if ((it.lastReadAt ?: 0L) >= now) it else it.copy(lastReadAt = now) }
    }

    suspend fun logActivity(
        personaId: String,
        kind: ActivityKind,
        conversationId: String? = null,
        count: Int = 1,
        note: String? = null,
        tags: List<String> = emptyList(),
    ) {
        activity.add(ActivityEntry(newId(), personaId, kind, clock.nowMillis(), conversationId, count, note, tags))
    }

    suspend fun apiKey(credentialId: String?): String? =
        credentialId?.let { secrets.get(SecretStore.credentialKey(it)) }

    /** Conversation model, else the persona's default, else the app default, else any profile. */
    suspend fun resolveProfile(conversation: Conversation): ModelProfile? {
        val persona = personas.get(conversation.personaId)
        val settings = settings.current()
        return listOfNotNull(conversation.modelProfileId, persona?.defaultModelProfileId, settings.defaultModelProfileId)
            .firstNotNullOfOrNull { profiles.get(it) }
            ?: profiles.getAll().minByOrNull { it.name }
    }

    suspend fun resolveTurn(conversationId: String): TurnSetup {
        val conversation = conversations.get(conversationId) ?: throw LlmException("Conversation not found")
        val persona = personas.get(conversation.personaId) ?: throw LlmException("This conversation's persona was deleted")
        val settings = settings.current()
        val userPersona = (conversation.userPersonaId ?: settings.activeUserPersonaId)?.let { userPersonas.get(it) }
        val profile = resolveProfile(conversation) ?: throw LlmException("Add a model profile in Settings first")
        return TurnSetup(conversation, persona, userPersona, profile, apiKey(profile.credentialId), settings)
    }

    /** Creates a conversation and posts the persona's greeting, if it has one. */
    suspend fun createConversation(personaId: String, userPersonaId: String? = null, modelProfileId: String? = null): Conversation {
        val persona = personas.get(personaId) ?: throw IllegalArgumentException("Unknown persona")
        val settings = settings.current()
        val userPersona = (userPersonaId ?: settings.activeUserPersonaId)?.let { userPersonas.get(it) }
        val now = clock.nowMillis()
        val conversation = Conversation(
            id = newId(),
            personaId = personaId,
            userPersonaId = userPersona?.id,
            modelProfileId = modelProfileId,
            title = persona.name,
            createdAt = now,
            updatedAt = now,
            // The greeting is read as soon as the new conversation opens; it isn't unread news.
            lastAgentMessageAt = now.takeIf { persona.greeting.isNotBlank() },
            lastReadAt = now,
        )
        val greeting = persona.greeting.takeIf { it.isNotBlank() }?.let { prompts.substitute(it, persona.name, userPersona?.name ?: "you") }
        val created = conversation.copy(preview = greeting?.let { Conversation.previewOf(MoodTags.strip(it)) })
        conversations.upsert(created)
        if (greeting != null) {
            conversations.appendMessage(
                ChatMessage(
                    id = newId(),
                    conversationId = conversation.id,
                    role = MessageRole.ASSISTANT,
                    parts = listOf(ContentPart.Text(greeting)),
                    createdAt = now,
                    origin = MessageOrigin.AGENT,
                ),
            )
        }
        return created
    }

    /**
     * Deletes or edits in the middle of a conversation rewrite history, so the next turn must start a
     * fresh context epoch (earlier turns are then sent without provider replay data).
     */
    suspend fun deleteMessages(conversationId: String, ids: Collection<String>) {
        conversations.deleteMessages(ids)
        invalidateContext(conversationId)
        refreshPreview(conversationId)
    }

    suspend fun editMessage(message: ChatMessage, newText: String) {
        val parts = message.parts.filterNot { it is ContentPart.Text } + ContentPart.Text(newText)
        conversations.updateMessage(message.copy(parts = parts, replay = null, mood = MoodTags.parse(newText) ?: message.mood))
        invalidateContext(message.conversationId)
        refreshPreview(message.conversationId)
    }

    /** Recomputes the letters-list summary of the latest reply after edits and deletes. */
    suspend fun refreshPreview(conversationId: String): Conversation? {
        val reply = conversations.getMessages(conversationId).lastOrNull {
            it.role == MessageRole.ASSISTANT && !it.hidden && !it.excludedFromContext && it.displayText.isNotBlank()
        }
        return updateConversation(conversationId) {
            it.copy(
                preview = reply?.let { r -> Conversation.previewOf(r.displayText) },
                previewFromHeartbeat = reply?.origin == MessageOrigin.HEARTBEAT,
                lastAgentMessageAt = reply?.createdAt,
            )
        }
    }

    suspend fun invalidateContext(conversationId: String) {
        // Keep the summary and window position; a fingerprint mismatch forces a new epoch.
        updateConversation(conversationId) { conversation ->
            conversation.copy(context = conversation.context?.copy(configFingerprint = INVALIDATED))
        }
    }

    companion object {
        private const val INVALIDATED = "invalidated"

        fun defaultProviders(): ProviderRegistry = ProviderRegistry(
            listOf(
                AnthropicProvider(),
                OpenAiCompatibleProvider(),
                GeminiProvider(),
                OpenAiCompatibleProvider.novelAi(),
            ),
        )
    }
}
