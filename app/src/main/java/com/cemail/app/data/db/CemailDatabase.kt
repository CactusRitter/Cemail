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
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY addedAtMillis")
    fun observeAll(): Flow<List<AccountEntity>>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE accountId = :accountId ORDER BY timeMillis DESC")
    fun observe(accountId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND remoteId IS NOT NULL")
    suspend fun remoteOnes(accountId: String): List<MessageEntity>

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
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class CemailDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun messages(): MessageDao
    abstract fun labels(): LabelDao

    companion object {
        fun create(context: Context): CemailDatabase =
            Room.databaseBuilder(context, CemailDatabase::class.java, "cemail.db").addMigrations(MIGRATION_3_4).build()
    }
}

/** 0.4.0 fixed how message text is decoded: forget the cached bodies so the next sync downloads them again. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE messages SET bodyHtml = NULL")
    }
}
