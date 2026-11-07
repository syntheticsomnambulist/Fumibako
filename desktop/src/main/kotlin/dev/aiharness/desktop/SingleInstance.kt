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
 * listens on the loopback for a later start, which asks it to show its window and then quits.
 */
class SingleInstance(private val dataDir: File) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /**
     * Claims the folder: true if this is the only Fumibako, which then calls [onShow] whenever
     * another start asks; false if one is already running (it has been asked to show itself).
     */
    fun claim(onShow: () -> Unit): Boolean {
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
            askTheOtherToShow()
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
                    val line = runCatching { c.getInputStream().bufferedReader().readLine() }.getOrNull()
                    if (line == SHOW) onShow()
                }
            }
        }
        return true
    }

    private fun askTheOtherToShow() {
        val port = runCatching { File(dataDir, PORT).readText().trim().toInt() }.getOrNull() ?: return
        runCatching {
            Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write("$SHOW\n".toByteArray()) }
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
    }
}
