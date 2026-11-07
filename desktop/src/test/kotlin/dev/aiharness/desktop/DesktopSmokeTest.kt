package dev.aiharness.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.aiharness.app.AppContainer
import dev.aiharness.app.ui.AppNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * The real desktop app (Room on the bundled SQLite, DataStore, the shared screens) offscreen, in a
 * fresh folder: the first launch seeds Aria, and the letters page's getting-started card names you.
 * Set HARNESS_SCREENSHOTS_DIR to keep a picture.
 *
 * Only the first page for now: the test composes on its own thread, but navigating checks it's on
 * the window's (Swing's) thread. The desktop's screenshot tour (docs/plan-desktop.md, step 7) will
 * walk every page; until then the other pages are checked in a real window.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    private fun shot(name: String) {
        val dir = outDir ?: return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(dir, "desktop_$name.png").writeBytes(it) }
    }

    @Test
    fun firstLaunchSeedsAriaAndNamesYou() {
        val container = AppContainer(DesktopPlatform(Files.createTempDirectory("fumibako-desktop").toFile(), keychain = { null }))
        container.start()
        compose.setContent { FumibakoWindow(container) { AppNavigation(MutableStateFlow(null)) } }

        // No model yet: the getting-started card; naming yourself ticks step 1.
        compose.waitUntilAtLeastOneExists(hasText("Getting started"), timeoutMillis = 10_000)
        shot("01_letters")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Robin")
        compose.onNodeWithText("Keep").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Choose a model"), timeoutMillis = 10_000)

        runBlocking {
            assertEquals(listOf("Aria"), container.harness.personas.getAll().map { it.name })
            compose.waitUntil(10_000) { runBlocking { container.harness.userPersonas.observeAll().first().map { it.name } } == listOf("Robin") }
        }
    }
}
