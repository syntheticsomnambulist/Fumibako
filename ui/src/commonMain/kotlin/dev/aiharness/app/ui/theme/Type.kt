package dev.aiharness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The faces chosen in the v0.5 font audit (`docs/design/v0.5.md`, *The font audit*), bundled as
 * Latin subsets (SIL OFL: see files/licenses/fonts.txt): Zen Old Mincho for names and titles,
 * Literata for letters (yours and theirs), Zen Kaku Gothic New for the interface, Yomogi for the
 * hand, Yuji Syuku for seals and kanji, Old Standard TT for cuttings. No italics: what was italic
 * (the P.S., mood words, her thoughts, feelings) is in her hand. Characters outside the subsets
 * (Japanese) fall back to the system fonts for now. The files are Android resources
 * (ui/src/androidMain/res/font), which the desktop reads from its classpath.
 */
object HarnessFonts {
    /** Names, titles, day dividers. */
    val Display = FontFamily(
        bundledFont("display_medium", FontWeight.Medium),
        bundledFont("display_semibold", FontWeight.SemiBold),
    )

    /** Letter text, previews, mood words. */
    val Read = FontFamily(
        bundledFont("read_regular", FontWeight.Normal),
        bundledFont("read_italic", FontWeight.Normal, FontStyle.Italic),
        bundledFont("read_medium", FontWeight.Medium),
        bundledFont("read_bold", FontWeight.Bold),
        bundledFont("read_bold_italic", FontWeight.Bold, FontStyle.Italic),
    )

    /** Interface text. */
    val Ui = FontFamily(
        bundledFont("ui_regular", FontWeight.Normal),
        bundledFont("ui_bold", FontWeight.Bold),
    )

    /** Kanji labels and seals. */
    val Brush = FontFamily(bundledFont("brush_regular", FontWeight.Normal))

    /**
     * The hand (Yomogi, the default): a persona's P.S., mood words, thoughts and feelings, and the
     * handwriting on frames (captions, cassette labels, luggage tags). One weight.
     */
    val Hand = FontFamily(bundledFont("hand_regular", FontWeight.Normal))

    /** Newsprint, for cuttings and ticket stubs. */
    val News = FontFamily(
        bundledFont("news_regular", FontWeight.Normal),
        bundledFont("news_bold", FontWeight.Bold),
    )
}

/** One of the bundled font files (`font/<name>.ttf`): R.font on the phone, a classpath file on a computer. */
internal expect fun bundledFont(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal): Font

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

/**
 * Text styles of the stationery: the aligned scale from the v0.5 font audit (names at one size
 * everywhere, meta and labels at 11, P.S. and mood words together, line heights on a 4dp step).
 * In her hand the P.S. and mood words are a step up (15), since handwriting faces run small.
 * Colours are applied at use.
 */
object PaperType {
    val screenTitle = display.copy(fontSize = 30.sp, lineHeight = 32.sp)
    val seasonKanji = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 16.sp)
    val letterName = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val listName = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val letterBody = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.5.sp, lineHeight = 24.sp)
    /** A P.S., her thoughts, a caption in her voice: in her hand. */
    val postscript = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 15.sp, lineHeight = 20.sp)
    /** Her mood word, in her hand. */
    val mood = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 15.sp, lineHeight = 20.sp)
    val preview = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 14.sp, lineHeight = 20.sp)
    /** Interface text: descriptions, hints, rows. */
    val noteBody = ui.copy(fontSize = 14.sp, lineHeight = 22.sp)
    /** Your notes in a thread: letters too, so in the letters face (v0.5). */
    val yourNote = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.sp, lineHeight = 24.sp)
    val meta = ui.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    val label = ui.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.12.em)
    val dayDivider = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.Medium, fontSize = 17.5.sp, lineHeight = 24.sp)
    val entryTitle = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val prose = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.5.sp, lineHeight = 24.sp)
    val navKanji = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 20.sp, lineHeight = 24.sp)
    val navLabel = ui.copy(fontSize = 11.sp, lineHeight = 16.sp)
    val heroName = display.copy(fontSize = 38.sp, lineHeight = 40.sp)
    val polaroidName = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val seal = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 18.sp)
    /** Captions and labels on frames, in the hand. */
    val caption = TextStyle(fontFamily = HarnessFonts.Hand, fontSize = 12.5.sp, lineHeight = 16.sp)
}
