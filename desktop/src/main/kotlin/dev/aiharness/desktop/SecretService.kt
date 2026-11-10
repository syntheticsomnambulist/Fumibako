package dev.aiharness.desktop

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * The Secret Service on Linux (GNOME Keyring, or KDE Wallet as KDE Plasma serves it), through the
 * dbus-java that FileKit's dialogs already bring: the few calls Fumibako needs to keep one secret.
 * (java-keyring's own Linux backend was built for an older dbus-java and can't run beside FileKit's.)
 *
 * The secret goes over the session bus in the "plain" session, as the bus is the user's own; the
 * Secret Service encrypts it at rest. A locked collection asks to be unlocked with the system's own
 * prompt, as other apps' keys do; dismissed or unanswered, nothing is read or written.
 */
internal class SecretService private constructor(private val connection: DBusConnection) : AutoCloseable {
    private val service: Service = connection.getRemoteObject(BUS, SERVICE_PATH, Service::class.java)
    private val session: DBusPath = service.OpenSession("plain", Variant("")).result

    /** The secret of the first item with [attributes], or null. */
    fun find(attributes: Map<String, String>): ByteArray? {
        val found = service.SearchItems(attributes)
        val item = found.unlocked.firstOrNull() ?: found.locked.firstOrNull()?.takeIf { unlock(it) } ?: return null
        return remote<Item>(item).GetSecret(session).value
    }

    /** Stores [secret] in the default collection under [attributes], replacing an item that has them. */
    fun store(label: String, attributes: Map<String, String>, secret: ByteArray) {
        val collection = service.ReadAlias("default").takeIf { it.path != "/" } ?: error("no default collection")
        if (lockedState(collection) && !unlock(collection)) error("the collection stayed locked")
        val properties = mapOf<String, Variant<*>>(
            "org.freedesktop.Secret.Item.Label" to Variant(label),
            "org.freedesktop.Secret.Item.Attributes" to Variant(attributes, "a{ss}"),
        )
        val made = remote<Collection>(collection).CreateItem(properties, Secret(session, ByteArray(0), secret, "text/plain; charset=utf8"), true)
        if (made.item.path == "/" && !prompt(made.prompt)) error("not stored")
    }

    /** Removes every item with [attributes]. */
    fun forget(attributes: Map<String, String>) {
        val found = service.SearchItems(attributes)
        (found.unlocked + found.locked).forEach { remote<Item>(it).Delete() }
    }

    private fun lockedState(path: DBusPath): Boolean =
        connection.getRemoteObject(BUS, path.path, Properties::class.java).Get<Boolean>("org.freedesktop.Secret.Collection", "Locked")

    private fun unlock(path: DBusPath): Boolean {
        val result = service.Unlock(listOf(path))
        return path in result.unlocked || (result.prompt.path != "/" && prompt(result.prompt))
    }

    /** Shows the Secret Service's prompt and waits for it: true unless it's dismissed or unanswered. */
    private fun prompt(path: DBusPath): Boolean {
        if (path.path == "/") return true
        val done = CompletableFuture<Boolean>()
        val handler = { signal: Prompt.Completed -> if (signal.path == path.path) done.complete(!signal.dismissed) }
        connection.addSigHandler(Prompt.Completed::class.java, handler)
        return try {
            remote<Prompt>(path).Prompt("")
            runCatching { done.get(PROMPT_WAIT_SECONDS, TimeUnit.SECONDS) }.getOrDefault(false)
        } finally {
            connection.removeSigHandler(Prompt.Completed::class.java, handler)
        }
    }

    private inline fun <reified T : DBusInterface> remote(path: DBusPath): T = connection.getRemoteObject(BUS, path.path, T::class.java)

    override fun close() {
        runCatching { remote<Session>(session).Close() }
        connection.close()
    }

    companion object {
        private const val BUS = "org.freedesktop.secrets"
        private const val SERVICE_PATH = "/org/freedesktop/secrets"
        private const val PROMPT_WAIT_SECONDS = 120L

        /** The session's Secret Service, or null where there's none (no D-Bus, or nothing serving it). */
        fun open(): SecretService? = runCatching {
            SecretService(DBusConnectionBuilder.forSessionBus().withShared(false).build())
        }.getOrNull()

        /** Runs [use] with the Secret Service, closing it after. */
        fun <T> with(use: (SecretService) -> T): T = (open() ?: error("no Secret Service")).use(use)
    }

    // The Secret Service's D-Bus API (org.freedesktop.Secret.*), as much of it as is used.

    @DBusInterfaceName("org.freedesktop.Secret.Service")
    interface Service : DBusInterface {
        fun OpenSession(algorithm: String, input: Variant<*>): OpenSessionResult
        fun SearchItems(attributes: Map<String, String>): SearchResult
        fun Unlock(objects: List<DBusPath>): UnlockResult
        fun ReadAlias(name: String): DBusPath
    }

    @DBusInterfaceName("org.freedesktop.Secret.Collection")
    interface Collection : DBusInterface {
        fun CreateItem(properties: Map<String, Variant<*>>, secret: Secret, replace: Boolean): CreateItemResult
    }

    @DBusInterfaceName("org.freedesktop.Secret.Item")
    interface Item : DBusInterface {
        fun GetSecret(session: DBusPath): Secret
        fun Delete(): DBusPath
    }

    @DBusInterfaceName("org.freedesktop.Secret.Session")
    interface Session : DBusInterface {
        fun Close()
    }

    @DBusInterfaceName("org.freedesktop.Secret.Prompt")
    interface Prompt : DBusInterface {
        fun Prompt(windowId: String)

        class Completed(path: String, val dismissed: Boolean, val result: Variant<*>) : DBusSignal(path, dismissed, result)
    }

    class Secret(
        @field:Position(0) val session: DBusPath,
        @field:Position(1) val parameters: ByteArray,
        @field:Position(2) val value: ByteArray,
        @field:Position(3) val contentType: String,
    ) : Struct()

    class OpenSessionResult(@field:Position(0) val output: Variant<*>, @field:Position(1) val result: DBusPath) : Tuple()

    class SearchResult(@field:Position(0) val unlocked: List<DBusPath>, @field:Position(1) val locked: List<DBusPath>) : Tuple()

    class UnlockResult(@field:Position(0) val unlocked: List<DBusPath>, @field:Position(1) val prompt: DBusPath) : Tuple()

    class CreateItemResult(@field:Position(0) val item: DBusPath, @field:Position(1) val prompt: DBusPath) : Tuple()
}
