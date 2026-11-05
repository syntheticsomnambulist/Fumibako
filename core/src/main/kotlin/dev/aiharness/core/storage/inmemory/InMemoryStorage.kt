package dev.aiharness.core.storage.inmemory

import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.ActivityRepository
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.CredentialRepository
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.ModelProfileRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.storage.UserPersonaRepository
import dev.aiharness.core.util.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Generic id-keyed in-memory table backing the repositories below. */
private class Table<T>(private val idOf: (T) -> String) {
    val state = MutableStateFlow<Map<String, T>>(emptyMap())
    fun all(): List<T> = state.value.values.toList()
    fun get(id: String): T? = state.value[id]
    fun upsert(item: T) = state.update { it + (idOf(item) to item) }
    fun delete(id: String) = state.update { it - id }
}

class InMemoryPersonaRepository : PersonaRepository {
    private val table = Table<Persona> { it.id }
    override fun observeAll(): Flow<List<Persona>> = table.state.map { m -> m.values.sortedBy { it.name } }
    override suspend fun getAll(): List<Persona> = table.all()
    override suspend fun get(id: String) = table.get(id)
    override suspend fun upsert(persona: Persona) = table.upsert(persona)
    override suspend fun delete(id: String) = table.delete(id)
}

class InMemoryUserPersonaRepository : UserPersonaRepository {
    private val table = Table<UserPersona> { it.id }
    override fun observeAll(): Flow<List<UserPersona>> = table.state.map { m -> m.values.sortedBy { it.name } }
    override suspend fun get(id: String) = table.get(id)
    override suspend fun upsert(persona: UserPersona) = table.upsert(persona)
    override suspend fun delete(id: String) = table.delete(id)
}

class InMemoryModelProfileRepository : ModelProfileRepository {
    private val table = Table<ModelProfile> { it.id }
    override fun observeAll(): Flow<List<ModelProfile>> = table.state.map { m -> m.values.sortedBy { it.name } }
    override suspend fun getAll(): List<ModelProfile> = table.all()
    override suspend fun get(id: String) = table.get(id)
    override suspend fun upsert(profile: ModelProfile) = table.upsert(profile)
    override suspend fun delete(id: String) = table.delete(id)
}

class InMemoryCredentialRepository : CredentialRepository {
    private val table = Table<Credential> { it.id }
    override fun observeAll(): Flow<List<Credential>> = table.state.map { m -> m.values.sortedBy { it.label } }
    override suspend fun get(id: String) = table.get(id)
    override suspend fun upsert(credential: Credential) = table.upsert(credential)
    override suspend fun delete(id: String) = table.delete(id)
}

class InMemorySecretStore : SecretStore {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override suspend fun get(key: String): String? = values.value[key]
    override suspend fun put(key: String, value: String) = values.update { it + (key to value) }
    override suspend fun remove(key: String) = values.update { it - key }
}

class InMemoryConversationRepository : ConversationRepository {
    private val conversations = Table<Conversation> { it.id }
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    override fun observeAll(): Flow<List<Conversation>> =
        conversations.state.map { m -> m.values.sortedByDescending { it.updatedAt } }

    override fun observe(id: String): Flow<Conversation?> = conversations.state.map { it[id] }
    override suspend fun get(id: String) = conversations.get(id)
    override suspend fun getAll(): List<Conversation> = conversations.all()
    override suspend fun upsert(conversation: Conversation) = conversations.upsert(conversation)
    override suspend fun delete(id: String) {
        conversations.delete(id)
        messages.update { list -> list.filterNot { it.conversationId == id } }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        messages.map { list -> list.filter { it.conversationId == conversationId } }

    override suspend fun getMessages(conversationId: String): List<ChatMessage> =
        messages.value.filter { it.conversationId == conversationId }

    override suspend fun appendMessage(message: ChatMessage) = messages.update { it + message }

    override suspend fun updateMessage(message: ChatMessage) =
        messages.update { list -> list.map { if (it.id == message.id) message else it } }

    override suspend fun deleteMessages(ids: Collection<String>) =
        messages.update { list -> list.filterNot { it.id in ids } }
}

class InMemoryMemoryRepository : MemoryRepository {
    private val table = Table<MemoryEntry> { it.id }
    private val vectors = MutableStateFlow<Map<Pair<String, String>, FloatArray>>(emptyMap())

    override fun observeForPersona(personaId: String): Flow<List<MemoryEntry>> =
        table.state.map { m -> m.values.filter { it.personaId == personaId }.sortedByDescending { it.updatedAt } }

    override suspend fun listForPersona(personaId: String) = table.all().filter { it.personaId == personaId }
    override suspend fun get(id: String) = table.get(id)
    override suspend fun upsert(entry: MemoryEntry) = table.upsert(entry)
    override suspend fun delete(id: String) {
        table.delete(id)
        vectors.update { v -> v.filterKeys { it.first != id } }
    }

    override suspend fun markAccessed(ids: Collection<String>, at: Long) {
        ids.forEach { id ->
            table.get(id)?.let { table.upsert(it.copy(lastAccessedAt = at, accessCount = it.accessCount + 1)) }
        }
    }

    override suspend fun markUsed(ids: Collection<String>, at: Long) {
        ids.forEach { id ->
            table.get(id)?.let {
                table.upsert(it.copy(lastUsedAt = at, useCount = it.useCount + 1, lastAccessedAt = at, accessCount = it.accessCount + 1))
            }
        }
    }

    override suspend fun getVectors(personaId: String, model: String): Map<String, FloatArray> {
        val ids = table.all().filter { it.personaId == personaId }.map { it.id }.toSet()
        return vectors.value.filterKeys { it.second == model && it.first in ids }.mapKeys { it.key.first }
    }

    override suspend fun putVector(memoryId: String, personaId: String, model: String, vector: FloatArray) =
        vectors.update { it + ((memoryId to model) to vector) }
}

class InMemoryActivityRepository : ActivityRepository {
    private val entries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    override fun observeSince(since: Long): Flow<List<ActivityEntry>> =
        entries.map { list -> list.filter { it.createdAt > since }.sortedBy { it.createdAt } }

    override suspend fun forPersona(personaId: String, since: Long): List<ActivityEntry> =
        entries.value.filter { it.personaId == personaId && it.createdAt > since }.sortedBy { it.createdAt }

    override suspend fun add(entry: ActivityEntry) = entries.update { it + entry }
    override suspend fun deleteBefore(time: Long) = entries.update { list -> list.filter { it.createdAt >= time } }
}

class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current(): AppSettings = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

class InMemoryBlobStore : BlobStore {
    private val blobs = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    override suspend fun put(bytes: ByteArray, extension: String): String {
        val ref = "mem:${newId()}.$extension"
        blobs.update { it + (ref to bytes) }
        return ref
    }

    override suspend fun get(ref: String): ByteArray? = blobs.value[ref]
    override suspend fun delete(ref: String) = blobs.update { it - ref }
}
