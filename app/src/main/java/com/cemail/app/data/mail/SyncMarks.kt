package com.cemail.app.data.mail

import android.content.Context

/** Where an account's sync remembers its place in Gmail's history, and when it last listed the whole mailbox. */
interface SyncMarks {
    /** The history id the local copy is up to date with; null if there is none yet (a full sync is needed). */
    fun historyId(): String?

    /** When the mailbox was last listed from scratch (0 if never). */
    fun lastFullMillis(): Long

    /** Remembers [historyId]; [fullToo] marks that a complete listing was just done. */
    fun save(historyId: String, fullToo: Boolean)

    /** Other small facts of the account (IMAP folder roles and UIDVALIDITY values). */
    fun get(key: String): String?
    fun put(key: String, value: String)

    class InMemory : SyncMarks {
        private var id: String? = null
        private var full = 0L
        private val extra = HashMap<String, String>()
        override fun historyId() = id
        override fun lastFullMillis() = full
        override fun get(key: String) = extra[key]
        override fun put(key: String, value: String) { extra[key] = value }
        override fun save(historyId: String, fullToo: Boolean) {
            id = historyId
            if (fullToo) full = System.currentTimeMillis()
        }
    }

    /** Kept in a private preferences file, one pair of keys per account. */
    class Prefs(context: Context, private val accountId: String) : SyncMarks {
        private val prefs = context.applicationContext.getSharedPreferences("sync_marks", Context.MODE_PRIVATE)
        override fun historyId() = prefs.getString("$accountId.history", null)
        override fun lastFullMillis() = prefs.getLong("$accountId.full", 0L)
        override fun get(key: String) = prefs.getString("$accountId.x.$key", null)
        override fun put(key: String, value: String) { prefs.edit().putString("$accountId.x.$key", value).apply() }
        override fun save(historyId: String, fullToo: Boolean) {
            prefs.edit().putString("$accountId.history", historyId).apply {
                if (fullToo) putLong("$accountId.full", System.currentTimeMillis())
            }.apply()
        }
    }
}
