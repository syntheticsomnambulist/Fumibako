package dev.aiharness.desktop

import com.github.javakeyring.Keyring
import dev.aiharness.app.platform.ClearableSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Holds the one key that encrypts the API keys. */
interface KeyHolder {
    /** "keychain" or "file": recorded with the keys, so a keychain that's locked isn't replaced by a file. */
    val kind: String

    /** What Settings says encrypts the keys. */
    val description: String

    fun read(): String?

    fun write(value: String)
}

/** The system's keychain: Windows Credential Manager, the macOS Keychain, the Secret Service on Linux. */
class KeychainKeyHolder private constructor(private val keyring: Keyring) : KeyHolder {
    override val kind = KIND
    override val description = "a key kept in your computer's keychain"

    override fun read(): String? = runCatching { keyring.getPassword(SERVICE, ACCOUNT) }.getOrNull()?.takeIf { it.isNotBlank() }

    override fun write(value: String) = keyring.setPassword(SERVICE, ACCOUNT, value)

    companion object {
        const val KIND = "keychain"
        private const val SERVICE = "Fumibako"
        private const val ACCOUNT = "API keys"

        /** The keychain, or null if this computer has none Fumibako can reach. */
        fun open(): KeyHolder? = runCatching { KeychainKeyHolder(Keyring.create()) }.getOrNull()
    }
}

/** Where there's no keychain (some Linux setups): the key in a file only you can read. */
class FileKeyHolder(private val file: File) : KeyHolder {
    override val kind = KIND
    override val description = "a key in a file only you can read (this computer has no keychain Fumibako can use)"

    override fun read(): String? = file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    override fun write(value: String) {
        file.parentFile?.mkdirs()
        ownerOnly(file)
        file.writeText(value)
    }

    companion object {
        const val KIND = "file"
    }
}

/**
 * API keys on a computer, as on the phone: each encrypted (AES-GCM) with one key that the system's
 * keychain holds ([keychain]), the ciphertext in Fumibako's folder. Where there's no keychain, the
 * key is in a file only you can read. Which one holds it is recorded, so a keychain that can't be
 * reached one day (locked, say) leaves the keys unreadable rather than replaced.
 */
class KeychainSecretStore(private val dir: File, keychain: () -> KeyHolder?) : ClearableSecretStore {
    private val valuesFile = File(dir, "secrets.properties")
    private val props = Properties().apply { if (valuesFile.exists()) valuesFile.inputStream().use { load(it) } }
    private val holder: KeyHolder?
    private val key: SecretKeySpec?

    init {
        val recorded = props.getProperty(HOLDER)
        val fileHolder = FileKeyHolder(File(dir, "secrets.key"))
        holder = when (recorded) {
            KeychainKeyHolder.KIND -> keychain()
            FileKeyHolder.KIND -> fileHolder
            else -> keychain() ?: fileHolder
        }
        key = holder?.let { h ->
            h.read()?.let(::keyOf) ?: if (recorded == null) newKey(h) else null
        }
        if (holder != null && key == null) {
            System.err.println("Fumibako: the key for API keys can't be read from ${holder.description}; keys are unavailable.")
        }
        importPlainSecrets(File(dir, "secrets"))
    }

    override val encryptedWith: String = when {
        holder == null -> "a key in your computer's keychain, which can't be reached now"
        props.getProperty(HOLDER) == FileKeyHolder.KIND -> FileKeyHolder(File(dir, "secrets.key")).description
        else -> holder.description
    }

    /** A new key, if the holder keeps it (a keychain that won't take it falls back to the file). */
    private fun newKey(h: KeyHolder): SecretKeySpec? {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val kept = runCatching { h.write(encoded); h.read() == encoded }.getOrDefault(false)
        val holderKind = if (kept) h.kind else FileKeyHolder.KIND
        if (!kept) FileKeyHolder(File(dir, "secrets.key")).write(encoded)
        props.setProperty(HOLDER, holderKind)
        save()
        return SecretKeySpec(bytes, "AES")
    }

    private fun keyOf(encoded: String): SecretKeySpec? =
        runCatching { SecretKeySpec(Base64.getDecoder().decode(encoded), "AES") }.getOrNull()

    /** Keys saved before the keychain (the first desktop builds kept them in plain text): moved in, then deleted. */
    private fun importPlainSecrets(old: File) {
        if (!old.isFile || key == null) return
        val plain = Properties().apply { old.inputStream().use { load(it) } }
        plain.stringPropertyNames().forEach { name -> props.setProperty(VALUE + name, encrypt(plain.getProperty(name), key)) }
        save()
        old.delete()
    }

    @Synchronized
    private fun save() {
        dir.mkdirs()
        ownerOnly(valuesFile)
        valuesFile.outputStream().use { props.store(it, "Fumibako: API keys, encrypted") }
    }

    private fun encrypt(value: String, secret: SecretKeySpec): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val secret = this@KeychainSecretStore.key ?: return@withContext null
        val stored = props.getProperty(VALUE + key) ?: return@withContext null
        runCatching {
            val bytes = Base64.getDecoder().decode(stored)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrNull()
    }

    override suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val secret = this@KeychainSecretStore.key ?: return@withContext
        props.setProperty(VALUE + key, encrypt(value, secret))
        save()
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        props.remove(VALUE + key)
        save()
    }

    override fun clearAll() {
        props.stringPropertyNames().filter { it.startsWith(VALUE) }.forEach { props.remove(it) }
        save()
    }

    private companion object {
        const val HOLDER = "holder"
        const val VALUE = "key."
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** Makes [file] readable and writable by its owner only (creating it empty if need be). */
internal fun ownerOnly(file: File) {
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        file.createNewFile()
    }
    runCatching {
        Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }.onFailure {
        // Not a POSIX file system (Windows): the folder is already the user's own; narrow what Java can.
        file.setReadable(false, false); file.setReadable(true, true)
        file.setWritable(false, false); file.setWritable(true, true)
    }
}
