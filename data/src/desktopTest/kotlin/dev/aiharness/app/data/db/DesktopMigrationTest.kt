package dev.aiharness.app.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomMarkRepository
import dev.aiharness.app.data.RoomPersonaRepository
import dev.aiharness.app.data.RoomPlannerRepository
import dev.aiharness.app.data.RoomStickerRepository
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.Weekly
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The kept migration steps on the desktop's SQLite (bundled with the app), as the phone's
 * MigrationTest checks them on Android's: each old database is built from its exported schema
 * (data/schemas), filled with rows as that version wrote them, and opened the way the desktop opens
 * it. Room checks the migrated schema against the current one as it opens. When a step is dropped
 * from [HarnessDatabase.MIGRATIONS], its test goes here and in the phone's MigrationTest.
 */
class DesktopMigrationTest {
    private val context = object : PlatformContext() {
        override val filesDir: File = Files.createTempDirectory("fumibako-migration").toFile()
    }
    private val name = "migration-test.db"

    @Test
    fun versionTwoGainsStickersAndKeepsItsLetters() {
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
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 3 database migrates instead of starting fresh")
    }

    @Test
    fun versionFourGainsThePlannerAndKeepsItsMarks() {
        createFromSchema(4) { db ->
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '$LETTER')")
            db.execSQL("INSERT INTO marks (id, attachmentId, conversationId, json) VALUES ('song1:PUNCHED:', 'song1', 'c1', '$PUNCHED')")
        }
        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        try {
            runBlocking {
                assertEquals(listOf("song1"), RoomMarkRepository(database.dao()).forAttachment("song1").map { it.attachmentId })
                val planner = RoomPlannerRepository(database.dao())
                val work = PlannerEntry(
                    "w1", "u1", null, EntryKind.ROUTINE, "Work",
                    weekly = Weekly(listOf(1, 2, 3, 4, 5), 540, 1050), routine = RoutineKind.WORK,
                    createdAt = 1, updatedAt = 1,
                )
                planner.upsert(work)
                assertEquals(listOf(work), planner.forPlanner("u1"))
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 4 database migrates instead of starting fresh")
    }

    @Test
    fun anOlderSchemaIsWipedAndReported() {
        open { db ->
            db.execSQL("CREATE TABLE personas (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, json TEXT NOT NULL)")
            db.execSQL("INSERT INTO personas VALUES ('p1', 'Aria', '{}')")
            db.execSQL("PRAGMA user_version = 1")
        }
        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        val personas = runBlocking { RoomPersonaRepository(database.dao()).getAll() }
        database.close()
        assertTrue(wiped, "the wipe hook ran")
        assertTrue(personas.isEmpty(), "old rows are gone")
    }

    @Test
    fun versionFiveGainsTheWordIndexAndItsLettersCanBeFound() {
        createFromSchema(5) { db ->
            db.execSQL("INSERT INTO conversations (id, personaId, updatedAt, json) VALUES ('c1', 'p1', 1, '$CONVERSATION')")
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '$LETTER')")
            db.execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m2', 'c1', 6, '$REPLY')")
        }

        var wiped = false
        val database = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        try {
            runBlocking {
                val letters = RoomConversationRepository(database.dao())
                assertEquals(emptyList(), letters.searchLetters(listOf("hello"), matchAll = true, personaId = null, limit = 10), "not indexed yet")
                assertEquals(2, letters.indexLetters(), "the letters already there go into the index")
                assertEquals(0, letters.indexLetters(), "once")
                assertEquals(listOf("m1"), letters.searchLetters(listOf("hel"), matchAll = true, personaId = "p1", limit = 10).map { it.id })
                assertEquals(emptyList(), letters.searchLetters(listOf("hello"), matchAll = true, personaId = "p2", limit = 10))
                assertEquals(listOf("m2", "m1"), letters.searchLetters(listOf("hello", "cafe"), matchAll = false, personaId = null, limit = 10).map { it.id })

                // New letters are indexed as they're written; edited ones again; deleted ones leave it.
                val note = dev.aiharness.core.model.ChatMessage(
                    "m3", "c1", dev.aiharness.core.model.MessageRole.USER,
                    listOf(dev.aiharness.core.model.ContentPart.Text("Kinako on toast")), createdAt = 7,
                )
                letters.appendMessage(note)
                assertEquals(listOf("m3"), letters.searchLetters(listOf("kinako"), matchAll = true, personaId = null, limit = 10).map { it.id })
                letters.updateMessage(note.copy(parts = listOf(dev.aiharness.core.model.ContentPart.Text("Honey on toast"))))
                assertEquals(emptyList(), letters.searchLetters(listOf("kinako"), matchAll = true, personaId = null, limit = 10))
                assertEquals(listOf("m3"), letters.searchLetters(listOf("honey"), matchAll = true, personaId = null, limit = 10).map { it.id })
                letters.deleteMessages(listOf("m3"))
                assertEquals(emptyList(), letters.searchLetters(listOf("toast"), matchAll = true, personaId = null, limit = 10))
                letters.delete("c1")
                assertEquals(emptyList(), letters.searchLetters(listOf("hello"), matchAll = false, personaId = null, limit = 10))
                assertEquals(0, letters.indexLetters(), "nothing left behind")
            }
        } finally {
            database.close()
        }
        assertFalse(wiped, "a version 5 database migrates instead of starting fresh")
    }

    /** Builds the database as Room would have at [version], from its exported schema, then lets [fill] add rows. */
    private fun createFromSchema(version: Int, fill: (SQLiteConnection) -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/${HarnessDatabase::class.java.name}/$version.json").readText())
            .jsonObject.getValue("database").jsonObject
        open { db ->
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("PRAGMA user_version = $version")
            fill(db)
        }
    }

    private fun open(block: (SQLiteConnection) -> Unit) {
        val file = File(context.filesDir, name).apply { delete() }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    private companion object {
        // Rows as v0.2.2 wrote them (JSON, with only the fields that have no defaults).
        const val ARIA = """{"id":"p1","name":"Aria","createdAt":1,"updatedAt":1}"""
        const val TADA = """{"id":"s1","conversationId":"c1","target":{"kind":"MESSAGE","id":"m1"},"ref":{"type":"emoji","emoji":"🎉","name":"tada"},"x":0.8,"y":0.6,"createdAt":9}"""
        const val PUNCHED = """{"attachmentId":"song1","kind":"PUNCHED","conversationId":"c1","at":12}"""
        const val LETTER = """{"id":"m1","conversationId":"c1","role":"USER","parts":[{"type":"text","text":"Hello"}],"createdAt":5}"""
        const val REPLY = """{"id":"m2","conversationId":"c1","role":"ASSISTANT","parts":[{"type":"text","text":"Greetings from the café! [mood: sunny]"}],"createdAt":6}"""
        const val CONVERSATION = """{"id":"c1","personaId":"p1","title":"Aria","createdAt":1,"updatedAt":1}"""
    }
}
