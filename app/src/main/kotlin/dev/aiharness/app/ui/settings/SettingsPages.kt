package dev.aiharness.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.activityPhrase
import dev.aiharness.app.ui.letterTime
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.IndexCardField
import dev.aiharness.app.ui.paper.InkLink
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.PaperChoice
import dev.aiharness.app.ui.paper.PaperCorner
import dev.aiharness.app.ui.paper.PaperSelect
import dev.aiharness.app.ui.paper.PortraitDot
import dev.aiharness.app.ui.paper.StepperRow
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.image.NovelAiImageGenerator
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.CredentialKind
import dev.aiharness.core.model.EmbeddingProviderType
import dev.aiharness.core.model.EmbeddingSettings
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.HeartbeatRhythm

internal typealias Update = ((AppSettings) -> AppSettings) -> Unit

@Composable
internal fun Hint(text: String) {
    Text(text, style = PaperType.noteBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = paper.usuzumi0)
}

// --- 一 Look ------------------------------------------------------------------------------------

internal fun SettingsCards.lookPage(s: AppSettings, update: Update) {
    card { LookDayNight(s, update) }
    card { LookPaper(s, update) }
}

// --- 二 Heartbeat ------------------------------------------------------------------------------

private val INTERVALS = listOf(15, 30, 60, 180, 360)

internal fun SettingsCards.heartbeatPage(nav: NavController, s: AppSettings, update: Update) {
    val hb = s.heartbeat
    fun rhythm(transform: (HeartbeatRhythm) -> HeartbeatRhythm) = update { it.copy(heartbeat = it.heartbeat.copy(rhythm = transform(it.heartbeat.rhythm))) }
    card {
        val modes = listOf(HeartbeatMode.PROACTIVE to "Proactive", HeartbeatMode.TIME_AWARENESS to "Time only", HeartbeatMode.OFF to "Off")
        PaperChoice(modes, hb.mode, { mode -> update { it.copy(heartbeat = it.heartbeat.copy(mode = mode)) } })
        Text(
            when (hb.mode) {
                HeartbeatMode.PROACTIVE -> "Personas wake up now and then to reflect and tend their memories, and may write to you. These are the usual settings; a persona can have its own on its page."
                HeartbeatMode.TIME_AWARENESS -> "No background activity. Each letter tells the persona the time and how long it's been."
                HeartbeatMode.OFF -> "No background activity and no time information."
            },
            style = PaperType.prose,
            color = paper.usuzumi1,
        )
    }
    if (hb.mode == HeartbeatMode.PROACTIVE) {
        card { OwnRhythms(nav) }
        card {
            CardTitle("Rhythm")
            Text("WAKE EVERY", style = PaperType.meta, color = paper.kasure2)
            PaperChoice(INTERVALS.map { it to shortInterval(it) }, hb.rhythm.intervalMinutes.takeIf { it in INTERVALS } ?: 60, { v -> rhythm { it.copy(intervalMinutes = v) } })
            QuietHours(hb.rhythm, onChange = { start, end -> rhythm { it.copy(quietHoursStart = start, quietHoursEnd = end) } })
            StepperRow("Activities per heartbeat", hb.rhythm.activityLimit, { n -> rhythm { it.copy(activityLimit = n) } }, 1..10)
            Hint("Writing to you uses one. Reflecting and tending memories are free.")
        }
        card {
            CardTitle("Reaching out")
            StepperRow("Wait after the last letter", hb.rhythm.minSilenceMinutes / 60, { h -> rhythm { it.copy(minSilenceMinutes = h * 60) } }, 0..48, describe = { "$it h" })
            StepperRow("At most, per day", hb.rhythm.maxProactivePerDay, { n -> rhythm { it.copy(maxProactivePerDay = n) } }, 0..12)
            StepperRow("Stop after unanswered", hb.rhythm.maxUnansweredProactive, { n -> rhythm { it.copy(maxUnansweredProactive = n) } }, 0..6)
            SwitchRow("Only on Wi-Fi", hb.requireUnmeteredNetwork, { v -> update { it.copy(heartbeat = it.heartbeat.copy(requireUnmeteredNetwork = v)) } })
        }
        card { RecentHeartbeats(s) }
    }
    if (hb.mode != HeartbeatMode.OFF) {
        card {
            CardTitle("Telling the time")
            StepperRow("Mention the gap after", hb.timeGapNoteMinutes, { n -> update { it.copy(heartbeat = it.heartbeat.copy(timeGapNoteMinutes = n)) } }, 5..720, step = 5, describe = { if (it < 60) "$it min" else "${it / 60} h ${it % 60}".removeSuffix(" 0") })
            Hint("When a letter comes after a longer silence than this, the persona is told how long it's been.")
        }
    }
}

fun shortInterval(minutes: Int) = if (minutes < 60) "$minutes min" else "${minutes / 60} h"

/** Quiet hours as two times and a little 24-hour ruler with the quiet part shaded. */
@Composable
fun QuietHours(rhythm: HeartbeatRhythm, onChange: (Int?, Int?) -> Unit) {
    val colors = paper
    val you = stationery.you
    val hours = (0..23).toList()
    Text("QUIET HOURS", style = PaperType.meta, color = colors.kasure2)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PaperSelect(null, hours, rhythm.quietHoursStart, { "%02d:00".format(it) }, { v -> onChange(v, rhythm.quietHoursEnd ?: 8) }, Modifier.weight(1f), noneLabel = "None")
        Text("to", style = PaperType.noteBody, color = colors.kasure2)
        PaperSelect(null, hours, rhythm.quietHoursEnd, { "%02d:00".format(it) }, { v -> onChange(rhythm.quietHoursStart ?: 23, v) }, Modifier.weight(1f), noneLabel = "None")
    }
    val start = rhythm.quietHoursStart
    val end = rhythm.quietHoursEnd
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val w = size.width
        val h = size.height
        fun x(hour: Int) = w * hour / 24f
        if (start != null && end != null && start != end) {
            val shade = you.copy(alpha = 0.3f)
            if (start < end) {
                drawRect(shade, Offset(x(start), 0f), Size(x(end) - x(start), h))
            } else {
                drawRect(shade, Offset(x(start), 0f), Size(w - x(start), h))
                drawRect(shade, Offset.Zero, Size(x(end), h))
            }
        }
        for (hour in 0..24) {
            val tick = if (hour % 6 == 0) h else h * 0.35f
            drawLine(colors.nijimi2, Offset(x(hour), h - tick), Offset(x(hour), h), 1.dp.toPx())
        }
        drawRect(colors.nijimi1, style = Stroke(1.dp.toPx()))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "06", "12", "18", "24").forEach { Text(it, style = PaperType.meta.copy(letterSpacing = 0.sp), color = colors.kasure2) }
    }
}

@Composable
private fun OwnRhythms(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val own = personas.filter { it.ownHeartbeat != null || !it.heartbeatEnabled }
    Text("WITH THEIR OWN SETTINGS", style = PaperType.meta, color = paper.kasure2)
    if (own.isEmpty()) {
        Hint("Every persona follows the usual settings. Give one its own rhythm from its page.")
    }
    own.forEach { p ->
        val ink = stationery.ink(p.ink)
        val detail = when {
            !p.heartbeatEnabled -> "heartbeat off"
            else -> p.ownHeartbeat!!.let { r -> "every ${interval(r.intervalMinutes)}" + (r.quietHoursStart?.let { qs -> ", quiet %02d:00 to %02d:00".format(qs, r.quietHoursEnd ?: 8) } ?: "") }
        }
        Row(
            Modifier.fillMaxWidth().clip(PaperCorner).clickable(role = Role.Button) { nav.navigate(Routes.personaHeartbeat(p.id)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PortraitDot(p, 20.dp)
            Text(
                buildString { append(p.name); append(" · "); append(detail) },
                style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = paper.usuzumi1,
                modifier = Modifier.weight(1f),
            )
            Text("Open", style = PaperType.meta.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Bold), color = ink)
        }
    }
}

@Composable
private fun RecentHeartbeats(s: AppSettings) {
    val container = LocalAppContainer.current
    val harness = container.harness
    val context = LocalContext.current
    val since = remember { System.currentTimeMillis() - 2 * 86_400_000L }
    val entries by remember { harness.activity.observeSince(since) }.collectAsStateWithLifecycle(emptyList())
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    CardTitle("Recent heartbeats", trailing = { PaperButton("Run one now", ink = stationery.you) { HeartbeatScheduler.runNow(context, s.heartbeat) } })
    val shown = entries.filter { it.kind.isBeat || it.kind == dev.aiharness.core.model.ActivityKind.SAVED_MEMORIES }.takeLast(8).asReversed()
    if (shown.isEmpty()) Hint("Nothing yet. Heartbeats show up here as they happen.")
    shown.forEach { entry ->
        val persona = personas.firstOrNull { it.id == entry.personaId }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PortraitDot(persona, 20.dp)
            Text(
                "${persona?.name ?: "Someone"} ${activityPhrase(entry.kind, entry.count, entry.note)}",
                style = PaperType.noteBody.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = if (entry.kind == dev.aiharness.core.model.ActivityKind.RESTED) paper.kasure2 else paper.usuzumi1,
                modifier = Modifier.weight(1f),
            )
            Text(letterTime(entry.createdAt), style = PaperType.meta, color = paper.kasure2)
        }
    }
}

// --- 三 Memory ----------------------------------------------------------------------------------

internal fun SettingsCards.memoryPage(s: AppSettings, update: Update) {
    val m = s.memory
    fun memory(transform: (dev.aiharness.core.model.MemorySettings) -> dev.aiharness.core.model.MemorySettings) = update { it.copy(memory = transform(it.memory)) }
    card {
        SwitchRow("Long-term memory", m.enabled, { v -> memory { it.copy(enabled = v) } }, subtitle = "Personas keep and recall memories between letters.")
        if (m.enabled) {
            SwitchRow("Memory tools", m.memoryToolsEnabled, { v -> memory { it.copy(memoryToolsEnabled = v) } }, subtitle = "Personas keep, search and change their own memories.")
            StepperRow("Memories brought into each letter", m.retrievalTopK, { n -> memory { it.copy(retrievalTopK = n) } }, 0..20)
            StepperRow("Write up conversation every", m.episodeEveryMessages, { n -> memory { it.copy(episodeEveryMessages = n) } }, 10..200, step = 10, describe = { "$it msgs" })
        }
    }
    if (!m.enabled) return
    card {
        CardTitle("Whose memories")
        SwitchRow(
            "Let me edit memories",
            m.userCanEdit,
            { v -> memory { it.copy(userCanEdit = v) } },
            subtitle = "Change what a persona remembers or how it felt, or make it forget. Off: you can read them, but they're the persona's own.",
        )
    }
    card {
        CardTitle("Forgetting")
        SwitchRow("Show memories aging", m.showAging, { v -> memory { it.copy(showAging = v) } }, subtitle = "Faded and worn pages in the memory book.")
        SwitchRow("Burn forgotten memories", m.burnOnForget, { v -> memory { it.copy(burnOnForget = v) } }, subtitle = "Play the burning when one is let go.")
        StepperRow("Most that matter deeply", m.deepLimit, { n -> memory { it.copy(deepLimit = n) } }, 5..60, step = 5)
        Hint("Over this, a heartbeat asks the persona which memories can settle to \"worth keeping\".")
    }
    card { RecallByMeaning(s, update) }
}

@Composable
private fun RecallByMeaning(s: AppSettings, update: Update) {
    val harness = LocalAppContainer.current.harness
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val embedding = s.memory.embedding
    fun set(value: EmbeddingSettings?) = update { it.copy(memory = it.memory.copy(embedding = value)) }
    CardTitle("Recall by meaning")
    Hint("Optional. An embedding model finds memories that mean the same thing in other words. Without it, recall matches words.")
    PaperSelect(
        "Embeddings",
        EmbeddingProviderType.entries,
        embedding?.provider,
        { if (it == EmbeddingProviderType.GEMINI) "Gemini" else "OpenAI-compatible" },
        { type ->
            set(type?.let { t -> EmbeddingSettings(provider = t, modelId = if (t == EmbeddingProviderType.GEMINI) "gemini-embedding-001" else "text-embedding-3-small") })
        },
        noneLabel = "Off (match words)",
    )
    if (embedding != null) {
        IndexCardField("Embedding model", embedding.modelId, { v -> set(embedding.copy(modelId = v)) }, singleLine = true, style = PaperType.noteBody)
        if (embedding.provider == EmbeddingProviderType.OPENAI_COMPATIBLE) {
            IndexCardField("Base URL", embedding.baseUrl.orEmpty(), { v -> set(embedding.copy(baseUrl = v.ifBlank { null })) }, placeholder = "https://api.openai.com/v1", singleLine = true, style = PaperType.noteBody)
        }
        PaperSelect("Key", credentials, credentials.firstOrNull { it.id == embedding.credentialId }, { it.label }, { c -> set(embedding.copy(credentialId = c?.id)) }, noneLabel = "None")
    }
}

// --- 四 Pictures --------------------------------------------------------------------------------

internal fun SettingsCards.picturesPage(nav: NavController, s: AppSettings, update: Update) {
    val img = s.imageGen
    fun image(transform: (dev.aiharness.core.model.ImageGenSettings) -> dev.aiharness.core.model.ImageGenSettings) = update { it.copy(imageGen = transform(it.imageGen)) }
    card {
        SwitchRow("Pictures", img.enabled, { v -> image { it.copy(enabled = v) } }, subtitle = "Drawn by NovelAI, your own Stable Diffusion, OpenAI or Google.")
        if (img.enabled) SwitchRow("Personas can send pictures", img.agentToolEnabled, { v -> image { it.copy(agentToolEnabled = v) } }, subtitle = "They can draw one when it fits the moment.")
    }
    if (!img.enabled) return
    card { ImageProfileList(nav, img) { id -> image { it.copy(defaultProfileId = id) } } }
    card {
        CardTitle("Style")
        IndexCardField("Drawing style", img.defaultStyle, { v -> image { it.copy(defaultStyle = v) } }, style = PaperType.noteBody, minLines = 2, placeholder = "watercolor, soft lighting, by (an artist)")
        Hint("Artist and style tags every persona draws in, unless its page gives it its own.")
    }
}

@Composable
private fun ImageProfileList(nav: NavController, img: dev.aiharness.core.model.ImageGenSettings, onDefault: (String?) -> Unit) {
    val colors = paper
    val default = img.profileFor(null)
    CardTitle("Image profiles")
    if (img.profiles.isEmpty()) Hint("None yet. Add one to start drawing.")
    img.profiles.forEach { profile ->
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PaperCorner)
                .background(colors.hyoshi, PaperCorner)
                .clickable(role = Role.Button, onClickLabel = "Edit ${profile.name}") { nav.navigate(Routes.imageProfile(profile.id)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = PaperType.entryTitle.copy(fontSize = 18.sp), color = colors.sumi, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.id == default?.id) Stamp("Default", stationery.you)
            }
            val detail = listOfNotNull(profile.host.label, profile.model.takeIf { it.isNotBlank() }, if (!profile.isUsable) "not set up" else null)
            Text(detail.joinToString(" · ").uppercase(), style = PaperType.meta.copy(letterSpacing = 0.04.sp), color = colors.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    DashedButton("+ New image profile") { nav.navigate(Routes.imageProfile(Routes.NEW)) }
    if (img.profiles.size > 1) {
        PaperSelect("Default for pictures", img.profiles, default, { it.name }, { p -> onDefault(p?.id) })
    }
}

// --- 五 Models and keys -------------------------------------------------------------------------

internal fun SettingsCards.modelsPage(nav: NavController, s: AppSettings, update: Update) {
    card { ModelProfiles(nav, s, update) }
    card { Keys(nav) }
}

@Composable
private fun ModelProfiles(nav: NavController, s: AppSettings, update: Update) {
    val harness = LocalAppContainer.current.harness
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val personas by remember { harness.personas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val colors = paper
    CardTitle("Model profiles")
    profiles.forEach { profile ->
        val users = personas.filter { it.defaultModelProfileId == profile.id }.map { it.name }
        val stamp = when (profile.id) {
            s.defaultModelProfileId -> "Default" to stationery.you
            s.utilityModelProfileId -> "Utility" to stationery.ink("tokiwa")
            else -> null
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PaperCorner)
                .background(colors.hyoshi, PaperCorner)
                .clickable(role = Role.Button, onClickLabel = "Edit ${profile.name}") { nav.navigate(Routes.model(profile.id)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = PaperType.entryTitle.copy(fontSize = 18.sp), color = colors.sumi, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                stamp?.let { (label, ink) -> Stamp(label, ink) }
            }
            Text("${profile.provider.displayName} · ${profile.modelId.ifBlank { "no model chosen" }}".uppercase(), style = PaperType.meta.copy(letterSpacing = 0.04.sp), color = colors.kasure2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (users.isNotEmpty()) Text("Used by ${users.joinToString()}", style = PaperType.noteBody.copy(fontSize = 12.sp), color = colors.kasure2)
        }
    }
    DashedButton("+ New model profile") { nav.navigate(Routes.model(Routes.NEW)) }
    PaperSelect("Default model", profiles, profiles.firstOrNull { it.id == s.defaultModelProfileId }, { it.name }, { p -> update { it.copy(defaultModelProfileId = p?.id) } }, noneLabel = "First available")
    PaperSelect("Utility model (summaries, memory upkeep)", profiles, profiles.firstOrNull { it.id == s.utilityModelProfileId }, { it.name }, { p -> update { it.copy(utilityModelProfileId = p?.id) } }, noneLabel = "Same as the letter's")
}

@Composable
private fun Keys(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val credentials by remember { harness.credentials.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val colors = paper
    CardTitle("Keys", trailing = { InkLink("Manage keys") { nav.navigate(Routes.KEYS) } })
    if (credentials.isEmpty()) Hint("No keys yet. Add one to start writing.")
    credentials.forEach { key -> KeyTag(key) { nav.navigate(Routes.KEYS) } }
    Hint("Keys are encrypted on this phone and only sent to the service they belong to.")
}

@Composable
fun Stamp(label: String, ink: androidx.compose.ui.graphics.Color) {
    Text(
        label.uppercase(),
        style = PaperType.meta.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp),
        color = ink,
        modifier = Modifier
            .rotate(-4f)
            .border(1.5.dp, ink, PaperCorner)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun DashedButton(label: String, onClick: () -> Unit) {
    val dash = paper.nijimi2
    Box(
        Modifier
            .fillMaxWidth()
            .clip(PaperCorner)
            .drawBehind {
                drawRect(dash, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.kasure2) }
}

// --- 七 Prompting -------------------------------------------------------------------------------

internal fun SettingsCards.promptingPage(s: AppSettings, update: Update) {
    card {
        CardTitle("Guidelines", trailing = {
            if (s.baseInstructions != AppSettings.DEFAULT_BASE_INSTRUCTIONS) InkLink("Reset") { update { it.copy(baseInstructions = AppSettings.DEFAULT_BASE_INSTRUCTIONS) } }
        })
        IndexCardField("Added to every persona", s.baseInstructions, { v -> update { it.copy(baseInstructions = v) } }, style = PaperType.prose, minLines = 4)
        Hint("{{char}} and {{user}} are filled in with the persona's and your names.")
    }
    card {
        CardTitle("Letters")
        SwitchRow("Mood words", s.moodTags, { v -> update { it.copy(moodTags = v) } }, subtitle = "Personas choose a mood for each reply, shown beside their name.")
        StepperRow("Tool rounds per reply", s.maxToolRounds, { n -> update { it.copy(maxToolRounds = n) } }, 1..12)
    }
}

// --- 八 About -----------------------------------------------------------------------------------

/** The last crash, if there was one: copy it to send to whoever gave you the app. */
@Composable
private fun LastCrash() {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    var report by remember { mutableStateOf(dev.aiharness.app.CrashReport.file(app).takeIf { it.exists() }?.readText()) }
    var copied by remember { mutableStateOf(false) }
    CardTitle("If something goes wrong")
    val text = report
    if (text == null) {
        Hint("If the app ever closes unexpectedly, a report appears here for you to copy and send. Nothing is sent on its own.")
        return
    }
    Hint("The app closed unexpectedly: ${text.lineSequence().drop(1).firstOrNull()?.substringBefore(", thread").orEmpty()}. Copy the report and send it along with what you were doing.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PaperButton(if (copied) "Copied" else "Copy the report", ink = stationery.you) {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Fumibako crash report", text))
            copied = true
        }
        InkLink("Clear it") {
            dev.aiharness.app.CrashReport.file(app).delete()
            report = null
        }
    }
}

internal fun SettingsCards.aboutPage() {
    card {
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // The icon's seal, as on the launcher.
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(dev.aiharness.app.R.drawable.ic_launcher_foreground),
                contentDescription = null,
                // The drawable is the whole 108-unit icon layer; show the seal at a readable size.
                modifier = Modifier.size(64.dp).wrapContentSize(unbounded = true).requiredSize(132.dp),
            )
            Column(Modifier.weight(1f)) {
                CardTitle("Fumibako", note = "version $version")
                Text("文箱 · a box for keeping letters", style = PaperType.noteBody.copy(fontSize = 13.sp), color = paper.kasure2)
            }
        }
        Text("Letters with personas who remember, feel, and write to you on their own.", style = PaperType.prose, color = paper.usuzumi1)
    }
    card { LastCrash() }
    card {
        CardTitle("Thanks and licenses")
        Text(
            "Colors: madoromi by samhain (madoromi.moonwit.ch), MPL-2.0.\nFonts: Cormorant Garamond, Literata, LINE Seed JP, Yuji Syuku, Klee One and Old Standard TT, SIL Open Font License 1.1.",
            style = PaperType.prose,
            color = paper.usuzumi1,
        )
        FontLicence()
    }
}

@Composable
private fun FontLicence() {
    val context = LocalContext.current
    val text by produceState("") { value = runCatching { context.assets.open("licenses/fonts.txt").bufferedReader().readText() }.getOrDefault("") }
    var open by remember { mutableStateOf(false) }
    InkLink(if (open) "Hide the font license" else "Read the font license") { open = !open }
    if (open) {
        Text(
            text,
            style = PaperType.noteBody.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = paper.usuzumi0,
            modifier = Modifier.background(lerp(paper.kami, paper.hyoshi, 0.5f)).padding(8.dp),
        )
    }
}
