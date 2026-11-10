package dev.aiharness.desktop

import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One Fumibako at a time: a second start brings the first one's window forward, then quits. */
class SingleInstanceTest {
    @Test
    fun aSecondStartAsksTheFirstToShowItself() {
        val dir = Files.createTempDirectory("fumibako-instance").toFile()
        val shown = CountDownLatch(1)
        val first = SingleInstance(dir)
        assertTrue(first.claim { shown.countDown() }, "the first start runs")

        assertFalse(SingleInstance(dir).claim { error("the second shouldn't listen") }, "the second doesn't")
        assertTrue(shown.await(5, TimeUnit.SECONDS), "the first was asked to show its window")

        first.release()
        val third = SingleInstance(dir)
        assertTrue(third.claim { }, "after the first quits, a new start runs")
        third.release()
    }

    @Test
    fun aSecondStartHandsOverTheFilesItWasOpenedWith() {
        val dir = Files.createTempDirectory("fumibako-instance").toFile()
        val backup = java.io.File(dir, "aria-2026-11-06 (copy).fumibako").apply { writeText("zip") }
        val handed = java.util.concurrent.atomic.AtomicReference<List<java.io.File>>()
        val shown = CountDownLatch(1)
        val first = SingleInstance(dir)
        assertTrue(first.claim(onOpen = { handed.set(it) }) { shown.countDown() })

        assertFalse(SingleInstance(dir).claim(listOf(backup)) { error("the second shouldn't listen") })
        assertTrue(shown.await(5, TimeUnit.SECONDS), "the first was asked to show its window")
        kotlin.test.assertEquals(listOf(backup.absoluteFile), handed.get(), "and given the file, spaces and all")
        first.release()
    }

    @Test
    fun theFilesAmongTheArgumentsAreTheOnesThatExist() {
        val dir = Files.createTempDirectory("fumibako-args").toFile()
        val backup = java.io.File(dir, "aria.fumibako").apply { writeText("zip") }
        kotlin.test.assertEquals(
            listOf(backup.absoluteFile),
            filesIn(arrayOf("--background", backup.path, java.io.File(dir, "gone.fumibako").path, dir.path)),
        )
    }
}
