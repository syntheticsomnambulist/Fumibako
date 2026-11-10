package dev.aiharness.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.aiharness.app.ui.Routes
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.paper.PaperButton
import dev.aiharness.app.ui.theme.stationery
import dev.aiharness.app.ui.transfer.rememberOpenFile

// --- 十一 Import and export -------------------------------------------------------------------

/** Opening cards and backups (told apart by what's inside), and exporting. */
internal fun SettingsCards.transferPage(nav: NavController) {
    card {
        CardTitle("Open a file")
        Hint("A character card (PNG, JSON or .charx) comes in as a new persona; a Fumibako backup shows what's in it before anything changes.")
        val open = rememberOpenFile(nav)
        PaperButton("Open a file…", Modifier.fillMaxWidth(), ink = stationery.you) { open() }
    }
    card {
        CardTitle("Export")
        Hint("One persona or everything, as a backup or a character card. Your API keys never go.")
        PaperButton("Export…", Modifier.fillMaxWidth()) { nav.navigate(Routes.EXPORT) }
    }
}
