package dev.aiharness.app.ui.personas

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.activityPhrase
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.StepperRow
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.settings.QuietHours
import dev.aiharness.app.ui.settings.shortInterval
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.ActivityKind
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatRhythm
import dev.aiharness.core.model.Persona
import kotlinx.coroutines.launch

private val INTERVALS = listOf(15, 30, 60, 180, 360)

/** One persona's heartbeat: on or off, the usual rhythm or its own, its model, and what it did. */
@Composable
fun PersonaHeartbeatScreen(nav: NavController, personaId: String) {
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val heartbeats = LocalAppContainer.current.heartbeats
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(null)
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val since = remember { System.currentTimeMillis() - 3 * 86_400_000L }
    val activity by remember { harness.activity.observeSince(since) }.collectAsStateWithLifecycle(emptyList())
    val p = personas?.firstOrNull { it.id == personaId } ?: return
    val s = settings ?: return
    val ink = stationery.ink(p.ink)
    val tape = Tape(ink, p.tapePattern)
    val layouts = tapeLayouts("heartbeat-${p.id}", 4)
    fun save(transform: (Persona) -> Persona) = scope.launch { harness.personas.upsert(transform(p)) }
    fun rhythm(transform: (HeartbeatRhythm) -> HeartbeatRhythm) = save { it.copy(ownHeartbeat = transform(it.ownHeartbeat ?: s.heartbeat.rhythm)) }
    val own = p.ownHeartbeat

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageBar(onBack = { nav.popBackStack() }, backLabel = "Back to ${p.name}'s page")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PortraitDot(p, 34.dp)
                Column {
                    Text("${p.name}'s heartbeat", style = PaperType.letterName.copy(fontSize = 22.sp, lineHeight = 24.sp), color = paper.sumi)
                    Text("ON ${p.name.uppercase()}'S PAGE", style = PaperType.meta, color = paper.kasure2)
                }
            }
        }
        item {
            TapedCard(layouts[0], tape, tilt = cardTilt("${p.id}-hb-0")) {
                SwitchRow("Wakes up between letters", p.heartbeatEnabled, { v -> save { it.copy(heartbeatEnabled = v) } }, subtitle = "Reflects, tends memories, and may write to you.", ink = ink)
                if (s.heartbeat.mode != HeartbeatMode.PROACTIVE) {
                    Text("Heartbeats are set to \"${if (s.heartbeat.mode == HeartbeatMode.OFF) "Off" else "Time only"}\" for everyone in Settings.", style = PaperType.noteBody.copy(fontSize = 12.5.sp), color = paper.usuzumi0)
                }
                PaperChoice(listOf(false to "The usual settings", true to "${p.name}'s own"), own != null, { mine -> save { it.copy(ownHeartbeat = if (mine) s.heartbeat.rhythm else null) } }, ink = ink)
            }
        }
        item {
            TapedCard(layouts[2], tape, tilt = cardTilt("${p.id}-hb-planner")) {
                Text("THE PLANNER", style = PaperType.meta, color = paper.kasure2)
                SwitchRow(
                    "Sees my planner", p.seesPlanner, { v -> save { it.copy(seesPlanner = v) } },
                    subtitle = "When the persona of yours ${p.name} writes to shares theirs. ${p.name} can then write in it: notes, suggestions, reminders, time away.", ink = ink,
                )
                SwitchRow(
                    "Writes a haiku as each season begins", p.planner.haiku, { v -> save { it.copy(planner = it.planner.copy(haiku = v)) } },
                    subtitle = "On a slip in your planner, on the first day of a new microseason. It counts as one of ${p.name}'s things in a heartbeat.", ink = ink,
                )
            }
        }
        if (own != null) {
            item {
                TapedCard(layouts[1], tape, tilt = cardTilt("${p.id}-hb-1")) {
                    Text("WAKE EVERY", style = PaperType.meta, color = paper.kasure2)
                    PaperChoice(INTERVALS.map { it to shortInterval(it) }, own.intervalMinutes.takeIf { it in INTERVALS } ?: 60, { v -> rhythm { it.copy(intervalMinutes = v) } }, ink = ink)
                    QuietHours(own, onChange = { start, end -> rhythm { it.copy(quietHoursStart = start, quietHoursEnd = end) } })
                    StepperRow("Wait after the last letter", own.minSilenceMinutes / 60, { h -> rhythm { it.copy(minSilenceMinutes = h * 60) } }, 0..48, describe = { "$it h" })
                    StepperRow("At most, per day", own.maxProactivePerDay, { n -> rhythm { it.copy(maxProactivePerDay = n) } }, 0..12)
                    StepperRow("Stop after unanswered", own.maxUnansweredProactive, { n -> rhythm { it.copy(maxUnansweredProactive = n) } }, 0..6)
                    StepperRow("Activities per heartbeat", own.activityLimit, { n -> rhythm { it.copy(activityLimit = n) } }, 1..10)
                    Text("Writing to you uses one. Reflecting and tending memories are free.", style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = paper.usuzumi0)
                }
            }
        }
        item {
            TapedCard(layouts[2], tape, tilt = cardTilt("${p.id}-hb-2")) {
                CardTitle("Model for heartbeats")
                PaperSelect(null, profiles, profiles.firstOrNull { it.id == p.heartbeatModelProfileId }, { it.name }, { m -> save { it.copy(heartbeatModelProfileId = m?.id) } }, noneLabel = "Same as ${p.name}'s letters")
                Text("A stronger model can tend memories in the background even if letters use a lighter one.", style = PaperType.noteBody.copy(fontSize = 12.5.sp), color = paper.usuzumi0)
            }
        }
        item {
            val mine = activity.filter { it.personaId == p.id && (it.kind.isBeat || it.kind == ActivityKind.SAVED_MEMORIES || it.kind == ActivityKind.REFLECTED) }.takeLast(8).asReversed()
            TapedCard(layouts[3], tape, tilt = cardTilt("${p.id}-hb-3")) {
                CardTitle("Recent heartbeats", trailing = { PaperButton("Run one now", ink = ink) { heartbeats.runNow(s.heartbeat, p.id) } })
                if (mine.isEmpty()) Text("Nothing yet.", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.usuzumi0)
                mine.forEach { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            activityPhrase(entry.kind, entry.count, entry.note).replaceFirstChar { it.uppercase() },
                            style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = if (entry.kind == ActivityKind.RESTED) paper.kasure2 else paper.usuzumi1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(letterTime(entry.createdAt), style = PaperType.meta, color = paper.kasure2)
                    }
                }
            }
        }
    }
}
