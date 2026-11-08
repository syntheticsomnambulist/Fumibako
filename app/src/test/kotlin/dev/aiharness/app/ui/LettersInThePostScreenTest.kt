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
        var arrives = 0L
        runBlocking {
            repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
            val aria = h.personas.getAll().first()
            val c = h.createConversation(aria.id)
            val now = System.currentTimeMillis()
            // Far enough off for the screens to come up under Robolectric before it arrives.
            arrives = now + 10_000
            h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.USER, listOf(ContentPart.Text("Look what I found")), createdAt = now - 60_000, origin = MessageOrigin.USER))
            h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.ASSISTANT, listOf(ContentPart.Text(reply)), createdAt = now - 50_000, deliverAt = arrives))
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

        // Once it arrives, it's there, and the postmark is gone.
        Thread.sleep((arrives - System.currentTimeMillis() + 200).coerceAtLeast(0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        compose.waitUntil(10_000) { shown(reply) }
        compose.waitForIdle()
        assertTrue(!shown("reply is on its way"))
    }
}
