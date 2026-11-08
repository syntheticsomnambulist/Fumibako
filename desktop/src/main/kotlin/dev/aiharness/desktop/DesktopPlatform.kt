package dev.aiharness.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.data.db.PlatformContext
import dev.aiharness.app.media.Playback
import dev.aiharness.app.platform.AboutApp
import dev.aiharness.app.platform.AppPlatform
import dev.aiharness.app.platform.AppState
import dev.aiharness.app.platform.ClearableSecretStore
import dev.aiharness.app.platform.DesktopOptions
import dev.aiharness.app.platform.MediaImporter
import dev.aiharness.core.planner.PhoneCalendar
import dev.aiharness.core.planner.PhoneCalendars
import dev.aiharness.core.planner.PhoneEvent
import dev.aiharness.core.storage.MarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Properties

/**
 * The desktop's services for the shared AppContainer. Everything lives in [dataDir], outside the
 * install folder, so updates and reinstalls keep your letters.
 */
class DesktopPlatform(
    val dataDir: File,
    /** The system's keychain; tests give none, so a key file stands in. */
    keychain: () -> KeyHolder? = KeychainKeyHolder::open,
    private val loginItem: LoginItem = LoginItem(),
    /** Whether the system has a tray (AWT's SystemTray); tests have none. */
    override val trayWorks: Boolean = runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false),
) : AppPlatform, DesktopOptions {
    override val context: PlatformContext = object : PlatformContext() {
        override val filesDir: File = dataDir
    }
    override val filesDir: File = dataDir
    override val secrets: ClearableSecretStore = KeychainSecretStore(dataDir, keychain)
    override val settingsStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(dataDir, "settings.preferences_pb") })
    override val appState: AppState = PropertiesAppState(File(dataDir, "app_state.properties"))

    /** No calendars to read on a computer yet (docs/plan-desktop.md, "Noted for later"). */
    override val phoneCalendars: PhoneCalendars = object : PhoneCalendars {
        override fun readable() = false
        override suspend fun calendars(): List<PhoneCalendar> = emptyList()
        override suspend fun events(from: Long, to: Long, calendarIds: Set<String>): List<PhoneEvent> = emptyList()
    }

    override val heartbeats = DesktopHeartbeats()

    private val _keepInTray = MutableStateFlow(appState.getBoolean(KEY_KEEP_IN_TRAY, true))
    override val keepInTray: StateFlow<Boolean> = _keepInTray

    override fun setKeepInTray(on: Boolean) {
        appState.putBoolean(KEY_KEEP_IN_TRAY, on)
        _keepInTray.value = on
    }

    // The system's own record (the registry, a LaunchAgent, an autostart entry) is the setting.
    private val _startAtLogin = MutableStateFlow(runCatching { loginItem.isOn() }.getOrDefault(false))
    override val startAtLogin: StateFlow<Boolean> = _startAtLogin
    override val canStartAtLogin: Boolean get() = loginItem.available

    override fun setStartAtLogin(on: Boolean): Boolean {
        val done = loginItem.set(on)
        _startAtLogin.value = runCatching { loginItem.isOn() }.getOrDefault(on && done)
        return done
    }

    override val about: AboutApp = object : AboutApp {
        override val version: String? = System.getProperty("fumibako.version")
        override fun crashReport(): String? = CrashReport.file(dataDir).takeIf { it.exists() }?.readText()
        override fun clearCrashReport() {
            CrashReport.file(dataDir).delete()
        }

        @Suppress("DEPRECATION") // The classpath loader reads the app's vector icon; Compose resources would need a second copy.
        @Composable
        override fun icon(): Painter = painterResource("seal.xml")
    }

    override fun mediaImporter(blobs: FileBlobStore): MediaImporter = DesktopMedia(blobs)

    override fun playback(blobs: FileBlobStore, marks: MarkRepository): Playback = DesktopPlayback(blobs, marks)

    override fun log(message: String) = Unit

    override fun warn(message: String, error: Throwable) {
        System.err.println("Fumibako: $message: $error")
    }

    companion object {
        private const val KEY_KEEP_IN_TRAY = "keep_in_tray"

        /**
         * Where Fumibako keeps its files: FUMIBAKO_HOME if set (tests, a second copy), else the
         * system's place for app data.
         */
        fun dataDir(): File {
            System.getenv("FUMIBAKO_HOME")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                "win" in os -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Fumibako")
                "mac" in os -> File(home, "Library/Application Support/Fumibako")
                else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "Fumibako")
            }.apply { mkdirs() }
        }
    }
}

/** A few values kept on this computer, in a properties file. */
private class PropertiesAppState(private val file: File) : AppState {
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }

    @Synchronized
    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "Fumibako") }
    }

    override fun getString(key: String): String? = props.getProperty(key)
    override fun putString(key: String, value: String) {
        props.setProperty(key, value); save()
    }

    override fun getLong(key: String, default: Long): Long = props.getProperty(key)?.toLongOrNull() ?: default
    override fun putLong(key: String, value: Long) {
        props.setProperty(key, value.toString()); save()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = props.getProperty(key)?.toBooleanStrictOrNull() ?: default
    override fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString()); save()
    }

    override fun clear() {
        props.clear(); save()
    }
}
