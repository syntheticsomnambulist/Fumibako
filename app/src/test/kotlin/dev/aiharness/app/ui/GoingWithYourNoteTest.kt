package dev.aiharness.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.ui.chat.Going
import dev.aiharness.app.ui.chat.GoingWithYourNote
import dev.aiharness.app.ui.chat.TrayItem
import dev.aiharness.app.ui.theme.HarnessTheme
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.AttachmentKind
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What waits under "Going with your note": things being made ready say how far along they are, and every × is whole. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GoingWithYourNoteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun preparingShowsHowFarAndTheRemoveButtonsAreWhole() = check("going_preparing")

    /** With the phone's text turned up, the percentage still shows (on a second line). */
    @Test
    @Config(fontScale = 1.3f)
    fun inLargeTextThePercentageStillShows() = check("going_preparing_large")

    private fun check(name: String) {
        // The running film is an endless animation: the clock moves only when told.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HarnessTheme(ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.AKI, staticTime = TimeOfDay.HIRU)) {
                Box(Modifier.background(paper.kami)) {
                    GoingWithYourNote(
                        listOf(
                            Going.Preparing("v1", "a video", TrayItem.VIDEO, progress = 0.42f),
                            Going.Preparing("v2", "a video", TrayItem.VIDEO),
                            // Three fit across the screen; a fourth would run on past its edge, where the strip scrolls.
                            Going.Item(ContentPart.Attachment("c", AttachmentKind.LINK, url = "https://sshhole.com/", site = "sshhole.com", title = "*sshole")),
                        ),
                        onRemove = {},
                        onOpen = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val text = compose.onNodeWithText("shrinking 42%", useUnmergedTree = true).fetchSemanticsNode()
        text.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        // The thumb is clipped to its tap target: the label needs room inside it for the "g"'s tail.
        val target = compose.onNode(
            androidx.compose.ui.test.hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasContentDescription("Getting a video ready, 42% done")),
        ).fetchSemanticsNode()
        val density = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        assertTrue(target.boundsInRoot.bottom - text.boundsInRoot.bottom >= 2 * density, "no room under the label for letters' tails")
        // Text that overflows is clipped to its box (a "g" loses its tail) or ends in "…".
        val l = layouts.first()
        assertTrue(!l.hasVisualOverflow && !l.isLineEllipsized(l.lineCount - 1), "the label is cut short (${l.lineCount} lines)")
        compose.onNodeWithContentDescription("Getting a video ready, 42% done").assertExists()

        val buttons = compose.onAllNodesWithText("×").fetchSemanticsNodes()
        assertEquals(3, buttons.size)
        buttons.forEach { b ->
            // Clipped by the strip, what shows would be smaller than the button.
            assertEquals(b.size.width.toFloat(), b.boundsInRoot.width, 0.5f, "a × cut at its side")
            assertEquals(b.size.height.toFloat(), b.boundsInRoot.height, 0.5f, "a × cut at its top")
        }

        System.getenv("HARNESS_SCREENSHOTS_DIR")?.let { dir ->
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 0)
        }
    }
}
