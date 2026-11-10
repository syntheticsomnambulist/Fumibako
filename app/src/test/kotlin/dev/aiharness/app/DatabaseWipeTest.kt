package dev.aiharness.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aiharness.app.data.RoomPersonaRepository
import dev.aiharness.app.data.db.HarnessDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** A database older than the kept migrations is recreated empty, and the app is told so it can start fresh. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseWipeTest {
    @Test
    fun anOlderSchemaIsWipedAndReported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wipe-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE personas (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, json TEXT NOT NULL)")
            old.execSQL("INSERT INTO personas VALUES ('p1', 'Aria', '{}')")
            old.version = 1
        }

        var wiped = false
        val db = HarnessDatabase.create(context, onWiped = { wiped = true }, name = name)
        val personas = runBlocking { RoomPersonaRepository(db.dao()).getAll() }
        db.close()

        assertTrue(wiped, "the wipe hook ran")
        assertTrue(personas.isEmpty(), "old rows are gone")
    }
}
