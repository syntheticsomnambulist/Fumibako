package dev.aiharness.app.data

import androidx.room.useWriterConnection
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.app.data.db.PlatformContext
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.StickerArt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sweep on the desktop's SQLite: what counts as unused, and that clearing deletes only that. */
class BlobSweepTest {
    private val root = Files.createTempDirectory("fumibako-sweep").toFile()
    private val context = object : PlatformContext() {
        override val filesDir: File = root
    }
    private val database = HarnessDatabase.create(context, name = "sweep-test.db")
    private val blobs = FileBlobStore(File(root, "blobs"))
    private val letters = RoomConversationRepository(database.dao())

    @After
    fun close() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun onlyWhatNothingRefersToIsUnused() = runBlocking {
        val photo = put(10, "jpg")
        val keptVideo = put(20, "mp4")
        val avatar = put(30, "jpg")
        val sticker = put(40, "png")
        val inDraft = put(50, "m4a")
        val inLaterTable = put(60, "pdf")
        val unused = put(70, "mp4")
        val unusedButNew = put(80, "jpg", old = false)
        val notARef = File(root, "blobs/notes.txt").apply { writeBytes(ByteArray(90)); setLastModified(longAgo()) }

        letters.appendMessage(letter("m1", ContentPart.Text("a walk"), ContentPart.Image(photo, "image/jpeg")))
        RoomMemoryRepository(database.dao()).upsert(memory(keepsakeOf = keptVideo))
        RoomPersonaRepository(database.dao()).upsert(Persona(id = "p1", name = "Aria", avatarRef = avatar, createdAt = 1, updatedAt = 1))
        RoomStickerRepository(database.dao()).upsertArt(StickerArt(id = "s1", name = "aria-wave", ownerPersonaId = "p1", imageRef = sticker, createdAt = 1))
        // A table added later (by a migration this sweep knows nothing about), with a ref followed by a full stop.
        database.useWriterConnection { c ->
            c.usePrepared("CREATE TABLE later (id TEXT NOT NULL, note TEXT)") { it.step() }
            c.usePrepared("INSERT INTO later VALUES ('l1', 'see $inLaterTable.')") { it.step() }
        }
        val draft = """{"text":"","waiting":[{"attachment":{"id":"a2","kind":"SONG","ref":"$inDraft"}}]}"""
        val sweep = BlobSweep(database, blobs, elsewhere = { listOf(draft) })

        val looked = sweep.look()
        assertEquals(1, looked.unusedFiles)
        assertEquals(70, looked.unusedBytes)
        assertEquals(9, looked.files)
        assertEquals(450, looked.bytes)
        assertEquals(20 + 70L, looked.videos)
        assertEquals(50L, looked.songs)
        assertEquals(10 + 30 + 40 + 80L, looked.pictures)
        assertEquals(60 + 90L, looked.other)
        assertTrue(fileOf(unused).exists(), "looking deletes nothing")

        val cleared = sweep.clear()
        assertFalse(fileOf(unused).exists())
        listOf(photo, keptVideo, avatar, sticker, inDraft, inLaterTable, unusedButNew).forEach { assertTrue(fileOf(it).exists(), it) }
        assertTrue(notARef.exists(), "a file not named like a ref is left alone")
        assertEquals(0, cleared.unusedFiles)
        assertEquals(8, cleared.files)
    }

    @Test
    fun aDeletedLettersFilesAreUnusedExceptWhatAMemoryKept() = runBlocking {
        val photo = put(10, "jpg")
        val video = put(20, "mp4")
        val frame = put(5, "jpg")
        letters.appendMessage(
            letter(
                "m1",
                ContentPart.Image(photo, "image/jpeg"),
                ContentPart.Attachment("a1", AttachmentKind.VIDEO, ref = video, frameRefs = listOf(frame), thumbRef = frame),
            ),
        )
        RoomMemoryRepository(database.dao()).upsert(memory(keepsakeOf = video))
        val sweep = BlobSweep(database, blobs, elsewhere = { emptyList() })
        assertEquals(0, sweep.look().unusedFiles)

        letters.delete("c1")
        val looked = sweep.look()
        // The photo and the frame went with the letter; the video is still the memory's keepsake.
        assertEquals(2, looked.unusedFiles)
        assertEquals(15, looked.unusedBytes)
        sweep.clear()
        assertTrue(fileOf(video).exists())
        assertFalse(fileOf(photo).exists())
        assertFalse(fileOf(frame).exists())
    }

    private suspend fun put(size: Int, extension: String, old: Boolean = true): String =
        blobs.put(ByteArray(size), extension).also { if (old) fileOf(it).setLastModified(longAgo()) }

    private fun fileOf(ref: String) = blobs.fileFor(ref)!!

    private fun longAgo() = System.currentTimeMillis() - 2 * BlobSweep.GRACE_MS

    private fun letter(id: String, vararg parts: ContentPart) = ChatMessage(id, "c1", MessageRole.USER, parts.toList(), createdAt = 5)

    private fun memory(keepsakeOf: String) = MemoryEntry(
        id = "mem1",
        personaId = "p1",
        content = "the walk by the river",
        createdAt = 1,
        updatedAt = 1,
        lastAccessedAt = 1,
        keepsakes = listOf(Keepsake("m1", ContentPart.Attachment("a1", AttachmentKind.VIDEO, ref = keepsakeOf), keptAt = 1)),
    )
}
