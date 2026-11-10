package dev.aiharness.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import dev.aiharness.core.model.Hands

/** A hand as drawn: its face, and how much it's scaled so its x-height matches the letters'. */
data class HandFace(val name: String, val family: FontFamily, val scale: Float = 1f)

/** The hand the text here is written in: the persona's in her letter, yours in your note. Yomogi elsewhere. */
val LocalHand = staticCompositionLocalOf { HandFace(Hands.DEFAULT.name, HarnessFonts.Hand) }

/**
 * The hand called [name] (a persona's, or one of yours): Yomogi, bundled, or another that downloads
 * the first time it's asked for. Until it's here, Yomogi stands in.
 */
@Composable
fun rememberHand(name: String?): HandFace {
    val hand = Hands.of(name)
    if (hand == Hands.DEFAULT) return HandFace(hand.name, HarnessFonts.Hand)
    val family = dev.aiharness.app.platform.downloadedHand(hand.name)
    return if (family != null) HandFace(hand.name, family, hand.scale) else HandFace(Hands.DEFAULT.name, HarnessFonts.Hand)
}

/** [content] written in the hand called [name]. */
@Composable
fun InHand(name: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHand provides rememberHand(name), content = content)
}
