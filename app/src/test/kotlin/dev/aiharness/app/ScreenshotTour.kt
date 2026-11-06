package dev.aiharness.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.core.model.Track
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import dev.aiharness.app.media.NowPlaying
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.MarkKind
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Keepsake
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

    private fun shot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()

    @Test
    fun nightTour() = tour("night", TimeOfDay.YORU)

    @Test
    fun dayTour() = tour("day", TimeOfDay.HIRU)

    /** Winter night with large system text, like a phone with its font size turned up. */
    @Test
    @Config(fontScale = 1.3f)
    fun largeTextTour() = tour("large", TimeOfDay.YORU, Season.FUYU)

    /** A fresh install: the getting-started card, and About with the seal. */
    @Test
    fun firstRunTour() {
        assumeTrue(outDir != null)
        runBlocking {
            val h = ApplicationProvider.getApplicationContext<HarnessApp>().container.harness
            repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
            h.settings.update { it.copy(theme = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.AKI, staticTime = TimeOfDay.HIRU)) }
        }
        waitFor("Getting started")
        shot("first_01_letters")
        click("Settings")
        waitFor("CONTENTS")
        click("About")
        waitFor("Fumibako")
        shot("first_02_about")
    }

    /**
     * The picture dialog in a letter. On its own, on Robolectric's small default screen: on a
     * phone-sized one, any dialog holding a text field (even a stock OutlinedTextField in a stock
     * AlertDialog) never goes idle under Robolectric, so the main tour leaves dialogs alone.
     */
    @Test
    @Config(qualifiers = "xxhdpi")
    fun pictureDialogShot() {
        assumeTrue(outDir != null)
        seed(TimeOfDay.YORU, Season.AKI)
        waitFor("Aria")
        click("Aria")
        waitFor("actually sleep")
        compose.onNodeWithContentDescription("Draw a picture").performClick()
        waitFor("What Aria is wearing")
        shot("picture_dialog")
    }

    private fun tour(prefix: String, time: TimeOfDay, season: Season = Season.AKI) {
        assumeTrue(outDir != null)
        seed(time, season)
        waitFor("Kestrel")
        // Aria's unread letter wears her sticker for hello.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Unread"), 10_000)
        shot("${prefix}_01_chats")

        click("Aria")
        waitFor("actually sleep")
        // Stickers bake in the background; wait until the ones on screen are drawn.
        listOf(":aria-heart: sticker", ":sleeping: sticker", ":gold-star: sticker").forEach { label ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(label), 10_000)
        }
        shot("${prefix}_02_chat")
        // Writing a reply: the field grows and the send button stays.
        compose.onNode(hasSetTextAction()).performTextInput("You'll get to pick for yourself :3\nWe've just updated to a new version.")
        assertDrawn(compose.onNodeWithContentDescription("Send"), shot("${prefix}_02b_chat_typing"))
        // The + tray: tap a sticker onto your note.
        compose.onNodeWithContentDescription("Add something").performClick()
        waitFor("Camera")
        shot("${prefix}_02c0_tray")
        click("Stickers")
        waitFor("Hold and drag")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush:"), 10_000)
        shot("${prefix}_02c_sticker_tray")
        click(":blush:")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush: sticker"), 10_000)
        shot("${prefix}_02d_sticker_on_draft")
        // Selecting your own sticker shows its handle; Aria's says whose it is.
        compose.onNodeWithContentDescription("Close the tray").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(":gold-star: sticker"))
        compose.onNodeWithContentDescription(":gold-star: sticker").performClick()
        waitFor("Peel off")
        shot("${prefix}_02e_sticker_selected")
        click("Done")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(":aria-heart: sticker"))
        compose.onNodeWithContentDescription(":aria-heart: sticker").performClick()
        waitFor("Only Aria can peel it off")
        shot("${prefix}_02f_her_sticker")
        click("Done")
        // A link written in the note waits as a cutting (a local address: never fetched).
        compose.onNode(hasSetTextAction()).performTextInput(" http://localhost/tea")
        waitFor("GOING WITH YOUR NOTE")
        shot("${prefix}_02c9_going_with_your_note")
        // Typing a :name: offers the stickers it could be.
        compose.onNode(hasSetTextAction()).performTextInput(" :ari")
        waitFor(":aria-hello:")
        shot("${prefix}_02g_sticker_names")
        // Frames: a cassette and a luggage tag in a note, a cutting in a letter, photos, a ticket and a film strip.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("something for when it rains", substring = true))
        waitFor("rainy day mix")
        shot("${prefix}_02k_frames_mix")
        // A tag swings and shows Open and Save beneath it.
        click("market-map.pdf")
        waitFor("Save to phone")
        shot("${prefix}_02k1_tag_actions")
        click("market-map.pdf")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("second steeping made me", substring = true))
        shot("${prefix}_02l_frames_cutting")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("from the walk", substring = true))
        shot("${prefix}_02m_frames_photos")
        // The players (shown without playing: Robolectric has no audio): the mini player docked above
        // the composer, then the full player for a playlist and for a song.
        val app = ApplicationProvider.getApplicationContext<HarnessApp>().container
        val sent = runBlocking { app.harness.conversations.getAll().flatMap { app.harness.conversations.getMessages(it.id) }.flatMap { it.attachments } }
        val samInk = 0xFF3A86A8.toInt()
        compose.runOnIdle {
            app.playback.preview(NowPlaying(sent.first { it.id == "mix1" }, null, samInk, fromYou = true, index = 1, playing = true, positionMs = 62_000, durationMs = 194_000))
        }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Now playing", substring = true), 10_000)
        shot("${prefix}_02n_mini_player")
        compose.onNodeWithContentDescription("Now playing", substring = true).performClick()
        waitFor("Track 2 of 2")
        shot("${prefix}_02o_player_mix")
        compose.onAllNodesWithContentDescription("Shrink the player").onFirst().performClick()
        compose.runOnIdle {
            app.playback.preview(NowPlaying(sent.first { it.id == "song1" }, null, samInk, fromYou = true, positionMs = 80_000, durationMs = 222_000))
        }
        compose.onNodeWithContentDescription("Now playing: October Walk", substring = true).performClick()
        waitFor("knows its title and artist")
        shot("${prefix}_02p_player_song")
        compose.onAllNodesWithContentDescription("Shrink the player").onFirst().performClick()
        compose.runOnIdle { app.playback.preview(null) }
        // The photo viewer, from the stack, and the video player (its file isn't there: the middle frame stands in).
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("from the walk", substring = true))
        click("2 photos")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Photo 2 of 2"), 10_000)
        shot("${prefix}_02q0_stack_fanned")
        compose.onNodeWithContentDescription("Photo 1 of 2").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Photo 1 of 2"), 10_000)
        shot("${prefix}_02q_photo_viewer")
        compose.onNodeWithContentDescription("Close").performClick()
        click("0:42")
        waitFor("From Sam")
        compose.waitUntilAtLeastOneExists(hasContentDescription("A frame from the video"), 10_000)
        shot("${prefix}_02r_video_player")
        compose.onNodeWithContentDescription("Close").performClick()
        // Aria's read stamp on the cutting you sent, and the song she shared back (a ticket that opens Spotify).
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("think of your tea", substring = true))
        waitFor("A field guide to oolong")
        shot("${prefix}_02s_read_stamp")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("walk home", substring = true))
        waitFor("Paper Lanterns")
        shot("${prefix}_02t_her_song")
        // A sticker written inline in a letter.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("writing this one down", substring = true))
        compose.waitUntilAtLeastOneExists(hasContentDescription(":aria-heart:"), 10_000)
        shot("${prefix}_02h_inline_sticker")
        back()
        back()

        click("Personas")
        waitFor("Juno")
        shot("${prefix}_03_personas")

        // A new persona: Done is disabled until it has a name, and must show once it does.
        scrollTo("New persona")
        click("New persona")
        waitFor("EDITING")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Wren")
        assertDrawn(compose.onNodeWithText("Done"), shot("${prefix}_03b_new_persona"))
        back()
        click("Discard")
        waitFor("Juno")
        top()

        click("Aria")
        waitFor("Write a letter")
        shot("${prefix}_04_persona_page")
        scrollTo("Wakes now and then")
        shot("${prefix}_04b_persona_page_more")
        scrollTo("Open sheet")
        click("Open sheet")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":aria-hello: sticker"), 10_000)
        shot("${prefix}_04c_persona_stickers")
        scrollTo("can peel off mine")
        shot("${prefix}_04d_persona_stickers_more")
        back()
        waitFor("Open sheet")

        top()
        click("Edit")
        waitFor("EDITING")
        shot("${prefix}_05_persona_editor")
        scrollTo("Another set", exact = false)
        shot("${prefix}_05a_persona_pronouns")
        scrollTo("Drawing style")
        shot("${prefix}_05b_persona_editor_more")
        back()
        top()
        waitFor("Edit")

        scrollTo("Wakes now and then")
        click("Settings")
        waitFor("Wakes up")
        shot("${prefix}_06_persona_heartbeat")
        back()
        back()

        scrollTo("All your personas")
        click("All your personas")
        waitFor("Your personas")
        shot("${prefix}_07_your_personas")
        click("Sam")
        waitFor("What they remember")
        shot("${prefix}_08_you_page")
        back()
        back()

        memoryTour(prefix)

        click("Settings")
        waitFor("CONTENTS")
        shot("${prefix}_09_settings")
        listOf(
            "Look" to "Day and night",
            "Heartbeat" to "Time only",
            "Memory" to "Long-term memory",
            "Models and keys" to "Model profiles",
            "Pictures" to "Image profiles",
        ).forEachIndexed { i, (section, marker) ->
            click(section)
            waitFor(marker)
            shot("${prefix}_1${i}_settings_${section.substringBefore(' ').lowercase()}")
            back()
        }

        // Stickers: the settings page and the shared library.
        click("Stickers")
        waitFor("Most on one letter")
        shot("${prefix}_15_settings_stickers")
        scrollTo("Open the library")
        click("Open the library")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":gold-star: sticker"), 10_000)
        shot("${prefix}_16_shared_stickers")
        back()
        back()

        // Lamplight on the Look page.
        click("Look")
        scrollTo("Lamplight at night")
        shot("${prefix}_17_settings_look_lamplight")
        back()

        // 九 Media: links, pictures, playback, paper motion.
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Media"))
        click("Media")
        waitFor("OPEN LINKS IN")
        shot("${prefix}_18_settings_media")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Haptic ticks"))
        compose.waitForIdle()
        shot("${prefix}_18b_settings_media_more")
        back()

        // An image profile.
        click("Pictures")
        waitFor("Image profiles")
        waitFor("Sketchbook")
        click("Sketchbook")
        waitFor("Where")
        shot("${prefix}_33_image_profile")
        back()
        back()

        // A model profile, a new one's presets, and the keys.
        click("Models and keys")
        waitFor("Model profiles")
        waitFor("Claude Opus 5.5")
        click("Claude Opus 5.5")
        waitFor("Provider and key")
        shot("${prefix}_30_model_profile")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Takes in", substring = true))
        compose.waitForIdle()
        shot("${prefix}_30b_model_takes_in")
        back()
        click("+ New model profile")
        waitFor("Start from")
        shot("${prefix}_31_new_model_profile")
        back()
        scrollTo("Manage keys")
        click("Manage keys")
        waitFor("Your keys")
        shot("${prefix}_32_keys")
        back()
        back()
    }

    private fun memoryTour(prefix: String) {
        top()
        click("Aria")
        waitFor("Write a letter")
        click("Memories")
        waitFor("memory book")
        shot("${prefix}_20_memory_book")
        scrollTo("Sam drinks too much coffee")
        shot("${prefix}_20b_memory_book_today")
        // Keepsakes on today's page (two, and "+N"), then only memories with something kept.
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("+", substring = true))
        compose.waitForIdle()
        shot("${prefix}_20b2_memory_book_keepsakes")
        top()
        compose.onNodeWithText("Keepsakes").performScrollTo().performClick()
        waitFor("rainy day mix")
        shot("${prefix}_20e_keepsakes_filter")
        compose.onNodeWithText("All").performScrollTo().performClick()
        scrollTo("October", exact = true)
        // The envelope opening, and its notes spilling out.
        compose.mainClock.autoAdvance = false
        click("October")
        compose.mainClock.advanceTimeBy(260)
        shot("${prefix}_20c_envelope_opening")
        compose.mainClock.advanceTimeBy(700)
        shot("${prefix}_20c_envelope_spilling")
        compose.mainClock.autoAdvance = true
        scrollTo("Had a cold")
        shot("${prefix}_20c_memory_book_october")
        scrollTo("August", exact = true)
        click("August")
        scrollTo("cardamom")
        shot("${prefix}_20d_memory_book_august")

        scrollTo("Sam drinks too much coffee")
        click("Sam drinks too much coffee when stressed")
        waitFor("FELT WHEN KEPT")
        shot("${prefix}_21_memory_card")
        back()
        // A memory card with its keepsake.
        top()
        compose.onNodeWithText("Keepsakes").performScrollTo().performClick()
        waitFor("rainy day mix")
        scrollTo("Sam made me a rainy day mix")
        click("Sam made me a rainy day mix, for when it rains")
        waitFor("FELT WHEN KEPT")
        shot("${prefix}_21b_memory_card_keepsake")
        back()
        top()
        compose.onNodeWithText("All").performScrollTo().performClick()

        // With memory editing allowed: Edit, then forget it and watch it burn.
        runBlocking {
            val h = ApplicationProvider.getApplicationContext<HarnessApp>().container.harness
            h.settings.update { it.copy(memory = it.memory.copy(userCanEdit = true)) }
        }
        scrollTo("Sam drinks too much coffee")
        click("Sam drinks too much coffee when stressed")
        waitFor("FELT WHEN KEPT")
        click("Edit")
        waitFor("EDITING")
        shot("${prefix}_22_memory_card_edit")
        scrollTo("Forget this memory")
        shot("${prefix}_22b_memory_card_edit_more")
        compose.mainClock.autoAdvance = false
        click("Forget this memory")
        // Let the snapshot and the delete finish, then run the burn partway.
        repeat(20) {
            Thread.sleep(50)
            compose.mainClock.advanceTimeByFrame()
        }
        top()
        shot("${prefix}_23_memory_burning_0")
        listOf(20, 15, 15, 15, 15).forEachIndexed { i, frames ->
            repeat(frames) { compose.mainClock.advanceTimeByFrame() }
            shot("${prefix}_23_memory_burning_${i + 1}")
        }
        compose.mainClock.autoAdvance = true
        waitFor("You let this memory go")
        shot("${prefix}_23b_memory_ash")
        click("Undo")
        waitFor("FELT WHEN KEPT")
        back()
        back()
        back()
    }

    /**
     * A button enabled by typing must be drawn in the screenshot [root]: its pixels differ from the
     * paper just beside it. (The chat's send button once vanished while staying tappable.)
     */
    private fun assertDrawn(node: androidx.compose.ui.test.SemanticsNodeInteraction, root: Bitmap) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        // Just inside the left edge: the button's own padding, clear of its label or icon.
        val inside = root.getPixel((bounds.left + 12).toInt(), bounds.center.y.toInt())
        val beside = root.getPixel((bounds.left - 4).toInt(), bounds.center.y.toInt())
        val distance = listOf(16, 8, 0).sumOf { shift -> kotlin.math.abs((inside shr shift and 0xff) - (beside shr shift and 0xff)) }
        check(distance > 60) { "the button isn't drawn (button ${Integer.toHexString(inside)}, around it ${Integer.toHexString(beside)}; bounds $bounds in ${root.width}x${root.height})" }
    }

    private fun top() {
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        compose.waitForIdle()
    }

    private fun scrollTo(text: String, exact: Boolean = false) {
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(if (exact) hasText(text) else hasText(text, substring = true, ignoreCase = true))
        compose.waitForIdle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** A sticker's picture on a clear background: a heart, a star, or an inked ring for a stamp. */
    private fun stickerShape(kind: String): ByteArray {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            "heart" -> {
                paint.color = 0xFFD80638.toInt()
                val path = android.graphics.Path().apply {
                    moveTo(100f, 178f)
                    cubicTo(20f, 120f, 10f, 60f, 55f, 42f)
                    cubicTo(80f, 32f, 96f, 48f, 100f, 62f)
                    cubicTo(104f, 48f, 120f, 32f, 145f, 42f)
                    cubicTo(190f, 60f, 180f, 120f, 100f, 178f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = 0x66FFFFFF
                canvas.drawCircle(64f, 70f, 12f, paint)
            }
            "star" -> {
                paint.color = 0xFFE3940B.toInt()
                val path = android.graphics.Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 92f else 38f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 100f + r * Math.cos(a).toFloat()
                    val y = 104f + r * Math.sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "sun" -> {
                paint.color = 0xFFE3940B.toInt()
                canvas.drawCircle(100f, 100f, 46f, paint)
                paint.strokeWidth = 12f
                paint.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    canvas.drawLine(100f + 62f * Math.cos(a).toFloat(), 100f + 62f * Math.sin(a).toFloat(), 100f + 88f * Math.cos(a).toFloat(), 100f + 88f * Math.sin(a).toFloat(), paint)
                }
                paint.color = 0xFF5D4C3C.toInt()
                canvas.drawCircle(84f, 94f, 6f, paint)
                canvas.drawCircle(116f, 94f, 6f, paint)
            }
            else -> {
                paint.color = 0xFFC43C32.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f
                canvas.drawCircle(100f, 100f, 80f, paint)
                canvas.drawCircle(100f, 100f, 40f, paint)
            }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun placeholderImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(416, 608, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 416f, 608f, 0xFFB39DDB.toInt(), 0xFFF8BBD0.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, 416f, 608f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun seed(time: TimeOfDay, season: Season) = runBlocking {
        val container = ApplicationProvider.getApplicationContext<HarnessApp>().container
        val h = container.harness
        // Wait for first-launch seeding (Aria + default user persona).
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val now = System.currentTimeMillis()
        val aria = h.personas.getAll().first { it.name == "Aria" }
        // Aria goes by she and they: the app's sentences take turns.
        h.personas.upsert(aria.copy(alsoPronouns = listOf(Pronouns(PronounKind.THEY))))

        val sam = UserPersona(newId(), "Sam", "A grad student who writes too late at night.", ink = "sora", tapePattern = TapePattern.STRIPES, createdAt = now, updatedAt = now)
        h.userPersonas.upsert(sam)
        val profile = ModelProfile(id = newId(), name = "Claude Opus 5.5", provider = ProviderType.ANTHROPIC, modelId = "claude-opus-5-5")
        h.profiles.upsert(profile)
        h.credentials.upsert(dev.aiharness.core.model.Credential("nai", "NovelAI", dev.aiharness.core.model.CredentialKind.NOVELAI))
        h.settings.update {
            it.copy(
                imageGen = it.imageGen.copy(
                    profiles = listOf(dev.aiharness.core.image.ImageProfiles.preset(dev.aiharness.core.model.ImageHost.NOVELAI, "pic").copy(name = "Sketchbook", credentialId = "nai")),
                    defaultStyle = "watercolor, soft lighting",
                ),
                activeUserPersonaId = sam.id,
                defaultModelProfileId = profile.id,
                theme = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = season, staticTime = time),
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
        val letters = listOf(
            msg(MessageRole.USER, ContentPart.Text("I finally finished the first draft of my thesis!"), origin = MessageOrigin.USER),
            msg(
                MessageRole.ASSISTANT,
                ContentPart.Reasoning("Sam has been working on this for months. This is a big milestone worth remembering."),
                ContentPart.Text("*gasps and claps both hands over her mouth* Sam! That's **huge**. After all those late nights?"),
                ContentPart.ToolCall("t1", "memory_save", """{"content":"Sam finished the first draft of the thesis"}"""),
            ),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t1", "memory_save", "Saved.")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("I'm writing this one down. How do you feel? :aria-heart:")),
            msg(MessageRole.USER, ContentPart.Text("Tired. Happy. Can you show me what you'd look like celebrating?"), origin = MessageOrigin.USER),
            msg(MessageRole.ASSISTANT, ContentPart.Text("*twirls* Like this!"), ContentPart.ToolCall("t2", "generate_image", "{}")),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t2", "generate_image", "Shown."), ContentPart.Image(image, caption = "celebrating")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("Good morning! Did you actually sleep after all that writing? *tilts head* [mood: fond]"), origin = MessageOrigin.HEARTBEAT),
        )
        // Earlier that evening: things sent as stationery (frames).
        var e = now - 6 * 3_600_000L
        fun early(role: MessageRole, vararg parts: ContentPart, origin: MessageOrigin = MessageOrigin.AGENT) =
            ChatMessage(newId(), conversation.id, role, parts.toList(), createdAt = e.also { e += 60_000 }, origin = origin)
        val picture2 = h.blobs.put(placeholderImage(), "png")
        val earlier = listOf(
            early(
                MessageRole.USER,
                ContentPart.Text("I made you something for when it rains."),
                ContentPart.Attachment(
                    "mix1", AttachmentKind.PLAYLIST, title = "rainy day mix", subtitle = "for Aria, from Sam", lengthMs = 2_530_000,
                    tracks = listOf(Track(title = "October Walk", artist = "Hana Sato", lengthMs = 222_000), Track(title = "Kettle Song", artist = "The Lanterns", lengthMs = 194_000)),
                ),
                ContentPart.Attachment("pdf1", AttachmentKind.FILE, ref = h.blobs.put(byteArrayOf(37, 80, 68, 70), "pdf"), mimeType = "application/pdf", name = "market-map.pdf", sizeBytes = 2_400_000, pages = 3),
                origin = MessageOrigin.USER,
            ),
            early(
                MessageRole.ASSISTANT,
                ContentPart.Text("I went looking for that tea you mentioned and found this. The part about the second steeping made me think of you."),
                ContentPart.Attachment(
                    "link1", AttachmentKind.LINK, url = "https://kettleandleaf.example/second-steeping", site = "Kettle & Leaf",
                    title = "Why the second steeping is the good one", snippet = "The first pour wakes the leaves. The second is where the flavor opens up.", pictureRef = picture2,
                ),
            ),
            early(
                MessageRole.USER,
                ContentPart.Text("And these, from the walk."),
                ContentPart.Image(picture2), ContentPart.Image(image),
                ContentPart.Attachment("song1", AttachmentKind.SONG, title = "October Walk", artist = "Hana Sato", lengthMs = 222_000),
                ContentPart.Attachment("film1", AttachmentKind.VIDEO, ref = "file:not-here.mp4", mimeType = "video/mp4", title = "the river", lengthMs = 42_000, frameRefs = listOf(image, picture2, image)),
                origin = MessageOrigin.USER,
            ),
            // A cutting you sent, which Aria opened (her read stamp), and a song she shared back.
            early(
                MessageRole.USER,
                ContentPart.Text("This made me think of your tea."),
                ContentPart.Attachment(
                    "link2", AttachmentKind.LINK, url = "https://teahouse.example/oolong", site = "The Tea House",
                    title = "A field guide to oolong", snippet = "From green to dark, the oolongs worth knowing.",
                ),
                origin = MessageOrigin.USER,
            ),
            early(
                MessageRole.ASSISTANT,
                ContentPart.Text("I read every word of it. And this one's for the walk home."),
                ContentPart.ToolCall("t9", "share_link", "{}"),
            ),
            early(
                MessageRole.TOOL,
                ContentPart.ToolResult("t9", "share_link", "In your letter now, as a ticket."),
                ContentPart.Attachment(
                    "song2", AttachmentKind.SONG, url = "https://open.spotify.com/track/paperlanterns", title = "Paper Lanterns",
                    artist = "The Lanterns", site = "Spotify",
                ),
            ),
        )
        earlier.forEach { h.conversations.appendMessage(it) }
        h.marks.add(AttachmentMark("link2", MarkKind.READ, conversationId = conversation.id, byPersonaId = aria.id, at = now))
        // The song has been played once: its ticket is punched.
        h.marks.add(AttachmentMark("song1", MarkKind.PUNCHED, conversationId = conversation.id, at = now))
        letters.forEach { h.conversations.appendMessage(it) }
        h.refreshPreview(conversation.id)

        // Stickers: Aria's heart on Sam's note, Sam's star and a stamp on her letters, an emoji on the last one.
        val heart = StickerArt(newId(), "aria-heart", aria.id, imageRef = h.blobs.put(stickerShape("heart"), "png"), feeling = "fond", createdAt = now)
        val star = StickerArt(newId(), "gold-star", imageRef = h.blobs.put(stickerShape("star"), "png"), createdAt = now)
        val seal = StickerArt(newId(), "hanamaru", kind = StickerArtKind.STAMP, imageRef = h.blobs.put(stickerShape("ring"), "png"), createdAt = now)
        val hello = StickerArt(newId(), "aria-hello", aria.id, imageRef = h.blobs.put(stickerShape("sun"), "png"), feeling = "hello", createdAt = now + 1)
        val proud = StickerArt(newId(), "aria-proud", aria.id, imageRef = h.blobs.put(stickerShape("star"), "png"), feeling = "proud", createdAt = now + 2)
        listOf(heart, star, seal, hello, proud).forEach { h.stickers.upsertArt(it) }
        val stick = h.stickerService
        stick.stick(conversation.id, StickerTarget.message(letters[4].id), StickerRef.Art(heart.id), aria.id, 0.03f, 0.7f, rotationDeg = -12f)
        stick.stick(conversation.id, StickerTarget.message(letters[5].id), StickerRef.Art(star.id), null, 0.86f, 0.72f, rotationDeg = 14f, scale = 1.1f)
        stick.stick(conversation.id, StickerTarget.message(letters[1].id), StickerRef.Art(seal.id), null, 0.88f, 0.3f, rotationDeg = -8f)
        stick.stick(conversation.id, StickerTarget.message(letters[7].id), StickerRef.Emoji("😴", "sleeping"), null, 0.9f, 0.7f, rotationDeg = 10f)
        h.updateConversation(conversation.id) {
            it.copy(updatedAt = now + 1, mood = "fond", lastAgentMessageAt = t, lastReadAt = now - 4 * 3_600_000L)
        }

        // Background activity since the user "left".
        container.awaySince.value = now - 10 * 3_600_000L
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.WROTE_LETTER, now - 2 * 3_600_000L, conversation.id))
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.SAVED_MEMORIES, now - 2 * 3_600_000L - 60_000, conversation.id, count = 2))
        h.activity.add(ActivityEntry(newId(), juno.id, ActivityKind.STAYED_QUIET, now - 5 * 3_600_000L))

        // The memory book: today, last month (fading and replaced), and one worn page from the summer.
        val day = 86_400_000L
        suspend fun keep(content: String, daysAgo: Int = 0, usedDaysAgo: Int = daysAgo, edit: (dev.aiharness.core.model.MemoryEntry) -> dev.aiharness.core.model.MemoryEntry = { it }, save: suspend () -> dev.aiharness.core.memory.SaveResult): dev.aiharness.core.model.MemoryEntry {
            val entry = save().entry
            val at = now - daysAgo * day - 3_600_000L
            val used = now - usedDaysAgo * day - 3_600_000L
            return edit(entry.copy(createdAt = at, updatedAt = at, lastAccessedAt = used, lastUsedAt = used)).also { h.memories.upsert(it) }.also { check(it.content == content) }
        }
        h.memory.save(aria.id, "Sam is writing a thesis on urban ecology", level = MemoryLevel.MATTERS_DEEPLY, pinned = true, feltMood = "proud")
        h.memory.save(aria.id, "Sam likes to be asked, not told", pinned = true, feltMood = "fond", aboutUserPersonaId = sam.id)
        h.memory.save(aria.id, "Sam finished the first draft of the thesis after months of late nights. Tired but happy; we talked about sleeping properly before the defense.", kind = MemoryKind.EPISODE, feltMood = "delighted", sourceConversationId = conversation.id, sourceMessageId = letters[1].id)
        h.memory.save(aria.id, "The defense is in early December", feltMood = "hopeful", aboutUserPersonaId = sam.id)
        h.memory.save(aria.id, "Ask about the defense date without adding pressure", kind = MemoryKind.REFLECTION, feltMood = "thoughtful", sourceConversationId = conversation.id, sourceMessageId = letters.last().id)
        keep("Sam drinks too much coffee when stressed", daysAgo = 0, edit = {
            it.copy(mood = "amused", moodReason = "It stopped being a worry once Sam started laughing about it too.", moodRevisedAt = now - 3 * day, accessCount = 7)
        }) { h.memory.save(aria.id, "Sam drinks too much coffee when stressed", level = MemoryLevel.IN_PASSING, feltMood = "fond", aboutUserPersonaId = sam.id) }
        h.memory.save(aria.id, "Sam loves walking in the rain", feltMood = "longing", aboutUserPersonaId = sam.id)
        // Keepsakes: the mix, the walk's photo and its song kept with today's memories; a song tucked into October.
        val sentParts = earlier.flatMap { it.parts }
        fun kept(id: String) = Keepsake(earlier.first { m -> m.parts.any { (it as? ContentPart.Attachment)?.id == id || (it as? ContentPart.Image)?.ref == id } }.id,
            sentParts.first { (it as? ContentPart.Attachment)?.id == id || (it as? ContentPart.Image)?.ref == id }, keptAt = now)
        h.memory.save(aria.id, "Sam made me a rainy day mix, for when it rains", feltMood = "touched", keepsakes = listOf(kept("mix1")))
        h.memory.save(aria.id, "The walk along the river, in photos and a song", kind = MemoryKind.EPISODE, feltMood = "fond", keepsakes = listOf(kept(picture2), kept("song1"), kept("link2")))
        val library = keep("Works at the campus library", daysAgo = 27) { h.memory.save(aria.id, "Works at the campus library", aboutUserPersonaId = sam.id) }
        h.memory.save(aria.id, "Left the library in October to write full time", aboutUserPersonaId = sam.id, replaces = library.id, feltMood = "proud")
        h.memory.get(library.id)?.let { h.memories.upsert(it.copy(createdAt = now - 27 * day, updatedAt = now - 27 * day)) }
        keep("Had a cold the week of the conference", daysAgo = 25) { h.memory.save(aria.id, "Had a cold the week of the conference", level = MemoryLevel.IN_PASSING, feltMood = "worried", aboutUserPersonaId = sam.id) }
        keep("Prefers walking routes along the river", daysAgo = 26, usedDaysAgo = 2) {
            h.memory.save(aria.id, "Prefers walking routes along the river", feltMood = "curious", aboutUserPersonaId = sam.id, keepsakes = listOf(kept("song1")))
        }
        keep("Sam's first conference talk went well: nerves before, relief after, and a long walk to come down from it.", daysAgo = 24) {
            h.memory.save(aria.id, "Sam's first conference talk went well: nerves before, relief after, and a long walk to come down from it.", kind = MemoryKind.EPISODE, feltMood = "amazed")
        }
        keep("Mentioned a favorite bakery near the old flat, the one with the cardamom buns", daysAgo = 75) {
            h.memory.save(aria.id, "Mentioned a favorite bakery near the old flat, the one with the cardamom buns", level = MemoryLevel.IN_PASSING, feltMood = "fond")
        }
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.REFLECTED, now - 6 * 3_600_000L))
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.RESTED, now - 8 * 3_600_000L, note = "quiet hours"))
    }
}
