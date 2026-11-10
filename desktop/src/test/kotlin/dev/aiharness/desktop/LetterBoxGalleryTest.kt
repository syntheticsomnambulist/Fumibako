package dev.aiharness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import dev.aiharness.app.ui.chats.LetterBoxPicture
import dev.aiharness.app.ui.theme.HarnessTheme
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.BoxFinish
import dev.aiharness.core.model.BoxLid
import dev.aiharness.core.model.BoxTie
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.LetterBox
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.Season
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.model.TimeOfDay
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Every little fumibako (docs/design/fumibako-studies.html): each finish and tie, every lid, shut and
 * open, by day and by night, drawn offscreen. Set HARNESS_SCREENSHOTS_DIR to keep the pictures;
 * without it, it checks every box draws.
 */
class LetterBoxGalleryTest {
    private val outDir: File? = System.getenv("HARNESS_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    private val aria = Persona(id = "aria", name = "Aria", ink = "sakura", tapePattern = TapePattern.DOTS, createdAt = 0, updatedAt = 0)
    private val kestrel = Persona(id = "kestrel", name = "Kestrel", ink = "tokiwa", tapePattern = TapePattern.STRIPES, createdAt = 0, updatedAt = 0)

    private fun render(name: String, time: TimeOfDay) {
        val theme = ThemeSettings(dayNight = DayNightMode.STATIC, staticSeason = Season.AKI, staticTime = time)
        ImageComposeScene(width = 2 * 1460, height = 2 * 1220, density = Density(2f)) {
            HarnessTheme(theme) {
                Column(Modifier.background(paper.hyoshi).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BoxFinish.entries.forEach { finish ->
                        BoxTie.entries.forEach { tie ->
                            val persona = if (tie.ordinal % 2 == 0) aria else kestrel
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${finish.name.lowercase()}\n${tie.name.lowercase()}", color = paper.usuzumi1, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                                listOf(false, true).forEach { open ->
                                    BoxLid.entries.forEach { lid ->
                                        LetterBoxPicture(persona, open = open, box = LetterBox(finish, lid, tie))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.render(0)
            // Fonts load on the first frame; draw again with them.
            val image = scene.render(1_000_000_000L)
            assertTrue(image.width > 0)
            outDir?.let { dir -> image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(dir, "letter_boxes_$name.png").writeBytes(it) } }
        }
    }

    @Test
    fun everyBoxByDay() = render("day", TimeOfDay.HIRU)

    @Test
    fun everyBoxByNight() = render("night", TimeOfDay.YORU)
}
