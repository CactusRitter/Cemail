package com.cemail.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cemail.app.CemailApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Looks for new mail in every account while the app is closed. Android runs it about every 15 minutes
 * (the shortest period it allows) when there is a connection.
 *
 * Phase 6 (notifications): this is where a notification will be posted for the messages that arrived.
 * Instant delivery ("real" push) needs a server that Gmail can call; see docs/ROADMAP.md.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CemailApplication
        val accounts = app.accountRepository.accounts.first()
        val ok = accounts.map { app.mailRepositoryFor(it.id).refresh(quick = true) }.all { it }
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "background-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
