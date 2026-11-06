package dev.aiharness.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.HarnessApp
import dev.aiharness.app.ui.stickers.StuckOn
import dev.aiharness.app.ui.theme.HarnessTheme
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.Sticker
import dev.aiharness.core.model.StickerRef
import dev.aiharness.core.model.StickerTarget
import dev.aiharness.core.model.ThemeSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stickers on paper: a tap selects one; one of yours held and dragged moves without being selected. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StuckOnTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sticker(by: String? = null) = Sticker(
        id = "s1", conversationId = "c1", target = StickerTarget.message("m1"), ref = StickerRef.Emoji("⭐", "star"),
        byPersonaId = by, x = 0.5f, y = 0.5f, createdAt = 0,
    )

    private var tapped = 0
    private var moved: Sticker? = null

    private fun show(start: Sticker) {
        val app = ApplicationProvider.getApplicationContext<HarnessApp>()
        compose.setContent {
            var now by androidx.compose.runtime.remember { mutableStateOf(start) }
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                HarnessTheme(ThemeSettings(dayNight = DayNightMode.STATIC)) {
                    StuckOn(
                        stickers = listOf(now),
                        inkOf = { Color.Black },
                        modifier = Modifier.size(300.dp),
                        nameOf = { ":star: sticker" },
                        onTap = { tapped++ },
                        onChange = { now = it; moved = it },
                    ) { Spacer(Modifier.size(300.dp)) }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescriptionExists(":star: sticker") }
    }

    @Test
    fun aTapSelectsAndAHoldAndDragMoves() {
        show(sticker())
        compose.onNodeWithContentDescription(":star: sticker", useUnmergedTree = true).performTouchInput { click(center) }
        assertEquals(1, tapped)
        assertNull(moved, "a tap doesn't move it")

        compose.onNodeWithContentDescription(":star: sticker", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(90f, 0f))
            moveBy(Offset(90f, 0f))
            up()
        }
        assertEquals(1, tapped, "a hold isn't a tap")
        val m = moved ?: error("held and dragged, it moved")
        assertTrue(m.x > 0.5f, "to the right: ${m.x}")
        assertEquals(0.5f, m.y, 0.01f)
    }

    @Test
    fun aPersonasStickerDoesntMoveWhenHeld() {
        show(sticker(by = "p1"))
        compose.onNodeWithContentDescription(":star: sticker", useUnmergedTree = true).performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(120f, 0f))
            up()
        }
        assertNull(moved)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionExists(label: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
