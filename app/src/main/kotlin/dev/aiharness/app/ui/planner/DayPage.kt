package dev.aiharness.app.ui.planner

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.TickBox
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.EntryKind
import dev.aiharness.core.model.EntryState
import dev.aiharness.core.model.PlannerEntry
import dev.aiharness.core.model.RoutineKind
import dev.aiharness.core.model.Weekly
import dev.aiharness.core.planner.Occurrence
import dev.aiharness.core.planner.Planner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle as DayText
import java.util.Locale

/** When a plan is: at a time, any time that day (a task), or all day. */
enum class PlanWhen { TIME, ANY, ALL_DAY }

/** A plan's card as it's being filled in: a new one for [day], or an existing [entry]. */
data class PlanDraft(
    val entry: PlannerEntry?,
    val day: LocalDate,
    val title: String = "",
    val whenKind: PlanWhen = PlanWhen.TIME,
    val from: LocalTime = LocalTime.of(18, 30),
    val to: LocalTime = LocalTime.of(20, 0),
    val place: String = "",
    val busy: Boolean = true,
    val weekly: Boolean = false,
) {
    companion object {
        fun new(day: LocalDate) = PlanDraft(null, day)

        /** The card for an entry already in the planner, as it is on [day]. */
        fun of(entry: PlannerEntry, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PlanDraft {
            val start = entry.start?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            val end = entry.end?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            return PlanDraft(
                entry = entry,
                day = entry.day?.let(LocalDate::parse) ?: entry.start?.let { PlannerDays.dayOf(it, zone) } ?: day,
                title = entry.title,
                whenKind = when {
                    entry.allDay -> PlanWhen.ALL_DAY
                    entry.start == null && entry.weekly == null -> PlanWhen.ANY
                    else -> PlanWhen.TIME
                },
                from = start ?: entry.weekly?.let { LocalTime.of(it.from / 60 % 24, it.from % 60) } ?: LocalTime.of(18, 30),
                to = end ?: entry.weekly?.let { LocalTime.of(it.to / 60 % 24, it.to % 60) } ?: (start ?: LocalTime.of(18, 30)).plusMinutes(90),
                place = entry.place.orEmpty(),
                busy = entry.busy,
                weekly = entry.weekly != null,
            )
        }
    }

    /** The entry it makes, from [blank] for a new one: a plan, or with "every week" part of your typical week. */
    fun toEntry(blank: (EntryKind) -> PlannerEntry?, zone: ZoneId): PlannerEntry? {
        val kind = if (weekly && whenKind == PlanWhen.TIME) EntryKind.ROUTINE else EntryKind.PLAN
        val base = entry?.copy(kind = kind) ?: blank(kind) ?: return null
        val name = title.trim()
        return when {
            kind == EntryKind.ROUTINE -> base.copy(
                title = name, start = null, end = null, day = null, allDay = false,
                weekly = Weekly(listOf(day.dayOfWeek.value), from.hour * 60 + from.minute, to.hour * 60 + to.minute),
                routine = base.routine ?: RoutineKind.OTHER, place = place.trim().ifBlank { null }, busy = busy,
            )
            whenKind == PlanWhen.TIME -> {
                val start = day.atTime(from).atZone(zone).toInstant().toEpochMilli()
                // An end before its start runs past midnight.
                val endDay = if (to <= from) day.plusDays(1) else day
                base.copy(
                    title = name, start = start, end = endDay.atTime(to).atZone(zone).toInstant().toEpochMilli(), day = null, allDay = false,
                    weekly = null, place = place.trim().ifBlank { null }, busy = busy,
                )
            }
            else -> base.copy(
                title = name, start = null, end = null, day = day.toString(), allDay = whenKind == PlanWhen.ALL_DAY,
                weekly = null, place = place.trim().ifBlank { null }, busy = busy && whenKind == PlanWhen.ALL_DAY,
            )
        }
    }
}

/**
 * A day of the planner: everything on it in time order (your typical week's bands, your plans,
 * what's untimed), and "Add a plan", which opens a plan's card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannerDayScreen(nav: NavController, date: String) {
    val container = LocalAppContainer.current
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container) }
    val day = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() } ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val seenBy by vm.seenBy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clock = remember(settings.theme.clock) { ClockFormat(settings.theme.clock, DateFormat.is24HourFormat(context)) }
    val occurrences = remember(entries, day) { vm.occurrences(entries, day, day).filter { it.entry.byPersonaId == null } }
    var card by remember { mutableStateOf<PlanDraft?>(null) }
    val colors = paper
    val ink = stationery.you
    val today = vm.today()
    val you by vm.you.collectAsStateWithLifecycle()
    val dayTarget = you?.let { dev.aiharness.core.model.StickerTarget.day(it.id, day.toString()) }
    PlannerStickerHost(you?.id, you) { stickers, bottom ->
    Box(Modifier.fillMaxSize().background(colors.kami)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottom)) {
            Row(
                Modifier.fillMaxWidth().drawBehind { drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the week", tint = colors.usuzumi0) }
                // The title takes what's left, so the stickers button always keeps its place.
                Column(Modifier.weight(1f)) {
                    Text(
                        "${day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(DayText.FULL, Locale.US)}",
                        style = PaperType.letterName.copy(fontSize = 24.sp, lineHeight = 26.sp),
                        color = colors.sumi,
                    )
                    val who = if (seenBy.isEmpty()) "ONLY YOU SEE YOUR PLANNER" else "${names(seenBy.map { it.name }).uppercase()} CAN SEE YOUR PLANNER"
                    Text((if (day == today) "TODAY · " else "") + who, style = PaperType.meta, color = ink)
                }
                if (dayTarget != null) {
                    Box(
                        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClickLabel = "Stick stickers on this day") { stickers.openTray(dayTarget) },
                        contentAlignment = Alignment.Center,
                    ) { StickersMark() }
                }
            }
            // The day's stickers go over everything on it.
            val page: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                if (dayTarget != null) stickers.On(dayTarget, Modifier.weight(1f).fillMaxWidth()) { inner() } else Box(Modifier.weight(1f)) { inner() }
            }
            page {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val timed = occurrences.filter { it.start != null }.sortedBy { it.start }
                val untimed = occurrences.filter { it.start == null }
                timed.forEach { o -> DayRow(o, day, clock, vm.zone, ink, onOpen = { card = PlanDraft.of(o.entry, day, vm.zone) }, onStamp = { vm.stamp(o.entry, EntryState.DONE) }) }
                untimed.forEach { o -> DayRow(o, day, clock, vm.zone, ink, onOpen = { card = PlanDraft.of(o.entry, day, vm.zone) }, onStamp = { vm.stamp(o.entry, EntryState.DONE) }) }
                if (occurrences.isEmpty()) Text("Nothing planned yet.", style = PaperType.postscript, color = colors.usuzumi1)
                PaperButton("+ Add a plan", modifier = Modifier.padding(top = 6.dp)) { card = PlanDraft.new(day) }
            }
            }
        }
        card?.let { draft ->
            PlanCard(
                draft = draft, clock = clock, zone = vm.zone, ink = ink, seenBy = seenBy.map { it.name },
                onSave = { e -> vm.save(e); card = null },
                onDelete = { e -> vm.delete(e); card = null },
                onClose = { card = null },
                blank = { kind -> vm.blank(kind) },
            )
        }
    }
    }
}

/** One thing on a day: a band of your typical week, a plan at a time (○), or an untimed one (•). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayRow(o: Occurrence, day: LocalDate, clock: ClockFormat, zone: ZoneId, ink: Color, onOpen: () -> Unit, onStamp: () -> Unit) {
    val colors = paper
    val e = o.entry
    val busy = Planner.isBusy(e)
    val start = o.start?.let { clock.time(PlannerDays.minuteOf(it, day, zone)) }
    val end = o.end?.takeIf { it != o.start }?.let { clock.time(PlannerDays.minuteOf(it, day, zone)) }
    if (e.kind == EntryKind.ROUTINE) {
        val what = e.title.ifBlank { if (e.routine == RoutineKind.SLEEP) "Sleep" else if (e.routine == RoutineKind.WORK) "Work" else "Routine" }
        // Until when it really ends: sleep from 23:00 runs to the morning, past this day's midnight.
        val end = e.weekly?.let { clock.time(it.to) } ?: end
        Row(
            Modifier.fillMaxWidth().background(colors.chitsu, RoundedCornerShape(2.dp)).clickable(onClickLabel = "Open your typical week's $what") { onOpen() }
                .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(4.dp).heightIn(min = 18.dp).then(if (busy) Modifier.hatched(colors.kasure2) else Modifier))
            Text(start ?: "", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp), color = colors.usuzumi1, modifier = Modifier.width(72.dp), maxLines = 1, softWrap = false)
            Text(
                listOfNotNull(what.uppercase() + (end?.let { " UNTIL $it" } ?: ""), "YOUR TYPICAL WEEK".takeIf { !busy }, "BUSY".takeIf { busy }).joinToString(" · "),
                style = PaperType.meta.copy(fontSize = 10.5.sp),
                color = colors.usuzumi1,
            )
        }
        return
    }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClickLabel = "Open ${e.title}", onLongClickLabel = "Stamp it done", onLongClick = onStamp, onClick = onOpen)
            .drawBehind { drawLine(colors.nijimi1, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).heightIn(min = 18.dp).then(if (busy) Modifier.hatched(colors.kasure2) else Modifier))
        Text(start ?: if (e.allDay) "all day" else "any time", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp), color = colors.usuzumi1, modifier = Modifier.width(72.dp), maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f)) {
            Text(
                (if (start != null) "○ " else "• ") + e.title.ifBlank { "A plan" } + (end?.let { ", until $it" } ?: ""),
                style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.sp, textDecoration = if (e.state == EntryState.CANCELLED) TextDecoration.LineThrough else null),
                color = ink,
            )
            val meta = listOfNotNull(e.place?.uppercase(), "BUSY".takeIf { busy }, "MOVED".takeIf { e.movedFrom != null }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = PaperType.meta.copy(fontSize = 10.5.sp), color = colors.kasure2)
        }
        stampOf(e.state)?.let { Stamp(it, ink, size = 22.dp) }
    }
}

/**
 * A plan's card, sliding up over the page: what, when (at a time, any time, all day), from and
 * to, where, whether it makes you busy, and whether it's every week (then it's part of your
 * typical week). Saving adds it, or changes the one it was opened on.
 */
@Composable
fun PlanCard(
    draft: PlanDraft,
    clock: ClockFormat,
    zone: ZoneId,
    ink: Color,
    seenBy: List<String>,
    onSave: (PlannerEntry) -> Unit,
    onDelete: (PlannerEntry) -> Unit,
    onClose: () -> Unit,
    blank: (EntryKind) -> PlannerEntry?,
) {
    BackHandler(onBack = onClose)
    val colors = paper
    var d by remember(draft) { mutableStateOf(draft) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close without saving", onClick = onClose),
        )
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(if (dev.aiharness.app.ui.media.reducedMotion()) 0 else 320)) { it }) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.86f)
                    .shadow(12.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(colors.kami, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (draft.entry == null) "A plan for ${d.day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)}" else "Your plan",
                        style = PaperType.letterName.copy(fontSize = 24.sp),
                        color = colors.sumi,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Cancel", style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 13.sp), color = colors.usuzumi0, modifier = Modifier.clickable(role = Role.Button, onClick = onClose).padding(10.dp))
                }
                IndexCardField("What", d.title, { d = d.copy(title = it) }, placeholder = "Tea with Mori", singleLine = true, ink = ink)
                DayStepper(d.day) { d = d.copy(day = it) }
                PaperChoice(
                    listOf(PlanWhen.TIME to "At a time", PlanWhen.ANY to "Any time", PlanWhen.ALL_DAY to "All day"),
                    d.whenKind,
                    { w -> d = d.copy(whenKind = w, busy = w == PlanWhen.TIME) },
                    ink = ink,
                )
                if (d.whenKind == PlanWhen.TIME) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField("From", d.from, clock, ink, Modifier.weight(1f)) { d = d.copy(from = it) }
                        TimeField("To", d.to, clock, ink, Modifier.weight(1f)) { d = d.copy(to = it) }
                    }
                }
                IndexCardField("Where", d.place, { d = d.copy(place = it) }, placeholder = "Optional", singleLine = true, ink = ink)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (d.whenKind != PlanWhen.ANY) LabeledTick("Busy", d.busy, ink) { d = d.copy(busy = it) }
                    if (d.whenKind == PlanWhen.TIME) LabeledTick("Every week", d.weekly, ink) { d = d.copy(weekly = it) }
                }
                val who = if (seenBy.isEmpty()) "your personas" else names(seenBy)
                Text(
                    when {
                        d.weekly -> "Every ${d.day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)}, as part of your typical week."
                        d.busy && d.whenKind != PlanWhen.ANY -> "While it's on, $who ${if (seenBy.size == 1) "is" else "are"} told you're busy."
                        else -> "${who.replaceFirstChar { it.uppercase() }} can see it, but it doesn't make you busy."
                    },
                    style = PaperType.postscript,
                    color = colors.usuzumi1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaperButton("Save", ink = ink, enabled = d.title.isNotBlank()) { d.toEntry(blank, zone)?.let(onSave) }
                    draft.entry?.let { e -> PaperButton("Take it out") { onDelete(e) } }
                }
            }
        }
    }
}

/** A tick box with its words, the whole row a target. */
@Composable
fun LabeledTick(label: String, checked: Boolean, ink: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TickBox(checked, null, ink = ink)
        Text(label, style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 13.5.sp), color = paper.sumi)
    }
}

/** The day a plan is on, a day earlier or later. */
@Composable
private fun DayStepper(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val colors = paper
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = TextStyle(fontFamily = HarnessFonts.Display, fontSize = 24.sp), color = colors.usuzumi1, modifier = Modifier.clickable(role = Role.Button, onClickLabel = "A day earlier") { onChange(day.minusDays(1)) }.padding(horizontal = 12.dp, vertical = 6.dp))
        Text(
            "${day.dayOfWeek.getDisplayName(DayText.FULL, Locale.US)} ${day.dayOfMonth} ${day.month.getDisplayName(DayText.FULL, Locale.US)}",
            style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 15.sp),
            color = colors.sumi,
        )
        Text("›", style = TextStyle(fontFamily = HarnessFonts.Display, fontSize = 24.sp), color = colors.usuzumi1, modifier = Modifier.clickable(role = Role.Button, onClickLabel = "A day later") { onChange(day.plusDays(1)) }.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** A time on an index card; tapping it opens the phone's time picker. */
@Composable
fun TimeField(label: String, time: LocalTime, clock: ClockFormat, ink: Color, modifier: Modifier = Modifier, onChange: (LocalTime) -> Unit) {
    val colors = paper
    val context = LocalContext.current
    Column(
        modifier
            .border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp))
            .background(colors.hyoshi, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClickLabel = "Change $label") {
                TimePickerDialog(context, { _, h, m -> onChange(LocalTime.of(h, m)) }, time.hour, time.minute, clock.is24).show()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label.uppercase(),
            style = PaperType.label.copy(fontSize = 10.sp),
            color = ink,
            modifier = Modifier.fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }.padding(bottom = 4.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(clock.time(time), style = TextStyle(fontFamily = HarnessFonts.Read, fontSize = 16.sp), color = colors.sumi)
    }
}
