package dev.aiharness.desktop

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Starting at login, each system's way, for the installed app only. */
class LoginItemTest {
    private val home: File = Files.createTempDirectory("fumibako-home").toFile()

    @Test
    fun onAMacItsALaunchAgentThatStartsInTheTray() {
        val item = LoginItem(appPath = "/Applications/Fumibako.app/Contents/MacOS/Fumibako", os = "Mac OS X", home = home)
        assertFalse(item.isOn())
        assertTrue(item.set(true))
        val agent = File(home, "Library/LaunchAgents/dev.fumibako.desktop.plist")
        assertTrue(item.isOn())
        val plist = agent.readText()
        assertTrue("<string>/Applications/Fumibako.app/Contents/MacOS/Fumibako</string>" in plist)
        assertTrue("<string>--background</string>" in plist && "<key>RunAtLoad</key>" in plist)
        assertTrue(item.set(false))
        assertFalse(agent.exists())
    }

    @Test
    fun onLinuxItsAnAutostartEntry() {
        val item = LoginItem(appPath = "/opt/fumibako/bin/Fumibako", os = "Linux", home = home, configHome = File(home, ".config"))
        assertTrue(item.set(true))
        val entry = File(home, ".config/autostart/fumibako.desktop").readText()
        assertTrue("Exec=\"/opt/fumibako/bin/Fumibako\" --background" in entry)
        assertTrue(item.isOn())
        assertTrue(item.set(false))
        assertFalse(item.isOn())
    }

    @Test
    fun onWindowsItsARunEntryInTheRegistry() {
        val commands = mutableListOf<List<String>>()
        val item = LoginItem(appPath = "C:\\Users\\Robin\\AppData\\Local\\Fumibako\\Fumibako.exe", os = "Windows 11", home = home, run = { commands += it; true })
        assertTrue(item.set(true))
        assertEquals(
            listOf("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Fumibako", "/t", "REG_SZ",
                "/d", "\"C:\\Users\\Robin\\AppData\\Local\\Fumibako\\Fumibako.exe\" --background", "/f"),
            commands.last(),
        )
        assertTrue(item.set(false))
        assertEquals(listOf("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Fumibako", "/f"), commands.last())
    }

    @Test
    fun notForTheAppRunFromSource() {
        val item = LoginItem(appPath = null, os = "Linux", home = home)
        assertFalse(item.available)
        assertFalse(item.set(true))
        assertFalse(File(home, ".config/autostart/fumibako.desktop").exists())
    }
}
