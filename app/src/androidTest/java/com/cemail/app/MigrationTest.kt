package com.cemail.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cemail.app.data.db.CemailDatabase
import com.cemail.app.data.db.MIGRATION_3_4
import com.cemail.app.data.db.MIGRATION_4_5
import com.cemail.app.data.db.MIGRATION_5_6
import com.cemail.app.data.db.MIGRATION_6_7
import com.cemail.app.data.db.MIGRATION_7_8
import com.cemail.app.data.db.MIGRATION_8_9
import com.cemail.app.data.db.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The database of a phone that already has messages must come through every update intact. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CemailDatabase::class.java)

    private val migrations = arrayOf(
        MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    )

    private fun insert(db: androidx.sqlite.db.SupportSQLiteDatabase, subject: String, body: String) {
        db.execSQL(
            "INSERT INTO messages (accountId, remoteId, folder, senderName, senderEmail, subject, body, timeMillis, " +
                "read, starred, pinned, hasAttachment) VALUES ('a1', '$subject', 'INBOX', 'Ana', 'ana@x.com', '$subject', '$body', 1, 0, 0, 0, 0)",
        )
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Int =
        db.query("SELECT COUNT(*) FROM messages WHERE rowid IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH '$query')")
            .use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun version7WithMessagesBecomesVersion10WithSearch() {
        helper.createDatabase("migration-test", 7).apply {
            insert(this, "Factura de mayo", "Adjunto la factura de la canción")
            insert(this, "Otra cosa", "nada que ver")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 10, true, *migrations)
        // The old messages are searchable straight away (the index was rebuilt), ignoring case and accents.
        assertEquals(1, count(db, "factur*"))
        assertEquals(1, count(db, "cancion*"))
        assertEquals(0, count(db, "inexistente*"))
        // New and changed messages keep the index in step (Room's triggers).
        insert(db, "Reunion", "lunes por la manana")
        assertEquals(1, count(db, "manana*"))
        db.execSQL("UPDATE messages SET body = 'solo martes' WHERE subject = 'Reunion'")
        assertEquals(0, count(db, "manana*"))
        assertEquals(1, count(db, "martes*"))
        db.execSQL("DELETE FROM messages WHERE subject = 'Reunion'")
        assertEquals(0, count(db, "martes*"))
        // The new columns exist with their defaults.
        db.query("SELECT cc, bcc, replyRefs, sendAt FROM messages WHERE subject = 'Otra cosa'").use {
            it.moveToFirst()
            assertEquals("", it.getString(0))
            assertEquals("", it.getString(1))
            assertEquals("", it.getString(2))
            assertEquals(true, it.isNull(3))
        }
        db.close()
    }
}
