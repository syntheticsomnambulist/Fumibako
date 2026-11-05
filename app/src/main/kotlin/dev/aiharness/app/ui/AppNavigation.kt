package dev.aiharness.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.aiharness.app.ui.chat.ChatScreen
import dev.aiharness.app.ui.chats.ChatsScreen
import dev.aiharness.app.ui.personas.MemoryScreen
import dev.aiharness.app.ui.personas.PersonaEditScreen
import dev.aiharness.app.ui.personas.PersonasScreen
import dev.aiharness.app.ui.settings.KeysScreen
import dev.aiharness.app.ui.settings.ModelEditScreen
import dev.aiharness.app.ui.settings.ModelsScreen
import dev.aiharness.app.ui.settings.SettingsScreen
import dev.aiharness.app.ui.settings.UserPersonasScreen
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val CHATS = "chats"
    const val PERSONAS = "personas"
    const val SETTINGS = "settings"
    const val MODELS = "models"
    const val KEYS = "keys"
    const val USER_PERSONAS = "user_personas"
    fun chat(id: String) = "chat/$id"
    fun persona(id: String) = "persona/$id"
    fun memory(personaId: String) = "memory/$personaId"
    fun model(id: String) = "model/$id"
    const val NEW = "new"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.CHATS, "Chats", Icons.AutoMirrored.Filled.Chat),
    Tab(Routes.PERSONAS, "Personas", Icons.Filled.Face),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
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
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CHATS, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.CHATS) { ChatsScreen(nav) }
            composable("chat/{id}") { entry -> ChatScreen(nav, entry.arguments?.getString("id").orEmpty()) }
            composable(Routes.PERSONAS) { PersonasScreen(nav) }
            composable("persona/{id}") { entry -> PersonaEditScreen(nav, entry.arguments?.getString("id").orEmpty()) }
            composable("memory/{id}") { entry -> MemoryScreen(nav, entry.arguments?.getString("id").orEmpty()) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.MODELS) { ModelsScreen(nav) }
            composable("model/{id}") { entry -> ModelEditScreen(nav, entry.arguments?.getString("id").orEmpty()) }
            composable(Routes.KEYS) { KeysScreen(nav) }
            composable(Routes.USER_PERSONAS) { UserPersonasScreen(nav) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
