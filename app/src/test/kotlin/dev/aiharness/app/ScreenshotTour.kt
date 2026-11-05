package dev.aiharness.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Renders the main screens to PNGs for design review. Skipped unless HARNESS_SCREENSHOTS_DIR is set:
 *   HARNESS_SCREENSHOTS_DIR=/tmp/shots ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTour*'
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTour {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.let { File(it).apply { mkdirs() } }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()

    @Test
    @Config(qualifiers = "+night")
    fun darkTour() = tour("dark")

    @Test
    fun lightChat() {
        assumeTrue(outDir != null)
        seed()
        click("Aria")
        waitFor("actually sleep")
        shot("light_chat")
    }

    private fun tour(prefix: String) {
        assumeTrue(outDir != null)
        seed()
        waitFor("Kestrel")
        shot("${prefix}_1_chats")

        click("Aria")
        waitFor("actually sleep")
        shot("${prefix}_2_chat")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        click("Personas")
        waitFor("Juno")
        shot("${prefix}_3_personas")

        click("Aria")
        waitFor("Appearance tags")
        shot("${prefix}_4_persona_editor")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        click("Settings")
        waitFor("Heartbeat")
        shot("${prefix}_5_settings")
    }

    private fun placeholderImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(416, 608, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 416f, 608f, 0xFFB39DDB.toInt(), 0xFFF8BBD0.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 416f, 608f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<HarnessApp>().container
        val h = container.harness
        // Wait for first-launch seeding (Aria + default user persona).
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val now = System.currentTimeMillis()
        val aria = h.personas.getAll().first { it.name == "Aria" }

        val sam = UserPersona(newId(), "Sam", "A grad student who writes too late at night.", createdAt = now, updatedAt = now)
        h.userPersonas.upsert(sam)
        val profile = ModelProfile(id = newId(), name = "Claude Opus 5.5", provider = ProviderType.ANTHROPIC, modelId = "claude-opus-5-5")
        h.profiles.upsert(profile)
        h.settings.update { it.copy(activeUserPersonaId = sam.id, defaultModelProfileId = profile.id) }

        listOf(
            Persona(newId(), "Kestrel", "A stoic knight-errant sworn to an order that no longer exists.", personality = "Dry humour, fiercely loyal.", createdAt = now, updatedAt = now),
            Persona(newId(), "Juno", "A sharp-tongued tech witch who runs a repair shop in a rainy megacity.", personality = "Sarcastic, secretly soft.", createdAt = now, updatedAt = now),
        ).forEach {
            h.personas.upsert(it)
            h.createConversation(it.id, sam.id)
        }

        val conversation = h.createConversation(aria.id, sam.id)
        val image = h.blobs.put(placeholderImage(), "png")
        var t = now - 3 * 3_600_000L
        fun msg(role: MessageRole, vararg parts: ContentPart, origin: MessageOrigin = MessageOrigin.AGENT) =
            ChatMessage(newId(), conversation.id, role, parts.toList(), createdAt = t.also { t += 60_000 }, origin = origin)
        listOf(
            msg(MessageRole.USER, ContentPart.Text("I finally finished the first draft of my thesis!"), origin = MessageOrigin.USER),
            msg(
                MessageRole.ASSISTANT,
                ContentPart.Reasoning("Sam has been working on this for months. This is a big milestone worth remembering."),
                ContentPart.Text("*gasps and claps both hands over her mouth* Sam! That's **huge**. After all those late nights?"),
                ContentPart.ToolCall("t1", "memory_save", "{}"),
            ),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t1", "memory_save", "Saved.")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("I'm writing this one down. How do you feel?")),
            msg(MessageRole.USER, ContentPart.Text("Tired. Happy. Can you show me what you'd look like celebrating?"), origin = MessageOrigin.USER),
            msg(MessageRole.ASSISTANT, ContentPart.Text("*twirls* Like this!"), ContentPart.ToolCall("t2", "generate_image", "{}")),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t2", "generate_image", "Shown."), ContentPart.Image(image, caption = "celebrating")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("Good morning! Did you actually sleep after all that writing? *tilts head*"), origin = MessageOrigin.HEARTBEAT),
        ).forEach { h.conversations.appendMessage(it) }
        h.conversations.upsert(h.conversations.get(conversation.id)!!.copy(updatedAt = now + 1))

        h.memory.save(aria.id, "Sam is writing a thesis on urban ecology", importance = 8, pinned = true)
        h.memory.save(aria.id, "Sam finished the first draft of the thesis", kind = MemoryKind.EPISODE, importance = 7)
        h.memory.save(aria.id, "Sam drinks too much coffee when stressed", importance = 4)
    }
}
