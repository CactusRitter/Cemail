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

    suspend fun add(result: SignInResult, provider: String = "GMAIL") {
        // Google only returns a refresh token the first time; keep the old one if this sign-in has none.
        val merged = if (result.tokens.refreshToken == null) {
            result.tokens.copy(refreshToken = tokens.load(result.accountId)?.refreshToken)
        } else {
            result.tokens
        }
        tokens.save(result.accountId, merged)
        dao.upsert(AccountEntity(result.accountId, result.email, result.name, System.currentTimeMillis(), provider))
        com.cemail.app.AppLog.i("account", "added ...${result.accountId.takeLast(4)} (refresh token: ${merged.refreshToken != null})")
    }

    suspend fun remove(accountId: String) {
        com.cemail.app.AppLog.i("account", "removed ...${accountId.takeLast(4)}")
        tokens.clear(accountId)
        messages.deleteAccount(accountId)
        messages.forgetKept(accountId)
        java.io.File(inlineRoot, accountId).deleteRecursively()
        // Its picture (see AccountPhotos) goes too.
        inlineRoot.parentFile?.let { java.io.File(it, "avatars/${accountId.filter { c -> c.isLetterOrDigit() }}.jpg").delete() }
        dao.delete(accountId)
    }

    /** Adds (or updates) an IMAP account; its settings and password are kept encrypted. */
    suspend fun addImap(config: com.cemail.app.data.imap.ImapConfig) {
        val id = "imap:" + config.email.lowercase()
        tokens.saveSecret(id, config.toJson())
        dao.upsert(AccountEntity(id, config.email, config.displayName, System.currentTimeMillis(), "IMAP"))
        com.cemail.app.AppLog.i("account", "added IMAP account ...${id.takeLast(4)}")
    }

    fun imapConfig(accountId: String): com.cemail.app.data.imap.ImapConfig? =
        tokens.loadSecret(accountId)?.let(com.cemail.app.data.imap.ImapConfig::fromJson)

    suspend fun providerOf(accountId: String): String = dao.providerOf(accountId) ?: "GMAIL"
}
