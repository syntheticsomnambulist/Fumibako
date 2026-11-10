package dev.aiharness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
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
    private val bundledDisplay = FontFamily(
        bundledFont("display_medium", FontWeight.Medium),
        bundledFont("display_semibold", FontWeight.SemiBold),
    )
    private val bundledRead = FontFamily(
        bundledFont("read_regular", FontWeight.Normal),
        bundledFont("read_italic", FontWeight.Normal, FontStyle.Italic),
        bundledFont("read_medium", FontWeight.Medium),
        bundledFont("read_bold", FontWeight.Bold),
        bundledFont("read_bold_italic", FontWeight.Bold, FontStyle.Italic),
    )
    private val bundledUi = FontFamily(
        bundledFont("ui_regular", FontWeight.Normal),
        bundledFont("ui_bold", FontWeight.Bold),
    )
    private val bundledBrush = FontFamily(bundledFont("brush_regular", FontWeight.Normal))
    private val bundledHand = FontFamily(bundledFont("hand_regular", FontWeight.Normal))
    private val bundledNews = FontFamily(
        bundledFont("news_regular", FontWeight.Normal),
        bundledFont("news_bold", FontWeight.Bold),
    )

    // Each part is the bundled Latin subset until its Japanese has come (dev.aiharness.app.platform.japaneseFace),
    // then the full face (or the Latin face with its Japanese companion chained behind it). Read in
    // composition, so text redraws in the full face when it arrives.

    /** Names, titles, day dividers. */
    val Display: FontFamily get() = japaneseFace(FontPart.DISPLAY) ?: bundledDisplay

    /** Letter text (yours and theirs), previews, prose. */
    val Read: FontFamily get() = japaneseFace(FontPart.READ) ?: bundledRead

    /** Interface text. */
    val Ui: FontFamily get() = japaneseFace(FontPart.UI) ?: bundledUi

    /** Kanji labels and seals. */
    val Brush: FontFamily get() = japaneseFace(FontPart.BRUSH) ?: bundledBrush

    /**
     * The hand (Yomogi, the default): a persona's P.S., mood words, thoughts and feelings, and the
     * handwriting on frames (captions, cassette labels, luggage tags). One weight.
     */
    val Hand: FontFamily get() = japaneseFace(FontPart.HAND) ?: bundledHand

    /** Newsprint, for cuttings and ticket stubs. */
    val News: FontFamily get() = japaneseFace(FontPart.NEWS) ?: bundledNews
}

/** The six parts of the type, each with one face (and its Japanese). */
enum class FontPart { DISPLAY, READ, UI, BRUSH, HAND, NEWS }

/**
 * [part]'s full face once its Japanese has come (downloaded the first time Japanese shows, or when
 * asked in Settings, Look); null before, and where it can't come.
 */
internal expect fun japaneseFace(part: FontPart): FontFamily?

/** One of the bundled font files (`font/<name>.ttf`): R.font on the phone, a classpath file on a computer. */
internal expect fun bundledFont(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal): Font

private val ui: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Ui)
private val display: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.SemiBold)

val HarnessTypography: Typography get() = Typography(
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
    val screenTitle: TextStyle get() = display.copy(fontSize = 30.sp, lineHeight = 32.sp)
    val seasonKanji: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 16.sp)
    val letterName: TextStyle get() = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val listName: TextStyle get() = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val letterBody: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.5.sp, lineHeight = 24.sp)
    /** A P.S., her thoughts, a line in her voice: in the hand here ([LocalHand]). */
    val postscript: TextStyle @Composable get() = inHand(15f, 20f)
    /** A quiet line in the interface (a hint under a setting, a tray's note): printed, in Literata's real italic. */
    val quiet: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Read, fontStyle = FontStyle.Italic, fontSize = 13.5.sp, lineHeight = 20.sp)
    /** Her mood word, in the hand here. */
    val mood: TextStyle @Composable get() = inHand(15f, 20f)
    val preview: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 14.sp, lineHeight = 20.sp)
    /** Interface text: descriptions, hints, rows. */
    val noteBody: TextStyle get() = ui.copy(fontSize = 14.sp, lineHeight = 22.sp)
    /** Your notes in a thread: letters too, so in the letters face (v0.5). */
    val yourNote: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.sp, lineHeight = 24.sp)
    val meta: TextStyle get() = ui.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    val label: TextStyle get() = ui.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.12.em)
    val dayDivider: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Display, fontWeight = FontWeight.Medium, fontSize = 17.5.sp, lineHeight = 24.sp)
    val entryTitle: TextStyle get() = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val prose: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.5.sp, lineHeight = 24.sp)
    val navKanji: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 20.sp, lineHeight = 24.sp)
    val navLabel: TextStyle get() = ui.copy(fontSize = 11.sp, lineHeight = 16.sp)
    val heroName: TextStyle get() = display.copy(fontSize = 38.sp, lineHeight = 40.sp)
    val polaroidName: TextStyle get() = display.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val seal: TextStyle get() = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 18.sp)
    /** Captions and labels on frames, in the hand here. */
    val caption: TextStyle @Composable get() = inHand(12.5f, 16f)

    /** [size] and [line] in the hand here, scaled so its x-height sits with the letters'. */
    @Composable
    private fun inHand(size: Float, line: Float): TextStyle {
        val hand = LocalHand.current
        // Yomogi is read live, so its Japanese shows once it has come.
        val family = if (hand.name == dev.aiharness.core.model.Hands.DEFAULT.name) HarnessFonts.Hand else hand.family
        return TextStyle(fontFamily = family, fontSize = (size * hand.scale).sp, lineHeight = line.sp)
    }
}
