package dev.aiharness.app.ui.paper

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.aiharness.app.ui.theme.InkKanji
import dev.aiharness.app.ui.theme.InkNames
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.TapeShade

/** The 14 madoromi inks as swatches; the chosen one is ringed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkPicker(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val s = stationery
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InkNames.forEach { name ->
                val on = name == selected
                Canvas(
                    Modifier
                        .size(34.dp)
                        .clip(PaperCorner)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(name) })
                        .border(if (on) 2.dp else 1.dp, if (on) s.colors.sumi else s.colors.nijimi1, PaperCorner)
                        .padding(4.dp),
                ) { drawRect(s.ink(name)) }
            }
        }
        selected?.let { Text("${InkKanji[it].orEmpty()}  $it", style = PaperType.meta, color = s.ink(it)) }
    }
}

/**
 * The thirteen tape patterns, each a short piece of tape in [inkName] and the chosen [shade], in a
 * grid with the Japanese name beside the English where there is one; a strip of the chosen tape
 * above; and the Shade row: with the time of day, light or dark.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapePicker(
    inkName: String?,
    selected: TapePattern,
    onSelect: (TapePattern) -> Unit,
    modifier: Modifier = Modifier,
    shade: TapeShade = TapeShade.THEME,
    onShade: ((TapeShade) -> Unit)? = null,
) {
    val ink = stationery.ink(inkName)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val chosen = tape(ink, selected, shade)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawTape(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, chosen)
        }
        TapePattern.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { pattern ->
                    val on = pattern == selected
                    val piece = tape(ink, pattern, shade, shadow = false)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(PaperCorner)
                            .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(pattern) })
                            .border(if (on) 2.dp else 1.dp, if (on) paper.sumi else paper.nijimi0, PaperCorner)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                            drawTape(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, piece)
                        }
                        Text(
                            pattern.label + (pattern.japanese?.let { " $it" } ?: ""),
                            style = PaperType.meta.copy(letterSpacing = 0.sp),
                            color = if (on) paper.sumi else paper.kasure2,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (onShade != null) {
            Text("SHADE", style = PaperType.meta, color = paper.kasure2, modifier = Modifier.padding(top = 4.dp))
            PaperChoice(TapeShade.entries.map { it to it.label }, shade, onShade, ink = ink, maxLines = 2)
        }
    }
}

/**
 * The hands to write in ([dev.aiharness.core.model.Hands]): each a line of P.S. in its own face
 * (Yomogi's bundled; the others show in it until they've come, the first time they're chosen),
 * with [about] saying what the hand covers.
 */
@Composable
fun HandPicker(selected: String?, ink: Color, about: String, onSelect: (String?) -> Unit) {
    val chosen = dev.aiharness.core.model.Hands.of(selected)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(about, style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = paper.usuzumi0)
        dev.aiharness.core.model.Hands.BUILT_IN.forEach { hand ->
            val on = hand == chosen && dev.aiharness.core.model.Hands.yours(selected, emptyList()) == null && selected?.startsWith("font:") != true
            // Only the chosen one, and Yomogi, are drawn in their own face: the others download when chosen.
            val face = if (on || hand == dev.aiharness.core.model.Hands.DEFAULT) dev.aiharness.app.ui.theme.rememberHand(hand.name) else null
            val note = when {
                hand == dev.aiharness.core.model.Hands.DEFAULT -> " · the default"
                on && face?.name != hand.name -> " · on its way"
                !on -> " · comes when chosen"
                else -> ""
            }
            HandRow(on, ink, face?.family ?: dev.aiharness.app.ui.theme.HarnessFonts.Hand, face?.scale ?: 1f, hand.name + note) {
                onSelect(hand.name.takeIf { hand != dev.aiharness.core.model.Hands.DEFAULT })
            }
        }
        YourFontsInPicker(selected, ink, onSelect)
    }
}
