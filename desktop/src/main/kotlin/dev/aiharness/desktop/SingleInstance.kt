package dev.aiharness.desktop

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * One Fumibako per folder (per computer user): the first to start holds a lock in [dataDir] and
 * listens on the loopback for a later start, which asks it to show its window (and to open the
 * files it was started with, a backup double-clicked, say) and then quits.
 */
class SingleInstance(private val dataDir: File) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /**
     * Claims the folder: true if this is the only Fumibako, which then calls [onShow] whenever
     * another start asks, and [onOpen] with the files it hands over; false if one is already
     * running (it has been asked to show itself and open [files]).
     */
    fun claim(files: List<File> = emptyList(), onOpen: (List<File>) -> Unit = {}, onShow: () -> Unit): Boolean {
        dataDir.mkdirs()
        val lockFile = File(dataDir, LOCK)
        val opened = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val held = try {
            opened.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        }
        if (held == null) {
            opened.close()
            askTheOtherToShow(files)
            return false
        }
        channel = opened
        lock = held
        val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        server = socket
        File(dataDir, PORT).writeText(socket.localPort.toString())
        thread(isDaemon = true, name = "fumibako-single-instance") {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use { c ->
                    // Any program on this computer can reach the port: read a little, and only files that exist.
                    val lines = runCatching { c.getInputStream().readNBytes(MOST).toString(Charsets.UTF_8).lines().take(MOST_LINES) }.getOrDefault(emptyList())
                    val opened = lines.filter { it.startsWith(OPEN) }.map { File(it.removePrefix(OPEN)) }.filter { it.isFile }
                    if (opened.isNotEmpty()) onOpen(opened)
                    if (SHOW in lines) onShow()
                }
            }
        }
        return true
    }

    private fun askTheOtherToShow(files: List<File>) {
        val port = runCatching { File(dataDir, PORT).readText().trim().toInt() }.getOrNull() ?: return
        val message = files.joinToString("") { "$OPEN${it.absolutePath}\n" } + "$SHOW\n"
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write(message.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** Lets the folder go (at quit; the system does it anyway when the process ends). */
    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        File(dataDir, PORT).delete()
    }

    private companion object {
        const val LOCK = "fumibako.lock"
        const val PORT = "fumibako.port"
        const val SHOW = "show"
        const val OPEN = "open "
        const val MOST = 64 * 1024
        const val MOST_LINES = 100
    }
}
