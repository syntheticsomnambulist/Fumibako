package dev.aiharness.desktop

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aiharness.app.AppContainer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.Date

/** Fumibako on a computer: the shared screens in a window (docs/plan-desktop.md). */
fun main() {
    val dataDir = DesktopPlatform.dataDir()
    CrashReport.install(dataDir)
    val platform = DesktopPlatform(dataDir)
    val container = AppContainer(platform)
    platform.heartbeats.run = { force, persona -> container.harness.heartbeat.run(force = force, onlyPersonaId = persona) }
    container.start()
    val backHandlers = BackHandlers()
    val openConversation = MutableStateFlow<String?>(null)

    application {
        val state = rememberWindowState(size = DpSize(440.dp, 860.dp))
        @Suppress("DEPRECATION") // The classpath loader reads the app's vector icon.
        val icon = painterResource("icon.xml")
        Window(
            onCloseRequest = ::exitApplication,
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
            FumibakoWindow(container, backHandlers) { AppNavigation(openConversation) }
        }
    }
}

/** The app's theme and settings around [content], as on the phone, in a phone-wide column. */
@Composable
fun FumibakoWindow(container: AppContainer, backHandlers: BackHandlers = remember { BackHandlers() }, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppContainer provides container, LocalBackHandlers provides backHandlers) {
        val settings by container.harness.settings.settings.collectAsStateWithLifecycle(initialValue = null)
        val usersFlow = remember { container.harness.userPersonas.observeAll() }
        val users by usersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val you = users.firstOrNull { it.id == settings?.activeUserPersonaId } ?: users.firstOrNull()
        HarnessTheme(settings?.theme ?: ThemeSettings(), you?.ink, you?.tapePattern ?: TapePattern.STRIPES) {
            CompositionLocalProvider(
                LocalStickerSettings provides (settings?.stickers ?: StickerSettings()),
                LocalMediaSettings provides (settings?.media ?: MediaSettings()),
            ) {
                Box(Modifier.fillMaxSize().background(paper.hyoshi)) {
                    // The phone's layouts, at most a phone and a half wide (wider layouts come later).
                    Box(Modifier.widthIn(max = 600.dp).fillMaxHeight().align(Alignment.TopCenter)) { content() }
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
