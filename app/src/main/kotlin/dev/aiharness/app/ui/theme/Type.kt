package dev.aiharness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aiharness.app.R

/*
 * Placeholder faces from the mockups (bundled, subsetted, SIL OFL: see assets/licenses/fonts.txt).
 * The choice isn't final; swapping a family here restyles the whole app. Characters outside the
 * subsets (e.g. Japanese in messages) fall back to the system fonts.
 */
object HarnessFonts {
    /** Names, titles, mood words on frames. */
    val Display = FontFamily(
        Font(R.font.display_medium, FontWeight.Medium),
        Font(R.font.display_medium_italic, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.display_semibold, FontWeight.SemiBold),
        Font(R.font.display_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    )

    /** Letter text, previews, mood words. */
    val Read = FontFamily(
        Font(R.font.read_regular, FontWeight.Normal),
        Font(R.font.read_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.read_medium, FontWeight.Medium),
        Font(R.font.read_bold, FontWeight.Bold),
        Font(R.font.read_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

    /** Interface text. */
    val Ui = FontFamily(
        Font(R.font.ui_regular, FontWeight.Normal),
        Font(R.font.ui_bold, FontWeight.Bold),
    )

    /** Kanji labels and seals. */
    val Brush = FontFamily(Font(R.font.brush_regular, FontWeight.Normal))

    /** Handwriting on media frames: polaroid captions, cassette labels, luggage tags. */
    val Hand = FontFamily(
        Font(R.font.hand_regular, FontWeight.Normal),
        Font(R.font.hand_semibold, FontWeight.SemiBold),
    )

    /** Newsprint, for cuttings and ticket stubs. */
    val News = FontFamily(
        Font(R.font.news_regular, FontWeight.Normal),
        Font(R.font.news_bold, FontWeight.Bold),
    )
}

private val ui = TextStyle(fontFamily = HarnessFonts.Ui)
private val display = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold)

val HarnessTypography = Typography(
    displayLarge = display.copy(fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = display.copy(fontSize = 42.sp, lineHeight = 46.sp),
    displaySmall = display.copy(fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = display.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = display.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = display.copy(fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = display.copy(fontSize = 24.sp, lineHeight = 28.sp),
    titleMedium = ui.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = ui.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = ui.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = ui.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = ui.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = ui.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = ui.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = ui.copy(fontSize = 11.sp, lineHeight = 15.sp),
)

/** Text styles of the stationery (sizes from the mockups in `docs/design`). Colours are applied at use. */
object PaperType {
    val screenTitle = display.copy(fontSize = 30.sp, lineHeight = 32.sp)
    val seasonKanji = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 15.sp)
    val letterName = display.copy(fontSize = 19.sp, lineHeight = 22.sp)
    val listName = display.copy(fontSize = 20.sp, lineHeight = 22.sp)
    val letterBody = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.5.sp, lineHeight = 25.sp)
    val postscript = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 13.5.sp, lineHeight = 20.sp)
    val mood = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 17.sp)
    val preview = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 14.sp, lineHeight = 20.sp)
    val noteBody = ui.copy(fontSize = 14.5.sp, lineHeight = 22.sp)
    val meta = ui.copy(fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
    val label = ui.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em)
    val dayDivider = TextStyle(fontFamily = HarnessFonts.Display, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val entryTitle = display.copy(fontSize = 19.sp, lineHeight = 22.sp)
    val prose = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 14.5.sp, lineHeight = 23.sp)
    val navKanji = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 20.sp, lineHeight = 24.sp)
    val navLabel = ui.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val heroName = display.copy(fontSize = 38.sp, lineHeight = 38.sp)
    val polaroidName = display.copy(fontSize = 19.sp, lineHeight = 20.sp)
    val seal = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 18.sp)
}
