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
import androidx.navigation.NavGraphBuilder
import androidx.savedstate.read
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first

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

/**
 * The app's screens and their tabs. Narrower than [WideLayout.MIN_WIDTH], the phone's: one screen at
 * a time, with the tabs along the bottom. Wider, the tabs run down the left, Letters keeps its list
 * beside the open letter and Personas its gallery beside the open page (the open side shows whatever
 * is on top of the back stack), and other screens keep the phone's layout, centered. Both use the
 * same back stack, so a window crossing that width only changes how it's drawn.
 */
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayout.MIN_WIDTH
        val tab = tabOf(currentRoute)
        LastOpen(nav, wide, currentRoute, backStack)
        CompositionLocalProvider(LocalWide provides wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(if (wide) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier),
            ) {
                if (wide) {
                    TapeNavRail(tab) { nav.navigateTab(it) }
                    when (tab) {
                        Routes.CHATS -> ListSide(WideLayout.LIST_WIDTH) {
                            val open = backStack?.takeIf { currentRoute == "chat/{id}" }?.arg("id")
                            CompositionLocalProvider(LocalOpenLetter provides open) { ChatsScreen(nav) }
                        }
                        Routes.PERSONAS -> ListSide(WideLayout.GALLERY_WIDTH) { PersonasScreen(nav) }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    bottomBar = {
                        if (!wide && currentRoute in tabs.map { it.route }) TapeNavBar(currentRoute) { nav.navigateTab(it) }
                    },
                ) { padding ->
                    NavHost(nav, startDestination = Routes.CHATS, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        screen(Routes.CHATS) { ChatsScreen(nav) }
                        screen("chat/{id}") { entry -> ChatScreen(nav, entry.arg("id")) }
                        screen(Routes.PERSONAS) { PersonasScreen(nav) }
                        screen("page/{id}/heartbeat") { entry -> PersonaHeartbeatScreen(nav, entry.arg("id")) }
                        screen("page/{id}/stickers") { entry -> dev.aiharness.app.ui.personas.PersonaStickersScreen(nav, entry.arg("id")) }
                        screen("you/{id}") { entry -> YouPageScreen(nav, entry.arg("id")) }
                        screen("page/{id}") { entry -> PersonaPageScreen(nav, entry.arg("id")) }
                        screen("memory/{id}") { entry -> MemoryBookScreen(nav, entry.arg("id")) }
                        screen("memory/{id}/{memory}") { entry -> MemoryCardScreen(nav, entry.arg("id"), entry.arg("memory")) }
                        screen(Routes.PLANNER) { dev.aiharness.app.ui.planner.PlannerScreen(nav) }
                        screen("planner/day/{date}") { entry -> dev.aiharness.app.ui.planner.PlannerDayScreen(nav, entry.arg("date")) }
                        screen(Routes.TYPICAL_WEEK) { dev.aiharness.app.ui.planner.TypicalWeekScreen(nav) }
                        screen(Routes.SETTINGS) { SettingsScreen(nav) }
                        screen("settings/{section}") { entry -> SettingsSectionScreen(nav, entry.arg("section")) }
                        screen("model/{id}") { entry -> ModelEditScreen(nav, entry.arg("id")) }
                        screen(Routes.KEYS) { KeysScreen(nav) }
                        screen(Routes.SHARED_STICKERS) { dev.aiharness.app.ui.settings.SharedStickersScreen(nav) }
                        screen("image/{id}") { entry -> dev.aiharness.app.ui.settings.ImageProfileScreen(nav, entry.arg("id")) }
                        screen(Routes.USER_PERSONAS) { YouGalleryScreen(nav) }
                    }
                }
            }
        }
    }
}

/** A destination, placed for the layout: as it is on the phone, or in the open side of a wide one. */
private fun NavGraphBuilder.screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route) { entry -> Placed(route) { content(entry) } }

/** The tabs with a list beside the open side when wide. */
private val listedTabs = setOf(Routes.CHATS, Routes.PERSONAS)

/** Which tab a screen belongs to, for the index tabs and the list beside it. */
private fun tabOf(route: String?): String? = when {
    route == null -> null
    route == Routes.CHATS || route.startsWith("chat/") -> Routes.CHATS
    route == Routes.PERSONAS || route == Routes.USER_PERSONAS || listOf("page/", "memory/", "you/").any { route.startsWith(it) } -> Routes.PERSONAS
    route.startsWith(Routes.PLANNER) -> Routes.PLANNER
    else -> Routes.SETTINGS
}

/**
 * Where a screen goes when the layout is wide: Letters' and Personas' own pages (the list is beside
 * them) leave a quiet desk; a letter keeps its reading width with room on each side; the persona
 * pages beside the gallery keep that width too; other screens keep the phone's width, centered.
 */
@Composable
private fun Placed(route: String, content: @Composable () -> Unit) {
    if (!LocalWide.current) {
        content()
        return
    }
    when (route) {
        Routes.CHATS -> EmptyDesk("文", "Choose a letter, or start a new one.")
        Routes.PERSONAS -> EmptyDesk("人", "Choose a persona to see their page.")
        "chat/{id}" -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val sides = ((maxWidth - WideLayout.READING_WIDTH) / 2).coerceAtLeast(WideLayout.PAGE_PADDING)
            CompositionLocalProvider(LocalBesideList provides true, LocalReadingSides provides sides) { content() }
        }
        else -> Box(Modifier.fillMaxSize()) {
            val width = if (tabOf(route) in listedTabs) WideLayout.READING_WIDTH + WideLayout.PAGE_PADDING * 2 else WideLayout.COLUMN_WIDTH
            Box(Modifier.widthIn(max = width).fillMaxHeight().align(Alignment.TopCenter)) {
                CompositionLocalProvider(LocalBesideList provides (route == "page/{id}")) { content() }
            }
        }
    }
}

/** The open side with nothing open: the tab's kanji, faint, and a line. */
@Composable
private fun EmptyDesk(kanji: String, line: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(kanji, style = PaperType.navKanji.copy(fontSize = 112.sp, lineHeight = 124.sp), color = paper.kasure0.copy(alpha = 0.5f))
        Text(line, style = PaperType.preview.copy(fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 24.sp), color = paper.usuzumi0)
    }
}

/** The letters list or the gallery, kept beside the open side when wide, with a line between them. */
@Composable
private fun ListSide(width: Dp, content: @Composable () -> Unit) {
    val line = paper.nijimi0
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .drawWithContent {
                drawContent()
                drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) { content() }
}

/**
 * Letters opens on the letter you last had open, and Personas on the page you last opened (chosen
 * 2026-11-08): the first time this session that a wide window shows the tab's own page, it opens
 * the one kept here. After that, what's open is up to you (Escape leaves the desk). A letter deleted
 * while it's open beside the list closes.
 */
@Composable
private fun LastOpen(nav: NavHostController, wide: Boolean, route: String?, entry: NavBackStackEntry?) {
    val container = LocalAppContainer.current
    val state = container.platform.appState
    val seen = remember { mutableSetOf<String>() }
    val id = entry?.arg("id")
    LaunchedEffect(wide, route, id) {
        when (route) {
            "chat/{id}" -> id?.let { state.putString(KEY_LAST_LETTER, it) }
            "page/{id}" -> id?.takeIf { it != Routes.NEW }?.let { state.putString(KEY_LAST_PAGE, it) }
        }
        if (!wide) return@LaunchedEffect
        val tab = tabOf(route) ?: return@LaunchedEffect
        if (!seen.add(tab)) return@LaunchedEffect
        when (route) {
            Routes.CHATS -> state.getString(KEY_LAST_LETTER)?.let { last ->
                if (container.harness.conversations.get(last)?.archived == false) nav.navigate(Routes.chat(last))
            }
            Routes.PERSONAS -> state.getString(KEY_LAST_PAGE)?.let { last ->
                if (container.harness.personas.get(last) != null) nav.navigate(Routes.personaPage(last))
            }
        }
    }
    if (wide && route == "chat/{id}" && id != null) {
        LaunchedEffect(id) {
            // Waits for the letter to be there, then closes it if it goes.
            container.harness.conversations.observe(id).first { it != null }
            container.harness.conversations.observe(id).first { it == null }
            if (nav.currentBackStackEntry?.arg("id") == id) nav.popBackStack()
        }
    }
}

private const val KEY_LAST_LETTER = "wide_last_letter"
private const val KEY_LAST_PAGE = "wide_last_page"

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

/** The tabs down the left of a wide layout, as index tabs: the kanji, and the label on tape when it's open. */
@Composable
private fun TapeNavRail(current: String?, onSelect: (String) -> Unit) {
    val colors = paper
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 84.dp)
            .fillMaxHeight()
            .background(colors.kami)
            .drawBehind { drawLine(colors.nijimi0, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .statusBarsPadding()
            .padding(start = 6.dp, end = 6.dp, top = 22.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        tabs.forEach { tab ->
            val on = current == tab.route
            Column(
                Modifier
                    .clip(dev.aiharness.app.ui.paper.PaperCorner)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab.route) })
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(tab.kanji, style = PaperType.navKanji.copy(fontSize = 23.sp, lineHeight = 28.sp), color = if (on) colors.sumi else colors.usuzumi0)
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

