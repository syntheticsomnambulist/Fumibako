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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
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
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.util.newId
import dev.aiharness.app.media.NowPlaying
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Keepsake
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    /**
     * Runs the tour in a time zone where it's early afternoon, whenever it runs: the seeded letters
     * go back ten hours, and just after midnight they'd fall on yesterday's page (keepsakes and
     * all). Set before the app starts, so all of it reads the same clock.
     */
    @get:Rule(order = 0)
    val afternoon = object : org.junit.rules.ExternalResource() {
        private var was: java.util.TimeZone? = null
        override fun before() {
            was = java.util.TimeZone.getDefault()
            val utcHour = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).hour
            val offset = (14 - utcHour).let { if (it > 14) it - 24 else if (it < -12) it + 24 else it }
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneOffset.ofHours(offset)))
        }
        override fun after() {
            was?.let { java.util.TimeZone.setDefault(it) }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.let { File(it).apply { mkdirs() } }

    private fun shot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun waitFor(text: String) = orPicture("waiting for $text") { compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000) }

    /** [block]; if it fails, a picture of the screen as it was then (FAILED_…), to see why. */
    private fun <T> orPicture(what: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        runCatching { shot("FAILED_" + what.replace(Regex("[^A-Za-z0-9]+"), "_").take(60)) }
        throw e
    }

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()

    /** Lets the app's short waits pass (a link noticed once typing pauses; a search, the same): Robolectric's clock moves only when told. */
    private fun pause() {
        compose.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        compose.waitForIdle()
    }

    /** A stand-in for Apple's catalog, with songs that don't exist, and their covers. */
    private val catalog = okhttp3.mockwebserver.MockWebServer()

    @org.junit.After
    fun closeCatalog() = runCatching { catalog.shutdown() }.let { }

    private fun startCatalog(): String {
        val cover = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 120f, 120f, 0xFFE8B07A.toInt(), 0xFF8E5A7E.toInt(), Shader.TileMode.CLAMP) }
        Canvas(cover).drawRect(0f, 0f, 120f, 120f, paint)
        val png = ByteArrayOutputStream().also { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        catalog.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val path = request.path.orEmpty()
                if (path.startsWith("/art")) return okhttp3.mockwebserver.MockResponse().setBody(okio.Buffer().write(png))
                fun song(track: String, artist: String, album: String, year: Int, ms: Long) =
                    """{"wrapperType":"track","kind":"song","trackName":"$track","artistName":"$artist","collectionName":"$album","releaseDate":"$year-09-01T12:00:00Z",""" +
                        """"trackTimeMillis":$ms,"artworkUrl100":"${catalog.url("/art/100x100bb.jpg")}","previewUrl":"${catalog.url("/preview.m4a")}",""" +
                        """"trackViewUrl":"https://music.apple.com/us/album/x/1?i=${track.length}"}"""
                val results = listOf(
                    song("October Walk", "Hana Sato", "Paper Seasons", 2024, 222_000),
                    song("October Walk (Acoustic)", "Hana Sato", "Paper Seasons (Deluxe)", 2025, 236_000),
                    song("Walk Home in October", "The Lanterns", "Kettle Songs", 2019, 194_000),
                    song("Octobers", "Mira Lane", "Low Tide", 2021, 251_000),
                )
                return okhttp3.mockwebserver.MockResponse().setBody("""{"resultCount":${results.size},"results":[${results.joinToString(",")}]}""")
            }
        }
        catalog.start()
        return catalog.url("/").toString().trimEnd('/')
    }

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
        // Her box may be below the "While you were away" card on this small screen.
        scrollTo("Aria", exact = true)
        click("Aria")
        // Her box opens before her letter comes in (the list's strip already holds the words).
        compose.waitUntilAtLeastOneExists(hasContentDescription("Draw a picture"), 10_000)
        compose.onNodeWithContentDescription("Draw a picture").performClick()
        waitFor("What Aria is wearing")
        shot("picture_dialog")
    }

    /**
     * The wide layouts (docs/plan-desktop.md) in a new desktop window's size: the index tabs, Letters'
     * list beside the open letter, the gallery beside a page, and screens without a wide layout
     * centered beside the tabs.
     */
    @Test
    @Config(qualifiers = "w1180dp-h780dp-land-xhdpi")
    fun wideTour() {
        assumeTrue(outDir != null)
        seed(TimeOfDay.HIRU, Season.AKI)
        waitFor("Kestrel")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Unread"), 10_000)
        // Nothing opened yet in this window: the desk beside the list.
        waitFor("Choose a letter")
        shot("wide_01_letters")
        click("Aria")
        waitFor("actually sleep")
        // (Her heart sticker sits above the window's first screen since v0.5's tabs and notice.)
        listOf(":sleeping: sticker", ":gold-star: sticker").forEach { label ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(label), 10_000)
        }
        shot("wide_02_letter")
        // Another letter takes its place; the list stays.
        click("Kestrel")
        waitFor("Write to Kestrel")
        shot("wide_03_another_letter")
        click("Personas")
        waitFor("Choose a persona")
        shot("wide_04_personas")
        click("Aria")
        waitFor("Write a letter")
        shot("wide_05_persona_page")
        click("See all")
        waitFor("Aria's memory book")
        shot("wide_06_memory_book")
        click("Settings")
        waitFor("CONTENTS")
        shot("wide_07_settings")
        click("Planner")
        pause()
        shot("wide_08_planner")
        // Back to Letters: the letter that was open is open again.
        click("Letters")
        waitFor("Write to Kestrel")
        shot("wide_09_letters_again")
    }

    /** A bigger window, by night: room on each side of the letters. */
    @Test
    @Config(qualifiers = "w1600dp-h900dp-land-xhdpi")
    fun wideNightTour() {
        assumeTrue(outDir != null)
        seed(TimeOfDay.YORU, Season.AKI)
        waitFor("Kestrel")
        click("Aria")
        waitFor("actually sleep")
        listOf(":aria-heart: sticker", ":sleeping: sticker", ":gold-star: sticker").forEach { label ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(label), 10_000)
        }
        shot("wide_night_01_letter")
        click("Personas")
        waitFor("Choose a persona")
        click("Kestrel")
        waitFor("Write a letter")
        shot("wide_night_02_persona_page")
        // Exporting her, from her page: Personas stays lit in the rail, with the gallery beside.
        compose.onAllNodesWithContentDescription("More").onFirst().performClick()
        click("Export Kestrel…")
        waitFor("What goes in")
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("Personas") and androidx.compose.ui.test.isSelected()).assertExists()
        shot("wide_night_03_export_from_her_page")
    }

    private fun tour(prefix: String, time: TimeOfDay, season: Season = Season.AKI) {
        assumeTrue(outDir != null)
        seed(time, season)
        waitFor("Kestrel")
        // Aria's unread letter wears her sticker for hello.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Unread"), 10_000)
        shot("${prefix}_01_chats")
        // Kestrel's reply on its way: the postmark, and the little watch with when it arrives.
        click("Kestrel")
        waitFor("arrives ")
        shot("${prefix}_01b_letter_on_its_way")
        back()
        waitFor("Kestrel")

        // v0.5: Search letters, from the loupe by the title; a result opens its thread at the match,
        // with the strip for stepping through the others.
        compose.onNodeWithContentDescription("Search letters").performClick()
        waitFor("A word, a name, a place")
        compose.onNode(hasSetTextAction()).performTextInput("tea")
        waitFor("found · newest first")
        pause()
        shot("${prefix}_01c_search_letters")
        compose.onAllNodes(hasClickActionLabel("Open ", substring = true)).onFirst().performClick()
        waitFor("with Aria")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Older match"), 10_000)
        pause()
        shot("${prefix}_01d_found_in_the_letters")
        click("Done")
        back()
        back()
        waitFor("Kestrel")

        click("Aria")
        waitFor("actually sleep")
        // Stickers bake in the background; wait until the ones on screen are drawn (with large text,
        // her heart sticker is above the first screen).
        (if (prefix == "large") listOf(":sleeping: sticker", ":gold-star: sticker") else listOf(":aria-heart: sticker", ":sleeping: sticker", ":gold-star: sticker")).forEach { label ->
            compose.waitUntilAtLeastOneExists(hasContentDescription(label), 10_000)
        }
        shot("${prefix}_02_chat")
        // v0.5: the newest letter's two tabs pull out what she had in mind, then her thoughts (a slip
        // grows the letter upward, so its tabs are scrolled back to); then they're tucked away.
        fun tab(name: String) = compose.onAllNodesWithContentDescription(name).onFirst().performScrollTo().performClick()
        tab("What she had in mind")
        waitFor("This turn")
        pause()
        shot("${prefix}_02a1_what_she_had_in_mind")
        tab("Her thoughts")
        waitFor("HER THOUGHTS")
        pause()
        shot("${prefix}_02a2_her_thoughts")
        tab("What she had in mind")
        tab("Her thoughts")
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        pause()
        // The + and send buttons stand as tall as the field holding one line: its text (what the
        // field's own node covers) and its 10 dp above and below.
        val density = ApplicationProvider.getApplicationContext<HarnessApp>().resources.displayMetrics.density
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().size.height + (20 * density).toInt()
        listOf("Add something", "Send").forEach { label ->
            val button = compose.onNodeWithContentDescription(label).fetchSemanticsNode().size.height
            assertTrue(kotlin.math.abs(field - button) <= 1, "$label is $button px tall, the field $field")
        }
        // Writing a reply: the field grows and the send button stays.
        compose.onNode(hasSetTextAction()).performTextInput("You'll get to pick for yourself :3\nWe've just updated to a new version.")
        assertDrawn(compose.onNodeWithContentDescription("Send"), shot("${prefix}_02b_chat_typing"))
        // The + tray: tap a sticker onto your note.
        compose.onNodeWithContentDescription("Add something").performClick()
        waitFor("Camera")
        shot("${prefix}_02c0_tray")
        // Music: a search of the catalog (a local stand-in here), with songs from the phone a tap away.
        click("Music")
        waitFor("Find a song")
        shot("${prefix}_02c01_music_tray")
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("october")
        pause()
        waitFor("Paper Seasons")
        // The covers come from the web too.
        compose.waitUntil(10_000) { catalog.requestCount >= 3 }
        pause()
        shot("${prefix}_02c02_music_results")
        click("‹ Add")
        waitFor("Camera")
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
        pause()
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
        scrollTo("Juno")
        shot("${prefix}_03_personas")

        // A new persona: Done is disabled until it has a name, and must show once it does.
        scrollTo("New persona")
        click("New persona")
        waitFor("EDITING")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Wren")
        assertDrawn(compose.onNodeWithText("Done"), shot("${prefix}_03b_new_persona"))
        back()
        // The question comes up a moment after the back.
        compose.waitUntilAtLeastOneExists(hasText("Discard"), 10_000)
        click("Discard")
        // Back on the gallery, still scrolled down to "New persona": with large text its top (and
        // Juno's polaroid) isn't laid out until it's scrolled up.
        scrollTo("New persona")
        top()
        scrollTo("Juno")

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
        scrollTo("marks in your planner")
        shot("${prefix}_04e_persona_planner_marks")
        back()
        waitFor("Open sheet")
        // v0.5: her lines for the postmark, while a reply is on its way.
        scrollTo("Let Aria write her own")
        shot("${prefix}_04f_persona_on_its_way")

        top()
        click("Edit")
        waitFor("EDITING")
        shot("${prefix}_05_persona_editor")
        // v0.5: her hand, under her ink and tape.
        scrollTo("Hachi Maru Pop")
        shot("${prefix}_05d_persona_hand")
        scrollTo("Another set", exact = false)
        shot("${prefix}_05a_persona_pronouns")
        scrollTo("Drawing style")
        shot("${prefix}_05b_persona_editor_more")
        // v0.5: her model, since when, and whether she's told when it changes.
        scrollTo("Tell her when her model changes")
        shot("${prefix}_05c_persona_model")
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
        // By its action: scrolled to the list's foot, its middle can be under the tab bar.
        compose.onAllNodesWithText("All your personas").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor("Your personas")
        shot("${prefix}_07_your_personas")
        click("Sam")
        waitFor("What they remember")
        shot("${prefix}_08_you_page")
        back()
        back()

        memoryTour(prefix)
        plannerTour(prefix)

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
        scrollTo("THE THEME'S SEASONS FOLLOW")
        shot("${prefix}_17a_settings_look_seasons")
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

        // 十 Planner: the week page, the seasons, public holidays.
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasClickActionLabel("Open Planner"))
        compose.onNode(hasClickActionLabel("Open Planner")).performClick()
        // Its first card; the cards further down only come in as the page scrolls (at large text,
        // the week page's is off the screen).
        waitFor("Phone calendars")
        shot("${prefix}_18c_settings_planner")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("THE WEEK STARTS ON"))
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Hide them all"))
        compose.waitForIdle()
        shot("${prefix}_18c2_settings_phone_calendars")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Change"))
        compose.waitForIdle()
        shot("${prefix}_18d_settings_planner_holidays")
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

        transferTour(prefix)
    }

    /**
     * v0.5, 十一 Import and export: the settings page; Export (her backup, as a card, everything,
     * locked); a card coming in, as if opened from a file manager; and backups opened (hers while
     * she's here, a locked one, everything).
     */
    private fun transferTour(prefix: String) {
        val app = ApplicationProvider.getApplicationContext<HarnessApp>().container
        val dir = File(ApplicationProvider.getApplicationContext<HarnessApp>().cacheDir, "tour-files").apply { mkdirs() }
        fun incoming(file: File) = compose.runOnIdle { app.transfers.incoming.value = dev.aiharness.app.platform.Picked(android.net.Uri.fromFile(file).toString()) }

        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasClickActionLabel("Open Import and export"))
        compose.onNode(hasClickActionLabel("Open Import and export")).performClick()
        waitFor("Open a file…")
        shot("${prefix}_19_settings_transfer")
        click("Export…")
        waitFor("Your letters with")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(" · …", substring = true)).fetchSemanticsNodes().isEmpty() }
        shot("${prefix}_19a_export_persona")
        click("A character card")
        waitFor("as the lorebook")
        shot("${prefix}_19b_export_card")
        click("Everything")
        waitFor("Settings and models")
        // Its counts and sizes, once everything has been measured.
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(" · …", substring = true)).fetchSemanticsNodes().isEmpty() }
        scrollTo("Lock it with a password")
        click("Lock it with a password")
        waitFor("THE SAME AGAIN")
        scrollTo("can't be recovered")
        shot("${prefix}_19c_export_everything_locked")
        back()
        waitFor("Open a file…")

        // A card coming in.
        val card = File(dir, "mira_keeper.png").apply { writeBytes(tourCard()) }
        incoming(card)
        waitFor("Character Card V2")
        pause()
        shot("${prefix}_19d_import_card")
        scrollTo("See them")
        click("See them")
        scrollTo("The card's own prompts")
        shot("${prefix}_19e_import_card_lore_and_prompts")
        scrollTo("tokens of", exact = false)
        shot("${prefix}_19f_import_card_ink")
        scrollTo("Cancel", exact = true)
        click("Cancel")
        waitFor("Open a file…")

        // Her backup, opened while she's here; locked; and everything.
        val aria = runBlocking { app.harness.personas.getAll().first { it.name == "Aria" } }
        val hers = File(dir, "aria-2026-11-06.fumibako")
        val locked = File(dir, "aria-2026-11-06-locked.fumibako")
        val everything = File(dir, "fumibako-2026-11-06.fumibako")
        runBlocking {
            hers.outputStream().use { app.harness.backups.write(dev.aiharness.core.transfer.BackupRequest.OnePersona(aria.id), it, app = "0.5.0") }
            locked.outputStream().use { app.harness.backups.write(dev.aiharness.core.transfer.BackupRequest.OnePersona(aria.id), it, app = "0.5.0", password = "kinako".toCharArray(), iterations = 1_000) }
            everything.outputStream().use { app.harness.backups.write(dev.aiharness.core.transfer.BackupRequest.Everything(), it, app = "0.5.0") }
        }
        incoming(hers)
        waitFor("IN THE BACKUP")
        scrollTo("Aria is already here")
        shot("${prefix}_19g_open_backup")
        click("Replace her")
        scrollTo("Replace Aria")
        shot("${prefix}_19h_open_backup_replace")
        scrollTo("Cancel", exact = true)
        click("Cancel")
        waitFor("Open a file…")
        incoming(locked)
        waitFor(locked.name)
        scrollTo("This backup is locked")
        shot("${prefix}_19i_open_backup_locked")
        back()
        waitFor("Open a file…")
        incoming(everything)
        waitFor("Everything, from")
        scrollTo("It replaces what's here")
        shot("${prefix}_19j_open_backup_everything")
        scrollTo("Cancel", exact = true)
        click("Cancel")
        waitFor("Open a file…")
        back()
    }

    /** A character card as another app makes them: V2 in a PNG, with lore, greetings and its own prompts. */
    private fun tourCard(): ByteArray {
        val json = """{"spec":"chara_card_v2","spec_version":"2.0","data":{
            "name":"Mira",
            "description":"Keeps a lighthouse nobody asked her to keep. Writes long letters on the night shift, mostly about weather and ships that didn't come.",
            "personality":"Wry, patient, a little lonely.", "scenario":"Gull Point, the off season.",
            "first_mes":"Hello from the light. The lamp's lit, the kettle's on.",
            "alternate_greetings":["The ferry's late again.","Storm tonight. Write to me?","Found a bottle on the rocks today."],
            "mes_example":"<START>\n{{char}}: The lamp's lit. Tell me something true.",
            "creator_notes":"Mira starts out not knowing you. Let her warm up; she's at her best a few letters in. Lore covers the island and the ferry timetable.",
            "system_prompt":"You are {{char}} in a never-ending roleplay with {{user}}. Write 3 paragraphs per reply. Never speak for {{user}}.",
            "post_history_instructions":"Stay in character.",
            "tags":["slice of life","cozy","seaside","slow burn"], "creator":"tidepool", "character_version":"1.2",
            "extensions":{},
            "character_book":{"name":"Gull Point","entries":[
                {"id":0,"keys":["lighthouse","Gull Point","the light"],"content":"Gull Point light was switched off in 1998. Mira keeps it lit anyway, on oil she buys from the ferry.","enabled":true,"constant":true,"insertion_order":0,"extensions":{}},
                {"id":1,"keys":["Tomas","ferry","Tuesday"],"content":"Old Tomas runs the ferry on Tuesdays and Fridays, and owes Mira a favor he won't talk about.","enabled":true,"insertion_order":1,"extensions":{}},
                {"id":2,"keys":["telescope","mother"],"content":"Mira's mother left her a brass telescope with a cracked lens. She won't have it mended.","enabled":true,"insertion_order":2,"extensions":{}},
                {"id":3,"keys":["storm"],"content":"An old storm entry.","enabled":false,"insertion_order":3,"extensions":{}}
            ]}
        }}"""
        val portrait = dev.aiharness.core.transfer.Png.plain(96, 128, 0x2973B0)
        return dev.aiharness.core.transfer.Png.withTexts(portrait, listOf("chara" to java.util.Base64.getEncoder().encodeToString(json.toByteArray()))) { false }
    }

    /** 帳 Planner: the week with Sam's typical week and plans, a plan's stamps, moving one, a day, a plan's card, the typical week. */
    private fun plannerTour(prefix: String) {
        click("Planner")
        waitFor("THIS WEEK, ANY TIME")
        // Its plans come in from the database a moment later.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Dinner with Jo", substring = true), 10_000)
        compose.waitForIdle()
        shot("${prefix}_24_planner_week")
        // The phone's calendars in pencil (work's stand-up with its title, yoga only as busy).
        compose.waitUntilAtLeastOneExists(hasContentDescription("Stand-up", substring = true), 10_000)
        compose.onNodeWithContentDescription("Stand-up", substring = true).performScrollTo()
        compose.waitForIdle()
        shot("${prefix}_24k_planner_phone")
        // The bookmark: put on the week after, which it then goes back to from this week; and put back.
        compose.onNodeWithContentDescription("The week after").performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Your bookmark", substring = true)).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("The week before").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Your bookmark, on the week of", substring = true), 10_000)
        compose.onNode(hasContentDescription("Your bookmark, on the week of", substring = true)).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Your bookmark, on this page"), 10_000)
        compose.onNodeWithContentDescription("The week before").performClick()
        compose.onNode(hasContentDescription("Your bookmark", substring = true)).performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Your bookmark, on this page"), 10_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Dinner with Jo", substring = true), 10_000)
        // Stickers: the tray, and one tapped onto today.
        compose.onNode(hasClickActionLabel("Stick stickers on the planner")).performClick()
        waitFor("Hold and drag")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush:"), 10_000)
        compose.onAllNodesWithContentDescription(":blush:").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush: sticker"), 10_000)
        shot("${prefix}_24a_planner_stickers")
        compose.onAllNodesWithText("Close").onFirst().performClick()
        compose.waitForIdle()
        // A tap just under a short plan still picks it (an hour is small to aim at), not its day.
        // (From the top of the hours, so nothing scrolled to lands under the days laid over them.)
        weekToTop()
        compose.onNodeWithContentDescription("Call Grandma", substring = true).performScrollTo()
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(center.x, height + 6.dp.toPx())) }
        waitFor("Done")
        assertTrue(compose.onAllNodes(hasClickActionLabel("Stick stickers on the planner")).fetchSemanticsNodes().isNotEmpty(), "still on the week")
        compose.onNode(hasClickActionLabel("Put the stamps away")).performClick()
        compose.waitForIdle()
        // A plan's stamps, then the days to move it to.
        compose.onNodeWithContentDescription("Dinner with Jo", substring = true).performClick()
        waitFor("Done")
        shot("${prefix}_24b_planner_stamps")
        compose.onNodeWithContentDescription("Move it to another day").performClick()
        waitFor("MOVE TO")
        shot("${prefix}_24c_planner_move")
        compose.onNode(hasClickActionLabel("Put the stamps away")).performClick()
        // An all-day plan takes a tap for its stamps too, not straight to its card.
        compose.onNodeWithContentDescription("Jo's birthday", substring = true).performClick()
        waitFor("Done")
        assertTrue(compose.onAllNodesWithText("At a time").fetchSemanticsNodes().isEmpty(), "the stamps, not the card")
        compose.onNodeWithContentDescription("Stamp it done").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Jo's birthday", substring = true) and hasContentDescription("all day, done", substring = true), 10_000)
        shot("${prefix}_24c2_planner_all_day_done")
        compose.onNodeWithContentDescription("Jo's birthday", substring = true).performClick()
        compose.onNodeWithContentDescription("Lift the done stamp").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Jo's birthday", substring = true) and hasContentDescription("all day. Tap", substring = true), 10_000)
        // The sleep hours open, and fold again.
        weekToTop()
        compose.onAllNodes(hasClickActionLabel("Unfold your sleep hours")).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        shot("${prefix}_24d_planner_unfolded")
        weekToTop()
        compose.onAllNodes(hasClickActionLabel("Fold your sleep hours")).onFirst().performScrollTo().performClick()
        // The メモ area, with Aria's slips, and a plan's card.
        compose.onNodeWithText("Add it").performScrollTo()
        shot("${prefix}_24e_planner_memo")
        compose.onNodeWithText("Your typical week").performScrollTo()
        shot("${prefix}_24e2_planner_memo_slips")
        // Her suggestion, taken down: it peels off, and a quiet line has "Put it back".
        compose.onNodeWithText("Not this time").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Taken down", substring = true), 10_000)
        compose.onNodeWithText("Taken down", substring = true).performScrollTo()
        compose.waitForIdle()
        shot("${prefix}_24e3_planner_taken_down")
        compose.onNodeWithText("Put it back").performClick()
        // Then added: it's your plan now, with her note beside it.
        compose.waitUntilAtLeastOneExists(hasText("Add it"), 10_000)
        compose.onNodeWithText("Add it").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("ADDED TO YOUR PLANNER", substring = true), 10_000)
        click("+ Add a plan")
        waitFor("At a time")
        shot("${prefix}_24f_plan_card")
        compose.onNodeWithText("Cancel").performClick()
        click("Your typical week")
        waitFor("comes round each week")
        shot("${prefix}_24g_typical_week")
        back()
        // A day.
        val friday = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).plusDays(4)
        compose.onAllNodes(hasClickActionLabel("Open ${friday.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)} ${friday.dayOfMonth}", substring = true)).onFirst().performClick()
        waitFor("Add a plan")
        // The sheet's corner for the day's stickers ("Add a plan" is on the week too, so this is
        // also the wait for the day page to have come in).
        compose.waitUntil(10_000) { runCatching { compose.onNode(hasClickActionLabel("Stick stickers on this day")).assertIsDisplayed() }.isSuccess }
        shot("${prefix}_24h_planner_day")
        back()
        // Today's page: the now line, and what has ended waiting for its stamp.
        val today = java.time.LocalDate.now()
        compose.onAllNodes(hasClickActionLabel("Open ${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)} ${today.dayOfMonth}", substring = true)).onFirst().performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNode(hasClickActionLabel("Stick stickers on this day")).assertIsDisplayed() }.isSuccess }
        compose.waitForIdle()
        shot("${prefix}_24h2_planner_today")
        back()
        // The month page, and back to the week.
        compose.onNode(hasClickActionLabel("Open the Month page")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Month, the page you're on"), 10_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Founders' Day", substring = true), 10_000)
        shot("${prefix}_24j_planner_month")
        compose.onNode(hasClickActionLabel("Open the Week page")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Week, the page you're on"), 10_000)
    }

    /** The week's hours scrolled back to midnight. */
    private fun weekToTop() {
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(hasClickActionLabel("Open ", substring = true))).onFirst()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -100_000f) }
        compose.waitForIdle()
    }

    private fun hasClickActionLabel(label: String, substring: Boolean = false) = androidx.compose.ui.test.SemanticsMatcher("has click label $label") { node ->
        val l = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label ?: return@SemanticsMatcher false
        if (substring) label in l else l == label
    }

    private fun memoryTour(prefix: String) {
        topWith("Aria")
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
        // Hold a keepsake to stick a sticker on it; it shows on the letter's cassette too.
        compose.onNodeWithText("RAINY DAY MIX").performTouchInput { longClick() }
        waitFor("Hold and drag")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush:"), 10_000)
        click(":blush:")
        compose.waitUntilAtLeastOneExists(hasContentDescription(":blush: sticker"), 10_000)
        shot("${prefix}_20b3_sticker_on_keepsake")
        click("Close")
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

    /**
     * Back at the top of the page showing [text]. Just after a back, the page being left can still
     * be there (under load, as when every suite runs at once) and get scrolled instead, so try
     * again until [text] shows.
     */
    private fun topWith(text: String) {
        repeat(30) {
            top()
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
            compose.waitForIdle()
        }
        waitFor(text)
    }

    private fun top() {
        // Just back from a card, the list may still be on its way in.
        compose.waitUntilAtLeastOneExists(hasScrollToIndexAction(), 10_000)
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        compose.waitForIdle()
    }

    /**
     * Scrolls the first list to [text], once that list holds it. A lazy list lays out only what's on
     * screen, and just after going back, the page being left can still be the first list there (on a
     * busy machine), so the scroll is tried again until it finds [text].
     */
    private fun scrollTo(text: String, exact: Boolean = false) = orPicture("scrolling to $text") {
        val matcher = if (exact) hasText(text) else hasText(text, substring = true, ignoreCase = true)
        compose.waitUntil(10_000) { runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(matcher) }.isSuccess }
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
        h.music.base = startCatalog()
        // Wait for first-launch seeding (Aria + default user persona).
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val now = System.currentTimeMillis()
        val aria = h.personas.getAll().first { it.name == "Aria" }
        // Aria goes by she and they: the app's sentences take turns.
        // Set boxes, so the pictures are the same every run (a box picked at random comes from the persona's id, new each run).
        h.personas.upsert(
            aria.copy(
                alsoPronouns = listOf(Pronouns(PronounKind.THEY)),
                letterBox = LetterBox(BoxFinish.WASHI, BoxLid.SEAL, BoxTie.BOW),
            ),
        )

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
                // Letters in the post, with the little watch saying when one arrives.
                letters = dev.aiharness.core.model.LetterSettings(showArrival = true),
            )
        }

        val kestrel = Persona(
            newId(), "Kestrel", "A stoic knight-errant sworn to an order that no longer exists.", personality = "Dry humor, fiercely loyal.",
            greeting = "You keep late hours. So do I. Sit, if you like.", ink = "tokiwa", tapePattern = TapePattern.STRIPES, createdAt = now, updatedAt = now,
            letterBox = LetterBox(BoxFinish.PAULOWNIA, BoxLid.KANJI, BoxTie.CROSSWISE),
        )
        val juno = Persona(
            newId(), "Juno", "A sharp-tongued tech witch who runs a repair shop in a rainy megacity.", personality = "Sarcastic, secretly soft.",
            greeting = "Shop's open. Don't touch the cat, she bites the curious.", ink = "kikyo", tapePattern = TapePattern.GINGHAM, createdAt = now, updatedAt = now,
            letterBox = LetterBox(BoxFinish.LACQUER, BoxLid.GILDED, BoxTie.MIZUHIKI, chosenByYou = true),
        )
        listOf(kestrel to "watchful", juno to "smug").forEach { (persona, mood) ->
            h.personas.upsert(persona)
            val c = h.createConversation(persona.id, sam.id)
            h.updateConversation(c.id) { it.copy(mood = mood, updatedAt = now - 86_400_000L, lastAgentMessageAt = now - 86_400_000L) }
            if (persona == kestrel) {
                // Kestrel's reply is in the post, arriving in a couple of hours (asked after the greeting).
                val asked = now + 30_000L
                val arrives = now + 2 * 3_600_000L + 25 * 60_000L
                h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.USER, listOf(ContentPart.Text("Are you still keeping watch out there?")), createdAt = asked, origin = MessageOrigin.USER))
                val reply = "Always. The road is long tonight, but the stars are good company."
                h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.ASSISTANT, listOf(ContentPart.Text(reply)), createdAt = asked + 20_000, deliverAt = arrives))
                h.updateConversation(c.id) { it.copy(updatedAt = asked + 20_000, lastUserMessageAt = asked, lastAgentMessageAt = arrives, preview = dev.aiharness.core.model.Conversation.previewOf(reply)) }
            }
        }

        val conversation = h.createConversation(aria.id, sam.id)
        h.conversations.deleteMessages(h.conversations.getMessages(conversation.id).map { it.id })
        val image = h.blobs.put(placeholderImage(), "png")
        var t = now - 3 * 3_600_000L
        fun msg(role: MessageRole, vararg parts: ContentPart, origin: MessageOrigin = MessageOrigin.AGENT) =
            ChatMessage(newId(), conversation.id, role, parts.toList(), createdAt = t.also { t += 60_000 }, origin = origin)
        // v0.5: what she had in mind (the stored context), the turn's tokens, her model, and a letter she looked up.
        val sonnet = dev.aiharness.core.model.WrittenWith("Claude Sonnet 5.5", ProviderType.ANTHROPIC, "claude-sonnet-5-5")
        val opus = dev.aiharness.core.model.WrittenWith("Claude Opus 5.5", ProviderType.ANTHROPIC, "claude-opus-5-5")
        val tokens = dev.aiharness.core.model.TokenUsage(inputTokens = 1_200, outputTokens = 310, cacheReadTokens = 16_800)
        val finished = msg(MessageRole.USER, ContentPart.Text("I finally finished the first draft of my thesis!"), origin = MessageOrigin.USER).copy(
            turnContext = "Current time: Monday, 9 November 2026, 21:04.\nTime since the previous message: 3 hours.\n" +
                "Sam's planner: free this evening. Tomorrow: work, 09:00 to 17:30.\n" +
                "Memories that come to mind:\n- (2 Nov 2026, felt worried, now hopeful) Sam's thesis is due in December.",
        )
        val letters = listOf(
            finished,
            msg(
                MessageRole.ASSISTANT,
                ContentPart.Reasoning("Sam has been working on this for months. This is a big milestone worth remembering."),
                ContentPart.Text("*gasps and claps both hands over her mouth* Sam! That's **huge**. After all those late nights?"),
                ContentPart.ToolCall("t1", "memory_save", """{"content":"Sam finished the first draft of the thesis"}"""),
            ).copy(providerType = ProviderType.ANTHROPIC, usage = tokens, writtenWith = sonnet),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t1", "memory_save", "Saved.")),
            msg(MessageRole.ASSISTANT, ContentPart.Text("I'm writing this one down. How do you feel? :aria-heart:")).copy(writtenWith = sonnet),
            msg(MessageRole.USER, ContentPart.Text("Tired. Happy. Can you show me what you'd look like celebrating?"), origin = MessageOrigin.USER).copy(
                turnContext = "Current time: Monday, 9 November 2026, 21:06.\n" +
                    "Your model changed: you were Claude Sonnet 5.5 until now and are Claude Opus 5.5 from here. Your memories and letters are the same.",
            ),
            msg(
                MessageRole.ASSISTANT,
                ContentPart.Reasoning("Celebrating! Something twirly. And she said 'first draft' earlier; look back so I get it right."),
                ContentPart.ToolCall("t3", "letter_search", """{"query":"thesis draft"}"""),
            ).copy(providerType = ProviderType.ANTHROPIC, usage = tokens, writtenWith = opus),
            msg(
                MessageRole.TOOL,
                ContentPart.ToolResult(
                    "t3", "letter_search", "1 passage:",
                    foundLetters = listOf(dev.aiharness.core.model.FoundLetter(finished.id, conversation.id, true, finished.createdAt, "I finally finished the first draft of my thesis!")),
                ),
            ),
            msg(MessageRole.ASSISTANT, ContentPart.Text("*twirls* Like this!"), ContentPart.ToolCall("t2", "generate_image", "{}")).copy(writtenWith = opus),
            msg(MessageRole.TOOL, ContentPart.ToolResult("t2", "generate_image", "Shown."), ContentPart.Image(image, caption = "celebrating")),
            // A heartbeat's own prompt, hidden, before the letter it wrote while Sam was away.
            msg(MessageRole.USER, ContentPart.Text("(heartbeat)"), origin = MessageOrigin.HEARTBEAT).copy(hidden = true),
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
                // Picked from the song search: its preview, and the whole song in Apple Music.
                ContentPart.Attachment(
                    "song1", AttachmentKind.SONG, title = "October Walk", artist = "Hana Sato", album = "Paper Seasons", year = 2024, lengthMs = 222_000,
                    url = "https://music.apple.com/us/album/october-walk/1?i=2", previewOnly = true,
                ),
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

        // Sam's planner: a typical week, and a few plans this week (one done, one moved, one with no time).
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        fun at(day: java.time.LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        suspend fun plan(title: String, kind: dev.aiharness.core.model.EntryKind = dev.aiharness.core.model.EntryKind.PLAN, build: (dev.aiharness.core.model.PlannerEntry) -> dev.aiharness.core.model.PlannerEntry) =
            h.plannerEntries.upsert(build(dev.aiharness.core.model.PlannerEntry(newId(), sam.id, null, kind, title, createdAt = now - 86_400_000L, updatedAt = now - 86_400_000L)))
        plan("Work", dev.aiharness.core.model.EntryKind.ROUTINE) { it.copy(routine = dev.aiharness.core.model.RoutineKind.WORK, weekly = dev.aiharness.core.model.Weekly(listOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60 + 30), busy = true) }
        plan("Sleep", dev.aiharness.core.model.EntryKind.ROUTINE) { it.copy(routine = dev.aiharness.core.model.RoutineKind.SLEEP, weekly = dev.aiharness.core.model.Weekly((1..7).toList(), 23 * 60, 7 * 60), busy = true) }
        val week = (0L until 7L).map { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).plusDays(it) }
        plan("Dinner with Jo") { it.copy(start = at(week[4], 19), end = at(week[4], 21), place = "Kettle & Leaf") }
        plan("Dentist") { it.copy(start = at(week[1], 8), end = at(week[1], 8, 45), busy = true, state = dev.aiharness.core.model.EntryState.DONE) }
        plan("Pottery") { it.copy(start = at(week[3], 19), end = at(week[3], 21), movedFrom = week[0].toString()) }
        plan("Call Grandma") { it.copy(start = at(week[6], 18), end = at(week[6], 19)) }
        plan("Interview") { it.copy(start = at(week[2], 15), end = at(week[2], 16), busy = true) }
        plan("Buy sencha") { it.copy(day = week[4].toString()) }
        plan("Return the library books") { it.copy(day = week[2].toString(), state = dev.aiharness.core.model.EntryState.DONE) }
        plan("Jo's birthday") { it.copy(day = week[5].toString(), allDay = true) }
        // What Aria wrote in Sam's planner (signed with her seal), and Juno's time away.
        // From the store: the copy from the top of the seed would undo her pronouns and box.
        h.personas.upsert(h.personas.get(aria.id)!!.copy(planner = dev.aiharness.core.model.PersonaPlanner(seal = "歌")))
        // Her model was changed on her page just before Sam's second note.
        h.personas.get(aria.id)?.let { h.personas.upsert(it.copy(modelSince = letters[4].createdAt - 30_000, previousModelName = "Claude Sonnet 5.5")) }
        suspend fun hers(by: String, kind: dev.aiharness.core.model.EntryKind, title: String, build: (dev.aiharness.core.model.PlannerEntry) -> dev.aiharness.core.model.PlannerEntry) =
            h.plannerEntries.upsert(build(dev.aiharness.core.model.PlannerEntry(newId(), sam.id, by, kind, title, createdAt = now - 3_600_000L, updatedAt = now - 3_600_000L)))
        hers(aria.id, dev.aiharness.core.model.EntryKind.NOTE, "ask how the interview went") { it.copy(day = week[2].toString()) }
        hers(aria.id, dev.aiharness.core.model.EntryKind.SUGGESTION, "Meteors? The shower peaks tonight") { it.copy(start = at(week[5], 22)) }
        hers(aria.id, dev.aiharness.core.model.EntryKind.REMINDER, "write after the interview") { it.copy(start = at(week[2], 16)) }
        hers(aria.id, dev.aiharness.core.model.EntryKind.HAIKU, "maple and ivy\nletting go of their summer\none leaf at a time") { it.copy(day = today.toString()) }
        // And a sticker of hers on Saturday.
        h.stickerService.stick(
            dev.aiharness.core.model.StickerTarget.plannerConversation(sam.id), dev.aiharness.core.model.StickerTarget.day(sam.id, week[5].toString()),
            dev.aiharness.core.model.StickerRef.Emoji("🌠", "shooting_star"), aria.id, 0.5f, 0.25f, rotationDeg = 8f, scale = 0.7f,
        )
        h.personas.getAll().firstOrNull { it.name == "Juno" }?.let { juno ->
            hers(juno.id, dev.aiharness.core.model.EntryKind.AWAY, "resting after the move") { it.copy(start = now - 3_600_000L, end = at(week[6], 9)) }
        }
        // The phone's calendars, as a fake (the tour has none): work's stand-up with its title, yoga only as busy.
        val app = ApplicationProvider.getApplicationContext<HarnessApp>()
        app.container.phoneCalendars.source = object : dev.aiharness.core.planner.PhoneCalendars {
            override fun readable() = true
            override suspend fun calendars() = listOf(
                dev.aiharness.core.planner.PhoneCalendar("1", "Work", "sam@example.com", 0xFF4A7BB0.toInt()),
                dev.aiharness.core.planner.PhoneCalendar("2", "Home", "sam@example.com", 0xFF6FA86A.toInt()),
            )
            override suspend fun events(from: Long, to: Long, calendarIds: Set<String>) = listOf(
                dev.aiharness.core.planner.PhoneEvent(101, "1", "Stand-up", "Room 4", at(week[1], 10), at(week[1], 10, 30)),
                dev.aiharness.core.planner.PhoneEvent(102, "2", "Yoga", null, at(week[3], 7), at(week[3], 8)),
            ).filter { it.calendarId in calendarIds && it.start < to && it.end > from }
        }
        h.settings.update { s ->
            s.copy(planner = s.planner.copy(phone = dev.aiharness.core.model.PhoneCalendarSettings(mapOf("1" to dev.aiharness.core.model.PhoneDetail.TITLES, "2" to dev.aiharness.core.model.PhoneDetail.BUSY))))
        }
        // Public holidays, as if already fetched (the tour stays offline): a made-up one on this week's Wednesday.
        fun fetched(y: Int) = dev.aiharness.core.model.HolidayYear(
            "US", y, now, listOfNotNull(dev.aiharness.core.model.Holiday(week[2].toString(), "Founders' Day").takeIf { week[2].year == y }),
        )
        h.settings.update { s ->
            s.copy(planner = s.planner.copy(holidays = dev.aiharness.core.model.HolidaySettings(show = true, country = "US", region = "US-MA", years = listOf(fetched(today.year), fetched(today.year + 1)))))
        }
    }
}
