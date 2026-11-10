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
        val ok = accounts.map { app.mailRepositoryFor(it.id).refresh(quick = true) }.all { it }
        AppLog.i("worker", "background check ${if (ok) "ok" else "failed, will retry"}")
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "background-sync"

        /** The choices offered in Settings, in minutes; [MANUAL] means "never check on its own". */
        const val MANUAL = 0
        val INTERVALS = listOf(15, 30, 60, 120, MANUAL)

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
