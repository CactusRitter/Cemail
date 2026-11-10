package com.cemail.app

import android.app.Application
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.auth.AccountRepository
import com.cemail.app.data.auth.AccountTokenProvider
import com.cemail.app.data.auth.GoogleAuth
import com.cemail.app.data.gmail.HttpGmailApi
import com.cemail.app.data.mail.RoomMailRepository
import com.cemail.app.data.mail.UnifiedMailRepository
import com.cemail.app.sync.SyncWorker
import com.cemail.app.data.auth.TokenStore
import com.cemail.app.data.db.CemailDatabase
import com.cemail.app.data.mail.DemoData
import com.cemail.app.data.mail.DemoMailRepository
import com.cemail.app.data.mail.MailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container, like Cenotes: each piece is created lazily on first use.
 * (No Hilt: the "Ce" apps keep dependencies simple.)
 */
class CemailApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val database: CemailDatabase by lazy { CemailDatabase.create(this) }
    val googleAuth: GoogleAuth by lazy { GoogleAuth(this) }
    val tokenStore: TokenStore by lazy { TokenStore(this) }
    val accountRepository: AccountRepository by lazy { AccountRepository(database.accounts(), database.messages(), tokenStore, java.io.File(filesDir, "inline")) }

    private val repositories = HashMap<String, MailRepository>()

    /** The cached Gmail account with this id (created once, lives as long as the process). */
    fun mailRepositoryFor(accountId: String): MailRepository = synchronized(repositories) {
        repositories.getOrPut(accountId) {
            RoomMailRepository(
                accountId, database.messages(), database.labels(),
                HttpGmailApi(AccountTokenProvider(accountId, tokenStore, googleAuth)), appScope,
                java.io.File(filesDir, "inline/$accountId"),
            )
        }
    }

    /** Demo store until Gmail sync + Room arrive (phases 1-2). */
    val demoRepository: MailRepository by lazy { DemoMailRepository(DemoData.seed(this), DemoData.labels(this)) }

    private val unified = HashMap<String, MailRepository>()

    /** All these accounts as one mailbox (the unified inbox). */
    fun unifiedRepository(accountIds: List<String>): MailRepository = synchronized(unified) {
        unified.getOrPut(accountIds.joinToString(" ")) { UnifiedMailRepository(accountIds.map(::mailRepositoryFor), appScope) }
    }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }

    /** Lives as long as the process: work started while leaving a screen always finishes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
