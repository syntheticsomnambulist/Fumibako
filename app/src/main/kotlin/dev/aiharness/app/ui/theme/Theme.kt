package dev.aiharness.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.aiharness.core.model.FrameStyle
import dev.aiharness.core.model.Lamplight
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import dev.aiharness.core.theme.ThemeChoice
import dev.aiharness.core.theme.ThemeClock
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * Everything the stationery look needs beyond Material's colour scheme: the current madoromi theme,
 * the season's day inks (stickers and seals are printed objects and keep them at night), the
 * polaroid frame, and the user's own ink and tape.
 */
@Immutable
data class Stationery(
    val colors: MadoromiColors,
    val dayColors: MadoromiColors,
    val outline: Color,
    val frame: Color,
    /** Text on the polaroid frame. */
    val frameInk: Color,
    /** Ink colours that read on the frame: day inks on a light frame, night inks on a dark one. */
    val frameInks: MadoromiColors,
    val you: Color,
    val youPattern: TapePattern,
    val inkSignature: Boolean,
    /** The outline setting as chosen: a hex colour, or paper or ink (see [ThemeSettings.outlineColor]). */
    val outlineSetting: String = "#FFFFFF",
    val lamplight: Lamplight = Lamplight.DIM_ALL,
) {
    val isNight: Boolean get() = colors.isNight

    fun ink(name: String?): Color = colors.ink(name)

    companion object {
        fun of(choice: ThemeChoice, settings: ThemeSettings, youInk: String?, youPattern: TapePattern): Stationery {
            val colors = Madoromi.byKey(choice.key)
            val day = Madoromi.byKey("${choice.season.name.lowercase()}-hiru")
            val night = Madoromi.byKey("${choice.season.name.lowercase()}-yoru")
            val outline = parseHex(settings.outlineColor) ?: Color.White
            val frame = if (settings.polaroidFrame == FrameStyle.PAPER) colors.kami else outline
            val lightFrame = frame.luminance() > 0.35f
            return Stationery(
                colors = colors,
                dayColors = day,
                outline = outline,
                frame = frame,
                frameInk = if (lightFrame) Color(0xFF3B2F27) else Color(0xFFF1E8DF),
                frameInks = if (lightFrame) day else night,
                you = colors.ink(youInk ?: "sora"),
                youPattern = youPattern,
                inkSignature = settings.inkSignature,
                outlineSetting = settings.outlineColor,
                lamplight = settings.lamplight,
            )
        }

        val Default: Stationery = of(ThemeClock.choose(ThemeSettings(), 0, ZoneId.of("UTC")), ThemeSettings(), null, TapePattern.STRIPES)
    }
}

val LocalStationery = staticCompositionLocalOf { Stationery.Default }

/** The current stationery (theme colours, inks, frame). */
val stationery: Stationery
    @Composable @ReadOnlyComposable get() = LocalStationery.current

/** The current madoromi colours. */
val paper: MadoromiColors
    @Composable @ReadOnlyComposable get() = LocalStationery.current.colors

/**
 * Applies the madoromi theme picked by [ThemeClock] from [settings], switching at the next sunrise,
 * sunset or configured hour (and re-checking whenever the app comes back to the foreground).
 */
@Composable
fun HarnessTheme(
    settings: ThemeSettings = ThemeSettings(),
    youInk: String? = null,
    youPattern: TapePattern = TapePattern.STRIPES,
    nowMillis: (() -> Long)? = null,
    content: @Composable () -> Unit,
) {
    val clock = nowMillis ?: System::currentTimeMillis
    var now by remember { mutableLongStateOf(clock()) }
    val choice = remember(settings, now) { ThemeClock.choose(settings, now, ZoneId.systemDefault()) }
    LaunchedEffect(choice.nextChangeAt) {
        val next = choice.nextChangeAt ?: return@LaunchedEffect
        delay((next - clock()).coerceAtLeast(1_000L))
        now = clock()
    }
    LifecycleResumeEffect(Unit) {
        now = clock()
        onPauseOrDispose { }
    }
    val stationery = remember(choice, settings, youInk, youPattern) { Stationery.of(choice, settings, youInk, youPattern) }
    CompositionLocalProvider(LocalStationery provides stationery) {
        MaterialTheme(colorScheme = stationery.colors.toColorScheme(), typography = HarnessTypography, shapes = PaperShapes, content = content)
    }
}

/** Near-square corners everywhere Material draws a shape (dialogs, fields, menus): stationery, not pills. */
val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Text that reads on a block of [ink]: this theme's darkest or lightest role, whichever contrasts. */
fun MadoromiColors.onInk(ink: Color): Color {
    val (dark, light) = if (kami.luminance() < sumi.luminance()) kami to sumi else sumi to kami
    return if (ink.luminance() > 0.36f) dark else light
}

/** Maps madoromi roles onto Material 3 so stock components (dialogs, fields, menus) fit the paper. */
fun MadoromiColors.toColorScheme(): ColorScheme {
    val base = if (isNight) darkColorScheme() else lightColorScheme()
    val error = ink("beni")
    return base.copy(
        primary = fuji,
        onPrimary = kami,
        primaryContainer = nijimi0,
        onPrimaryContainer = sumi,
        inversePrimary = nijimi2,
        secondary = usuzumi1,
        onSecondary = kami,
        secondaryContainer = chitsu,
        onSecondaryContainer = sumi,
        tertiary = ink("sora"),
        onTertiary = kami,
        tertiaryContainer = nijimi0,
        onTertiaryContainer = sumi,
        background = hyoshi,
        onBackground = sumi,
        surface = hyoshi,
        onSurface = sumi,
        surfaceVariant = chitsu,
        onSurfaceVariant = usuzumi1,
        surfaceTint = fuji,
        inverseSurface = sumi,
        inverseOnSurface = kami,
        error = error,
        onError = kami,
        errorContainer = nijimi0,
        onErrorContainer = error,
        outline = nijimi2,
        outlineVariant = nijimi0,
        scrim = Color.Black,
        surfaceBright = kami,
        surfaceDim = chitsu,
        surfaceContainerLowest = kami,
        surfaceContainerLow = kami,
        surfaceContainer = kami,
        surfaceContainerHigh = kami,
        surfaceContainerHighest = chitsu,
    )
}

/** `#RRGGBB` or `#AARRGGBB` to a colour, or null. */
fun parseHex(hex: String): Color? {
    val digits = hex.trim().removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}
