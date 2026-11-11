package com.cemail.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cemail.app.AppLog
import com.cemail.app.CemailApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Looks for new mail in every account while the app is closed, as often as the user chose in Settings
 * (15 minutes is the shortest period Android allows) and only when there is a connection.
 *
 * New unread Inbox messages found here are announced by `CemailApplication.notifyNewMail` (see MailNotifier).
 * Instant delivery ("real" push) needs a server that Gmail can call; see docs/ROADMAP.md.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CemailApplication
        val accounts = app.accountRepository.accounts.first()
        AppLog.i("worker", "background check, ${accounts.size} account(s)")
        // Each account has its own pace (Settings → Accounts): it is checked only when its own interval has passed.
        val now = System.currentTimeMillis()
        val due = accounts.filter { account ->
            val minutes = app.settings.state.value.syncMinutesFor(account.id)
            minutes > 0 && now - app.settings.lastBackgroundCheck(account.id) >= minutes * 60_000L - SLACK_MILLIS
        }
        val ok = due.map { account ->
            app.mailRepositoryFor(account.id).refresh(quick = true).also { done -> if (done) app.settings.setLastBackgroundCheck(account.id, now) }
        }.all { it }
        AppLog.i("worker", "background check ${if (ok) "ok" else "failed, will retry"}")
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "background-sync"
        /** The worker wakes a little early or late: an account due within this margin is checked now. */
        private const val SLACK_MILLIS = 2 * 60_000L

        /** The choices offered in Settings, in minutes; [MANUAL] means "never check on its own". */
        const val MANUAL = 0
        val INTERVALS = listOf(15, 30, 60, 120, MANUAL)

        /** The wake-up period that serves every account: the shortest of their intervals; 0 when all are manual. */
        fun periodFor(settings: com.cemail.app.data.AppSettings, accountIds: List<String>): Int =
            (if (accountIds.isEmpty()) listOf(settings.syncMinutes) else accountIds.map { settings.syncMinutesFor(it) }).filter { it > 0 }.minOrNull() ?: MANUAL

        /** Applies the chosen interval: replaces the scheduled check, or cancels it for [MANUAL]. */
        fun schedule(context: Context, minutes: Int) {
            val manager = WorkManager.getInstance(context)
            if (minutes <= 0) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
