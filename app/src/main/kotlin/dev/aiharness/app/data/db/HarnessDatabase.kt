package dev.aiharness.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "activity", indices = [Index("createdAt")])
data class ActivityEntity(@PrimaryKey val id: String, val createdAt: Long, val json: String)

/** A sticker on a letter, a draft or (later) a planner day. Peeled ones stay until the persona has heard. */
@Entity(tableName = "stickers", indices = [Index("conversationId"), Index("targetKind", "targetId")])
data class StickerEntity(
    @PrimaryKey val id: String,
    val conversationId: String?,
    val targetKind: String,
    val targetId: String,
    val updatedAt: Long,
    val peeledAt: Long?,
    val json: String,
)

/** A persona's own sticker (ownerPersonaId set) or one from the shared library (null). */
@Entity(tableName = "sticker_art", indices = [Index("ownerPersonaId")])
data class StickerArtEntity(@PrimaryKey val id: String, val ownerPersonaId: String?, val name: String, val json: String)

/** A mark on an attachment (a punched ticket, a read stamp), kept apart from its message. */
@Entity(tableName = "marks", indices = [Index("attachmentId"), Index("conversationId")])
data class MarkEntity(@PrimaryKey val id: String, val attachmentId: String, val conversationId: String?, val json: String)

/** An entry in one of your personas' planners (a plan, your typical week, a persona's note). */
@Entity(tableName = "planner_entries", indices = [Index("plannerId")])
data class PlannerEntryEntity(@PrimaryKey val id: String, val plannerId: String, val byPersonaId: String?, val json: String)

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

    // Background activity log
    @Query("SELECT * FROM activity WHERE createdAt > :since ORDER BY createdAt")
    fun observeActivity(since: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE createdAt > :since ORDER BY createdAt")
    suspend fun activitySince(since: Long): List<ActivityEntity>

    @Upsert suspend fun upsertActivity(entity: ActivityEntity)
    @Query("DELETE FROM activity WHERE createdAt < :time") suspend fun deleteActivityBefore(time: Long)

    @Upsert suspend fun upsertVector(entity: MemoryVectorEntity)
    @Query("DELETE FROM memory_vectors WHERE memoryId = :memoryId") suspend fun deleteVectors(memoryId: String)

    // Stickers (rowid order is the order they were stuck: an upsert keeps the row)
    @Query("SELECT * FROM stickers WHERE conversationId = :conversationId ORDER BY rowid")
    fun observeStickers(conversationId: String): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE conversationId = :conversationId ORDER BY rowid")
    suspend fun stickers(conversationId: String): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id") suspend fun sticker(id: String): StickerEntity?

    @Query("SELECT * FROM stickers WHERE conversationId = :conversationId AND updatedAt > :since ORDER BY updatedAt, rowid")
    suspend fun stickersChangedSince(conversationId: String, since: Long): List<StickerEntity>

    @Upsert suspend fun upsertSticker(entity: StickerEntity)
    @Query("DELETE FROM stickers WHERE id = :id") suspend fun deleteSticker(id: String)
    @Query("DELETE FROM stickers WHERE peeledAt IS NOT NULL AND updatedAt < :time") suspend fun deletePeeledStickersBefore(time: Long)
    @Query("DELETE FROM stickers WHERE conversationId = :conversationId") suspend fun deleteStickersFor(conversationId: String)
    @Query("DELETE FROM stickers WHERE targetKind = 'MESSAGE' AND targetId IN (:messageIds)") suspend fun deleteStickersOn(messageIds: List<String>)
    @Query("DELETE FROM stickers WHERE targetKind = 'ENTRY' AND targetId = :entryId") suspend fun deleteStickersOnEntry(entryId: String)

    // Sticker sheets and the shared library
    @Query("SELECT * FROM sticker_art ORDER BY rowid") fun observeStickerArt(): Flow<List<StickerArtEntity>>
    @Query("SELECT * FROM sticker_art WHERE ownerPersonaId IS :ownerPersonaId ORDER BY rowid") suspend fun stickerArt(ownerPersonaId: String?): List<StickerArtEntity>
    @Query("SELECT * FROM sticker_art WHERE id = :id") suspend fun stickerArtById(id: String): StickerArtEntity?
    @Upsert suspend fun upsertStickerArt(entity: StickerArtEntity)
    @Query("DELETE FROM sticker_art WHERE id = :id") suspend fun deleteStickerArt(id: String)

    // Marks on attachments
    @Query("SELECT * FROM marks ORDER BY rowid") fun observeMarks(): Flow<List<MarkEntity>>
    @Query("SELECT * FROM marks WHERE attachmentId = :attachmentId ORDER BY rowid") suspend fun marksFor(attachmentId: String): List<MarkEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMark(entity: MarkEntity)
    @Query("DELETE FROM marks WHERE conversationId = :conversationId") suspend fun deleteMarksFor(conversationId: String)

    // The planner
    @Query("SELECT * FROM planner_entries WHERE plannerId = :plannerId ORDER BY rowid") fun observePlanner(plannerId: String): Flow<List<PlannerEntryEntity>>
    @Query("SELECT * FROM planner_entries WHERE plannerId = :plannerId ORDER BY rowid") suspend fun plannerEntries(plannerId: String): List<PlannerEntryEntity>
    @Query("SELECT * FROM planner_entries WHERE id = :id") suspend fun plannerEntry(id: String): PlannerEntryEntity?
    @Upsert suspend fun upsertPlannerEntry(entity: PlannerEntryEntity)
    @Query("DELETE FROM planner_entries WHERE id = :id") suspend fun deletePlannerEntry(id: String)
    @Query("DELETE FROM planner_entries WHERE plannerId = :plannerId") suspend fun deletePlanner(plannerId: String)
    @Query("DELETE FROM planner_entries WHERE byPersonaId = :personaId") suspend fun deletePlannerEntriesBy(personaId: String)
    @Query("SELECT * FROM planner_entries WHERE byPersonaId = :personaId ORDER BY rowid") suspend fun plannerEntriesBy(personaId: String): List<PlannerEntryEntity>
    @Query("SELECT * FROM planner_entries WHERE byPersonaId IS NOT NULL ORDER BY rowid") fun observePersonasEntries(): Flow<List<PlannerEntryEntity>>
}

@Database(
    entities = [
        PersonaEntity::class, UserPersonaEntity::class, ModelProfileEntity::class, CredentialEntity::class,
        ConversationEntity::class, MessageEntity::class, MemoryEntity::class, MemoryVectorEntity::class,
        ActivityEntity::class, StickerEntity::class, StickerArtEntity::class, MarkEntity::class,
        PlannerEntryEntity::class,
    ],
    // A schema change bumps the version and adds a step to MIGRATIONS (keeping the last three, and
    // any a tester's phone still needs: see MIGRATIONS);
    // field changes on domain models need neither, since rows are JSON. Schemas are exported to
    // app/schemas for the migration tests.
    version = 5,
    exportSchema = true,
)
abstract class HarnessDatabase : RoomDatabase() {
    abstract fun dao(): HarnessDao

    companion object {
        /**
         * One step per schema change, oldest first. The last three are kept: a phone on an older
         * database has no path forward and starts fresh (the fallback below). But a step a tester's
         * phone still needs is never dropped, even past three: friends were last given v0.2.2
         * (database version 2), so every step from 2→3 stays until they have the next tester build
         * (see CLAUDE.md). When a step can go, drop its test too, and its exported schema once no
         * test uses it.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(Migration2To3, Migration3To4, Migration4To5)

        /** [onWiped] runs, while the database opens, when it had to start fresh instead of migrating. */
        fun create(context: Context, onWiped: () -> Unit = {}, name: String = "harness.db"): HarnessDatabase =
            Room.databaseBuilder(context, HarnessDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                // For databases older than MIGRATIONS covers, and downgrades. (Don't add
                // fallbackToDestructiveMigrationOnDowngrade: it switches upgrades back to requiring a migration.)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(
                    object : Callback() {
                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) = onWiped()
                    },
                )
                .build()
    }
}

/** v0.3: stickers, and the pictures they can be. Two new tables; nothing else changes. */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stickers` (`id` TEXT NOT NULL, `conversationId` TEXT, `targetKind` TEXT NOT NULL, " +
                "`targetId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `peeledAt` INTEGER, `json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_conversationId` ON `stickers` (`conversationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_targetKind_targetId` ON `stickers` (`targetKind`, `targetId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sticker_art` (`id` TEXT NOT NULL, `ownerPersonaId` TEXT, `name` TEXT NOT NULL, " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sticker_art_ownerPersonaId` ON `sticker_art` (`ownerPersonaId`)")
    }
}

/** v0.3.5: marks on attachments (punched tickets, read stamps). One new table; nothing else changes. */
object Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `marks` (`id` TEXT NOT NULL, `attachmentId` TEXT NOT NULL, `conversationId` TEXT, " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_marks_attachmentId` ON `marks` (`attachmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_marks_conversationId` ON `marks` (`conversationId`)")
    }
}

/** v0.4: the planner's entries. */
object Migration4To5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planner_entries` (`id` TEXT NOT NULL, `plannerId` TEXT NOT NULL, `byPersonaId` TEXT, " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planner_entries_plannerId` ON `planner_entries` (`plannerId`)")
    }
}
