package dev.aiharness.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.chats.ScreenTitle
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.paper.Tape
import dev.aiharness.app.ui.paper.TapedCard
import dev.aiharness.app.ui.paper.cardTilt
import dev.aiharness.app.ui.paper.tapeLayouts
import dev.aiharness.app.ui.theme.HarnessFonts
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.model.DayNightMode
import dev.aiharness.core.model.HeartbeatMode
import dev.aiharness.core.model.TapePattern
import kotlinx.coroutines.launch

/** The sections of Settings, numbered like a notebook's contents. */
enum class SettingsSection(val key: String, val kanji: String, val title: String) {
    LOOK("look", "一", "Look"),
    HEARTBEAT("heartbeat", "二", "Heartbeat"),
    MEMORY("memory", "三", "Memory"),
    PICTURES("pictures", "四", "Pictures"),
    MODELS("models", "五", "Models and keys"),
    YOU("you", "六", "You"),
    PROMPTING("prompting", "七", "Prompting"),
    ABOUT("about", "八", "About"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** Settings: a contents page, each line showing where things stand, opening a page per section. */
@Composable
fun SettingsScreen(nav: NavController) {
    val harness = LocalAppContainer.current.harness
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val profiles by remember { harness.profiles.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val users by remember { harness.userPersonas.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    val s = settings ?: return

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenTitle("Settings", Modifier.statusBarsPadding()) }
        item {
            TapedCard(dev.aiharness.app.ui.paper.TapeLayout.LEFT, Tape(stationery.you, stationery.youPattern), spacing = 0.dp) {
                Text("CONTENTS", style = PaperType.label, color = paper.kasure2, modifier = Modifier.padding(bottom = 6.dp))
                SettingsSection.entries.forEach { section ->
                    ContentsLine(section, summary(section, s, profiles.size, users.firstOrNull { it.id == s.activeUserPersonaId }?.name)) {
                        if (section == SettingsSection.YOU) nav.navigate(Routes.USER_PERSONAS) else nav.navigate(Routes.settings(section.key))
                    }
                }
            }
        }
        if (s.heartbeat.mode == HeartbeatMode.PROACTIVE) {
            item {
                PaperButton("Run a heartbeat now", Modifier.fillMaxWidth()) { HeartbeatScheduler.runNow(context, s.heartbeat) }
            }
        }
    }
}

@Composable
private fun ContentsLine(section: SettingsSection, summary: String, onClick: () -> Unit) {
    val colors = paper
    val leader = colors.nijimi2
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open ${section.title}", onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(section.kanji, style = PaperType.seasonKanji.copy(fontFamily = HarnessFonts.Brush, fontSize = 15.sp), color = colors.kasure2, modifier = Modifier.width(18.dp))
        Text(section.title, style = PaperType.entryTitle, color = colors.sumi)
        Box(
            Modifier
                .weight(1f)
                .padding(bottom = 6.dp)
                .drawBehind {
                    drawLine(leader, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 4.dp.toPx())))
                },
        )
        Text(summary.uppercase(), style = PaperType.meta.copy(letterSpacing = 0.04.sp), color = colors.kasure2, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
    }
}

private fun summary(section: SettingsSection, s: AppSettings, profileCount: Int, writingAs: String?): String = when (section) {
    SettingsSection.LOOK -> when (s.theme.dayNight) {
        DayNightMode.FIXED_HOURS -> "Set hours"
        DayNightMode.SUN -> "Sunrise and sunset"
        DayNightMode.STATIC -> "Fixed theme"
    }
    SettingsSection.HEARTBEAT -> when (s.heartbeat.mode) {
        HeartbeatMode.PROACTIVE -> "Proactive · every ${interval(s.heartbeat.rhythm.intervalMinutes)}"
        HeartbeatMode.TIME_AWARENESS -> "Time only"
        HeartbeatMode.OFF -> "Off"
    }
    SettingsSection.MEMORY -> if (!s.memory.enabled) "Off" else "On · editing ${if (s.memory.userCanEdit) "on" else "off"}"
    SettingsSection.PICTURES -> if (s.imageGen.enabled && s.imageGen.credentialId != null) "NovelAI" else "Not set up"
    SettingsSection.MODELS -> if (profileCount == 1) "1 model" else "$profileCount models"
    SettingsSection.YOU -> writingAs?.let { "Writing as $it" } ?: "Your personas"
    SettingsSection.PROMPTING -> if (s.baseInstructions == AppSettings.DEFAULT_BASE_INSTRUCTIONS) "Default" else "Your own"
    SettingsSection.ABOUT -> "v0.2 · licences"
}

/** "15 minutes", "1 hour", "3 hours". */
fun interval(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes minutes"
}

/** Opens one section's page. */
@Composable
fun SettingsSectionScreen(nav: NavController, key: String) {
    val section = SettingsSection.of(key) ?: return
    val harness = LocalAppContainer.current.harness
    val scope = rememberCoroutineScope()
    val settings by remember { harness.settings.settings }.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val update: ((AppSettings) -> AppSettings) -> Unit = { transform -> scope.launch { harness.settings.update(transform) } }
    SettingsPage(nav, section) {
        when (section) {
            SettingsSection.LOOK -> lookPage(s, update)
            SettingsSection.HEARTBEAT -> heartbeatPage(nav, s, update)
            SettingsSection.MEMORY -> memoryPage(s, update)
            SettingsSection.PICTURES -> picturesPage(s, update)
            SettingsSection.MODELS -> modelsPage(nav, s, update)
            SettingsSection.PROMPTING -> promptingPage(s, update)
            SettingsSection.ABOUT -> aboutPage()
            SettingsSection.YOU -> Unit
        }
    }
}

/** A settings section's page: back, its title and number, then taped cards. */
@Composable
private fun SettingsPage(nav: NavController, section: SettingsSection, content: SettingsCards.() -> Unit) {
    val colors = paper
    val cards = remember(section) { SettingsCards(section.key) }
    Column(Modifier.fillMaxSize().background(colors.hyoshi)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .drawBehind { drawLine(colors.nijimi0, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to settings", tint = colors.usuzumi0) }
            Column {
                Text(section.title, style = PaperType.letterName.copy(fontSize = 22.sp, lineHeight = 24.sp), color = colors.sumi)
                Text("${section.kanji} · SETTINGS", style = PaperType.meta, color = colors.kasure2)
            }
        }
        cards.items.clear()
        cards.content()
        val layouts = tapeLayouts("settings-${section.key}", cards.items.size)
        val tape = Tape(stationery.you, stationery.youPattern.takeIf { it != TapePattern.PLAIN } ?: TapePattern.STRIPES)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            cards.items.forEachIndexed { i, card ->
                item(key = "card-$i") {
                    TapedCard(layouts[i], tape, tilt = cardTilt("${section.key}-$i")) { card() }
                }
            }
        }
    }
}

/** Collects a page's cards so each gets its own tape placement. */
class SettingsCards internal constructor(val key: String) {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun card(content: @Composable () -> Unit) {
        items += content
    }
}
