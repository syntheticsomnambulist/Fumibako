package dev.aiharness.app.ui.planner

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.Weekly
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle as DayText
import java.util.Locale

/**
 * Your typical week: your work, your sleep and anything else that comes round each week, printed
 * faintly on every day of the planner. Work and sleep make you busy; personas who see your
 * planner are told you're at work, or asleep, until when.
 */
@Composable
fun TypicalWeekScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val you by vm.you.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clock = remember(settings.theme.clock) { ClockFormat(settings.theme.clock, DateFormat.is24HourFormat(context)) }
    val colors = paper
    val ink = stationery.you
    val routines = entries.filter { it.kind == EntryKind.ROUTINE }.sortedBy { it.routine?.ordinal ?: 9 }
    Column(Modifier.fillMaxSize().background(colors.hyoshi).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the planner", tint = colors.usuzumi0) }
            Column {
                Text("Your typical week", style = PaperType.letterName.copy(fontSize = 22.sp, lineHeight = 24.sp), color = colors.sumi)
                Text("帳 · ${you?.name?.uppercase() ?: "YOUR"}'S PLANNER", style = PaperType.meta, color = colors.kasure2)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "What comes round each week, printed faintly on every day. Work and sleep make you busy: a persona who sees your planner is told you're at work, or asleep, and until when.",
                style = PaperType.postscript,
                color = colors.usuzumi1,
            )
            routines.forEach { r -> RoutineCard(r, clock, ink, onSave = vm::save, onDelete = vm::delete) }
            if (routines.isEmpty()) Text("Nothing yet.", style = PaperType.postscript, color = colors.kasure2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val add = { kind: RoutineKind, title: String, days: List<Int>, from: Int, to: Int ->
                    vm.blank(EntryKind.ROUTINE)?.let { vm.save(it.copy(title = title, routine = kind, weekly = Weekly(days, from, to), busy = kind != RoutineKind.OTHER)) }
                    Unit
                }
                if (routines.none { it.routine == RoutineKind.WORK }) PaperButton("+ Work") { add(RoutineKind.WORK, "Work", listOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60 + 30) }
                if (routines.none { it.routine == RoutineKind.SLEEP }) PaperButton("+ Sleep") { add(RoutineKind.SLEEP, "Sleep", (1..7).toList(), 23 * 60, 7 * 60) }
                PaperButton("+ Something else") { add(RoutineKind.OTHER, "", listOf(1), 18 * 60, 19 * 60) }
            }
        }
    }
}

/** One part of your typical week: what it is, which days, from when to when. */
@Composable
private fun RoutineCard(entry: PlannerEntry, clock: ClockFormat, ink: Color, onSave: (PlannerEntry) -> Unit, onDelete: (PlannerEntry) -> Unit) {
    val colors = paper
    // Its own copy while you change it, so typing isn't overtaken by the saved one coming back.
    var r by remember(entry.id) { mutableStateOf(entry) }
    val change = { next: PlannerEntry -> r = next; onSave(next) }
    val weekly = r.weekly ?: Weekly(listOf(1), 18 * 60, 19 * 60)
    Column(
        Modifier.fillMaxWidth().background(colors.kami, RoundedCornerShape(2.dp)).border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PaperChoice(
            listOf(RoutineKind.WORK to "Work", RoutineKind.SLEEP to "Sleep", RoutineKind.OTHER to "Something else"),
            r.routine ?: RoutineKind.OTHER,
            { k -> change(r.copy(routine = k, busy = k != RoutineKind.OTHER || r.busy)) },
            ink = ink,
        )
        IndexCardField("Called", r.title, { change(r.copy(title = it)) }, placeholder = "Swimming", singleLine = true, ink = ink)
        // Which days: tap to take one out or put it back.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { d ->
                val on = d.value in weekly.days
                Column(
                    Modifier
                        .size(40.dp, 46.dp)
                        .background(if (on) ink.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(2.dp))
                        .border(1.dp, if (on) ink else colors.nijimi1, RoundedCornerShape(2.dp))
                        .toggleable(on, role = Role.Checkbox) { now ->
                            val days = if (now) (weekly.days + d.value).sorted() else weekly.days - d.value
                            if (days.isNotEmpty()) change(r.copy(weekly = weekly.copy(days = days)))
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(PlannerDays.kanji(d), style = TextStyle(fontFamily = HarnessFonts.Brush, fontSize = 13.sp, lineHeight = 14.sp), color = dayInk(d, colors.sumi, stationery.isNight))
                    Text(d.getDisplayName(DayText.SHORT, Locale.US), style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 9.sp), color = colors.usuzumi1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("From", LocalTime.of(weekly.from / 60 % 24, weekly.from % 60), clock, ink, Modifier.weight(1f)) { t -> change(r.copy(weekly = weekly.copy(from = t.hour * 60 + t.minute))) }
            TimeField("To", LocalTime.of(weekly.to / 60 % 24, weekly.to % 60), clock, ink, Modifier.weight(1f)) { t -> change(r.copy(weekly = weekly.copy(to = t.hour * 60 + t.minute))) }
        }
        if (weekly.crossesMidnight) Text("It runs past midnight, into the next morning.", style = PaperType.postscript, color = colors.usuzumi1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r.routine == RoutineKind.OTHER) LabeledTick("Busy", r.busy, ink) { change(r.copy(busy = it)) }
            PaperButton("Take it out") { onDelete(r) }
        }
    }
}
