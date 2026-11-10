package dev.aiharness.app.ui.memory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer

/** For now the card fades away on the computer; the burn comes later (docs/plan-desktop.md). */
@Composable
actual fun BurnAway(paper: ImageBitmap, seed: Int, modifier: Modifier, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val fade = remember(paper) { Animatable(1f) }
    LaunchedEffect(paper) {
        fade.animateTo(0f, tween(1_200))
        done()
    }
    Image(paper, contentDescription = null, modifier = modifier.graphicsLayer { alpha = fade.value })
}
