package dev.aiharness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import dev.aiharness.app.platform.onRightClick
import dev.aiharness.app.platform.onWheel
import dev.aiharness.app.platform.scrollingPage
import dev.aiharness.app.platform.sendOnCtrlEnter
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A computer's mouse and keyboard in the shared screens' hooks (nothing on the phone). */
@OptIn(ExperimentalTestApi::class)
class MouseKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aRightClickDoesWhatALongPressDoes() {
        var held = 0
        var tapped = 0
        compose.setContent {
            Box(
                Modifier.size(100.dp).testTag("letter")
                    .combinedClickable(onLongClick = { held++ }) { tapped++ }
                    .onRightClick { held++ },
            )
        }
        compose.onNodeWithTag("letter").performMouseInput { rightClick() }
        compose.waitForIdle()
        assertEquals(1 to 0, held to tapped)
        compose.onNodeWithTag("letter").performClick()
        compose.waitForIdle()
        assertEquals(1 to 1, held to tapped)
    }

    @Test
    fun onlyTheInnermostAnswersARightClick() {
        var outer = 0
        var inner = 0
        compose.setContent {
            Box(Modifier.size(200.dp).onRightClick { outer++ }) {
                Box(Modifier.size(80.dp).testTag("plan").onRightClick { inner++ })
            }
        }
        compose.onNodeWithTag("plan").performMouseInput { rightClick() }
        compose.waitForIdle()
        assertEquals(1 to 0, inner to outer)
    }

    @Test
    fun theWheelTellsItsStepsAndShift() {
        val turns = mutableListOf<Pair<Float, Boolean>>()
        compose.setContent {
            Box(Modifier.size(100.dp).testTag("sticker").onWheel { steps, shift -> turns += steps to shift })
        }
        compose.onNodeWithTag("sticker").performMouseInput { scroll(-1f) }
        compose.waitForIdle()
        assertEquals(1, turns.size)
        assertTrue(turns.single().first < 0f, "away from you: ${turns.single()}")
        assertEquals(false, turns.single().second)
    }

    @Test
    fun ctrlEnterSendsAndEnterStartsANewLine() {
        var text by mutableStateOf("")
        var sent = 0
        compose.setContent {
            BasicTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("note").sendOnCtrlEnter { sent++ })
        }
        compose.onNodeWithTag("note").performTextInput("Dear Aria")
        compose.onNodeWithTag("note").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(0, sent)
        assertEquals("Dear Aria\n", text)
        compose.onNodeWithTag("note").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()
        assertEquals(1, sent)
        assertEquals("Dear Aria\n", text, "Ctrl+Enter adds no line")
    }

    @Test
    fun aLongPageHasAScrollbarThatDrags() {
        val state = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            Box(Modifier.size(200.dp, 300.dp).background(Color.White).testTag("page").scrollingPage(state)) {
                Column { repeat(40) { Box(Modifier.fillMaxWidth().height(50.dp)) } }
            }
        }
        compose.waitForIdle()
        // The thumb at the top right, and nothing at the bottom right yet.
        val before = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        val x = before.width - 5
        assertTrue(before[x, 10] != Color.White, "a thumb at the top")
        assertEquals(Color.White, before[x, before.height - 10])
        // Dragging the thumb down scrolls the page.
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(Offset(width - 5f, 10f))
            press()
            moveBy(Offset(0f, 100f))
            release()
        }
        compose.waitForIdle()
        assertTrue(state.value > 0, "scrolled to ${state.value}")
    }

    @Test
    fun aPageThatFitsHasNoScrollbar() {
        compose.setContent {
            Box(Modifier.size(200.dp, 300.dp).background(Color.White).testTag("page").scrollingPage(rememberScrollState())) {
                Column { repeat(2) { Box(Modifier.fillMaxWidth().height(50.dp)) } }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        assertEquals(Color.White, image[image.width - 5, 10])
    }
}
