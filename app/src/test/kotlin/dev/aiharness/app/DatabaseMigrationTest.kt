package dev.aiharness.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aiharness.app.data.db.HarnessDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Installs of the stock-look build have a version 1 database; updating must keep its data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HarnessDatabase::class.java)

    @Test
    fun version1UpgradesToVersion2KeepingData() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO personas (id, name, json) VALUES ('p1', 'Aria', '{}')")
            execSQL("INSERT INTO messages (id, conversationId, createdAt, json) VALUES ('m1', 'c1', 5, '{}')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT name FROM personas").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Aria", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.execSQL("INSERT INTO activity (id, createdAt, json) VALUES ('a1', 10, '{}')")
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
