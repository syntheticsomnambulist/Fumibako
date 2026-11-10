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
}
