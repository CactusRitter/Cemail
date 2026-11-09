package com.cemail.app

import android.app.Application
import com.cemail.app.data.SettingsRepository
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

    /** Demo store until Gmail sync + Room arrive (phases 1-2). */
    val mailRepository: MailRepository by lazy { DemoMailRepository(DemoData.seed(this)) }

    /** Lives as long as the process: work started while leaving a screen always finishes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
