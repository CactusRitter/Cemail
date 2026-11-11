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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import com.cemail.app.widget.updateWidgets
import kotlinx.coroutines.launch
import com.cemail.app.notify.MailNotifier

/**
 * Manual dependency container, like Cenotes: each piece is created lazily on first use.
 * (No Hilt: the "Ce" apps keep dependencies simple.)
 */
class CemailApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val database: CemailDatabase by lazy { CemailDatabase.create(this) }
    val googleAuth: GoogleAuth by lazy { GoogleAuth(this) }
    val microsoftAuth: com.cemail.app.data.auth.MicrosoftAuth by lazy { com.cemail.app.data.auth.MicrosoftAuth(this) }
    val tokenStore: TokenStore by lazy { TokenStore(this) }
    val accountRepository: AccountRepository by lazy { AccountRepository(database.accounts(), database.messages(), tokenStore, java.io.File(filesDir, "inline")) }

    private val repositories = HashMap<String, MailRepository>()

    /** The cached Gmail account with this id (created once, lives as long as the process). */
    fun mailRepositoryFor(accountId: String): MailRepository = synchronized(repositories) {
        repositories.getOrPut(accountId) {
            val provider = kotlinx.coroutines.runBlocking(Dispatchers.IO) { accountRepository.providerOf(accountId) }
            if (provider == "MICROSOFT") {
                return@getOrPut com.cemail.app.data.graph.MicrosoftMailRepository(
                    accountId,
                    com.cemail.app.data.graph.GraphApi(AccountTokenProvider(accountId, tokenStore) { microsoftAuth.freshAccessToken(it) }),
                    database.messages(), appScope,
                    java.io.File(cacheDir, "attachments/${accountId.filter { it.isLetterOrDigit() }}"),
                    selfEmail = { accountRepository.accounts.first().firstOrNull { it.id == accountId }?.email },
                    onNewMail = ::notifyNewMail,
                )
            }
            if (provider == "IMAP") {
                val config = accountRepository.imapConfig(accountId)
                    ?: com.cemail.app.data.imap.ImapConfig("", "", "", "invalid.invalid", smtpHost = "invalid.invalid")
                return@getOrPut com.cemail.app.data.imap.ImapMailRepository(
                    accountId, com.cemail.app.data.imap.ImapClient(config), database.messages(), appScope,
                    java.io.File(cacheDir, "attachments/${accountId.filter { it.isLetterOrDigit() }}"),
                    selfEmail = { accountRepository.accounts.first().firstOrNull { it.id == accountId }?.email },
                    onNewMail = ::notifyNewMail,
                    marks = com.cemail.app.data.mail.SyncMarks.Prefs(this, accountId),
                )
            }
            RoomMailRepository(
                accountId, database.messages(), database.labels(),
                HttpGmailApi(AccountTokenProvider(accountId, tokenStore) { googleAuth.freshAccessToken(it) }), appScope,
                java.io.File(filesDir, "inline/$accountId"),
                java.io.File(cacheDir, "attachments/${accountId.filter { it.isLetterOrDigit() }}"),
                selfEmail = { accountRepository.accounts.first().firstOrNull { it.id == accountId }?.email },
                onNewMail = ::notifyNewMail,
                marks = com.cemail.app.data.mail.SyncMarks.Prefs(this, accountId),
                hidden = settings.state.map { it.hiddenLabels[accountId].orEmpty() }.distinctUntilChanged()
                    .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, settings.state.value.hiddenLabels[accountId].orEmpty()),
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


    /** With several accounts the launcher menu also offers "Write from <account>" for each (up to 3). */
    private fun updateShortcuts(accounts: List<com.cemail.app.data.db.AccountEntity>) {
        val shortcuts = if (accounts.size < 2) {
            emptyList()
        } else {
            accounts.take(3).map { account ->
                androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "compose-${account.id}")
                    .setShortLabel(account.displayName ?: account.email.substringBefore('@'))
                    .setLongLabel(getString(R.string.shortcut_compose_from, account.email))
                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.drawable.ic_action_compose))
                    .setIntent(Launches.intent(this, LaunchRequest.Compose(account.id)))
                    .build()
            }
        }
        try {
            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
        } catch (e: Exception) {
            AppLog.e("shortcuts", "could not update the launcher shortcuts", e)
        }
    }
    val notifier: MailNotifier by lazy { MailNotifier(this) }

    /** Number of activities on screen: the app counts as open while it is above 0. */
    @Volatile private var visibleActivities = 0

    /** Notifies only when the user is not looking at the app (it already shows the new mail by itself). */
    private fun notifyNewMail(accountId: String, mails: List<com.cemail.app.data.mail.Mail>) {
        val skip = when {
            visibleActivities > 0 -> "app is open"
            !settings.state.value.notifications -> "notifications off"
            !notifier.allowed() -> "Android permission missing"
            else -> null
        }
        if (skip != null) {
            AppLog.i("notif", "${mails.size} new message(s) not announced: $skip")
            return
        }
        AppLog.i("notif", "announcing ${mails.size} new message(s)")
        appScope.launch {
            val accounts = accountRepository.accounts.first()
            val label = accounts.firstOrNull { it.id == accountId }?.email?.takeIf { accounts.size > 1 }
            notifier.show(accountId, label, mails)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        com.cemail.app.data.mail.Outbox.init(java.io.File(filesDir, "outbox"))
        CrashLog.install(this)
        notifier.createChannel()
        appScope.launch { accountRepository.accounts.collect { updateShortcuts(it) } }
        // The widgets redraw whenever the Inbox changes (a short pause first, so a sync counts as one change).
        appScope.launch {
            database.messages().inboxSignature().distinctUntilChanged().debounce(1_500).collect {
                try { updateWidgets(this@CemailApplication) } catch (e: Exception) { AppLog.e("widget", "could not update the widgets", e) }
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { visibleActivities++ }
            override fun onActivityStopped(activity: android.app.Activity) { visibleActivities-- }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        // The background check follows the shortest interval of any account (each account keeps its own pace inside the worker).
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.state, accountRepository.accounts) { s, accounts -> SyncWorker.periodFor(s, accounts.map { it.id }) }
                .distinctUntilChanged().collect { SyncWorker.schedule(this@CemailApplication, it) }
        }
        // The Outbox: the delivery always follows the earliest message waiting to be sent.
        appScope.launch {
            database.messages().nextSend().distinctUntilChanged().collect { com.cemail.app.sync.OutboxWorker.schedule(this@CemailApplication, it) }
        }
        // Snoozed messages come back on time: the wake-up always follows the earliest pending snooze.
        appScope.launch {
            database.messages().nextSnooze().distinctUntilChanged().collect { com.cemail.app.sync.SnoozeWorker.schedule(this@CemailApplication, it) }
        }
    }

    /** Lives as long as the process: work started while leaving a screen always finishes. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // Background work (sync, sending) must never close the app: the error is kept for Settings instead.
            CoroutineExceptionHandler { _, error -> CrashLog.write(this, error) },
    )
}
