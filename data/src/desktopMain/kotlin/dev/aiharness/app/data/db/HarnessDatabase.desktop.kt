package dev.aiharness.app.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/** On a computer: the folder Fumibako keeps its files in (the database, pictures, settings). */
actual abstract class PlatformContext {
    abstract val filesDir: File
}

/** SQLite bundled with the app (the same on every system), in [PlatformContext.filesDir]. */
internal actual fun databaseBuilder(context: PlatformContext, name: String): RoomDatabase.Builder<HarnessDatabase> =
    Room.databaseBuilder<HarnessDatabase>(name = File(context.filesDir.apply { mkdirs() }, name).path).setDriver(BundledSQLiteDriver())
