package dev.aiharness.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import dev.aiharness.app.ui.WideLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.AppContainer
import dev.aiharness.app.heartbeat.BusyRules
import dev.aiharness.app.platform.AppState
import dev.aiharness.app.platform.BackHandlers
import dev.aiharness.app.platform.LocalBackHandlers
import dev.aiharness.app.platform.Toasts
import dev.aiharness.app.ui.AppNavigation
import dev.aiharness.app.ui.LocalAppContainer
import dev.aiharness.app.ui.media.LocalMediaSettings
import dev.aiharness.app.ui.stickers.LocalStickerSettings
import dev.aiharness.app.ui.theme.HarnessTheme
import dev.aiharness.app.ui.theme.PaperType
import dev.aiharness.app.ui.theme.paper
import dev.aiharness.core.model.MediaSettings
import dev.aiharness.core.model.StickerSettings
import dev.aiharness.core.model.TapePattern
import dev.aiharness.core.model.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.desktop.AppReopenedListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

/**
 * Fumibako on a computer: the shared screens in a window, and a tray icon that keeps it running
 * while the window is closed (docs/plan-desktop.md). `--background` starts it in the tray (as at
 * login). A second start brings this one's window forward instead.
 */
fun main(args: Array<String>) {
    val dataDir = DesktopPlatform.dataDir()
    val showRequests = MutableStateFlow(0)
    val instance = SingleInstance(dataDir)
    if (!instance.claim { showRequests.update { it + 1 } }) return
    CrashReport.install(dataDir)

    val platform = DesktopPlatform(dataDir)
    val container = AppContainer(platform)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val trayState = TrayState()
    val openConversation = MutableStateFlow<String?>(null)
    val lastNotice = AtomicReference<String?>(null)
    val notices = DesktopNotices(container.harness, platform.appState) { notice ->
        lastNotice.set(notice.conversationId)
        if (platform.trayWorks) trayState.sendNotification(Notification(notice.title, notice.message))
    }
    platform.heartbeats.run = { force, persona -> container.harness.heartbeat.run(force = force, onlyPersonaId = persona) }
    platform.heartbeats.onOutcomes = { outcomes -> notices.deliver(outcomes) }
    platform.heartbeats.onPosted = { conversationId, at -> notices.expect(conversationId, at) }
    notices.start()
    container.start()
    whenDockIconClicked { showRequests.update { it + 1 } }

    val inTray = { platform.trayWorks && platform.keepInTray.value }
    val startHidden = LoginItem.BACKGROUND in args && inTray()
    val backHandlers = BackHandlers()
    val bounds = WindowBounds(platform.appState)

    application {
        var visible by remember { mutableStateOf(!startHidden) }
        val quit = {
            instance.release()
            exitApplication()
        }
        val show = {
            lastNotice.getAndSet(null)?.let { openConversation.value = it }
            visible = true
            showRequests.update { it + 1 }
        }
        // The classpath loader reads the app's vector icons (tools/make_icon.py draws them).
        @Suppress("DEPRECATION")
        val icon = painterResource("icon.xml")
        @Suppress("DEPRECATION")
        val trayIcon = painterResource("tray.xml")
        if (platform.trayWorks) {
            Tray(icon = trayIcon, state = trayState, tooltip = "Fumibako", onAction = show) {
                Item("Open Fumibako", onClick = show)
                Separator()
                Menu("Busy for now") {
                    Item("For an hour") { busy(scope, container, trayState, BusyRules.HOUR, "Busy for an hour") }
                    Item("Till tonight") { busy(scope, container, trayState, BusyRules.TONIGHT, "Busy till tonight") }
                    Item("Till tomorrow") { busy(scope, container, trayState, BusyRules.TOMORROW, "Busy till tomorrow") }
                }
                Item("Run a heartbeat now") {
                    scope.launch { container.heartbeats.runNow(container.harness.settings.current().heartbeat) }
                }
                Separator()
                Item("Quit Fumibako", onClick = quit)
            }
        }
        val state = rememberWindowState(position = bounds.position, size = bounds.size)
        Window(
            onCloseRequest = {
                if (inTray()) {
                    visible = false
                    container.onBackground()
                    firstCloseNotice(platform, trayState)
                } else {
                    quit()
                }
            },
            visible = visible,
            title = "Fumibako",
            state = state,
            icon = icon,
            onPreviewKeyEvent = backHandlers::onKey,
        ) {
            // "While you were away" counts from when the window last lost focus.
            LaunchedEffect(window) {
                container.onForeground()
                window.addWindowFocusListener(object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) = container.onForeground()
                    override fun windowLostFocus(e: WindowEvent) = container.onBackground()
                })
            }
            // Shown again (a second start, the dock, the tray): to the front.
            LaunchedEffect(window) {
                showRequests.collect { asked ->
                    if (asked > 0) {
                        visible = true
                        window.isMinimized = false
                        window.toFront()
                        window.requestFocus()
                    }
                }
            }
            LaunchedEffect(state) { bounds.keep(state) }
            FumibakoWindow(container, backHandlers) { AppNavigation(openConversation) }
        }
    }
}

/** "Busy for now" from the tray: a busy plan in your planner, as from a notification on the phone. */
private fun busy(scope: CoroutineScope, container: AppContainer, tray: TrayState, length: String, words: String) {
    scope.launch {
        BusyRules.busyForNow(container.harness, length)
        tray.sendNotification(Notification(words, "In your planner."))
    }
}

/** The first time the window closes into the tray, it says where Fumibako went. */
private fun firstCloseNotice(platform: DesktopPlatform, tray: TrayState) {
    if (platform.appState.getBoolean(KEY_TRAY_NOTICE, false)) return
    platform.appState.putBoolean(KEY_TRAY_NOTICE, true)
    tray.sendNotification(
        Notification(
            "Fumibako is still here",
            "Personas can go on writing to you. It's in the tray: open it or quit from its menu. Settings, Heartbeat changes this.",
        ),
    )
}

private const val KEY_TRAY_NOTICE = "tray_notice_shown"

/** On a Mac, clicking the dock icon with the window closed shows it again. */
private fun whenDockIconClicked(show: () -> Unit) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) desktop.addAppEventListener(AppReopenedListener { show() })
    }
}

/** The window's size and place, kept for the next start (wide enough for the wide layouts the first time). */
private class WindowBounds(private val state: AppState) {
    val size = DpSize(state.getLong(W, FIRST.width.value.toLong()).toInt().dp, state.getLong(H, FIRST.height.value.toLong()).toInt().dp)
    val position: WindowPosition =
        if (state.getString(X) != null && state.getString(Y) != null) WindowPosition(state.getLong(X, 0).toInt().dp, state.getLong(Y, 0).toInt().dp)
        else WindowPosition.PlatformDefault

    suspend fun keep(window: WindowState) {
        snapshotFlow { window.size to window.position }.collectLatest { (size, position) ->
            delay(500)
            if (size.isSpecified) {
                state.putLong(W, size.width.value.toLong())
                state.putLong(H, size.height.value.toLong())
            }
            if (position.isSpecified) {
                state.putLong(X, position.x.value.toLong())
                state.putLong(Y, position.y.value.toLong())
            }
        }
    }

    private companion object {
        const val W = "window_width"
        const val H = "window_height"

        /** A new window: 1180 by 780 (chosen 2026-11-08), or smaller to fit the screen's free space. */
        val FIRST: DpSize = runCatching {
            val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            DpSize(minOf(1180, screen.width - 40).dp, minOf(780, screen.height - 40).dp)
        }.getOrDefault(DpSize(1180.dp, 780.dp))
        const val X = "window_x"
        const val Y = "window_y"
    }
}

/** The app's theme and settings around [content], as on the phone: the whole window once it's wide enough for the wide layouts, else a phone-wide column. */
@Composable
fun FumibakoWindow(container: AppContainer, backHandlers: BackHandlers = remember { BackHandlers() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppContainer provides container, LocalBackHandlers provides backHandlers) {
        val settings by container.harness.settings.settings.collectAsStateWithLifecycle(initialValue = null)
        val usersFlow = remember { container.harness.userPersonas.observeAll() }
        val users by usersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val you = users.firstOrNull { it.id == settings?.activeUserPersonaId } ?: users.firstOrNull()
        HarnessTheme(settings?.theme ?: ThemeSettings(), you?.ink, you?.tapePattern ?: TapePattern.STRIPES, you?.tapeShade ?: dev.aiharness.core.model.TapeShade.THEME) {
            CompositionLocalProvider(
                LocalStickerSettings provides (settings?.stickers ?: StickerSettings()),
                LocalMediaSettings provides (settings?.media ?: MediaSettings()),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().background(paper.hyoshi)) {
                    // Narrower than the wide layouts, the phone's, at most a phone and a half wide.
                    val wide = maxWidth >= WideLayout.MIN_WIDTH
                    Box(Modifier.then(if (wide) Modifier.fillMaxSize() else Modifier.widthIn(max = 600.dp).fillMaxHeight()).align(Alignment.TopCenter)) { content() }
                    Notice(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/** A short notice (the phone's toasts), for a few seconds. */
@Composable
private fun Notice(modifier: Modifier) {
    val message = Toasts.current ?: return
    LaunchedEffect(message) {
        delay(2_600)
        Toasts.expire()
    }
    Text(
        message,
        style = PaperType.noteBody,
        color = paper.kami,
        modifier = modifier.padding(bottom = 72.dp).background(paper.sumi, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Keeps the last crash in Fumibako's folder, so a tester can copy it from Settings, About. */
object CrashReport {
    fun file(dataDir: File): File = File(dataDir, "last-crash.txt")

    fun install(dataDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(dataDir).writeText(
                    "Fumibako ${System.getProperty("fumibako.version")} on ${System.getProperty("os.name")} ${System.getProperty("os.version")} (Java ${System.getProperty("java.version")})\n" +
                        "${Date()}, thread ${thread.name}\n\n" + error.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
