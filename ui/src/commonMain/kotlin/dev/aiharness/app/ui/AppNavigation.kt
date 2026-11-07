package dev.aiharness.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.personas.PersonaPageScreen
import dev.aiharness.app.ui.paper.TapeLabel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.aiharness.app.ui.chat.ChatScreen
import dev.aiharness.app.ui.chats.ChatsScreen
import dev.aiharness.app.ui.memory.MemoryBookScreen
import dev.aiharness.app.ui.memory.MemoryCardScreen
import dev.aiharness.app.ui.personas.PersonasScreen
import dev.aiharness.app.ui.settings.KeysScreen
import dev.aiharness.app.ui.personas.PersonaHeartbeatScreen
import dev.aiharness.app.ui.personas.YouGalleryScreen
import dev.aiharness.app.ui.personas.YouPageScreen
import dev.aiharness.app.ui.settings.SettingsSectionScreen
import dev.aiharness.app.ui.settings.ModelEditScreen
import dev.aiharness.app.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import dev.aiharness.app.platform.RequestNotificationPermission
import androidx.navigation.NavBackStackEntry
import androidx.savedstate.read

object Routes {
    const val CHATS = "chats"
    const val PERSONAS = "personas"
    const val SETTINGS = "settings"
    /** Model profiles are listed in Settings, Models and keys. */
    val MODELS = settings("models")
    const val KEYS = "keys"
    const val SHARED_STICKERS = "stickers/shared"
    const val USER_PERSONAS = "user_personas"
    const val PLANNER = "planner"
    const val TYPICAL_WEEK = "planner/week"
    fun plannerDay(date: String) = "planner/day/$date"
    fun chat(id: String) = "chat/$id"
    fun personaPage(id: String) = "page/$id"
    fun personaHeartbeat(id: String) = "page/$id/heartbeat"
    fun personaStickers(id: String) = "page/$id/stickers"
    fun youPage(id: String) = "you/$id"
    fun settings(section: String) = "settings/$section"
    fun memory(personaId: String) = "memory/$personaId"
    fun memoryCard(personaId: String, memoryId: String) = "memory/$personaId/$memoryId"
    fun model(id: String) = "model/$id"
    fun imageProfile(id: String) = "image/$id"
    const val NEW = "new"
}

private data class Tab(val route: String, val label: String, val kanji: String)

private val tabs = listOf(
    Tab(Routes.CHATS, "Letters", "文"),
    Tab(Routes.PERSONAS, "Personas", "人"),
    Tab(Routes.PLANNER, "Planner", "帳"),
    Tab(Routes.SETTINGS, "Settings", "設"),
)

@Composable
fun AppNavigation(openConversation: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val pending by openConversation.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            nav.navigate(Routes.chat(it)) { launchSingleTop = true }
            openConversation.value = null
        }
    }
    RequestNotificationPermission()

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) TapeNavBar(currentRoute) { nav.navigateTab(it) }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CHATS, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.CHATS) { ChatsScreen(nav) }
            composable("chat/{id}") { entry -> ChatScreen(nav, entry.arg("id")) }
            composable(Routes.PERSONAS) { PersonasScreen(nav) }
            composable("page/{id}/heartbeat") { entry -> PersonaHeartbeatScreen(nav, entry.arg("id")) }
            composable("page/{id}/stickers") { entry -> dev.aiharness.app.ui.personas.PersonaStickersScreen(nav, entry.arg("id")) }
            composable("you/{id}") { entry -> YouPageScreen(nav, entry.arg("id")) }
            composable("page/{id}") { entry -> PersonaPageScreen(nav, entry.arg("id")) }
            composable("memory/{id}") { entry -> MemoryBookScreen(nav, entry.arg("id")) }
            composable("memory/{id}/{memory}") { entry -> MemoryCardScreen(nav, entry.arg("id"), entry.arg("memory")) }
            composable(Routes.PLANNER) { dev.aiharness.app.ui.planner.PlannerScreen(nav) }
            composable("planner/day/{date}") { entry -> dev.aiharness.app.ui.planner.PlannerDayScreen(nav, entry.arg("date")) }
            composable(Routes.TYPICAL_WEEK) { dev.aiharness.app.ui.planner.TypicalWeekScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable("settings/{section}") { entry -> SettingsSectionScreen(nav, entry.arg("section")) }
            composable("model/{id}") { entry -> ModelEditScreen(nav, entry.arg("id")) }
            composable(Routes.KEYS) { KeysScreen(nav) }
            composable(Routes.SHARED_STICKERS) { dev.aiharness.app.ui.settings.SharedStickersScreen(nav) }
            composable("image/{id}") { entry -> dev.aiharness.app.ui.settings.ImageProfileScreen(nav, entry.arg("id")) }
            composable(Routes.USER_PERSONAS) { YouGalleryScreen(nav) }
        }
    }
}

/** Kanji tabs; the active tab's label is written on a strip of plain tape in the user's ink. */
@Composable
private fun TapeNavBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = paper
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi0, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEach { tab ->
            val on = currentRoute == tab.route
            // Four tabs share the bar equally, so a longer label ("Settings") doesn't wrap in large text.
            Column(
                Modifier
                    .weight(1f)
                    .clip(dev.aiharness.app.ui.paper.PaperCorner)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab.route) })
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(tab.kanji, style = PaperType.navKanji, color = if (on) colors.sumi else colors.usuzumi0)
                TapeLabel(tab.label, stationery.you, on = on)
            }
        }
    }
}

/** A route's argument ("chat/{id}"), or "" without it. */
private fun NavBackStackEntry.arg(name: String): String = arguments?.read { getStringOrNull(name) }.orEmpty()

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

