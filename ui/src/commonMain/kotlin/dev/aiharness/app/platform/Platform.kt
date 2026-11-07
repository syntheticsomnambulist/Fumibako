package dev.aiharness.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.aiharness.app.data.FileBlobStore
import dev.aiharness.app.data.db.PlatformContext
import dev.aiharness.app.media.Playback
import dev.aiharness.core.model.ContentPart
import dev.aiharness.core.model.HeartbeatSettings
import dev.aiharness.core.model.Track
import dev.aiharness.core.planner.PhoneCalendars
import dev.aiharness.core.storage.MarkRepository
import dev.aiharness.core.storage.SecretStore
import java.io.File

/*
 * What the shared app (AppContainer and the screens) asks of the platform it runs on. The phone's
 * side is in app/ (services) and ui/src/androidMain (what screens draw or launch); the desktop's in
 * desktop/ and ui/src/desktopMain. Composable needs are the expect declarations in Expect.kt.
 */

/** The platform's services, handed to [dev.aiharness.app.AppContainer] once at startup. */
interface AppPlatform {
    /** Where the database lives (on Android, the application context). */
    val context: PlatformContext

    /** The app's private folder: pictures (`blobs`), unsent notes (`drafts`). */
    val filesDir: File

    val secrets: ClearableSecretStore

    /** The settings file (one JSON document; see DataStoreSettingsRepository). */
    val settingsStore: DataStore<Preferences>

    /** Small things kept on this device only: when you last left, recent stickers, first launch. */
    val appState: AppState

    val phoneCalendars: PhoneCalendars
    val heartbeats: HeartbeatScheduling
    val about: AboutApp

    fun mediaImporter(blobs: FileBlobStore): MediaImporter

    fun playback(blobs: FileBlobStore, marks: MarkRepository): Playback

    fun log(message: String)

    fun warn(message: String, error: Throwable)
}

/** API keys, encrypted where the platform keeps keys. */
interface ClearableSecretStore : SecretStore {
    /** Forgets every stored secret (when the database is wiped, their credentials are gone). */
    fun clearAll()
}

/** A few values kept on this device: SharedPreferences on Android. */
interface AppState {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    /** Clears everything, before returning. */
    fun clear()
}

/** A file someone picked: a content URI on Android, a file's URI on a computer. */
@JvmInline
value class Picked(val uri: String)

/** Photos, files, songs and videos brought into a letter (see [Picked]). */
interface MediaImporter {
    /** The picked file's MIME type, when it can be told. */
    fun typeOf(item: Picked): String?

    suspend fun photo(item: Picked, maxSide: Int = 1280): ContentPart.Image?

    suspend fun file(item: Picked): ContentPart.Attachment?

    suspend fun songs(items: List<Picked>, toName: String, fromName: String, onProgress: (Float) -> Unit = {}): ContentPart.Attachment?

    suspend fun tracks(items: List<Picked>): List<Track>

    suspend fun video(item: Picked, onProgress: (Float) -> Unit = {}): ContentPart.Attachment?

    /** The picked file's bytes (a portrait, a photo to cut a sticker from). */
    suspend fun read(item: Picked): ByteArray?

    /** Where the camera puts its next photo, or null without a camera. */
    fun cameraTarget(): Picked?
}

/** One wakeup a persona's reminder asked for. */
data class Wake(val entryId: String, val personaId: String, val at: Long)

/** Background heartbeats: WorkManager on Android; while the app runs, on a computer. */
interface HeartbeatScheduling {
    /** Schedules the heartbeat at [intervalMinutes], the shortest any persona needs (or stops it). */
    fun apply(settings: HeartbeatSettings, intervalMinutes: Int)

    /** One-off wakeups for reminders personas set themselves. */
    fun wakeFor(settings: HeartbeatSettings, wakes: Set<Wake>)

    /** A heartbeat now: for one persona, or every persona (whatever their pace). */
    fun runNow(settings: HeartbeatSettings, personaId: String? = null)
}

/** Settings, About. */
interface AboutApp {
    /** The app's version, as the build names it (0.4.1). */
    val version: String?

    /** The last crash's report, kept on this device until it's cleared. */
    fun crashReport(): String?

    fun clearCrashReport()

    /** The app's icon (the seal). */
    @Composable
    fun icon(): Painter
}
