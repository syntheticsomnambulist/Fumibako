package dev.aiharness.desktop

import dev.aiharness.app.platform.AudioEvents
import dev.aiharness.app.platform.FxAudio
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JavaFX's player on this computer, with real files: only when FUMIBAKO_REAL_AUDIO names a folder
 * of songs (it needs a display, and a sound device or a stand-in). Each plays a moment, or says it
 * can't (then the app opens it in the computer's player).
 */
class RealAudioCheck {
    @Test
    fun songsPlayOrSayTheyCant() {
        val dir = System.getenv("FUMIBAKO_REAL_AUDIO")?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue("set FUMIBAKO_REAL_AUDIO to a folder of songs", dir != null && dir.isDirectory)
        val results = dir!!.listFiles()!!.filter { it.isFile }.sortedBy { it.name }.associate { file ->
            val audio = FxAudio()
            val done = CountDownLatch(1)
            var outcome = "no answer"
            var length = 0L
            audio.load(
                file.toURI().toString(),
                object : AudioEvents {
                    override fun ready(durationMs: Long) {
                        length = durationMs
                        outcome = "ready"
                        done.countDown()
                    }

                    override fun failed(why: String) {
                        outcome = "can't: $why"
                        done.countDown()
                    }
                },
            )
            done.await(15, TimeUnit.SECONDS)
            if (outcome == "ready") {
                Thread.sleep(1_000)
                outcome = "played: ${length}ms long, at ${audio.positionMs}ms after a second, playing=${audio.playing}"
            }
            audio.stop()
            file.name to outcome
        }
        results.forEach { (name, outcome) -> println("$name: $outcome") }
        assertTrue(results.values.any { it.startsWith("played") })
    }
}
