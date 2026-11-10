package com.cemail.app.data.auth

import com.cemail.app.data.db.AccountDao
import com.cemail.app.data.db.AccountEntity
import kotlinx.coroutines.flow.Flow

/** Signed-in accounts (Room) and their tokens (encrypted). The UI only talks to this class. */
class AccountRepository(private val dao: AccountDao, private val tokens: TokenStore) {
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
    }

    suspend fun remove(accountId: String) {
        tokens.clear(accountId)
        dao.delete(accountId) // phase 2: also deletes the account's cached messages
    }

    fun tokensOf(accountId: String): Tokens? = tokens.load(accountId)
}
