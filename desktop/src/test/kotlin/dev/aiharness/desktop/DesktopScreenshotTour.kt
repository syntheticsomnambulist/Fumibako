package dev.aiharness.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.aiharness.app.AppContainer
import dev.aiharness.app.platform.BackHandlers
import dev.aiharness.app.ui.AppNavigation
import dev.aiharness.core.model.ActivityEntry
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.AttachmentMark
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.ChatMessage
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.Conversation
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Holiday
import dev.aiharness.core.model.HolidaySettings
import dev.aiharness.core.model.HolidayYear
import dev.aiharness.core.model.Keepsake
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.LetterSettings
import dev.aiharness.core.model.MarkKind
import dev.aiharness.core.model.MemoryKind
import dev.aiharness.core.model.MemoryLevel
import dev.aiharness.core.model.MessageOrigin
import dev.aiharness.core.model.MessageRole
import dev.aiharness.core.model.ModelProfile
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PersonaPlanner
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.PronounKind
import dev.aiharness.core.model.Pronouns
import dev.aiharness.core.model.ProviderType
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.StickerArt
import dev.aiharness.core.model.StickerArtKind
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import dev.aiharness.core.model.Track
import dev.aiharness.core.model.UserPersona
import dev.aiharness.core.model.Weekly
import dev.aiharness.core.model.WrittenWith
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * The desktop's screenshot tour: the real desktop app (its platform, Room on the bundled SQLite,
 * the shared screens) offscreen at a new window's size, 1180 by 780, visiting the phone tour's
 * main screens with the same letters, personas, memories and planner. Skipped unless
 * HARNESS_SCREENSHOTS_DIR is set (no display needed):
 *   HARNESS_SCREENSHOTS_DIR=/tmp/shots ./gradlew :desktop:test --tests '*DesktopScreenshotTour*'
 *
 * The phone's tour (app/.../ScreenshotTour.kt) also tries what's done on the screens (stamps, the
 * bookmark, the burn); this one looks at how the computer shows them. Its seed is the phone tour's,
 * with Skia drawing the pictures and without the phone's calendars or the song search.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DesktopScreenshotTour {
    /** Early afternoon wherever it runs, as the phone's tour (its letters go back ten hours). */
    @get:Rule
    val afternoon = object : ExternalResource() {
        private var was: TimeZone? = null
        override fun before() {
            was = TimeZone.getDefault()
            val utcHour = ZonedDateTime.now(ZoneOffset.UTC).hour
            val offset = (14 - utcHour).let { if (it > 14) it - 24 else if (it < -12) it + 24 else it }
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofHours(offset)))
        }
        override fun after() {
            was?.let { TimeZone.setDefault(it) }
        }
    }

    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    @Test
    fun dayTour() = tour("day", TimeOfDay.HIRU) {
        firstLetters()
        letters()
        personas()
        planner()
        settings()
    }

    @Test
    fun nightTour() = tour("night", TimeOfDay.YORU) {
        firstLetters()
        letters()
        personas()
        planner()
    }

    /** A fresh start: the getting-started card, and About with the seal and its version. */
    @Test
    fun firstRunTour() {
        assumeTrue(outDir != null)
        val container = AppContainer(DesktopPlatform(Files.createTempDirectory("fumibako-tour").toFile(), keychain = { null }))
        container.start()
        runBlocking {
            val h = container.harness
            repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
            h.settings.update { it.copy(theme = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.AKI, staticTime = TimeOfDay.HIRU)) }
        }
        window(container, "first") {
            waitFor("Getting started")
            shot("01_letters")
            click("Settings")
            waitFor("CONTENTS")
            click("About")
            waitFor("Thanks and licenses")
            shot("02_about")
        }
    }

    // The tour's steps, each from Letters' list, where the one before left it.

    private fun Tour.firstLetters() {
        waitFor("Kestrel")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Unread"), 10_000)
        waitFor("Choose a letter")
        shot("01_letters")
    }

    private fun Tour.letters() {
        click("Kestrel")
        waitFor("arrives ")
        shot("01b_letter_on_its_way")
        // Her row (the first "Aria" is in While you were away), and a line only the letter has.
        click("Good morning!")
        waitFor("Like this!")
        settle()
        shot("02_letter")
        // The newest letter's tabs: what she had in mind, then her thoughts.
        tab("What she had in mind")
        waitFor("This turn")
        shot("02a1_what_she_had_in_mind")
        tab("Her thoughts")
        waitFor("HER THOUGHTS")
        shot("02a2_her_thoughts")
        tab("What she had in mind")
        tab("Her thoughts")
        // The + tray: what can go with a note on a computer (no camera).
        compose.onAllNodes(hasContentDescription("Add something")).onFirst().performClick()
        compose.waitForIdle()
        shot("02c0_tray")
        back()
        // Earlier that evening: a mix, a PDF, a cutting, photos, a song and a film.
        scrollTo("I made you something for when it rains")
        shot("02k_frames_mix")
        scrollTo("And these, from the walk")
        shot("02m_frames_photos")
    }

    private fun Tour.personas() {
        click("Personas")
        waitFor("Choose a persona")
        // The gallery comes a moment after the empty side; Juno is only in it.
        waitFor("Juno")
        settle()
        shot("03_personas")
        click("Aria")
        waitFor("Write a letter")
        settle()
        shot("04_persona_page")
        click("Edit", exact = true)
        waitFor("EDITING")
        shot("05_persona_editor")
        back()
        waitFor("Write a letter")
        click("See all")
        waitFor("Aria's memory book")
        settle()
        shot("20_memory_book")
        scrollTo("Sam drinks too much coffee")
        shot("20b_memory_book_today")
        click("Sam drinks too much coffee when stressed")
        waitFor("FELT WHEN KEPT")
        shot("21_memory_card")
        back()
        // Beside the gallery, the book has no back arrow: her polaroid opens her page again.
        click("Aria")
        waitFor("Memory book")
        scrollTo("Wakes now and then")
        shot("04b_persona_page_more")
        click("Settings", last = true)
        waitFor("Wakes up")
        shot("06_persona_heartbeat")
        back()
        // Her page again, scrolled about where it was (the heartbeat card; her memory book is above, off the screen).
        waitFor("Wakes now and then")
        scrollTo("All your personas")
        click("All your personas")
        waitFor("Your personas")
        shot("07_your_personas")
        click("Sam")
        waitFor("What they remember")
        shot("08_you_page")
        back()
        back()
        click("Letters")
        waitFor("Kestrel")
    }

    private fun Tour.planner() {
        click("Planner")
        waitFor("Notes this week")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Dinner with Jo", substring = true), 10_000)
        shot("24_planner_week")
        compose.onNode(hasClickActionLabel("Open the Month page")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Month, the page you're on"), 10_000)
        compose.waitUntilAtLeastOneExists(hasContentDescription("Founders' Day", substring = true), 10_000)
        shot("24j_planner_month")
        compose.onNode(hasClickActionLabel("Open the Week page")).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Week, the page you're on"), 10_000)
        click("Letters")
        waitFor("Kestrel")
    }

    private fun Tour.settings() {
        click("Settings")
        waitFor("CONTENTS")
        shot("09_settings")
        listOf(
            "Look" to "Day and night",
            // The desktop's own card: the tray and starting at login.
            "Heartbeat" to "On this computer",
            "Memory" to "Long-term memory",
            "Models and keys" to "Model profiles",
            "Pictures" to "Image profiles",
            "Stickers" to "Most on one letter",
            "Media" to "OPEN LINKS IN",
            "Planner" to "Your typical week",
        ).forEachIndexed { i, (section, marker) ->
            scrollTo(section, exact = true)
            // The last: "Planner" is also the side's tab.
            click(section, exact = true, last = true)
            waitFor(marker)
            shot("1${i}_settings_${section.substringBefore(' ').lowercase()}")
            when (section) {
                // No phone calendars on a computer; the holidays come from Nager.Date.
                "Planner" -> {
                    scrollTo("Change", exact = true)
                    shot("1${i}b_settings_planner_holidays")
                }
                "Models and keys" -> {
                    click("Manage keys")
                    waitFor("Your keys")
                    shot("1${i}b_settings_keys")
                    back()
                    waitFor(marker)
                }
                else -> {}
            }
            back()
            waitFor("CONTENTS")
        }
        scrollTo("About", exact = true)
        click("About", exact = true)
        waitFor("Thanks and licenses")
        shot("19_settings_about")
        back()
        click("Letters")
        waitFor("Kestrel")
    }

    // The tour's machinery.

    private class Tour(val compose: DesktopComposeUiTest, val prefix: String, val outDir: File, val backs: BackHandlers)

    private fun Tour.shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(outDir, "desktop_${prefix}_$name.png").writeBytes(it) }
    }

    private fun Tour.waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    /** Clicks [text]: the first such node, or the [last] (a page beside a list repeats the list's names). */
    private fun Tour.click(text: String, exact: Boolean = false, last: Boolean = false) {
        val nodes = compose.onAllNodes(hasText(text, substring = !exact))
        (if (last) nodes[nodes.fetchSemanticsNodes().size - 1] else nodes.onFirst()).performClick()
        compose.waitForIdle()
    }

    private fun Tour.tab(name: String) {
        compose.onAllNodes(hasContentDescription(name)).onFirst().performScrollTo().performClick()
        settle()
    }

    /** Escape, as the window's key handling does (a tray's or an overlay's handler); else the page's back arrow. */
    private fun Tour.back() {
        if (!compose.runOnIdle { backs.back() }) {
            val arrows = compose.onAllNodes(hasContentDescription("Back", substring = true))
            arrows[arrows.fetchSemanticsNodes().size - 1].performClick()
        }
        compose.waitForIdle()
    }

    /** Scrolls whichever list or page holds [text] (beside a list, the page is the later one). */
    private fun Tour.scrollTo(text: String, exact: Boolean = false) {
        val matcher = if (exact) hasText(text) else hasText(text, substring = true, ignoreCase = true)
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        for (i in lists.fetchSemanticsNodes().size - 1 downTo 0) {
            if (runCatching { lists[i].performScrollToNode(matcher) }.isSuccess) {
                compose.waitForIdle()
                return
            }
        }
        error("No list holds \"$text\"")
    }

    /** Lets pictures decoding in the background land before a picture is taken. */
    private fun Tour.settle() {
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private fun hasClickActionLabel(label: String) = SemanticsMatcher("has click label $label") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label
    }

    private fun tour(prefix: String, time: TimeOfDay, steps: Tour.() -> Unit) {
        assumeTrue(outDir != null)
        val container = AppContainer(DesktopPlatform(Files.createTempDirectory("fumibako-tour").toFile(), keychain = { null }))
        container.start()
        seed(container, time)
        window(container, prefix, steps)
    }

    /**
     * The window's content in an offscreen scene of the window's size, with Dispatchers.Main as
     * [TourMain] meanwhile.
     */
    private fun window(container: AppContainer, prefix: String, steps: Tour.() -> Unit) {
        Dispatchers.setMain(TourMain(Thread.currentThread()))
        try {
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                val backs = BackHandlers()
                setContent { FumibakoWindow(container, backs) { AppNavigation(MutableStateFlow(null)) } }
                val tour = Tour(this, prefix, outDir!!, backs)
                // Where it stopped, if it stops: a picture says more than the timeout. Given ten seconds,
                // since a page that never goes idle would otherwise hold the failure back for good.
                runCatching { tour.steps() }.onFailure { failure ->
                    kotlin.concurrent.thread(isDaemon = true) { runCatching { tour.shot("stopped_here") } }.join(10_000)
                    throw failure
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The phone tour's letters, personas, memories and planner (app/.../ScreenshotTour.kt, seed). */
    private fun seed(container: AppContainer, time: TimeOfDay, season: Season = Season.AKI) = runBlocking {
        val h = container.harness
        repeat(50) { if (h.personas.getAll().isEmpty()) Thread.sleep(100) }
        val now = System.currentTimeMillis()
        val aria = h.personas.getAll().first { it.name == "Aria" }
        h.personas.upsert(aria.copy(alsoPronouns = listOf(Pronouns(PronounKind.THEY)), letterBox = LetterBox(BoxFinish.WASHI, BoxLid.SEAL, BoxTie.BOW)))

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
                letters = LetterSettings(showArrival = true),
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
                // Kestrel's reply is in the post, arriving in a couple of hours.
                val asked = now + 30_000L
                val arrives = now + 2 * 3_600_000L + 25 * 60_000L
                h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.USER, listOf(ContentPart.Text("Are you still keeping watch out there?")), createdAt = asked, origin = MessageOrigin.USER))
                val reply = "Always. The road is long tonight, but the stars are good company."
                h.conversations.appendMessage(ChatMessage(newId(), c.id, MessageRole.ASSISTANT, listOf(ContentPart.Text(reply)), createdAt = asked + 20_000, deliverAt = arrives))
                h.updateConversation(c.id) { it.copy(updatedAt = asked + 20_000, lastUserMessageAt = asked, lastAgentMessageAt = arrives, preview = Conversation.previewOf(reply)) }
            }
        }

        val conversation = h.createConversation(aria.id, sam.id)
        h.conversations.deleteMessages(h.conversations.getMessages(conversation.id).map { it.id })
        val image = h.blobs.put(placeholderImage(), "png")
        var t = now - 3 * 3_600_000L
        fun msg(role: MessageRole, vararg parts: ContentPart, origin: MessageOrigin = MessageOrigin.AGENT) =
            ChatMessage(newId(), conversation.id, role, parts.toList(), createdAt = t.also { t += 60_000 }, origin = origin)
        val sonnet = WrittenWith("Claude Sonnet 5.5", ProviderType.ANTHROPIC, "claude-sonnet-5-5")
        val opus = WrittenWith("Claude Opus 5.5", ProviderType.ANTHROPIC, "claude-opus-5-5")
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
            msg(MessageRole.USER, ContentPart.Text("(heartbeat)"), origin = MessageOrigin.HEARTBEAT).copy(hidden = true),
            msg(MessageRole.ASSISTANT, ContentPart.Text("Good morning! Did you actually sleep after all that writing? *tilts head* [mood: fond]"), origin = MessageOrigin.HEARTBEAT),
        )
        // Earlier that evening: things sent as stationery.
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
                ContentPart.Attachment(
                    "song1", AttachmentKind.SONG, title = "October Walk", artist = "Hana Sato", album = "Paper Seasons", year = 2024, lengthMs = 222_000,
                    url = "https://music.apple.com/us/album/october-walk/1?i=2", previewOnly = true,
                ),
                ContentPart.Attachment("film1", AttachmentKind.VIDEO, ref = "file:not-here.mp4", mimeType = "video/mp4", title = "the river", lengthMs = 42_000, frameRefs = listOf(image, picture2, image)),
                origin = MessageOrigin.USER,
            ),
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
                ContentPart.Attachment("song2", AttachmentKind.SONG, url = "https://open.spotify.com/track/paperlanterns", title = "Paper Lanterns", artist = "The Lanterns", site = "Spotify"),
            ),
        )
        earlier.forEach { h.conversations.appendMessage(it) }
        h.marks.add(AttachmentMark("link2", MarkKind.READ, conversationId = conversation.id, byPersonaId = aria.id, at = now))
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
        h.updateConversation(conversation.id) { it.copy(updatedAt = now + 1, mood = "fond", lastAgentMessageAt = t, lastReadAt = now - 4 * 3_600_000L) }

        // Background activity since Sam "left".
        container.awaySince.value = now - 10 * 3_600_000L
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.WROTE_LETTER, now - 2 * 3_600_000L, conversation.id))
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.SAVED_MEMORIES, now - 2 * 3_600_000L - 60_000, conversation.id, count = 2))
        h.activity.add(ActivityEntry(newId(), juno.id, ActivityKind.STAYED_QUIET, now - 5 * 3_600_000L))

        // The memory book: today, last month (one replaced), and one worn page from the summer.
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
        val sentParts = earlier.flatMap { it.parts }
        fun kept(id: String) = Keepsake(earlier.first { m -> m.parts.any { (it as? ContentPart.Attachment)?.id == id || (it as? ContentPart.Image)?.ref == id } }.id,
            sentParts.first { (it as? ContentPart.Attachment)?.id == id || (it as? ContentPart.Image)?.ref == id }, keptAt = now)
        h.memory.save(aria.id, "Sam made me a rainy day mix, for when it rains", feltMood = "touched", keepsakes = listOf(kept("mix1")))
        h.memory.save(aria.id, "The walk along the river, in photos and a song", kind = MemoryKind.EPISODE, feltMood = "fond", keepsakes = listOf(kept(picture2), kept("song1"), kept("link2")))
        val library = keep("Works at the campus library", daysAgo = 27) { h.memory.save(aria.id, "Works at the campus library", aboutUserPersonaId = sam.id) }
        h.memory.save(aria.id, "Left the library in October to write full time", aboutUserPersonaId = sam.id, replaces = library.id, feltMood = "proud")
        h.memory.get(library.id)?.let { h.memories.upsert(it.copy(createdAt = now - 27 * day, updatedAt = now - 27 * day)) }
        keep("Had a cold the week of the conference", daysAgo = 25) { h.memory.save(aria.id, "Had a cold the week of the conference", level = MemoryLevel.IN_PASSING, feltMood = "worried", aboutUserPersonaId = sam.id) }
        keep("Mentioned a favorite bakery near the old flat, the one with the cardamom buns", daysAgo = 75) {
            h.memory.save(aria.id, "Mentioned a favorite bakery near the old flat, the one with the cardamom buns", level = MemoryLevel.IN_PASSING, feltMood = "fond")
        }
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.REFLECTED, now - 6 * 3_600_000L))
        h.activity.add(ActivityEntry(newId(), aria.id, ActivityKind.RESTED, now - 8 * 3_600_000L, note = "quiet hours"))

        // Sam's planner: a typical week, and a few plans this week.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        suspend fun plan(title: String, kind: EntryKind = EntryKind.PLAN, build: (PlannerEntry) -> PlannerEntry) =
            h.plannerEntries.upsert(build(PlannerEntry(newId(), sam.id, null, kind, title, createdAt = now - 86_400_000L, updatedAt = now - 86_400_000L)))
        plan("Work", EntryKind.ROUTINE) { it.copy(routine = RoutineKind.WORK, weekly = Weekly(listOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60 + 30), busy = true) }
        plan("Sleep", EntryKind.ROUTINE) { it.copy(routine = RoutineKind.SLEEP, weekly = Weekly((1..7).toList(), 23 * 60, 7 * 60), busy = true) }
        val week = (0L until 7L).map { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(it) }
        plan("Dinner with Jo") { it.copy(start = at(week[4], 19), end = at(week[4], 21), place = "Kettle & Leaf") }
        plan("Dentist") { it.copy(start = at(week[1], 8), end = at(week[1], 8, 45), busy = true, state = EntryState.DONE) }
        plan("Pottery") { it.copy(start = at(week[3], 19), end = at(week[3], 21), movedFrom = week[0].toString()) }
        plan("Call Grandma") { it.copy(start = at(week[6], 18), end = at(week[6], 19)) }
        plan("Interview") { it.copy(start = at(week[2], 15), end = at(week[2], 16), busy = true) }
        plan("Buy sencha") { it.copy(day = week[4].toString()) }
        plan("Return the library books") { it.copy(day = week[2].toString(), state = EntryState.DONE) }
        plan("Jo's birthday") { it.copy(day = week[5].toString(), allDay = true) }
        h.personas.upsert(h.personas.get(aria.id)!!.copy(planner = PersonaPlanner(seal = "歌")))
        h.personas.get(aria.id)?.let { h.personas.upsert(it.copy(modelSince = letters[4].createdAt - 30_000, previousModelName = "Claude Sonnet 5.5")) }
        suspend fun hers(by: String, kind: EntryKind, title: String, build: (PlannerEntry) -> PlannerEntry) =
            h.plannerEntries.upsert(build(PlannerEntry(newId(), sam.id, by, kind, title, createdAt = now - 3_600_000L, updatedAt = now - 3_600_000L)))
        hers(aria.id, EntryKind.NOTE, "ask how the interview went") { it.copy(day = week[2].toString()) }
        hers(aria.id, EntryKind.SUGGESTION, "Meteors? The shower peaks tonight") { it.copy(start = at(week[5], 22)) }
        hers(aria.id, EntryKind.REMINDER, "write after the interview") { it.copy(start = at(week[2], 16)) }
        hers(aria.id, EntryKind.HAIKU, "maple and ivy\nletting go of their summer\none leaf at a time") { it.copy(day = today.toString()) }
        h.stickerService.stick(
            StickerTarget.plannerConversation(sam.id), StickerTarget.day(sam.id, week[5].toString()),
            StickerRef.Emoji("🌠", "shooting_star"), aria.id, 0.5f, 0.25f, rotationDeg = 8f, scale = 0.7f,
        )
        hers(juno.id, EntryKind.AWAY, "resting after the move") { it.copy(start = now - 3_600_000L, end = at(week[6], 9)) }
        // Public holidays, as if already fetched (the tour stays offline): a made-up one on this week's Wednesday.
        fun fetched(y: Int) = HolidayYear("US", y, now, listOfNotNull(Holiday(week[2].toString(), "Founders' Day").takeIf { week[2].year == y }))
        h.settings.update { s ->
            s.copy(planner = s.planner.copy(holidays = HolidaySettings(show = true, country = "US", region = "US-MA", years = listOf(fetched(today.year), fetched(today.year + 1)))))
        }
    }

    /** A sticker's picture on a clear background: a heart, a star, a sun, or an inked ring for a stamp. */
    private fun stickerShape(kind: String): ByteArray = picture(200, 200) { canvas ->
        val paint = Paint().apply { isAntiAlias = true }
        when (kind) {
            "heart" -> {
                paint.color = Color(0xFFD80638)
                val path = Path().apply {
                    moveTo(100f, 178f)
                    cubicTo(20f, 120f, 10f, 60f, 55f, 42f)
                    cubicTo(80f, 32f, 96f, 48f, 100f, 62f)
                    cubicTo(104f, 48f, 120f, 32f, 145f, 42f)
                    cubicTo(190f, 60f, 180f, 120f, 100f, 178f)
                    close()
                }
                canvas.drawPath(path, paint)
                paint.color = Color(0x66FFFFFF)
                canvas.drawCircle(Offset(64f, 70f), 12f, paint)
            }
            "star" -> {
                paint.color = Color(0xFFE3940B)
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 92f else 38f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 100f + r * cos(a).toFloat()
                    val y = 104f + r * sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "sun" -> {
                paint.color = Color(0xFFE3940B)
                canvas.drawCircle(Offset(100f, 100f), 46f, paint)
                paint.strokeWidth = 12f
                paint.strokeCap = StrokeCap.Round
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    canvas.drawLine(
                        Offset(100f + 62f * cos(a).toFloat(), 100f + 62f * sin(a).toFloat()),
                        Offset(100f + 88f * cos(a).toFloat(), 100f + 88f * sin(a).toFloat()),
                        paint,
                    )
                }
                paint.color = Color(0xFF5D4C3C)
                canvas.drawCircle(Offset(84f, 94f), 6f, paint)
                canvas.drawCircle(Offset(116f, 94f), 6f, paint)
            }
            else -> {
                paint.color = Color(0xFFC43C32)
                paint.style = PaintingStyle.Stroke
                paint.strokeWidth = 10f
                canvas.drawCircle(Offset(100f, 100f), 80f, paint)
                canvas.drawCircle(Offset(100f, 100f), 40f, paint)
            }
        }
    }

    /** A photo's stand-in: lavender to pink. */
    private fun placeholderImage(): ByteArray = picture(416, 608) { canvas ->
        val paint = Paint().apply { shader = LinearGradientShader(Offset.Zero, Offset(416f, 608f), listOf(Color(0xFFB39DDB), Color(0xFFF8BBD0))) }
        canvas.drawRect(0f, 0f, 416f, 608f, paint)
    }

    private fun picture(width: Int, height: Int, draw: (Canvas) -> Unit): ByteArray {
        val bitmap = ImageBitmap(width, height)
        draw(Canvas(bitmap))
        return Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    /**
     * Main for the tour: work runs at once on the test's thread (input comes from it, and navigation,
     * through lifecycle, checks it's on Main's thread) or on Swing's, where the scene is measured and
     * drawn; work resuming anywhere else goes to Swing's, so it never changes what's on screen while
     * a page is being measured.
     */
    private class TourMain(private val testThread: Thread) : kotlinx.coroutines.MainCoroutineDispatcher() {
        override val immediate: kotlinx.coroutines.MainCoroutineDispatcher get() = this

        override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean =
            Thread.currentThread() != testThread && !javax.swing.SwingUtilities.isEventDispatchThread()

        // What it changed is announced at once, so the test's scene sees it on its next frame.
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) = javax.swing.SwingUtilities.invokeLater {
            block.run()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
    }

    private companion object {
        /** A new desktop window's size (Main.kt, FIRST). */
        const val WIDTH = 1180
        const val HEIGHT = 780
    }
}
