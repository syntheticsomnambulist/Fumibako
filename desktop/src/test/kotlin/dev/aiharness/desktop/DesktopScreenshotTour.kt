package dev.aiharness.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.aiharness.app.AppContainer
import dev.aiharness.app.ui.AppNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalTestApi::class)
class DesktopScreenshotTour {
    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    private fun DesktopComposeUiTest.shot(name: String) {
        waitForIdle()
        val bitmap = onRoot().captureToImage().asSkiaBitmap()
        Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(outDir, "desktop_$name.png").writeBytes(it) }
    }

    @Test
    fun spike() {
        assumeTrue(outDir != null)
        val container = AppContainer(DesktopPlatform(Files.createTempDirectory("fumibako-tour").toFile(), keychain = { null }))
        container.start()
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
        try {
        runDesktopComposeUiTest(1180, 780) {
            try {
            setContent { FumibakoWindow(container) { AppNavigation(MutableStateFlow(null)) } }
            waitUntilAtLeastOneExists(hasText("Getting started"), 10_000)
            shot("spike_01"); System.err.println("SPIKE at 1")
            onAllNodes(hasText("Settings")).onFirst().performClick()
            try { waitUntilAtLeastOneExists(hasText("CONTENTS"), 10_000) } catch (t: Throwable) {
                shot("spike_02_fail"); System.err.println("SPIKE: " + t); System.err.println(onRoot().printToString()); throw t }
            shot("spike_02")
            onAllNodes(hasText("About")).onFirst().performClick()
            waitUntilAtLeastOneExists(hasText("Thanks and licenses"), 10_000)
            shot("spike_03")
            } catch (t: Throwable) { System.err.println("SPIKE body: " + t.stackTraceToString().take(3000)); throw t }
        }
        } finally { @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) kotlinx.coroutines.Dispatchers.resetMain() }
    }
}
