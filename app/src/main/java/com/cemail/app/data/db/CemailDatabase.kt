package com.cemail.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A signed-in Google account. [id] is Google's stable subject id (not the email, which can change). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val email: String,
    val displayName: String?,
    val addedAtMillis: Long,
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

@Database(entities = [AccountEntity::class], version = 1, exportSchema = true)
abstract class CemailDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao

    companion object {
        fun create(context: Context): CemailDatabase =
            Room.databaseBuilder(context, CemailDatabase::class.java, "cemail.db").build()
    }
}
