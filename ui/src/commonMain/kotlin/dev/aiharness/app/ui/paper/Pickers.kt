package dev.aiharness.app.ui.paper

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** The four tape patterns, each shown as a short piece of tape in [inkName]. */
@Composable
fun TapePicker(inkName: String?, selected: TapePattern, onSelect: (TapePattern) -> Unit, modifier: Modifier = Modifier) {
    val ink = stationery.ink(inkName)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TapePattern.entries.forEach { pattern ->
            val on = pattern == selected
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
                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    drawTape(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, Tape(ink, pattern, shadow = false))
                }
                Text(pattern.name.lowercase(), style = PaperType.meta.copy(letterSpacing = 0.sp), color = if (on) paper.sumi else paper.kasure2, maxLines = 1)
            }
        }
    }
}
