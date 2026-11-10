package dev.aiharness.app.data

import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.app.data.db.PlatformContext
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.storage.inmemory.InMemorySecretStore
import dev.aiharness.core.storage.inmemory.InMemorySettingsRepository
import dev.aiharness.core.transfer.BackupRequest
import dev.aiharness.core.transfer.Backups
import dev.aiharness.core.util.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backups on the desktop's SQLite (v0.5): restoring is all or nothing, and files come back under their own names. */
class BackupsRoomTest {
    private val root = Files.createTempDirectory("fumibako-backups").toFile()

    /** One install: its own database and files. */
    private inner class Install(name: String) {
        private val context = object : PlatformContext() {
            override val filesDir: File = File(root, name).apply { mkdirs() }
        }
        val database = HarnessDatabase.create(context, name = "$name.db")
        val dao = database.dao()
        val blobs = FileBlobStore(File(root, "$name/blobs"))
        val personas = RoomPersonaRepository(dao)
        val conversations = RoomConversationRepository(dao)
        val transactor = RoomTransactor(database)
        val backups = Backups(
            personas, RoomUserPersonaRepository(dao), RoomModelProfileRepository(dao), RoomCredentialRepository(dao), InMemorySecretStore(),
            conversations, RoomMemoryRepository(dao), RoomStickerRepository(dao), RoomMarkRepository(dao), RoomPlannerRepository(dao),
            RoomActivityRepository(dao), InMemorySettingsRepository(AppSettings()), blobs, transactor, SystemClock,
        )
    }

    private val installs = mutableListOf<Install>()
    private fun install(name: String) = Install(name).also { installs += it }

    @After
    fun close() {
        installs.forEach { it.database.close() }
        root.deleteRecursively()
    }

    @Test
    fun aTransactionThatFailsLeavesNothingBehind() = runBlocking {
        val here = install("here")
        here.personas.upsert(Persona(id = "kit", name = "Kit", createdAt = 1, updatedAt = 1))
        assertFailsWith<IllegalStateException> {
            here.transactor.inTransaction {
                here.personas.delete("kit")
                here.personas.upsert(Persona(id = "aria", name = "Aria", createdAt = 1, updatedAt = 1))
                assertNotNull(here.personas.get("aria"), "inside, the change is seen")
                error("the disk filled up")
            }
        }
        assertNull(here.personas.get("aria"))
        assertEquals("Kit", here.personas.get("kit")?.name, "Kit is still here")

        here.transactor.inTransaction { here.personas.upsert(Persona(id = "aria", name = "Aria", createdAt = 1, updatedAt = 1)) }
        assertEquals("Aria", here.personas.get("aria")?.name, "and one that works stays")
    }

    @Test
    fun everythingComesBackOnAnotherInstallWithItsFilesAndCanBeSearched() = runBlocking {
        val first = install("first")
        val photo = first.blobs.put(byteArrayOf(1, 2, 3, 4), "jpg")
        first.personas.upsert(Persona(id = "aria", name = "Aria", avatarRef = photo, createdAt = 1, updatedAt = 1))
        first.conversations.upsert(Conversation(id = "thread", personaId = "aria", title = "Aria", createdAt = 1, updatedAt = 1))
        first.conversations.appendMessage(
            ChatMessage(id = "note", conversationId = "thread", role = MessageRole.USER, parts = listOf(ContentPart.Text("The genmaicha arrived"), ContentPart.Image(photo, "image/jpeg")), createdAt = 2),
        )
        val file = File(root, "everything.fumibako")
        file.outputStream().use { first.backups.write(BackupRequest.Everything(), it, app = "0.5.0") }

        val second = install("second")
        second.personas.upsert(Persona(id = "kit", name = "Kit", createdAt = 1, updatedAt = 1))
        second.backups.restoreEverything(file)

        assertEquals(listOf("Aria"), second.personas.getAll().map { it.name })
        val avatar = assertNotNull(second.personas.get("aria")?.avatarRef)
        assertEquals(photo, avatar, "the file came back under its own name")
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(second.blobs.get(avatar)))
        val found = second.conversations.searchLetters(listOf("genmaicha"), matchAll = true, personaId = "aria", limit = 5)
        assertEquals(listOf("note"), found.map { it.id }, "restored letters are in the word index")
    }
}
