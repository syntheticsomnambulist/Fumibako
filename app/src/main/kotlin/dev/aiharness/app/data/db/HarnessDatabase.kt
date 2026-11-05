package dev.aiharness.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Rows store the full domain object as JSON next to a few indexed columns. New fields on the
 * domain models (with defaults) therefore need no schema migration, which keeps the harness easy
 * to extend.
 */

@Entity(tableName = "personas")
data class PersonaEntity(@PrimaryKey val id: String, val name: String, val json: String)

@Entity(tableName = "user_personas")
data class UserPersonaEntity(@PrimaryKey val id: String, val name: String, val json: String)

@Entity(tableName = "model_profiles")
data class ModelProfileEntity(@PrimaryKey val id: String, val name: String, val json: String)

@Entity(tableName = "credentials")
data class CredentialEntity(@PrimaryKey val id: String, val label: String, val json: String)

@Entity(tableName = "conversations", indices = [Index("personaId")])
data class ConversationEntity(@PrimaryKey val id: String, val personaId: String, val updatedAt: Long, val json: String)

@Entity(tableName = "messages", indices = [Index("conversationId", "createdAt")])
data class MessageEntity(@PrimaryKey val id: String, val conversationId: String, val createdAt: Long, val json: String)

@Entity(tableName = "memories", indices = [Index("personaId")])
data class MemoryEntity(@PrimaryKey val id: String, val personaId: String, val updatedAt: Long, val json: String)

@Entity(tableName = "memory_vectors", primaryKeys = ["memoryId", "model"], indices = [Index("personaId")])
data class MemoryVectorEntity(val memoryId: String, val model: String, val personaId: String, val vector: ByteArray)

@Dao
interface HarnessDao {
    // Personas
    @Query("SELECT * FROM personas ORDER BY name COLLATE NOCASE") fun observePersonas(): Flow<List<PersonaEntity>>
    @Query("SELECT * FROM personas") suspend fun personas(): List<PersonaEntity>
    @Query("SELECT * FROM personas WHERE id = :id") suspend fun persona(id: String): PersonaEntity?
    @Upsert suspend fun upsertPersona(entity: PersonaEntity)
    @Query("DELETE FROM personas WHERE id = :id") suspend fun deletePersona(id: String)

    // User personas
    @Query("SELECT * FROM user_personas ORDER BY name COLLATE NOCASE") fun observeUserPersonas(): Flow<List<UserPersonaEntity>>
    @Query("SELECT * FROM user_personas WHERE id = :id") suspend fun userPersona(id: String): UserPersonaEntity?
    @Upsert suspend fun upsertUserPersona(entity: UserPersonaEntity)
    @Query("DELETE FROM user_personas WHERE id = :id") suspend fun deleteUserPersona(id: String)

    // Model profiles
    @Query("SELECT * FROM model_profiles ORDER BY name COLLATE NOCASE") fun observeProfiles(): Flow<List<ModelProfileEntity>>
    @Query("SELECT * FROM model_profiles") suspend fun profiles(): List<ModelProfileEntity>
    @Query("SELECT * FROM model_profiles WHERE id = :id") suspend fun profile(id: String): ModelProfileEntity?
    @Upsert suspend fun upsertProfile(entity: ModelProfileEntity)
    @Query("DELETE FROM model_profiles WHERE id = :id") suspend fun deleteProfile(id: String)

    // Credentials
    @Query("SELECT * FROM credentials ORDER BY label COLLATE NOCASE") fun observeCredentials(): Flow<List<CredentialEntity>>
    @Query("SELECT * FROM credentials WHERE id = :id") suspend fun credential(id: String): CredentialEntity?
    @Upsert suspend fun upsertCredential(entity: CredentialEntity)
    @Query("DELETE FROM credentials WHERE id = :id") suspend fun deleteCredential(id: String)

    // Conversations
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun observeConversations(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id") fun observeConversation(id: String): Flow<ConversationEntity?>
    @Query("SELECT * FROM conversations") suspend fun conversations(): List<ConversationEntity>
    @Query("SELECT * FROM conversations WHERE id = :id") suspend fun conversation(id: String): ConversationEntity?
    @Upsert suspend fun upsertConversation(entity: ConversationEntity)
    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: String)

    // Messages (rowid breaks ties between messages created in the same millisecond)
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, rowid")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, rowid")
    suspend fun messages(conversationId: String): List<MessageEntity>

    @Upsert suspend fun upsertMessage(entity: MessageEntity)
    @Query("DELETE FROM messages WHERE id IN (:ids)") suspend fun deleteMessages(ids: List<String>)
    @Query("DELETE FROM messages WHERE conversationId = :conversationId") suspend fun deleteMessagesFor(conversationId: String)

    // Memories
    @Query("SELECT * FROM memories WHERE personaId = :personaId ORDER BY updatedAt DESC")
    fun observeMemories(personaId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE personaId = :personaId") suspend fun memories(personaId: String): List<MemoryEntity>
    @Query("SELECT * FROM memories WHERE id = :id") suspend fun memory(id: String): MemoryEntity?
    @Query("SELECT * FROM memories WHERE id IN (:ids)") suspend fun memoriesById(ids: List<String>): List<MemoryEntity>
    @Upsert suspend fun upsertMemory(entity: MemoryEntity)
    @Upsert suspend fun upsertMemories(entities: List<MemoryEntity>)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: String)
    @Query("DELETE FROM memories WHERE personaId = :personaId") suspend fun deleteMemoriesFor(personaId: String)

    @Query("SELECT * FROM memory_vectors WHERE personaId = :personaId AND model = :model")
    suspend fun vectors(personaId: String, model: String): List<MemoryVectorEntity>

    @Upsert suspend fun upsertVector(entity: MemoryVectorEntity)
    @Query("DELETE FROM memory_vectors WHERE memoryId = :memoryId") suspend fun deleteVectors(memoryId: String)
}

@Database(
    entities = [
        PersonaEntity::class, UserPersonaEntity::class, ModelProfileEntity::class, CredentialEntity::class,
        ConversationEntity::class, MessageEntity::class, MemoryEntity::class, MemoryVectorEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HarnessDatabase : RoomDatabase() {
    abstract fun dao(): HarnessDao

    companion object {
        fun create(context: Context): HarnessDatabase =
            Room.databaseBuilder(context, HarnessDatabase::class.java, "harness.db").build()
    }
}
