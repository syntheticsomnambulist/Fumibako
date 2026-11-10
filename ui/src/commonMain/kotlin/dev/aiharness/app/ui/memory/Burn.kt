package dev.aiharness.app.ui.memory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Burns [paper] (a snapshot of the card, at its size) away from the bottom right corner, with a
 * flickering glow, charring ahead of the front and a few embers that sway as they rise. Calls
 * [onDone] once the paper is gone and the last ember has faded. Embers drift above the card, so
 * leave a little room over it.
 */
@Composable
expect fun BurnAway(paper: ImageBitmap, seed: Int, modifier: Modifier = Modifier, onDone: () -> Unit)
