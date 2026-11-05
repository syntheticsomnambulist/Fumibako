package dev.aiharness.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.aiharness.app.ui.AppNavigation
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.theme.HarnessTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Conversation to open, e.g. from a heartbeat notification. */
    private val openConversation = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openConversation.value = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        val container = (application as HarnessApp).container
        setContent {
            HarnessTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AppNavigation(openConversation)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_CONVERSATION_ID)?.let { openConversation.value = it }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
