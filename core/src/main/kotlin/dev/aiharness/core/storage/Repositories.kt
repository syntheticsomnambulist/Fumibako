package dev.aiharness.core.storage

import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.UserPersona
import kotlinx.coroutines.flow.Flow

/*
 * Storage ports. The Android app implements these with Room / DataStore / Keystore;
 * [dev.aiharness.core.storage.inmemory] holds in-memory versions for tests and tooling.
 */

interface PersonaRepository {
    fun observeAll(): Flow<List<Persona>>
    suspend fun getAll(): List<Persona>
    suspend fun get(id: String): Persona?
    suspend fun upsert(persona: Persona)
    suspend fun delete(id: String)
}

interface UserPersonaRepository {
    fun observeAll(): Flow<List<UserPersona>>
    suspend fun get(id: String): UserPersona?
    suspend fun upsert(persona: UserPersona)
    suspend fun delete(id: String)
}

interface ModelProfileRepository {
    fun observeAll(): Flow<List<ModelProfile>>
    suspend fun getAll(): List<ModelProfile>
    suspend fun get(id: String): ModelProfile?
    suspend fun upsert(profile: ModelProfile)
    suspend fun delete(id: String)
}

interface CredentialRepository {
    fun observeAll(): Flow<List<Credential>>
    suspend fun get(id: String): Credential?
    suspend fun upsert(credential: Credential)
    suspend fun delete(id: String)
}

/** Encrypted key/value storage for API keys. */
interface SecretStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)

    companion object {
        fun credentialKey(credentialId: String) = "credential:$credentialId"
    }
}

interface ConversationRepository {
    fun observeAll(): Flow<List<Conversation>>
    fun observe(id: String): Flow<Conversation?>
    suspend fun get(id: String): Conversation?
    suspend fun getAll(): List<Conversation>
    suspend fun upsert(conversation: Conversation)

    /** Deletes the conversation with its messages, and the stickers on them where they're kept together. */
    suspend fun delete(id: String)

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    /** Messages ordered by creation time, oldest first. */
    suspend fun getMessages(conversationId: String): List<ChatMessage>
    suspend fun appendMessage(message: ChatMessage)
    suspend fun updateMessage(message: ChatMessage)

    /** Deletes the messages, and the stickers on them where they're kept together. */
    suspend fun deleteMessages(ids: Collection<String>)
}

interface MemoryRepository {
    fun observeForPersona(personaId: String): Flow<List<MemoryEntry>>
    suspend fun listForPersona(personaId: String): List<MemoryEntry>
    suspend fun get(id: String): MemoryEntry?
    suspend fun upsert(entry: MemoryEntry)
    suspend fun delete(id: String)
    /** It came up (including automatic recall): updates `lastAccessedAt` and `accessCount`. */
    suspend fun markAccessed(ids: Collection<String>, at: Long)

    /** It was really used: updates `lastUsedAt` and `useCount`, which keep a memory strong. */
    suspend fun markUsed(ids: Collection<String>, at: Long)

    /** Embedding vectors keyed by memory id, for one embedding model. */
    suspend fun getVectors(personaId: String, model: String): Map<String, FloatArray>
    suspend fun putVector(memoryId: String, personaId: String, model: String, vector: FloatArray)
}

/** Background activity log (heartbeat outcomes, upkeep), newest last. */
interface ActivityRepository {
    fun observeSince(since: Long): Flow<List<ActivityEntry>>

    /** One persona's entries since [since], oldest first. */
    suspend fun forPersona(personaId: String, since: Long): List<ActivityEntry>
    suspend fun add(entry: ActivityEntry)
    suspend fun deleteBefore(time: Long)
}

/** Stickers stuck on things, and the pictures they can be (persona sheets and the shared library). */
/** Marks on attachments (a punched ticket, a read stamp), apart from the messages they're in. */
interface MarkRepository {
    fun observeAll(): Flow<List<AttachmentMark>>
    suspend fun forAttachment(attachmentId: String): List<AttachmentMark>

    /** Keeps the first mark of each kind and person: marking again changes nothing. */
    suspend fun add(mark: AttachmentMark)
    suspend fun deleteForConversation(conversationId: String)
}

interface StickerRepository {
    /** A conversation's stickers, peeled ones included (callers show only those still stuck). */
    fun observeForConversation(conversationId: String): Flow<List<Sticker>>
    suspend fun forConversation(conversationId: String): List<Sticker>
    suspend fun get(id: String): Sticker?

    /** Stuck, moved or peeled after [since], oldest first: what a persona hears about on its next turn. */
    suspend fun changedSince(conversationId: String, since: Long): List<Sticker>
    suspend fun upsert(sticker: Sticker)
    suspend fun delete(id: String)

    /** Peeled stickers last changed before [time], once there's no one left to tell. */
    suspend fun deletePeeledBefore(time: Long)

    fun observeArt(): Flow<List<StickerArt>>

    /** A persona's sheet, or the shared library for null. */
    suspend fun art(ownerPersonaId: String?): List<StickerArt>
    suspend fun getArt(id: String): StickerArt?
    suspend fun upsertArt(art: StickerArt)
    suspend fun deleteArt(id: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Binary storage for generated images and avatars. Refs are opaque strings. */
interface BlobStore {
    suspend fun put(bytes: ByteArray, extension: String): String
    suspend fun get(ref: String): ByteArray?
    suspend fun delete(ref: String)
}
