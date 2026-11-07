package dev.aiharness.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import dev.aiharness.app.data.AndroidPhoneCalendars
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.data.KeystoreSecretStore
import dev.aiharness.app.data.settingsDataStore
import dev.aiharness.app.heartbeat.HeartbeatScheduler
import dev.aiharness.app.heartbeat.Notifier
import dev.aiharness.app.media.AndroidMediaImport
import dev.aiharness.app.media.AndroidPlayback
import dev.aiharness.app.platform.AboutApp
import dev.aiharness.app.platform.AppPlatform
import dev.aiharness.app.platform.AppState
import dev.aiharness.app.platform.HeartbeatScheduling
import dev.aiharness.app.platform.Wake
import dev.aiharness.core.model.HeartbeatSettings
import dev.aiharness.core.storage.MarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The phone's services for the shared [AppContainer]: the same stores and names the app always used. */
class AndroidPlatform(private val app: Application) : AppPlatform {
    override val context: Context = app
    override val filesDir = app.filesDir
    override val secrets = KeystoreSecretStore(app)
    override val settingsStore = app.settingsDataStore
    override val appState: AppState = PreferencesAppState(app)
    override val phoneCalendars = AndroidPhoneCalendars(app)

    /** Letters' notifications (and Busy for now), from heartbeats. */
    val notifier = Notifier(app)

    override val heartbeats = object : HeartbeatScheduling {
        override fun apply(settings: HeartbeatSettings, intervalMinutes: Int) = HeartbeatScheduler.apply(app, settings, intervalMinutes)
        override fun wakeFor(settings: HeartbeatSettings, wakes: Set<Wake>) = HeartbeatScheduler.wakeFor(app, settings, wakes)
        override fun runNow(settings: HeartbeatSettings, personaId: String?) = HeartbeatScheduler.runNow(app, settings, personaId)
    }

    override val about = object : AboutApp {
        override val version: String? = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
        override fun crashReport(): String? = CrashReport.file(app).takeIf { it.exists() }?.readText()
        override fun clearCrashReport() {
            CrashReport.file(app).delete()
        }

        @Composable
        override fun icon(): Painter = painterResource(R.drawable.ic_launcher_foreground)
    }

    override fun mediaImporter(blobs: FileBlobStore) = AndroidMediaImport(app, blobs)

    /** A media controller lives on the main thread. */
    override fun playback(blobs: FileBlobStore, marks: MarkRepository) =
        AndroidPlayback(app, blobs, marks, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    override fun log(message: String) {
        Log.d("Harness", message)
    }

    override fun warn(message: String, error: Throwable) {
        Log.w("Harness", message, error)
    }
}

/** The `app_state` preferences file, as before. */
private class PreferencesAppState(context: Context) : AppState {
    private val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun clear() {
        prefs.edit().clear().commit()
    }
}
