package dev.aiharness.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** App settings stored as one JSON document, so new settings fields need no migration. */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val key = stringPreferencesKey("settings_json")

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        prefs[key]?.let { runCatching { HarnessJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { HarnessJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
            prefs[key] = HarnessJson.encodeToString(AppSettings.serializer(), transform(current))
        }
    }
}

/** Pictures, stickers and attachments as files in [dir] (the app's private `blobs` folder). Refs look like `file:<name>`. */
class FileBlobStore(private val dir: File) : BlobStore {
    init {
        dir.mkdirs()
    }

    fun fileFor(ref: String): File? = ref.removePrefix(PREFIX).takeIf { ref.startsWith(PREFIX) && !it.contains('/') }?.let { File(dir, it) }

    /** The file behind [ref], when it's stored here and still there. */
    fun existingFile(ref: String?): File? = ref?.let { fileFor(it) }?.takeIf { it.exists() }

    override suspend fun put(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.${extension.trimStart('.')}"
        File(dir, name).writeBytes(bytes)
        PREFIX + name
    }

    override suspend fun get(ref: String): ByteArray? = withContext(Dispatchers.IO) {
        fileFor(ref)?.takeIf { it.exists() }?.readBytes()
    }

    /** Stores what [input] holds without reading it all into memory (songs and videos can be large). */
    suspend fun putStream(input: java.io.InputStream, extension: String): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.${extension.trimStart('.')}"
        File(dir, name).outputStream().use { out -> input.use { it.copyTo(out) } }
        PREFIX + name
    }

    /**
     * Takes [file] in (a copy just made in the cache), moving it rather than writing it again
     * where it's on the same disk.
     */
    suspend fun move(file: File, extension: String): String = withContext(Dispatchers.IO) {
        val name = "${newId()}.${extension.trimStart('.')}"
        val target = File(dir, name)
        if (!file.renameTo(target)) {
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            file.delete()
        }
        PREFIX + name
    }

    override suspend fun delete(ref: String) {
        withContext(Dispatchers.IO) { fileFor(ref)?.delete() }
    }

    /** Every file stored here. */
    fun files(): List<File> = dir.listFiles()?.filter { it.isFile }.orEmpty()

    /** Deletes every stored image (used when the database is wiped: nothing refers to them any more). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "file:"
    }
}
