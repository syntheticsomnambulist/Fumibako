package dev.aiharness.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.aiharness.app.ui.R

internal actual fun bundledFont(name: String, weight: FontWeight, style: FontStyle): Font = Font(fontIds.getValue(name), weight, style)

private val fontIds = mapOf(
    "brush_regular" to R.font.brush_regular,
    "display_medium" to R.font.display_medium,
    "display_medium_italic" to R.font.display_medium_italic,
    "display_semibold" to R.font.display_semibold,
    "display_semibold_italic" to R.font.display_semibold_italic,
    "hand_regular" to R.font.hand_regular,
    "hand_semibold" to R.font.hand_semibold,
    "news_bold" to R.font.news_bold,
    "news_regular" to R.font.news_regular,
    "read_bold" to R.font.read_bold,
    "read_bold_italic" to R.font.read_bold_italic,
    "read_italic" to R.font.read_italic,
    "read_medium" to R.font.read_medium,
    "read_regular" to R.font.read_regular,
    "ui_bold" to R.font.ui_bold,
    "ui_regular" to R.font.ui_regular,
)
