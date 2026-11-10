package dev.aiharness.app.ui.planner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.aiharness.app.ui.media.reducedMotion
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.onInk
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.Persona
import dev.aiharness.core.model.PlannerEntry

/*
 * A persona's suggestion (docs/design/calendar.md, Third pass): a 付箋 sticky tab in its ink on the
 * grid at its time, and a matching sticky note below the grid with "Add it" and "Not this time".
 * Declining peels them off: the bottom right corner folds back, showing the paler back of the paper,
 * and the fold keeps going until the whole note has come away; then it lifts off and fades. The tab
 * does the same a moment later, leaving a faint mark where it was stuck. A quiet line takes the
 * note's place, with "Put it back".
 */

/** How long a note takes to peel off; the tab follows [TAB_DELAY] later and is a little quicker. */
internal const val PEEL_MILLIS = 1100
internal const val TAB_PEEL_MILLIS = 1000
internal const val TAB_DELAY = 160

/** The sticky paper in a persona's [ink]: its face, the paler back that shows as it peels, and the strip at its stuck end. */
internal data class Sticky(val face: Color, val back: Color, val backEdge: Color, val strip: Color, val words: Color)

@Composable
internal fun stickyOf(ink: Color): Sticky {
    val colors = paper
    val night = stationery.isNight
    return Sticky(
        face = lerp(colors.kami, ink, if (night) 0.24f else 0.14f),
        back = lerp(Color.White, ink, if (night) 0.18f else 0.03f),
        backEdge = lerp(colors.kami, ink, if (night) 0.3f else 0.16f),
        strip = ink.copy(alpha = 0.55f),
        words = lerp(ink, colors.sumi, if (night) 0.1f else 0.3f),
    )
}

/**
 * Peeling off, [t] of the way (0 stuck down, 1 gone): the part still stuck is drawn, the corner
 * folded back over it shows [Sticky.back]; in the last fifth it lifts away and fades.
 */
internal fun Modifier.peelingOff(t: Float, sticky: Sticky, lift: androidx.compose.ui.unit.Dp = 36.dp): Modifier {
    if (t <= 0f) return this
    val up = ((t - 0.8f) / 0.2f).coerceIn(0f, 1f)
    return this
        .graphicsLayer {
            translationX = -lift.toPx() * 0.5f * up
            translationY = -lift.toPx() * up
            rotationZ = -7f * up
            alpha = 1f - up
        }
        .drawWithContent {
            val w = size.width
            val h = size.height
            // The fold line, u + v = c in the note's own proportions, from the corner (c = 2) to the far corner (c = 0).
            val c = 2f * (1f - t)
            val square = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
            val kept = clip(square) { c - (it.x + it.y) }
            val corner = clip(square) { (it.x + it.y) - c }.map { Offset(c - it.y, c - it.x) }
            fun path(points: List<Offset>) = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * w, p.y * h) else lineTo(p.x * w, p.y * h) }
                close()
            }
            if (kept.size >= 3) clipPath(path(kept)) { this@drawWithContent.drawContent() }
            if (corner.size >= 3) {
                val flap = path(corner)
                // Its shadow up and to the left, then the paler back of the paper.
                translate(-1.dp.toPx(), -1.dp.toPx()) { drawPath(flap, Color.Black.copy(alpha = 0.12f)) }
                drawPath(flap, Brush.linearGradient(listOf(sticky.back, sticky.backEdge), start = Offset(0f, 0f), end = Offset(w, h)))
            }
        }
}

/** [poly] cut to where [inside] is at least 0 (Sutherland–Hodgman, one edge). */
private fun clip(poly: List<Offset>, inside: (Offset) -> Float): List<Offset> {
    val out = mutableListOf<Offset>()
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val fa = inside(a)
        val fb = inside(b)
        if (fa >= 0f) out += a
        if ((fa >= 0f) != (fb >= 0f)) {
            val k = fa / (fa - fb)
            out += Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
        }
    }
    return out
}

/** The peel's progress for something [takenDown], after [delay]; still with "Remove animations" on, it's simply gone. */
@Composable
internal fun rememberPeel(key: String, takenDown: Boolean, millis: Int = PEEL_MILLIS, delay: Int = 0): Float {
    val still = reducedMotion()
    val peel = remember(key) { Animatable(if (takenDown) 1f else 0f) }
    LaunchedEffect(key, takenDown) {
        when {
            !takenDown -> peel.snapTo(0f)
            still -> peel.snapTo(1f)
            peel.value < 1f -> peel.animateTo(1f, tween(millis, delayMillis = delay, easing = LinearEasing))
        }
    }
    return peel.value
}

/**
 * The tab on the grid: a small sticky tab in [ink], its stuck end a strip of colour, the suggestion
 * in the persona's hand. Taken down, it peels off and leaves a faint mark that fades.
 */
@Composable
internal fun SuggestionTab(id: String, title: String, ink: Color, takenDown: Boolean, spoken: String, modifier: Modifier = Modifier) {
    val sticky = stickyOf(ink)
    val peel = rememberPeel(id, takenDown, TAB_PEEL_MILLIS, TAB_DELAY)
    val mark = rememberMark(id, takenDown && peel > 0f)
    Box(modifier) {
        if (mark > 0f) Box(Modifier.matchParentSize().background(ink.copy(alpha = 0.1f * mark)))
        if (peel < 1f) {
            Row(
                Modifier
                    .matchParentSize()
                    .peelingOff(peel, sticky, lift = 16.dp)
                    .drawBehind {
                        translate(0f, 0.6.dp.toPx()) { drawRect(Color.Black.copy(alpha = 0.08f)) }
                        drawRect(sticky.face)
                        drawRect(sticky.strip, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                    }
                    .semantics { contentDescription = spoken }
                    .padding(start = 7.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = handText(10.5.sp, 12.sp), color = sticky.words, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The faint mark where a tab was stuck: there as it peels, then fading. */
@Composable
private fun rememberMark(key: String, shown: Boolean): Float {
    val still = reducedMotion()
    val mark = remember(key) { Animatable(0f) }
    LaunchedEffect(key, shown) {
        if (!shown || still) {
            mark.snapTo(0f)
            return@LaunchedEffect
        }
        mark.snapTo(0.9f)
        mark.animateTo(0f, tween(1400 + TAB_PEEL_MILLIS, easing = LinearEasing))
    }
    return mark.value
}

/**
 * The note below the grid: the suggestion in the persona's hand on a sticky note in its ink, who
 * suggests it and when, and "Add it" and "Not this time". Added, it stays beside your plan ("ADDED
 * TO YOUR PLANNER"); taken down, it peels off and a quiet line takes its place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionNote(
    e: PlannerEntry,
    persona: Persona,
    ink: Color,
    whenWords: String,
    takenDown: Boolean,
    onAdd: (PlannerEntry) -> Unit,
    onDecline: (PlannerEntry) -> Unit,
    onPutBack: (PlannerEntry) -> Unit,
) {
    val colors = paper
    val you = stationery.you
    val sticky = stickyOf(ink)
    val peel = rememberPeel(e.id, takenDown)
    if (takenDown && peel >= 1f) {
        FlowRow(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
            Text("Taken down; ${persona.name} will hear it wasn't for this time.", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp, lineHeight = 16.sp), color = colors.usuzumi1)
            Text(
                "Put it back",
                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp, lineHeight = 16.sp, textDecoration = TextDecoration.Underline),
                color = lerp(you, colors.sumi, 0.3f),
                modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClickLabel = "Put ${persona.name}'s suggestion back") { onPutBack(e) }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        return
    }
    // Declined somewhere else (or before): it was taken down then.
    if (e.state == EntryState.DECLINED && !takenDown) return
    val added = e.state == EntryState.ADDED
    Box(
        Modifier
            .padding(start = 4.dp, end = 30.dp, top = 6.dp, bottom = 4.dp)
            .widthIn(max = 300.dp)
            .graphicsLayer { rotationZ = -0.6f }
            .peelingOff(peel, sticky),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    translate(0f, 1.dp.toPx()) { drawRect(Color.Black.copy(alpha = 0.10f)) }
                    drawRect(sticky.face)
                    drawRect(sticky.strip, size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height))
                }
                .padding(start = 17.dp, end = 12.dp, top = 9.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(e.title, style = handText(15.sp, 21.sp), color = sticky.words)
            Text(
                (if (added) "ADDED TO YOUR PLANNER" else "${persona.name.uppercase()} SUGGESTS") + " · " + whenWords.uppercase(),
                style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.sp, letterSpacing = 0.06.em),
                color = colors.usuzumi1,
            )
            if (!added) {
                FlowRow(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.heightIn(min = 36.dp).background(you, RoundedCornerShape(2.dp))
                            .clickable(enabled = !takenDown, role = Role.Button, onClickLabel = "Add it to your planner") { onAdd(e) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Add it", style = TextStyle(fontFamily = HarnessFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = colors.onInk(you)) }
                    Box(
                        Modifier.heightIn(min = 36.dp).background(lerp(colors.kami, Color.White, 0.4f), RoundedCornerShape(2.dp)).border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp))
                            .clickable(enabled = !takenDown, role = Role.Button, onClickLabel = "Take it down, not this time") { onDecline(e) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Not this time", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 12.sp), color = colors.sumi) }
                }
            }
        }
    }
}
