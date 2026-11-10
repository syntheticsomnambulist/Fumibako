package dev.aiharness.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** The phone's R.font files, from the classpath (see ui/build.gradle.kts). */
internal actual fun bundledFont(name: String, weight: FontWeight, style: FontStyle): Font {
    val bytes = HarnessFonts::class.java.getResourceAsStream("/font/$name.ttf")?.use { it.readBytes() }
        ?: error("Missing bundled font $name")
    return androidx.compose.ui.text.platform.Font(identity = name, data = bytes, weight = weight, style = style)
}

/** A part's Japanese face once it has come (DesktopFonts); before, the bundled Latin and the system's Japanese. */
internal actual fun japaneseFace(part: FontPart): androidx.compose.ui.text.font.FontFamily? = DesktopFonts.japanese[part]
