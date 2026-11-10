package com.cemail.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A signed-in Google account. [id] is Google's stable subject id (not the email, which can change). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String?,
    val addedAtMillis: Long,
    /** GMAIL, IMAP or MICROSOFT: which kind of server the account talks to. */
    @ColumnInfo(defaultValue = "GMAIL") val provider: String = "GMAIL",
)

/**
 * A cached message. [localId] is what the UI uses; [remoteId] is Gmail's id (null for messages that
 * only exist on the phone, like drafts). [pinned] and [snoozedUntil] are Cemail-only and never sent to Gmail.
 */
@Entity(tableName = "messages", indices = [Index(value = ["accountId", "remoteId"], unique = true)])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val accountId: String,
    val remoteId: String?,
    val folder: String,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val body: String,
    val timeMillis: Long,
    val read: Boolean,
    val starred: Boolean,
    val pinned: Boolean,
    val snoozedUntil: Long?,
    val hasAttachment: Boolean,
    /** Gmail label ids joined with spaces. */
    @ColumnInfo(defaultValue = "") val labelIds: String = "",
    /** null = not downloaded yet, empty = the message has no HTML part. */
    val bodyHtml: String? = null,
    /** Gmail's conversation id (null for messages saved before it was kept, and for local drafts). */
    val threadId: String? = null,
    /** Copy and blind-copy recipients of a saved draft (comma separated), and the reply ids as "In-Reply-To
References". */
    @ColumnInfo(defaultValue = "") val cc: String = "",
    @ColumnInfo(defaultValue = "") val bcc: String = "",
    @ColumnInfo(defaultValue = "") val replyRefs: String = "",
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY addedAtMillis")
    fun observeAll(): Flow<List<AccountEntity>>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT provider FROM accounts WHERE id = :id")
    suspend fun providerOf(id: String): String?
}

@Dao
interface MessageDao {
    /**
     * The list: every column except the HTML, with the text cut to the start (drafts keep it whole, they are
     * edited from here). Android reads query results through a 2 MB window, so one huge newsletter in a
     * `SELECT *` made the whole list fail ("Couldn't read row 14"). The full message comes from [get].
     */
    @Query(
        "SELECT localId, accountId, remoteId, folder, senderName, senderEmail, subject, " +
            "CASE WHEN folder = 'DRAFTS' THEN body ELSE substr(body, 1, 2000) END AS body, " +
            "timeMillis, read, starred, pinned, snoozedUntil, hasAttachment, labelIds, " +
            "CASE WHEN folder = 'DRAFTS' THEN bodyHtml ELSE NULL END AS bodyHtml, threadId, cc, bcc, replyRefs " +
            "FROM messages WHERE accountId = :accountId ORDER BY timeMillis DESC",
    )
    fun observe(accountId: String): Flow<List<MessageEntity>>

    /**
     * What the sync needs to compare with the server, without the texts. [MessageEntity.bodyHtml] is null if
     * the HTML was never downloaded and empty otherwise. Never write these rows back: use [updateState].
     */
    @Query(
        "SELECT localId, accountId, remoteId, folder, senderName, senderEmail, subject, '' AS body, " +
            "timeMillis, read, starred, pinned, snoozedUntil, hasAttachment, labelIds, " +
            "CASE WHEN bodyHtml IS NULL THEN NULL ELSE '' END AS bodyHtml, threadId, cc, bcc, replyRefs " +
            "FROM messages WHERE accountId = :accountId AND remoteId IS NOT NULL",
    )
    suspend fun remoteOnes(accountId: String): List<MessageEntity>

    /** One message without its texts (see [bodyChunk] and [htmlChunk] for those). */
    @Query(
        "SELECT localId, accountId, remoteId, folder, senderName, senderEmail, subject, '' AS body, " +
            "timeMillis, read, starred, pinned, snoozedUntil, hasAttachment, labelIds, NULL AS bodyHtml, threadId, cc, bcc, replyRefs " +
            "FROM messages WHERE localId = :localId",
    )
    suspend fun light(localId: Long): MessageEntity?

    /** Characters [start]..[start]+[length] of the text; read piece by piece so no row nears the 2 MB window. */
    @Query("SELECT substr(body, :start, :length) FROM messages WHERE localId = :localId")
    suspend fun bodyChunk(localId: Long, start: Int, length: Int): String?

    @Query("SELECT substr(bodyHtml, :start, :length) FROM messages WHERE localId = :localId")
    suspend fun htmlChunk(localId: Long, start: Int, length: Int): String?

    @Query("SELECT remoteId FROM messages WHERE accountId = :accountId AND remoteId IS NOT NULL AND bodyHtml LIKE '%cid:%'")
    suspend fun remoteIdsWithInlineImages(accountId: String): List<String>

    @Query("SELECT remoteId FROM messages WHERE localId = :localId AND accountId = :accountId")
    suspend fun remoteIdOf(localId: Long, accountId: String): String?

    @Query("SELECT remoteId FROM messages WHERE localId = :localId")
    suspend fun remoteIdOf(localId: Long): String?

    @Query("UPDATE messages SET folder = :folder, read = :read, starred = :starred, pinned = :pinned, snoozedUntil = :snoozedUntil WHERE localId = :localId")
    suspend fun updateFlags(localId: Long, folder: String, read: Boolean, starred: Boolean, pinned: Boolean, snoozedUntil: Long?)

    @Query("UPDATE messages SET folder = :folder, read = :read, starred = :starred, labelIds = :labelIds WHERE localId = :localId")
    suspend fun updateState(localId: Long, folder: String, read: Boolean, starred: Boolean, labelIds: String)

    @Query("UPDATE messages SET labelIds = :labelIds WHERE localId = :localId")
    suspend fun updateLabelIds(localId: Long, labelIds: String)

    @Query("UPDATE messages SET folder = :folder WHERE localId = :localId")
    suspend fun updateFolder(localId: Long, folder: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE localId = :localId")
    suspend fun get(localId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun delete(localId: Long)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND localId IN (:localIds)")
    suspend fun deleteAll(accountId: String, localIds: List<Long>)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)

    /** The newest Inbox messages of every account, without their texts (for the widgets). */
    @Query(
        "SELECT localId, accountId, remoteId, folder, senderName, senderEmail, subject, '' AS body, " +
            "timeMillis, read, starred, pinned, snoozedUntil, hasAttachment, labelIds, NULL AS bodyHtml, threadId, cc, bcc, replyRefs " +
            "FROM messages WHERE folder = 'INBOX' AND (snoozedUntil IS NULL OR snoozedUntil < :now) " +
            "ORDER BY timeMillis DESC LIMIT :limit",
    )
    suspend fun recentInbox(now: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE folder = 'INBOX' AND read = 0 AND (snoozedUntil IS NULL OR snoozedUntil < :now)")
    suspend fun unreadInboxCount(now: Long): Int

    /** Changes whenever the Inbox does: the widgets redraw then. */
    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END), 0) AS unread, " +
            "COALESCE(MAX(timeMillis), 0) AS latest FROM messages WHERE folder = 'INBOX'",
    )
    fun inboxSignature(): Flow<InboxSignature>
}

/** A Gmail label the user created (system labels like INBOX are not stored). */
@Entity(tableName = "labels", primaryKeys = ["accountId", "id"])
data class LabelEntity(val accountId: String, val id: String, val name: String)

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE accountId = :accountId")
    fun observe(accountId: String): Flow<List<LabelEntity>>

    @Query("DELETE FROM labels WHERE accountId = :accountId")
    suspend fun clear(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(labels: List<LabelEntity>)
}

@Database(
    entities = [AccountEntity::class, MessageEntity::class, LabelEntity::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class CemailDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun messages(): MessageDao
    abstract fun labels(): LabelDao

    companion object {
        fun create(context: Context): CemailDatabase =
            Room.databaseBuilder(context, CemailDatabase::class.java, "cemail.db").addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
}

/** 0.4.0 fixed how message text is decoded: forget the cached bodies so the next sync downloads them again. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE messages SET bodyHtml = NULL")
    }
}

/** 0.25.0 keeps each message's conversation id: the cached messages are downloaded again once to fill it in. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN threadId TEXT")
        db.execSQL("UPDATE messages SET bodyHtml = NULL WHERE remoteId IS NOT NULL")
    }
}

/** 0.28.0: saved drafts keep Cc, Bcc and the reply ids. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN cc TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN bcc TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN replyRefs TEXT NOT NULL DEFAULT ''")
    }
}

/** 0.26.0: accounts can be IMAP (or Microsoft), not only Google. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN provider TEXT NOT NULL DEFAULT 'GMAIL'")
    }
}

data class InboxSignature(val total: Int, val unread: Int, val latest: Long)
