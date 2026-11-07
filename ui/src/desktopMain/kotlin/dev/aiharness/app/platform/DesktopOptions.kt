package dev.aiharness.app.platform

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.SwitchRow
import dev.aiharness.app.ui.paper.CardTitle
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import kotlinx.coroutines.flow.StateFlow

/** What only a computer has: the desktop's platform offers these (see DesktopPlatform). */
interface DesktopOptions {
    /** Closing the window keeps Fumibako in the tray, so heartbeats go on (on by default). */
    val keepInTray: StateFlow<Boolean>

    /** Fumibako starts, in the tray, when you log in (off until you turn it on). */
    val startAtLogin: StateFlow<Boolean>

    /** Whether there's a tray to keep it in (some Linux desktops have none). */
    val trayWorks: Boolean

    /** Whether starting at login can be set: only for Fumibako installed from its installer. */
    val canStartAtLogin: Boolean

    fun setKeepInTray(on: Boolean)

    /** Turns starting at login on or off; false if the system wouldn't. */
    fun setStartAtLogin(on: Boolean): Boolean
}

/** Settings, Heartbeat: "On this computer". */
@Composable
internal fun DesktopOptionsCard() {
    val options = LocalAppContainer.current.platform as? DesktopOptions ?: return
    val keep by options.keepInTray.collectAsState()
    val atLogin by options.startAtLogin.collectAsState()
    var problem by remember { mutableStateOf<String?>(null) }
    CardTitle("On this computer")
    SwitchRow(
        "Keep Fumibako in the tray when its window closes",
        keep && options.trayWorks,
        { v -> if (options.trayWorks) options.setKeepInTray(v) },
        subtitle = if (options.trayWorks) "So personas can go on writing to you. Quit from the tray's menu."
        else "This computer's desktop has no tray, so closing the window quits Fumibako.",
    )
    SwitchRow(
        "Start Fumibako when I log in",
        atLogin,
        { v ->
            if (options.canStartAtLogin) {
                problem = if (options.setStartAtLogin(v)) null else "Couldn't change that. Try again, or change it in your computer's settings."
            }
        },
        subtitle = if (options.canStartAtLogin) "In the tray, without opening its window."
        else "Only for Fumibako installed from its installer.",
    )
    problem?.let { Text(it, style = PaperType.postscript, color = paper.usuzumi1) }
}
