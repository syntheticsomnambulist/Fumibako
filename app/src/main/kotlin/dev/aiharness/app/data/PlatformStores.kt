package dev.aiharness.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.aiharness.core.model.AppSettings
import dev.aiharness.core.storage.BlobStore
import dev.aiharness.core.storage.SecretStore
import dev.aiharness.core.storage.SettingsRepository
import dev.aiharness.core.util.HarnessJson
import dev.aiharness.core.util.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys encrypted with an AES-GCM key held in the Android Keystore (never leaves secure
 * hardware where available). Ciphertext is kept in a private SharedPreferences file.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(key, null) ?: return@withContext null
        runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(key).apply()
    }

    /** Forgets every stored secret (used when the database is wiped: their credentials are gone). */
    fun clearAll() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ai_harness_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** App settings stored as one JSON document, so new settings fields need no migration. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    private val key = stringPreferencesKey("settings_json")

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { HarnessJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { HarnessJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
            prefs[key] = HarnessJson.encodeToString(AppSettings.serializer(), transform(current))
        }
    }
}

/** Pictures, stickers and attachments as files under the app's private storage. Refs look like `file:<name>`. */
class FileBlobStore(context: Context) : BlobStore {
    private val dir = File(context.filesDir, "blobs").apply { mkdirs() }

    fun fileFor(ref: String): File? = ref.removePrefix(PREFIX).takeIf { ref.startsWith(PREFIX) && !it.contains('/') }?.let { File(dir, it) }

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

    override suspend fun delete(ref: String) {
        withContext(Dispatchers.IO) { fileFor(ref)?.delete() }
    }

    /** Deletes every stored image (used when the database is wiped: nothing refers to them any more). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val PREFIX = "file:"
    }
}
