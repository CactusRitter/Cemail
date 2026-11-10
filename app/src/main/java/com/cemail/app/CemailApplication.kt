package com.cemail.app

import android.app.Application
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.auth.AccountRepository
import com.cemail.app.data.auth.GoogleAuth
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
    val accountRepository: AccountRepository by lazy { AccountRepository(database.accounts(), TokenStore(this)) }

    /** Demo store until Gmail sync + Room arrive (phases 1-2). */
    val mailRepository: MailRepository by lazy { DemoMailRepository(DemoData.seed(this)) }

    /** Lives as long as the process: work started while leaving a screen always finishes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
