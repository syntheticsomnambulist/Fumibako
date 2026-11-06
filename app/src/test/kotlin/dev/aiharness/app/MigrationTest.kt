package dev.aiharness.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomMarkRepository
import dev.aiharness.app.data.RoomPersonaRepository
import dev.aiharness.app.data.RoomStickerRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Each kept migration step, from a database built with the schema it starts from (exported to
 * app/schemas) to the current one, opened the way the app opens it. Room checks the migrated
 * schema against the current one as it opens, so a step that leaves out a column or an index fails
 * here. When a step is dropped from [HarnessDatabase.MIGRATIONS], its test goes too.
 *
 * (Room's MigrationTestHelper reads schemas from assets, which Robolectric doesn't see for unit
 * tests, so the old database is built from the exported JSON directly.)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Test
    fun versionTwoGainsStickersAndKeepsItsLetters() {
        context.deleteDatabase(name)
        createFromSchema(2) { db ->
            db.execSQL("INSERT INTO personas (id, name, json) VALUES ('p1', 'Aria', '$ARIA')")
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '$LETTER')")
        }

        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        try {
            runBlocking {
                assertEquals(listOf("Aria"), RoomPersonaRepository(database.dao()).getAll().map { it.name })
                assertEquals(listOf("m1"), RoomConversationRepository(database.dao()).getMessages("c1").map { it.id })
                val stickers = RoomStickerRepository(database.dao())
                val tada = Sticker("s1", "c1", StickerTarget.message("m1"), StickerRef.Emoji("🎉", "tada"), x = 0.8f, y = 0.6f, createdAt = 9)
                stickers.upsert(tada)
                assertEquals(listOf(tada), stickers.forConversation("c1"))
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 2 database migrates instead of starting fresh")
    }

    @Test
    fun versionThreeGainsMarksAndKeepsItsStickers() {
        context.deleteDatabase(name)
        createFromSchema(3) { db ->
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '$LETTER')")
            db.execSQL(
                "INSERT INTO stickers (id, conversationId, targetKind, targetId, updatedAt, peeledAt, json) " +
                    "VALUES ('s1', 'c1', 'MESSAGE', 'm1', 9, NULL, '$TADA')",
            )
        }

        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        try {
            runBlocking {
                assertEquals(listOf("s1"), RoomStickerRepository(database.dao()).forConversation("c1").map { it.id })
                val marks = RoomMarkRepository(database.dao())
                val punched = AttachmentMark("song1", MarkKind.PUNCHED, conversationId = "c1", at = 12)
                marks.add(punched)
                marks.add(punched.copy(at = 99))
                assertEquals(listOf(punched), marks.forAttachment("song1"), "the first punch stays")
                RoomConversationRepository(database.dao()).delete("c1")
                assertEquals(emptyList(), marks.forAttachment("song1"))
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 3 database migrates instead of starting fresh")
    }

    @Test
    fun versionFourGainsThePlannerAndKeepsItsMarks() {
        context.deleteDatabase(name)
        createFromSchema(4) { db ->
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '$LETTER')")
            db.execSQL("INSERT INTO marks (id, attachmentId, conversationId, json) VALUES ('song1:PUNCHED:', 'song1', 'c1', '$PUNCHED')")
        }

        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        try {
            runBlocking {
                assertEquals(listOf("song1"), RoomMarkRepository(database.dao()).forAttachment("song1").map { it.attachmentId })
                val planner = dev.aiharness.app.data.RoomPlannerRepository(database.dao())
                val work = dev.aiharness.core.model.PlannerEntry(
                    "w1", "u1", null, dev.aiharness.core.model.EntryKind.ROUTINE, "Work",
                    weekly = dev.aiharness.core.model.Weekly(listOf(1, 2, 3, 4, 5), 540, 1050), routine = dev.aiharness.core.model.RoutineKind.WORK,
                    createdAt = 1, updatedAt = 1,
                )
                planner.upsert(work)
                assertEquals(listOf(work), planner.forPlanner("u1"))
                assertEquals(emptyList(), planner.forPlanner("u2"), "each of your personas keeps a planner of its own")
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 4 database migrates instead of starting fresh")
    }

    /** Builds the database as Room would have at [version], from its exported schema, then lets [fill] add rows. */
    private fun createFromSchema(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/${HarnessDatabase::class.java.name}/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.version = version
            fill(db)
        }
    }

    private companion object {
        // Rows as v0.2.2 wrote them (JSON, with only the fields that have no defaults).
        const val ARIA = """{"id":"p1","name":"Aria","createdAt":1,"updatedAt":1}"""
        const val TADA = """{"id":"s1","conversationId":"c1","target":{"kind":"MESSAGE","id":"m1"},"ref":{"type":"emoji","emoji":"🎉","name":"tada"},"x":0.8,"y":0.6,"createdAt":9}"""
        const val PUNCHED = """{"attachmentId":"song1","kind":"PUNCHED","conversationId":"c1","at":12}"""
        const val LETTER = """{"id":"m1","conversationId":"c1","role":"USER","parts":[{"type":"text","text":"Hello"}],"createdAt":5}"""
    }
}
