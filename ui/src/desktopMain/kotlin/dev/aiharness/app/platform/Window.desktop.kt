package dev.aiharness.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/** In front of you while the window has the focus, as a computer's letters apps count what you've seen. */
@Composable
actual fun inFrontOfYou(): Boolean = LocalWindowInfo.current.isWindowFocused
