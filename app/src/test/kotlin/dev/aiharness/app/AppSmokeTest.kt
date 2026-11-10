package dev.aiharness.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Launches the real app (Room, DataStore, Harness, Compose navigation) under Robolectric and walks
 * through the main screens: the seeded persona's page, adding one of your own personas, the settings
 * contents, starting a chat, and the no-model error path.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun walksThroughTheMainScreens() {
        // Home: no model configured yet, so the getting-started card shows; naming yourself ticks step 1.
        waitForText("Getting started")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Robin")
        compose.onNodeWithText("Keep").performClick()
        waitForText("Choose a model")

        // Personas tab lists the seeded persona; its page opens, edits in place, and back leaves editing.
        compose.onAllNodesWithText("Personas").onFirst().performClick()
        waitForText("Aria")
        compose.onAllNodesWithText("Aria").onFirst().performClick()
        waitForText("Write a letter")
        compose.onNodeWithText("Edit").performClick()
        waitForText("EDITING")
        back()
        waitForText("Edit")
        back()

        // Your personas: add one, and it opens on its own page.
        waitForText("New persona")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("All your personas"))
        compose.onNodeWithText("All your personas").performClick()
        waitForText("New you")
        compose.onNodeWithText("New you").performClick()
        waitForText("EDITING")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Wren")
        compose.onNodeWithText("Done").performClick()
        waitForText("New letter as Wren")
        back()
        waitForText("Wren")
        back()

        // Settings is a contents page; each section opens its own page.
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        waitForText("CONTENTS")
        waitForText("PROACTIVE", substring = true)
        compose.onNodeWithText("Heartbeat").performClick()
        waitForText("Time only")
        back()

        // Start a letter to Aria; the greeting has {{user}} filled in.
        compose.onAllNodesWithText("Letters").onFirst().performClick()
        compose.onNodeWithText("New letter").performClick()
        waitForText("Write to…")
        compose.onAllNodesWithText("Aria").onFirst().performClick()
        // The new letter also shows in the list behind, so wait for the letter's own composer.
        compose.waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 10_000)
        waitForText("Hi Robin! I'm Aria", substring = true)

        // Sending without a model surfaces a clear error instead of crashing.
        compose.onNode(hasSetTextAction()).performTextInput("Hello!")
        compose.onNodeWithContentDescription("Send").performClick()
        waitForText("Add a model profile", substring = true)
    }
}
