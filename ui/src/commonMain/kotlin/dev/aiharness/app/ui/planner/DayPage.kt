package dev.aiharness.app.ui.planner

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.aiharness.app.platform.scrollingPage
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
import dev.aiharness.app.platform.PlatformBackHandler
import dev.aiharness.app.platform.rememberOutside
import dev.aiharness.app.platform.rememberTimePicker
import dev.aiharness.app.platform.systemUses24HourClock
import dev.aiharness.app.platform.onRightClick

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
 * A day of the planner: header B, then everything on it as a list in time order (see [DayList]),
 * "Add a plan", which opens a plan's card, and who sees your planner at the foot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannerDayScreen(nav: NavController, date: String) {
    val container = LocalAppContainer.current
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container) }
    // The day shown: the one opened, then the days either side ("‹ Thursday · Saturday ›").
    var shownDate by rememberSaveable(date) { mutableStateOf(date) }
    val day = remember(shownDate) { runCatching { LocalDate.parse(shownDate) }.getOrNull() } ?: return
    // The week or month page under this one, to go back up to (the day shown, in the view chosen).
    val below = nav.previousBackStackEntry?.takeIf { it.destination.route == dev.aiharness.app.ui.Routes.PLANNER }
    val under: PlannerViewModel? = below?.let { viewModel(it) { PlannerViewModel(container) } }
    val up = { v: PlannerView ->
        under?.let { it.shownDay.value = day; it.view.value = v }
        if (below != null) nav.popBackStack() else nav.navigate(dev.aiharness.app.ui.Routes.PLANNER)
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val seenBy by vm.seenBy.collectAsStateWithLifecycle()
    val outside = rememberOutside()
    val system24 = systemUses24HourClock()
    val clock = remember(settings.theme.clock) { ClockFormat(settings.theme.clock, system24) }
    // This day's phone calendar events too (the reading covers the month around the day shown).
    LaunchedEffect(day) { vm.shownDay.value = day }
    val phone by vm.phone.collectAsStateWithLifecycle()
    val all = remember(entries, phone, day) { vm.occurrences(entries + phone.entries, day, day) }
    val occurrences = all.filter { it.entry.byPersonaId == null }
    val theirs = all.filter { it.entry.byPersonaId != null }.sortedWith(compareBy<Occurrence> { it.entry.kind != EntryKind.AWAY }.thenBy { it.start ?: Long.MIN_VALUE })
    val personas by vm.personas.collectAsStateWithLifecycle()
    var card by remember { mutableStateOf<PlanDraft?>(null) }
    val takenDown = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val colors = paper
    val ink = stationery.you
    val today = vm.today()
    val you by vm.you.collectAsStateWithLifecycle()
    val dayTarget = you?.let { dev.aiharness.core.model.StickerTarget.day(it.id, day.toString()) }
    val weekday = { d: LocalDate -> d.dayOfWeek.getDisplayName(DayText.FULL, Locale.US) }
    PlannerStickerHost(you?.id, you) { stickers, bottom ->
    dev.aiharness.app.ui.theme.InHand(you?.hand) {
    Box(Modifier.fillMaxSize().background(colors.kami).pageDots(colors.sumi.copy(alpha = 0.12f))) {
        Column(Modifier.fillMaxSize().stitched(androidx.compose.ui.graphics.lerp(colors.nijimi2, colors.kami, 0.2f)).statusBarsPadding().padding(bottom = bottom)) {
            PlannerHeader(
                label = dayLabel(day, today),
                before = weekday(day.minusDays(1)), after = weekday(day.plusDays(1)),
                spokenBefore = "The day before, ${dayName(day.minusDays(1))}", spokenAfter = "The day after, ${dayName(day.plusDays(1))}",
                view = null,
                onBefore = { shownDate = day.minusDays(1).toString() },
                onAfter = { shownDate = day.plusDays(1).toString() },
                onWeek = { up(PlannerView.WEEK) },
                onMonth = { up(PlannerView.MONTH) },
            )
            // The rule under the header, by hand.
            Box(Modifier.fillMaxWidth().height(12.dp).padding(start = 24.dp, end = 22.dp).inkRule(ink, y = 7.dp))
            // The day's stickers go over everything on it.
            val page: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                if (dayTarget != null) stickers.On(dayTarget, Modifier.weight(1f).fillMaxWidth()) { inner() } else Box(Modifier.weight(1f)) { inner() }
            }
            page {
            Column(
                Modifier.fillMaxSize().scrollingPage().padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 28.dp + CORNER_ROOM),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // A public holiday, in red like a Sunday.
                vm.holidaysOn(day).forEach { name ->
                    Text(name.uppercase() + " · PUBLIC HOLIDAY", style = PaperType.meta.copy(fontSize = 11.sp), color = beni())
                }
                // A phone calendar's event opens in the phone's calendar app; yours, its card.
                val open = { o: Occurrence -> if (o.entry.phone != null) outside.openPhoneEvent(o.entry) else card = PlanDraft.of(o.entry, day, vm.zone) }
                // Everything on the day in time order, what your personas wrote among it.
                DayList(
                    day = day, today = today, now = vm.now(), yours = occurrences, theirs = theirs, personas = personas, clock = clock, zone = vm.zone,
                    actions = DayActions(
                        open = open,
                        stamp = { e, state -> vm.stamp(e, state) },
                        slip = { o, persona ->
                            PersonaSlip(
                                o, persona, vm.zone, { vm.hemisphere(settings) }, clock, onAdd = vm::addSuggestion,
                                onDecline = { e -> vm.declineSuggestion(e); takenDown += e.id },
                                takenDown = o.entry.id in takenDown,
                                onPutBack = { e -> vm.putBackSuggestion(e); takenDown -= e.id },
                            )
                        },
                    ),
                )
                PaperButton("+ Add a plan", modifier = Modifier.padding(top = 6.dp)) { card = PlanDraft.new(day) }
                // Who sees your planner, at the foot, quietly.
                WhoSees(seenBy.map { it.name }) { nav.navigate(dev.aiharness.app.ui.Routes.settings("planner")) }
            }
            }
        }
        // The ribbon: back to the day it marks; a long press moves it here.
        val marked = vm.bookmark(settings)
        PlannerRibbon(
            day == marked, "${marked.dayOfMonth} ${marked.month.getDisplayName(DayText.FULL, Locale.US)}", Modifier.align(Alignment.TopEnd),
            onGo = { shownDate = marked.toString() },
            onMoveHere = { vm.moveBookmark(day); outside.toast("Bookmark moved to ${weekday(day)}") },
        )
        if (dayTarget != null) StickersCorner(Modifier.align(Alignment.BottomEnd).padding(bottom = bottom), spoken = "Stick stickers on this day") { stickers.openTray(dayTarget) }
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
}

/** "Aria and Juno can see your planner. Who sees what", with an eye, at the foot of a day. */
@Composable
internal fun WhoSees(seenBy: List<String>, onWho: () -> Unit) {
    val colors = paper
    val ink = stationery.you
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            val w = size.width
            val h = size.height
            val eye = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.08f, h * 0.5f)
                cubicTo(w * 0.3f, h * 0.18f, w * 0.7f, h * 0.18f, w * 0.92f, h * 0.5f)
                cubicTo(w * 0.7f, h * 0.82f, w * 0.3f, h * 0.82f, w * 0.08f, h * 0.5f)
                close()
            }
            drawPath(eye, colors.usuzumi1, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx() * 0.6f))
            drawCircle(colors.usuzumi1, w * 0.13f, Offset(w / 2, h / 2))
        }
        Text(
            if (seenBy.isEmpty()) "Only you see your planner." else "${names(seenBy)} can see your planner.",
            style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp, lineHeight = 16.sp),
            color = colors.usuzumi1,
        )
        Text(
            "Who sees what",
            style = TextStyle(fontFamily = HarnessFonts.Ui, fontSize = 11.5.sp, lineHeight = 16.sp, textDecoration = TextDecoration.Underline),
            color = androidx.compose.ui.graphics.lerp(ink, colors.sumi, 0.3f),
            modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClickLabel = "Choose who sees your planner", onClick = onWho).padding(vertical = 8.dp),
        )
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
    PlatformBackHandler(onBack = onClose)
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
                    .scrollingPage()
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
    val pickTime = rememberTimePicker(clock.is24, onChange)
    Column(
        modifier
            .border(1.dp, colors.nijimi1, RoundedCornerShape(2.dp))
            .background(colors.hyoshi, RoundedCornerShape(2.dp))
            .clickable(role = Role.Button, onClickLabel = "Change $label") {
                pickTime(time)
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
