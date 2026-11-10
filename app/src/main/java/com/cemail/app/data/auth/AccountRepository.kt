package com.cemail.app.data.auth

import com.cemail.app.data.db.AccountDao
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.data.db.MessageDao
import kotlinx.coroutines.flow.Flow

/** Signed-in accounts (Room) and their tokens (encrypted). The UI only talks to this class. */
class AccountRepository(
    private val dao: AccountDao,
    private val messages: MessageDao,
    private val tokens: TokenStore,
    /** Root folder of the embedded pictures kept on the phone (one sub-folder per account). */
    private val inlineRoot: java.io.File,
) {
    val accounts: Flow<List<AccountEntity>> = dao.observeAll()

    suspend fun add(result: SignInResult) {
        // Google only returns a refresh token the first time; keep the old one if this sign-in has none.
        val merged = if (result.tokens.refreshToken == null) {
            result.tokens.copy(refreshToken = tokens.load(result.accountId)?.refreshToken)
        } else {
            result.tokens
        }
        tokens.save(result.accountId, merged)
        dao.upsert(AccountEntity(result.accountId, result.email, result.name, System.currentTimeMillis()))
        com.cemail.app.AppLog.i("account", "added ...${result.accountId.takeLast(4)} (refresh token: ${merged.refreshToken != null})")
    }

    suspend fun remove(accountId: String) {
        com.cemail.app.AppLog.i("account", "removed ...${accountId.takeLast(4)}")
        tokens.clear(accountId)
        messages.deleteAccount(accountId)
        java.io.File(inlineRoot, accountId).deleteRecursively()
        dao.delete(accountId)
    }

    fun tokensOf(accountId: String): Tokens? = tokens.load(accountId)
}
