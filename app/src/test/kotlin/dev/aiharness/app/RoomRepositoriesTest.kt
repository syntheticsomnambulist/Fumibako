package dev.aiharness.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.data.RoomConversationRepository
import dev.aiharness.app.data.RoomMemoryRepository
import dev.aiharness.app.data.db.HarnessDatabase
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.ContextState
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MemoryEntry
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ProviderType
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
}
