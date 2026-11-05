package dev.aiharness.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Launches the real app (Room, DataStore, Harness, Compose navigation) under Robolectric and walks
 * through the main screens: seeded persona, settings, starting a chat, and the no-model error path.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring), timeoutMillis = 10_000)

    @Test
    fun walksThroughTheMainScreens() {
        // Home: no model configured yet, so the setup hint shows.
        waitForText("Set up a model")

        // Personas tab lists the seeded persona.
        compose.onAllNodesWithText("Personas").onFirst().performClick()
        waitForText("Aria")

        // Settings tab shows the heartbeat controls.
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        waitForText("Heartbeat")
        compose.onNodeWithText("Proactive").assertExists()

        // Start a letter to Aria; the greeting has {{user}} filled in.
        compose.onAllNodesWithText("Letters").onFirst().performClick()
        compose.onNodeWithText("New letter").performClick()
        waitForText("Write to…")
        compose.onAllNodesWithText("Aria").onFirst().performClick()
        waitForText("Hi You! I'm Aria", substring = true)

        // Sending without a model surfaces a clear error instead of crashing.
        compose.onNode(hasSetTextAction()).performTextInput("Hello!")
        compose.onNodeWithContentDescription("Send").performClick()
        waitForText("Add a model profile", substring = true)
    }
}
