package dev.aiharness.app.ui.planner

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.HarnessApp
import dev.aiharness.app.MainActivity
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.test.assertTrue

/** A day's page shows the stickers on the day it shows, and they change as it goes to the next. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
// Native graphics, so the stickers are drawn (and described) as on a phone.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DayPageStickersTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun described(label: String) = compose.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty()

    private fun hasClickLabel(label: String) = SemanticsMatcher("has click label $label") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.OnClick) { null }?.label == label
    }

    private fun words(day: LocalDate) = "${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, Locale.US)}"

    @Test
    fun goingToTheNextDayShowsItsOwnStickers() {
        val h = ApplicationProvider.getApplicationContext<HarnessApp>().container.harness
        // Today in the week shown, and the day after it (in this week or the next: the page goes either way).
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        runBlocking {
            val you = run {
                repeat(50) { if (h.userPersonas.observeAll().first().isEmpty()) Thread.sleep(100) }
                h.userPersonas.observeAll().first().first()
            }
            val planner = StickerTarget.plannerConversation(you.id)
            h.stickerService.stick(planner, StickerTarget.day(you.id, today.toString()), StickerRef.Emoji("😊", "blush"), null, 0.5f, 0.3f)
            h.stickerService.stick(planner, StickerTarget.day(you.id, tomorrow.toString()), StickerRef.Emoji("😴", "sleeping"), null, 0.5f, 0.3f)
        }

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Planner").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Planner").onFirst().performClick()
        // Its heading over the week's columns (a tap on the column would land on its sticker).
        val heading = hasClickLabel("Open ${words(today)}") and hasContentDescription(words(today), substring = true)
        compose.waitUntil(10_000) { compose.onAllNodes(heading).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(heading).onFirst().performClick()

        // Today's page: its own sticker.
        compose.waitUntil(10_000) { described(":blush: sticker") }
        assertTrue(!described(":sleeping: sticker"), "only today's sticker on today's page")

        // The day after: its sticker, and not today's.
        compose.onNodeWithContentDescription("The day after", substring = true).performClick()
        compose.waitUntil(10_000) { described(":sleeping: sticker") }
        assertTrue(!described(":blush: sticker"), "the day after doesn't keep today's sticker")
        compose.waitForIdle()
        val stepped = compose.onNode(hasContentDescription(":sleeping: sticker")).captureToImage().toPixelMap()

        // And back again.
        compose.onNodeWithContentDescription("The day before", substring = true).performClick()
        compose.waitUntil(10_000) { described(":blush: sticker") }
        assertTrue(!described(":sleeping: sticker"), "back on today, only today's")

        // The day after opened straight from the week draws its sticker the same as stepping there
        // did: stepping once kept today's picture under the day after's name.
        compose.onAllNodes(hasClickLabel("Open the Week page")).onFirst().performClick()
        if (tomorrow.dayOfWeek == java.time.DayOfWeek.MONDAY) compose.onNodeWithContentDescription("The week after").performClick()
        val next = hasClickLabel("Open ${words(tomorrow)}") and hasContentDescription(words(tomorrow), substring = true)
        compose.waitUntil(10_000) { compose.onAllNodes(next).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(next).onFirst().performClick()
        compose.waitUntil(10_000) { described(":sleeping: sticker") }
        compose.waitForIdle()
        val direct = compose.onNode(hasContentDescription(":sleeping: sticker")).captureToImage().toPixelMap()
        assertTrue(stepped.width == direct.width && stepped.height == direct.height, "the same size both ways")
        assertTrue(stepped.buffer.contentEquals(direct.buffer), "stepping to the day after draws its own sticker, not the day before's")
    }
}
