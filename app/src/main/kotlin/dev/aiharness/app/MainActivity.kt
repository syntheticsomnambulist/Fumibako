package dev.aiharness.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.ui.AppNavigation
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.theme.HarnessTheme
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.core.model.StickerSettings
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Conversation to open, e.g. from a heartbeat notification. */
    private val openConversation = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openConversation.value = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        val container = (application as HarnessApp).container
        // A card or backup opened or shared into the app (not again when the activity is recreated).
        if (savedInstanceState == null) intent?.let(::fileFrom)?.let { container.transfers.incoming.value = it }
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppTheme { AppNavigation(openConversation) }
            }
        }
    }

    @Composable
    private fun AppTheme(content: @Composable () -> Unit) {
        val container = (application as HarnessApp).container
        val settings by container.harness.settings.settings.collectAsStateWithLifecycle(initialValue = null)
        val usersFlow = remember { container.harness.userPersonas.observeAll() }
        val users by usersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val you = users.firstOrNull { it.id == settings?.activeUserPersonaId } ?: users.firstOrNull()
        HarnessTheme(settings?.theme ?: ThemeSettings(), you?.ink, you?.tapePattern ?: TapePattern.STRIPES) {
            val night = stationery.isNight
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                }
            }
            CompositionLocalProvider(
                LocalStickerSettings provides (settings?.stickers ?: StickerSettings()),
                dev.aiharness.app.ui.media.LocalMediaSettings provides (settings?.media ?: dev.aiharness.core.model.MediaSettings()),
            ) { content() }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as HarnessApp).container.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as HarnessApp).container.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { openConversation.value = it }
        fileFrom(intent)?.let { (application as HarnessApp).container.transfers.incoming.value = it }
    }

    /** The file an intent brings: one opened with the app (VIEW) or shared to it (SEND). */
    private fun fileFrom(intent: Intent): dev.aiharness.app.platform.Picked? {
        val uri: android.net.Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        return uri?.let { dev.aiharness.app.platform.Picked(it.toString()) }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
