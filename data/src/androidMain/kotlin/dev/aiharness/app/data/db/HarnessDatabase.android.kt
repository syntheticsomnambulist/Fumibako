package dev.aiharness.app.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

actual typealias PlatformContext = android.content.Context

/** The phone's own SQLite, in the app's databases folder, as before the database became shared. */
internal actual fun databaseBuilder(context: PlatformContext, name: String): RoomDatabase.Builder<HarnessDatabase> =
    Room.databaseBuilder(context, HarnessDatabase::class.java, name).setDriver(AndroidSQLiteDriver())
