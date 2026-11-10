package dev.aiharness.app.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Wide layouts (docs/plan-desktop.md, chosen 2026-11-08 from docs/design/wide-studies.html). From
 * [MIN_WIDTH] wide (a computer's window, a tablet, a phone on its side) the tabs run down the left,
 * Letters keeps its list beside the open letter, and Personas its gallery beside the open page.
 * Narrower, every screen is the phone's.
 */
object WideLayout {
    val MIN_WIDTH = 840.dp
    val LIST_WIDTH = 360.dp
    val GALLERY_WIDTH = 400.dp

    /** Letters keep this width, centered, however wide the window; the phone's [PAGE_PADDING] goes around it. */
    val READING_WIDTH = 640.dp
    val PAGE_PADDING = 18.dp

    /** Screens without a wide layout of their own (the planner, Settings): the phone's, centered. */
    val COLUMN_WIDTH = 600.dp
}

/**
 * Opens [route] from a list ([listRoute]: the letters list, the gallery). Wide, the list stays beside
 * what's open, so this replaces what's open instead of piling pages up behind it; on the phone the
 * list is the tab's own page, so it's an ordinary step forward.
 */
fun NavController.openFromList(route: String, listRoute: String) = navigate(route) { popUpTo(listRoute) }

/** The conversation open beside the letters list in a wide layout (its box is drawn open), or null. */
val LocalOpenLetter = compositionLocalOf<String?> { null }

/** True when the screen is laid out wide. */
val LocalWide = compositionLocalOf { false }

/** A page in the open side, beside its list: it leaves out its back arrow, since the list is right there. */
val LocalBesideList = compositionLocalOf { false }

/**
 * A thread in the open side: the space on each side of its reading width (at least the phone's
 * padding). Null where the thread fills the screen, as on the phone.
 */
val LocalReadingSides = compositionLocalOf<Dp?> { null }

/**
 * Lays the content out at most [max] wide and centered, while the element itself keeps the full
 * width (so a background or a line drawn before this in the chain still spans it). Null: unchanged.
 */
fun Modifier.centeredWithin(max: Dp?): Modifier = if (max == null) this else layout { measurable, constraints ->
    val cap = max.roundToPx()
    if (!constraints.hasBoundedWidth || constraints.maxWidth <= cap) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, cap), maxWidth = cap))
        layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - placeable.width) / 2, 0) }
    }
}
