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
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
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
    fun nightTour() = tour("night", TimeOfDay.YORU)

    @Test
    fun dayTour() = tour("day", TimeOfDay.HIRU)

    private fun tour(prefix: String, time: TimeOfDay) {
        assumeTrue(outDir != null)
        seed(time)
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
        waitFor("Memory book")
        shot("${prefix}_4_persona_page")

        click("Edit")
        waitFor("Appearance tags")
        shot("${prefix}_5_persona_editor")
        back()
        back()

        click("Settings")
        waitFor("Day and night")
        shot("${prefix}_6_settings")
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun placeholderImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(416, 608, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 416f, 608f, 0xFFB39DDB.toInt(), 0xFFF8BBD0.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 416f, 608f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun seed(time: TimeOfDay) = runBlocking {
        val container = ApplicationProvider.getApplicationContext<HarnessApp>().container
        val h = container.harness
        // Wait for first-launch seeding (Aria + default user persona).
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val now = System.currentTimeMillis()
        val aria = h.personas.getAll().first { it.name == "Aria" }

        val sam = UserPersona(newId(), "Sam", "A grad student who writes too late at night.", ink = "sora", tapePattern = TapePattern.STRIPES, createdAt = now, updatedAt = now)
        h.userPersonas.upsert(sam)
        val profile = ModelProfile(id = newId(), name = "Claude Opus 5.5", provider = ProviderType.ANTHROPIC, modelId = "claude-opus-5-5")
        h.profiles.upsert(profile)
        h.settings.update {
            it.copy(
                activeUserPersonaId = sam.id,
                defaultModelProfileId = profile.id,
                theme = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.AKI, staticTime = time),
            )
        }

        val kestrel = Persona(
            newId(), "Kestrel", "A stoic knight-errant sworn to an order that no longer exists.", personality = "Dry humour, fiercely loyal.",
            greeting = "You keep late hours. So do I. Sit, if you like.", ink = "tokiwa", tapePattern = TapePattern.STRIPES, createdAt = now, updatedAt = now,
        )
        val juno = Persona(
            newId(), "Juno", "A sharp-tongued tech witch who runs a repair shop in a rainy megacity.", personality = "Sarcastic, secretly soft.",
            greeting = "Shop's open. Don't touch the cat, she bites the curious.", ink = "kikyo", tapePattern = TapePattern.GINGHAM, createdAt = now, updatedAt = now,
        )
        listOf(kestrel to "watchful", juno to "smug").forEach { (persona, mood) ->
            h.personas.upsert(persona)
            val c = h.createConversation(persona.id, sam.id)
            h.updateConversation(c.id) { it.copy(mood = mood, updatedAt = now - 86_400_000L, lastAgentMessageAt = now - 86_400_000L) }
        }

        val conversation = h.createConversation(aria.id, sam.id)
        h.conversations.deleteMessages(h.conversations.getMessages(conversation.id).map { it.id })
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
                ContentPart.ToolCall("t1", "memory_save", """{"content":"Sam finished the first draft of the thesis"}"""),
            ),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t1", "memory_save", "Saved.")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("I'm writing this one down. How do you feel?")),
            msg(MessageRole.USER, ContentPart.Text("Tired. Happy. Can you show me what you'd look like celebrating?"), origin = MessageOrigin.USER),
            msg(MessageRole.ASSISTANT, ContentPart.Text("*twirls* Like this!"), ContentPart.ToolCall("t2", "generate_image", "{}")),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t2", "generate_image", "Shown."), ContentPart.Image(image, caption = "celebrating")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("Good morning! Did you actually sleep after all that writing? *tilts head* [mood: fond]"), origin = MessageOrigin.HEARTBEAT),
        ).forEach { h.conversations.appendMessage(it) }
        h.refreshPreview(conversation.id)
        h.updateConversation(conversation.id) {
            it.copy(updatedAt = now + 1, mood = "fond", lastAgentMessageAt = t, lastReadAt = now - 4 * 3_600_000L)
        }

        // Background activity since the user "left".
        container.awaySince.value = now - 10 * 3_600_000L
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.WROTE_LETTER, now - 2 * 3_600_000L, conversation.id))
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.SAVED_MEMORIES, now - 2 * 3_600_000L - 60_000, conversation.id, count = 2))
        h.activity.add(ActivityEntry(newId(), juno.id, ActivityKind.STAYED_QUIET, now - 5 * 3_600_000L))

        h.memory.save(aria.id, "Sam is writing a thesis on urban ecology", importance = 8, pinned = true)
        h.memory.save(aria.id, "Sam finished the first draft of the thesis", kind = MemoryKind.EPISODE, importance = 7)
        h.memory.save(aria.id, "Sam drinks too much coffee when stressed", importance = 4)
    }
}
