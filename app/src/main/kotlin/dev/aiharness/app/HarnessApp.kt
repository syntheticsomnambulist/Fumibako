package dev.aiharness.app

import android.app.Application
import android.os.Build
import java.io.File
import java.util.Date

class HarnessApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        container = AppContainer(this)
        container.notifier.ensureChannel()
        container.start()
    }
}

/**
 * Keeps the last crash on the phone, so a tester can copy it from Settings, About and send it.
 * Nothing leaves the phone on its own.
 */
object CrashReport {
    private const val FILE = "last-crash.txt"

    fun file(app: Application): File = File(app.filesDir, FILE)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
                file(app).writeText(
                    "Fumibako $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "${Date()}, thread ${thread.name}\n\n" + error.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
