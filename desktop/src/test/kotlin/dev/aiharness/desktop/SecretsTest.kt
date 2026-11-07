package dev.aiharness.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** API keys on a computer: encrypted with a key the keychain holds, never written in plain text. */
class SecretsTest {
    private val dir: File = Files.createTempDirectory("fumibako-secrets").toFile()

    /** A keychain in memory; [refuses] makes it take nothing. */
    private class FakeKeychain(var refuses: Boolean = false) : KeyHolder {
        var value: String? = null
        override val kind = KeychainKeyHolder.KIND
        override val description = "a key kept in your computer's keychain"
        override fun read() = value
        override fun write(value: String) {
            if (!refuses) this.value = value
        }
    }

    private val values get() = File(dir, "secrets.properties")

    @Test
    fun keysAreEncryptedWithTheKeychainsKey() = runBlocking {
        val keychain = FakeKeychain()
        val store = KeychainSecretStore(dir) { keychain }
        store.put("cred1", "sk-ant-secret")
        assertEquals("sk-ant-secret", store.get("cred1"))
        assertFalse("sk-ant-secret" in values.readText(), "the key isn't written in plain text")
        assertTrue(keychain.value != null, "the keychain holds the key")
        assertFalse(File(dir, "secrets.key").exists(), "no key file beside a keychain")
        assertEquals("a key kept in your computer's keychain", store.encryptedWith)

        // Opened again (the next launch), the same keychain unlocks them.
        assertEquals("sk-ant-secret", KeychainSecretStore(dir) { keychain }.get("cred1"))
    }

    @Test
    fun aKeychainThatCantBeReachedLeavesTheKeysUnreadNotReplaced() = runBlocking {
        val keychain = FakeKeychain()
        KeychainSecretStore(dir) { keychain }.put("cred1", "sk-ant-secret")
        val before = values.readText()

        val locked = KeychainSecretStore(dir) { null }
        assertNull(locked.get("cred1"))
        locked.put("cred2", "sk-other")
        assertEquals(before, values.readText(), "nothing is overwritten while the keychain is away")
        assertFalse(File(dir, "secrets.key").exists(), "no key file takes the keychain's place")
        assertTrue("can't be reached" in locked.encryptedWith)

        assertEquals("sk-ant-secret", KeychainSecretStore(dir) { keychain }.get("cred1"), "back when the keychain is")
    }

    @Test
    fun withoutAKeychainTheKeyIsInAFileOnlyYouCanRead() = runBlocking {
        val store = KeychainSecretStore(dir) { null }
        store.put("cred1", "sk-ant-secret")
        assertEquals("sk-ant-secret", store.get("cred1"))
        assertTrue(File(dir, "secrets.key").exists())
        assertFalse("sk-ant-secret" in values.readText())
        assertTrue("file only you can read" in store.encryptedWith)
        assertEquals("sk-ant-secret", KeychainSecretStore(dir) { null }.get("cred1"))
    }

    @Test
    fun aKeychainThatWontTakeTheKeyFallsBackToTheFile() = runBlocking {
        val store = KeychainSecretStore(dir) { FakeKeychain(refuses = true) }
        store.put("cred1", "sk-ant-secret")
        assertEquals("sk-ant-secret", store.get("cred1"))
        assertTrue(File(dir, "secrets.key").exists())
        assertTrue("file only you can read" in store.encryptedWith)
    }

    @Test
    fun keysFromTheFirstDesktopBuildsAreMovedInAndThePlainFileDeleted() = runBlocking {
        File(dir, "secrets").writeText("cred1=sk-plain\n")
        val store = KeychainSecretStore(dir) { FakeKeychain() }
        assertEquals("sk-plain", store.get("cred1"))
        assertFalse(File(dir, "secrets").exists())
        assertFalse("sk-plain" in values.readText())
    }

    @Test
    fun clearingForgetsEveryKey() = runBlocking {
        val keychain = FakeKeychain()
        val store = KeychainSecretStore(dir) { keychain }
        store.put("cred1", "a")
        store.put("cred2", "b")
        store.clearAll()
        assertNull(store.get("cred1"))
        assertNull(KeychainSecretStore(dir) { keychain }.get("cred2"))
    }
}
