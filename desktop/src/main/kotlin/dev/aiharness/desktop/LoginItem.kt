package dev.aiharness.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Starting Fumibako when you log in, in the tray (`--background`), the way each system does it:
 * a Run entry in the Windows registry, a LaunchAgent on macOS, an autostart entry on Linux.
 * Only for the installed app, whose launcher the installer names in `jpackage.app-path`.
 */
class LoginItem(
    private val appPath: String? = System.getProperty("jpackage.app-path"),
    private val os: String = System.getProperty("os.name"),
    private val home: File = File(System.getProperty("user.home")),
    private val configHome: File = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config"),
    private val run: (List<String>) -> Boolean = ::runCommand,
) {
    private val system = os.lowercase()
    private val windows = "win" in system
    private val mac = "mac" in system

    /** Whether it can be set: an installed app on a system this knows. */
    val available: Boolean get() = appPath != null

    private val macAgent get() = File(home, "Library/LaunchAgents/$LABEL.plist")
    private val linuxEntry get() = File(configHome, "autostart/fumibako.desktop")

    fun isOn(): Boolean = when {
        appPath == null -> false
        windows -> run(listOf("reg", "query", RUN_KEY, "/v", NAME))
        mac -> macAgent.exists()
        else -> linuxEntry.exists()
    }

    /** Turns it on or off; false if it couldn't. */
    fun set(on: Boolean): Boolean {
        val path = appPath ?: return false
        return runCatching {
            when {
                windows -> if (on) run(listOf("reg", "add", RUN_KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$path\" $BACKGROUND", "/f"))
                else run(listOf("reg", "delete", RUN_KEY, "/v", NAME, "/f")) || !isOn()
                mac -> if (on) write(macAgent, macPlist(path)) else remove(macAgent)
                else -> if (on) write(linuxEntry, linuxDesktopEntry(path)) else remove(linuxEntry)
            }
        }.getOrDefault(false)
    }

    private fun write(file: File, text: String): Boolean {
        file.parentFile?.mkdirs()
        file.writeText(text)
        return true
    }

    private fun remove(file: File): Boolean = !file.exists() || file.delete()

    companion object {
        const val BACKGROUND = "--background"
        const val NAME = "Fumibako"
        const val LABEL = "dev.fumibako.desktop"
        const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

        fun macPlist(path: String): String = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$LABEL</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |        <string>${xml(path)}</string>
            |        <string>$BACKGROUND</string>
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |</dict>
            |</plist>
            |""".trimMargin()

        fun linuxDesktopEntry(path: String): String = """
            |[Desktop Entry]
            |Type=Application
            |Name=Fumibako
            |Exec="${path.replace("\\", "\\\\").replace("\"", "\\\"")}" $BACKGROUND
            |X-GNOME-Autostart-enabled=true
            |""".trimMargin()

        private fun xml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}

/** Runs [command]; true if it exited with 0 within ten seconds. */
private fun runCommand(command: List<String>): Boolean = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.inputStream.readAllBytes()
    process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
}.getOrDefault(false)
