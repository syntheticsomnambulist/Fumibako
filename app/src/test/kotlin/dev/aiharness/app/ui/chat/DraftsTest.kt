package dev.aiharness.app.ui.chat

import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Unsent notes outlast their screen and the app: the words, and what's waiting with them. */
class DraftsTest {
    private val dir: File = Files.createTempDirectory("drafts").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tidy() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out waiting for $what" }
            Thread.sleep(20)
        }
    }

    @Test
    fun aDraftIsKeptUntilItsEmpty() {
        val cutting = ContentPart.Attachment("c1", AttachmentKind.LINK, url = "https://sshhole.com/", title = "*sshole")
        val drafts = Drafts(dir, scope)
        drafts.of("conv-1").apply {
            text.value = "Half a thought about"
            going.value = listOf(Going.Item(cutting), Going.Picture(ContentPart.Image("blob:photo.jpg")), Going.Preparing("v", "a video", TrayItem.VIDEO))
            dismissedLinks += "https://example.com/"
        }
        waitFor("the draft's file") { File(dir, "conv-1.json").exists() }

        // As the app finds it the next time it opens.
        val again = Drafts(dir, scope).of("conv-1")
        assertEquals("Half a thought about", again.text.value)
        assertEquals(listOf("c1", "blob:photo.jpg"), again.going.value.map { it.key }, "what was ready; what was still being prepared starts again")
        assertTrue("https://example.com/" in again.dismissedLinks)

        // Sent (or emptied), there's nothing to keep.
        again.text.value = ""
        again.going.value = emptyList()
        waitFor("the file to go") { !File(dir, "conv-1.json").exists() }
        assertFalse(File(dir, "conv-1.json").exists())
    }

    @Test
    fun aDiscardedDraftIsGone() {
        val drafts = Drafts(dir, scope)
        drafts.of("conv-2").text.value = "words"
        waitFor("the draft's file") { File(dir, "conv-2.json").exists() }
        drafts.discard("conv-2")
        waitFor("the file to go") { !File(dir, "conv-2.json").exists() }
        assertEquals("", Drafts(dir, scope).of("conv-2").text.value)
    }
}
