package dev.aiharness.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** Back puts a letter's tray away (its sticker page first) before it leaves the letter. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrayBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun described(text: String) = compose.onAllNodes(hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun backClosesTheTrayBeforeLeavingTheLetter() {
        compose.waitUntilAtLeastOneExists(hasText("Personas"), 10_000)
        compose.onAllNodesWithText("Personas").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Aria"), 10_000)
        compose.onAllNodesWithText("Aria").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Write a letter"), 10_000)
        compose.onAllNodesWithText("Write a letter").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Add something"), 10_000)

        compose.onNodeWithContentDescription("Add something").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Stickers"), 10_000)
        compose.onAllNodesWithText("Stickers").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Hold and drag", substring = true), 10_000)

        // The sticker page goes back to the tray.
        back()
        assertTrue(!shown("Hold and drag") && shown("Stickers"), "back on the tray")
        // The tray closes, and the letter stays.
        back()
        assertTrue(!shown("Stickers") && described("Add something"), "tray closed, letter still open")
        // Then back leaves the letter.
        back()
        assertTrue(!described("Add something"), "left the letter")
    }
}
