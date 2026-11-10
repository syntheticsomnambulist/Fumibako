package dev.aiharness.core.transfer

import dev.aiharness.core.util.HarnessJson
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A backup locked with a password. The file is a short plain header (that it's a locked Fumibako
 * backup, the format and app versions, the salt and key-derivation settings) and then the whole
 * backup encrypted with AES-256-GCM, under a key derived from the password with
 * PBKDF2-HMAC-SHA256. Nothing but the header can be read without the password.
 *
 * The backup is encrypted in segments of [SEGMENT] bytes, each with its own nonce (a random prefix,
 * its number, and whether it's the last), so a large one never has to fit in memory and a file cut
 * short, or with its segments reordered, fails to open rather than opening wrong. The header is
 * every segment's associated data, so it can't be changed either.
 */
object Lock {
    /** How a locked backup starts. */
    const val MAGIC = "FUMIBAKO LOCKED\n"

    const val ITERATIONS = 600_000
    const val SEGMENT = 1 shl 20
    private const val TAG_BITS = 128
    private const val TAG = TAG_BITS / 8
    private const val PREFIX = 7
    private const val MIN_SEGMENT = 1 shl 10
    private const val MAX_SEGMENT = 16 shl 20
    private const val MAX_ITERATIONS = 10_000_000

    /** What a locked backup says of itself before it's unlocked. */
    @Serializable
    data class Header(
        val format: Int,
        val app: String,
        val kdf: String = "PBKDF2-HMAC-SHA256",
        val iterations: Int = ITERATIONS,
        val salt: String,
        val cipher: String = "AES-256-GCM",
        val segment: Int = SEGMENT,
        val noncePrefix: String,
    )

    /** The password is wrong (or the file isn't what it says). */
    class WrongPassword : Exception("That isn't the password it was saved with.")

    /** The file was cut short or changed after it was locked. */
    class Damaged : Exception("This backup is damaged: part of it is missing or changed.")

    fun isLocked(start: ByteArray): Boolean = start.size >= MAGIC.length && String(start, 0, MAGIC.length, Charsets.ISO_8859_1) == MAGIC

    /** Reads the header at the start of [input] (which is left just after it); null when it isn't a locked backup. */
    fun readHeader(input: InputStream): Pair<Header, ByteArray>? {
        val magic = ByteArray(MAGIC.length)
        if (input.readNBytesCompat(magic) < magic.size || !isLocked(magic)) return null
        val line = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) break
            line.write(b)
            if (line.size() > 4096) return null
        }
        val header = runCatching { HarnessJson.decodeFromString(Header.serializer(), line.toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        return header to (magic + line.toByteArray() + '\n'.code.toByte())
    }

    /** Encrypts [plain] into [out] under [password]. */
    fun encrypt(plain: InputStream, out: OutputStream, password: CharArray, format: Int, app: String, iterations: Int = ITERATIONS) {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(PREFIX).also(random::nextBytes)
        val header = Header(format = format, app = app, iterations = iterations, salt = b64(salt), noncePrefix = b64(prefix))
        val headerBytes = MAGIC.toByteArray(Charsets.ISO_8859_1) + HarnessJson.encodeToString(Header.serializer(), header).toByteArray() + '\n'.code.toByte()
        out.write(headerBytes)
        val key = key(password, salt, iterations)
        val input = BufferedInputStream(plain, SEGMENT)
        var current = ByteArray(SEGMENT)
        var currentLength = input.readNBytesCompat(current)
        var index = 0
        while (true) {
            val next = ByteArray(SEGMENT)
            val nextLength = if (currentLength < SEGMENT) 0 else input.readNBytesCompat(next)
            val last = nextLength == 0
            out.write(cipher(Cipher.ENCRYPT_MODE, key, prefix, index, last, headerBytes).doFinal(current, 0, currentLength))
            if (last) break
            current = next
            currentLength = nextLength
            index++
        }
        out.flush()
    }

    /**
     * Decrypts [input] (a locked backup from its start) into [out]. Throws [WrongPassword] when the
     * first segment won't open, [Damaged] when a later one won't or the file ends too soon.
     */
    fun decrypt(input: InputStream, out: OutputStream, password: CharArray) {
        val source = BufferedInputStream(input, SEGMENT + TAG + 1)
        val (header, headerBytes) = readHeader(source) ?: throw Damaged()
        require(header.cipher == "AES-256-GCM" && header.kdf == "PBKDF2-HMAC-SHA256") { "Locked with something this version doesn't know" }
        // The header comes from the file: a damaged or made-up one mustn't ask for gigabytes or hours.
        if (header.segment !in MIN_SEGMENT..MAX_SEGMENT || header.iterations !in 1..MAX_ITERATIONS) throw Damaged()
        val key = key(password, unb64(header.salt), header.iterations)
        val prefix = unb64(header.noncePrefix)
        val full = header.segment + TAG
        val buffer = ByteArray(full)
        var index = 0
        while (true) {
            val length = source.readNBytesCompat(buffer)
            source.mark(1)
            val last = length < full || source.read() < 0
            if (!last) source.reset()
            if (length < TAG) throw if (index == 0) WrongPassword() else Damaged()
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, prefix, index, last, headerBytes).doFinal(buffer, 0, length)
            } catch (e: AEADBadTagException) {
                throw if (index == 0) WrongPassword() else Damaged()
            }
            out.write(plain)
            if (last) break
            index++
        }
        out.flush()
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, index: Int, last: Boolean, aad: ByteArray): Cipher {
        val nonce = ByteBuffer.allocate(12).put(prefix).putInt(index).put(if (last) 1 else 0).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = java.util.Base64.getDecoder().decode(text)

    /** Fills [buffer] as far as [this] goes (InputStream.readNBytes is newer than Android 8). */
    private fun InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
