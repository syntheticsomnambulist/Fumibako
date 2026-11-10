package dev.aiharness.app.ui

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.HarnessApp
import dev.aiharness.app.MainActivity
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.util.newId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertTrue

/** Letters in the post: a reply on its way shows as a postmark, and its words only once it arrives. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LettersInThePostScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aReplyOnItsWayShowsOnlyWhenItArrives() {
        val h = ApplicationProvider.getApplicationContext<HarnessApp>().container.harness
        val reply = "The fern is a maidenhair, I think."
        var conversationId = ""
        runBlocking {
            repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
            val aria = h.personas.getAll().first()
            val c = h.createConversation(aria.id)
            val now = System.currentTimeMillis()
            // An hour off, so the screens have all the time they need to come up first.
            val arrives = now + 3_600_000L
            conversationId = c.id
            h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.USER, listOf(ContentPart.Text("Look what I found")), createdAt = now - 60_000, origin = MessageOrigin.USER))
            h.conversations.appendMessage(ChatMessage("reply", c.id, MessageRole.ASSISTANT, listOf(ContentPart.Text(reply)), createdAt = now - 50_000, deliverAt = arrives))
            h.updateConversation(c.id) { it.copy(updatedAt = now - 50_000, lastAgentMessageAt = arrives, preview = Conversation.previewOf(reply)) }
        }

        // The letters list says it's on its way, without its words.
        // (Below the getting-started card, on Robolectric's small screen.)
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("reply is on its way", substring = true)) }.isSuccess
        }
        assertTrue(!shown(reply), "the list doesn't give the reply away")

        // The thread: the postmark, and still not the words.
        compose.onAllNodesWithText("reply is on its way", substring = true).onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Look what I found"), 10_000)
        assertTrue(shown("reply is on its way"))
        assertTrue(!shown(reply))

        // It arrives a moment from now instead: then it's there, and the postmark is gone.
        val soon = System.currentTimeMillis() + 1_500
        runBlocking {
            val held = h.conversations.getMessages(conversationId).first { it.id == "reply" }
            h.conversations.updateMessage(held.copy(deliverAt = soon))
            h.updateConversation(conversationId) { it.copy(lastAgentMessageAt = soon) }
        }
        Thread.sleep((soon - System.currentTimeMillis() + 300).coerceAtLeast(0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitUntil(10_000) { shown(reply) }
        compose.waitForIdle()
        assertTrue(!shown("reply is on its way"))
    }
}
