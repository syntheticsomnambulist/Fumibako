package dev.aiharness.app.data

import dev.aiharness.app.data.db.ActivityEntity
import dev.aiharness.app.data.db.ConversationEntity
import dev.aiharness.app.data.db.CredentialEntity
import dev.aiharness.app.data.db.HarnessDao
import dev.aiharness.app.data.db.MarkEntity
import dev.aiharness.app.data.db.MemoryEntity
import dev.aiharness.app.data.db.MemoryVectorEntity
import dev.aiharness.app.data.db.MessageEntity
import dev.aiharness.app.data.db.ModelProfileEntity
import dev.aiharness.app.data.db.PersonaEntity
import dev.aiharness.app.data.db.StickerArtEntity
import dev.aiharness.app.data.db.StickerEntity
import dev.aiharness.app.data.db.UserPersonaEntity
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.Credential
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.storage.ActivityRepository
import dev.aiharness.core.storage.ConversationRepository
import dev.aiharness.core.storage.MarkRepository
import dev.aiharness.core.storage.CredentialRepository
import dev.aiharness.core.storage.MemoryRepository
import dev.aiharness.core.storage.ModelProfileRepository
import dev.aiharness.core.storage.PersonaRepository
import dev.aiharness.core.storage.StickerRepository
import dev.aiharness.core.storage.UserPersonaRepository
import dev.aiharness.core.util.HarnessJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun <T> KSerializer<T>.encode(value: T): String = HarnessJson.encodeToString(this, value)
private fun <T> KSerializer<T>.decode(json: String): T = HarnessJson.decodeFromString(this, json)

class RoomPersonaRepository(private val dao: HarnessDao) : PersonaRepository {
    private val s = Persona.serializer()
    override fun observeAll(): Flow<List<Persona>> = dao.observePersonas().map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun getAll() = dao.personas().map { s.decode(it.json) }
    override suspend fun get(id: String) = dao.persona(id)?.let { s.decode(it.json) }
    override suspend fun upsert(persona: Persona) = dao.upsertPersona(PersonaEntity(persona.id, persona.name, s.encode(persona)))
    override suspend fun delete(id: String) = dao.deletePersona(id)
}

class RoomUserPersonaRepository(private val dao: HarnessDao) : UserPersonaRepository {
    private val s = UserPersona.serializer()
    override fun observeAll(): Flow<List<UserPersona>> = dao.observeUserPersonas().map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun get(id: String) = dao.userPersona(id)?.let { s.decode(it.json) }
    override suspend fun upsert(persona: UserPersona) = dao.upsertUserPersona(UserPersonaEntity(persona.id, persona.name, s.encode(persona)))
    override suspend fun delete(id: String) = dao.deleteUserPersona(id)
}

class RoomModelProfileRepository(private val dao: HarnessDao) : ModelProfileRepository {
    private val s = ModelProfile.serializer()
    override fun observeAll(): Flow<List<ModelProfile>> = dao.observeProfiles().map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun getAll() = dao.profiles().map { s.decode(it.json) }
    override suspend fun get(id: String) = dao.profile(id)?.let { s.decode(it.json) }
    override suspend fun upsert(profile: ModelProfile) = dao.upsertProfile(ModelProfileEntity(profile.id, profile.name, s.encode(profile)))
    override suspend fun delete(id: String) = dao.deleteProfile(id)
}

class RoomCredentialRepository(private val dao: HarnessDao) : CredentialRepository {
    private val s = Credential.serializer()
    override fun observeAll(): Flow<List<Credential>> = dao.observeCredentials().map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun get(id: String) = dao.credential(id)?.let { s.decode(it.json) }
    override suspend fun upsert(credential: Credential) =
        dao.upsertCredential(CredentialEntity(credential.id, credential.label, s.encode(credential)))
    override suspend fun delete(id: String) = dao.deleteCredential(id)
}

class RoomConversationRepository(private val dao: HarnessDao) : ConversationRepository {
    private val c = Conversation.serializer()
    private val m = ChatMessage.serializer()

    override fun observeAll(): Flow<List<Conversation>> = dao.observeConversations().map { rows -> rows.map { c.decode(it.json) } }
    override fun observe(id: String): Flow<Conversation?> = dao.observeConversation(id).map { row -> row?.let { c.decode(it.json) } }
    override suspend fun get(id: String) = dao.conversation(id)?.let { c.decode(it.json) }
    override suspend fun getAll() = dao.conversations().map { c.decode(it.json) }
    override suspend fun upsert(conversation: Conversation) =
        dao.upsertConversation(ConversationEntity(conversation.id, conversation.personaId, conversation.updatedAt, c.encode(conversation)))

    override suspend fun delete(id: String) {
        dao.deleteStickersFor(id)
        dao.deleteMarksFor(id)
        dao.deleteMessagesFor(id)
        dao.deleteConversation(id)
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { rows -> rows.map { m.decode(it.json) } }

    override suspend fun getMessages(conversationId: String) = dao.messages(conversationId).map { m.decode(it.json) }
    override suspend fun appendMessage(message: ChatMessage) = dao.upsertMessage(message.toEntity())
    override suspend fun updateMessage(message: ChatMessage) = dao.upsertMessage(message.toEntity())
    override suspend fun deleteMessages(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dao.deleteStickersOn(ids.toList())
        dao.deleteMessages(ids.toList())
    }

    private fun ChatMessage.toEntity() = MessageEntity(id, conversationId, createdAt, m.encode(this))
}

class RoomMemoryRepository(private val dao: HarnessDao) : MemoryRepository {
    private val s = MemoryEntry.serializer()

    override fun observeForPersona(personaId: String): Flow<List<MemoryEntry>> =
        dao.observeMemories(personaId).map { rows -> rows.map { s.decode(it.json) } }

    override suspend fun listForPersona(personaId: String) = dao.memories(personaId).map { s.decode(it.json) }
    override suspend fun get(id: String) = dao.memory(id)?.let { s.decode(it.json) }
    override suspend fun upsert(entry: MemoryEntry) = dao.upsertMemory(entry.toEntity())

    override suspend fun delete(id: String) {
        dao.deleteVectors(id)
        dao.deleteMemory(id)
    }

    override suspend fun markAccessed(ids: Collection<String>, at: Long) {
        if (ids.isEmpty()) return
        val updated = dao.memoriesById(ids.toList()).map { row ->
            val entry = s.decode(row.json)
            entry.copy(lastAccessedAt = at, accessCount = entry.accessCount + 1).toEntity()
        }
        dao.upsertMemories(updated)
    }

    override suspend fun markUsed(ids: Collection<String>, at: Long) {
        if (ids.isEmpty()) return
        val updated = dao.memoriesById(ids.toList()).map { row ->
            val entry = s.decode(row.json)
            entry.copy(lastUsedAt = at, useCount = entry.useCount + 1, lastAccessedAt = at, accessCount = entry.accessCount + 1).toEntity()
        }
        dao.upsertMemories(updated)
    }

    override suspend fun getVectors(personaId: String, model: String): Map<String, FloatArray> =
        dao.vectors(personaId, model).associate { it.memoryId to it.vector.toFloatArray() }

    override suspend fun putVector(memoryId: String, personaId: String, model: String, vector: FloatArray) =
        dao.upsertVector(MemoryVectorEntity(memoryId, model, personaId, vector.toBytes()))

    private fun MemoryEntry.toEntity() = MemoryEntity(id, personaId, updatedAt, s.encode(this))

    private fun FloatArray.toBytes(): ByteArray =
        ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(this@toBytes) }.array()

    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}

class RoomActivityRepository(private val dao: HarnessDao) : ActivityRepository {
    private val s = ActivityEntry.serializer()
    override fun observeSince(since: Long): Flow<List<ActivityEntry>> =
        dao.observeActivity(since).map { rows -> rows.map { s.decode(it.json) } }

    override suspend fun forPersona(personaId: String, since: Long): List<ActivityEntry> =
        dao.activitySince(since).map { s.decode(it.json) }.filter { it.personaId == personaId }

    override suspend fun add(entry: ActivityEntry) = dao.upsertActivity(ActivityEntity(entry.id, entry.createdAt, s.encode(entry)))
    override suspend fun deleteBefore(time: Long) = dao.deleteActivityBefore(time)
}

class RoomStickerRepository(private val dao: HarnessDao) : StickerRepository {
    private val s = Sticker.serializer()
    private val a = StickerArt.serializer()

    override fun observeForConversation(conversationId: String): Flow<List<Sticker>> =
        dao.observeStickers(conversationId).map { rows -> rows.map { s.decode(it.json) } }

    override suspend fun forConversation(conversationId: String) = dao.stickers(conversationId).map { s.decode(it.json) }
    override suspend fun get(id: String) = dao.sticker(id)?.let { s.decode(it.json) }
    override suspend fun changedSince(conversationId: String, since: Long) =
        dao.stickersChangedSince(conversationId, since).map { s.decode(it.json) }

    override suspend fun upsert(sticker: Sticker) = dao.upsertSticker(
        StickerEntity(sticker.id, sticker.conversationId, sticker.target.kind.name, sticker.target.id, sticker.updatedAt, sticker.peeledAt, s.encode(sticker)),
    )

    override suspend fun delete(id: String) = dao.deleteSticker(id)
    override suspend fun deletePeeledBefore(time: Long) = dao.deletePeeledStickersBefore(time)

    override fun observeArt(): Flow<List<StickerArt>> = dao.observeStickerArt().map { rows -> rows.map { a.decode(it.json) } }
    override suspend fun art(ownerPersonaId: String?) = dao.stickerArt(ownerPersonaId).map { a.decode(it.json) }
    override suspend fun getArt(id: String) = dao.stickerArtById(id)?.let { a.decode(it.json) }
    override suspend fun upsertArt(art: StickerArt) = dao.upsertStickerArt(StickerArtEntity(art.id, art.ownerPersonaId, art.name, a.encode(art)))
    override suspend fun deleteArt(id: String) = dao.deleteStickerArt(id)
}

class RoomMarkRepository(private val dao: HarnessDao) : MarkRepository {
    private val s = AttachmentMark.serializer()
    override fun observeAll(): Flow<List<AttachmentMark>> = dao.observeMarks().map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun forAttachment(attachmentId: String) = dao.marksFor(attachmentId).map { s.decode(it.json) }
    override suspend fun add(mark: AttachmentMark) = dao.addMark(MarkEntity(mark.id, mark.attachmentId, mark.conversationId, s.encode(mark)))
    override suspend fun deleteForConversation(conversationId: String) = dao.deleteMarksFor(conversationId)
}

class RoomPlannerRepository(private val dao: HarnessDao) : dev.aiharness.core.storage.PlannerRepository {
    private val s = dev.aiharness.core.model.PlannerEntry.serializer()
    private fun entity(e: dev.aiharness.core.model.PlannerEntry) = dev.aiharness.app.data.db.PlannerEntryEntity(e.id, e.plannerId, e.byPersonaId, s.encode(e))
    override fun observe(plannerId: String) = dao.observePlanner(plannerId).map { rows -> rows.map { s.decode(it.json) } }
    override suspend fun forPlanner(plannerId: String) = dao.plannerEntries(plannerId).map { s.decode(it.json) }
    override suspend fun get(id: String) = dao.plannerEntry(id)?.let { s.decode(it.json) }
    override suspend fun upsert(entry: dev.aiharness.core.model.PlannerEntry) = dao.upsertPlannerEntry(entity(entry))
    // A plan's stickers go with it, and a planner's with the planner.
    override suspend fun delete(id: String) {
        dao.deleteStickersOnEntry(id)
        dao.deletePlannerEntry(id)
    }
    override suspend fun deletePlanner(plannerId: String) {
        dao.deleteStickersFor(dev.aiharness.core.model.StickerTarget.plannerConversation(plannerId))
        dao.deletePlanner(plannerId)
    }
    override suspend fun deleteWrittenBy(personaId: String) = dao.deletePlannerEntriesBy(personaId)
}
