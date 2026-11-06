package dev.aiharness.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.data.RoomActivityRepository
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomMemoryRepository
import dev.aiharness.app.data.RoomStickerRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ContextState
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoomRepositoriesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HarnessDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After fun close() = db.close()

    @Test
    fun messagesRoundTripInOrderWithEveryPartType() = runTest {
        val repo = RoomConversationRepository(db.dao())
        val conversation = Conversation(
            id = "c1", personaId = "p1", title = "t", createdAt = 1, updatedAt = 1,
            context = ContextState("e1", "system", "cfg", "tools", windowStartMessageId = "m1", summary = "s", startedAt = 1),
        )
        repo.upsert(conversation)
        val parts = listOf(
            ContentPart.Reasoning("thinking"),
            ContentPart.Text("hello"),
            ContentPart.ToolCall("t1", "memory_save", """{"content":"x"}"""),
            ContentPart.ToolResult("t1", "memory_save", "Saved.", isError = false),
            ContentPart.Image("file:abc.png", caption = "cat"),
        )
        // Same timestamp: insertion order (rowid) must be preserved.
        repo.appendMessage(ChatMessage("m1", "c1", MessageRole.USER, listOf(ContentPart.Text("first")), createdAt = 5))
        repo.appendMessage(ChatMessage("m2", "c1", MessageRole.ASSISTANT, parts, createdAt = 5, providerType = ProviderType.ANTHROPIC, replay = "{\"role\":\"assistant\"}"))

        val loaded = repo.getMessages("c1")
        assertEquals(listOf("m1", "m2"), loaded.map { it.id })
        assertEquals(parts, loaded[1].parts)
        assertEquals("{\"role\":\"assistant\"}", loaded[1].replay)
        assertEquals(conversation, repo.get("c1"))
        assertEquals(2, repo.observeMessages("c1").first().size)

        repo.delete("c1")
        assertEquals(emptyList(), repo.getMessages("c1"))
    }

    @Test
    fun memoryVectorsAndAccessTracking() = runTest {
        val repo = RoomMemoryRepository(db.dao())
        val entry = MemoryEntry(id = "x", personaId = "p", content = "likes tea", createdAt = 1, updatedAt = 1, lastAccessedAt = 1)
        repo.upsert(entry)
        repo.putVector("x", "p", "model-a", floatArrayOf(0.25f, -1f, 3.5f))

        assertContentEquals(floatArrayOf(0.25f, -1f, 3.5f), repo.getVectors("p", "model-a")["x"])
        assertEquals(emptyMap(), repo.getVectors("p", "model-b"))

        repo.markAccessed(listOf("x"), at = 99)
        assertEquals(99, repo.get("x")!!.lastAccessedAt)
        assertEquals(1, repo.get("x")!!.accessCount)

        repo.delete("x")
        assertEquals(emptyMap(), repo.getVectors("p", "model-a"))
    }

    @Test
    fun activityLogFiltersByTimeAndPrunes() = runTest {
        val repo = RoomActivityRepository(db.dao())
        repo.add(ActivityEntry("a1", "p1", ActivityKind.STAYED_QUIET, createdAt = 100))
        repo.add(ActivityEntry("a2", "p1", ActivityKind.WROTE_LETTER, createdAt = 300, conversationId = "c1"))
        repo.add(ActivityEntry("a3", "p1", ActivityKind.SAVED_MEMORIES, createdAt = 200, count = 2))

        assertEquals(listOf("a3", "a2"), repo.observeSince(150).first().map { it.id })
        assertEquals(2, repo.observeSince(150).first().first().count)

        repo.deleteBefore(250)
        assertEquals(listOf("a2"), repo.observeSince(0).first().map { it.id })
    }

    @Test
    fun stickersRoundTripAndReportChanges() = runTest {
        val repo = RoomStickerRepository(db.dao())
        val tada = Sticker("s1", "c1", StickerTarget.message("m1"), StickerRef.Emoji("🎉", "tada"), x = 0.8f, y = 0.6f, rotationDeg = 12f, createdAt = 10)
        val heart = Sticker("s2", "c1", StickerTarget.message("m2"), StickerRef.Art("a1"), byPersonaId = "p1", x = 0.1f, y = 0.2f, scale = 1.3f, createdAt = 20)
        val elsewhere = Sticker("s3", "c2", StickerTarget.draft("c2"), StickerRef.Emoji("🍵", "tea"), x = 0.9f, y = 0f, createdAt = 30)
        listOf(tada, heart, elsewhere).forEach { repo.upsert(it) }

        assertEquals(listOf(tada, heart), repo.forConversation("c1"))
        assertEquals(listOf(tada, heart), repo.observeForConversation("c1").first())

        // Moving one keeps its place in the stacking order; peeling keeps the row for the persona to hear about.
        repo.upsert(tada.copy(x = 0.5f, updatedAt = 40))
        repo.upsert(heart.copy(peeledAt = 50, updatedAt = 50))
        assertEquals(listOf("s1", "s2"), repo.forConversation("c1").map { it.id })
        assertEquals(listOf("s1", "s2"), repo.changedSince("c1", 35).map { it.id })
        assertEquals(listOf("s2"), repo.changedSince("c1", 45).map { it.id })

        repo.deletePeeledBefore(60)
        assertEquals(listOf("s1"), repo.forConversation("c1").map { it.id })
    }

    @Test
    fun deletingLettersTakesTheirStickers() = runTest {
        val conversations = RoomConversationRepository(db.dao())
        val stickers = RoomStickerRepository(db.dao())
        conversations.appendMessage(ChatMessage("m1", "c1", MessageRole.USER, listOf(ContentPart.Text("a")), createdAt = 1))
        conversations.appendMessage(ChatMessage("m2", "c1", MessageRole.USER, listOf(ContentPart.Text("b")), createdAt = 2))
        stickers.upsert(Sticker("s1", "c1", StickerTarget.message("m1"), StickerRef.Emoji("🎉", "tada"), x = 0.5f, y = 0.5f, createdAt = 3))
        stickers.upsert(Sticker("s2", "c1", StickerTarget.message("m2"), StickerRef.Emoji("🍵", "tea"), x = 0.5f, y = 0.5f, createdAt = 4))
        stickers.upsert(Sticker("s3", "c2", StickerTarget.draft("c2"), StickerRef.Emoji("🌙", "crescent_moon"), x = 0.5f, y = 0.5f, createdAt = 5))

        conversations.deleteMessages(listOf("m1"))
        assertEquals(listOf("s2"), stickers.forConversation("c1").map { it.id })

        conversations.delete("c1")
        assertEquals(emptyList(), stickers.forConversation("c1"))
        assertEquals(listOf("s3"), stickers.forConversation("c2").map { it.id })
    }

    @Test
    fun stickerArtIsKeptPerSheet() = runTest {
        val repo = RoomStickerRepository(db.dao())
        val wave = StickerArt("a1", "aria-wave", ownerPersonaId = "p1", imageRef = "file:wave.png", feeling = "hello", createdAt = 1)
        val mochi = StickerArt("a2", "mochi", imageRef = "file:mochi.png", createdAt = 2)
        val seal = StickerArt("a3", "hanamaru", kind = StickerArtKind.STAMP, imageRef = "file:hanamaru.png", createdAt = 3)
        listOf(wave, mochi, seal).forEach { repo.upsertArt(it) }

        assertEquals(listOf(wave), repo.art("p1"))
        assertEquals(listOf(mochi, seal), repo.art(null))
        assertEquals(seal, repo.getArt("a3"))
        assertEquals(listOf(wave, mochi, seal), repo.observeArt().first())

        repo.deleteArt("a2")
        assertEquals(listOf(seal), repo.art(null))
    }
}
