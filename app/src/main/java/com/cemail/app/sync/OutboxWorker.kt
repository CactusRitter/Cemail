package com.cemail.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cemail.app.AppLog
import com.cemail.app.CemailApplication
import java.util.concurrent.TimeUnit

/**
 * Sends the messages of the Outbox (scheduled, or waiting out their "undo send" seconds) when their time comes.
 * One work request follows the earliest pending message; when it has run it plans the next one. It needs a connection
 * (Android waits for it); a message that still fails is retried by `deliverOutbox` and ends up in Drafts if it never goes.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CemailApplication
        val dao = app.database.messages()
        val due = dao.dueOutbox(System.currentTimeMillis())
        if (due.isNotEmpty()) AppLog.i("outbox", "${due.size} message(s) due")
        for (row in due) {
            app.mailRepositoryFor(row.accountId).deliver(row.localId)
        }
        schedule(applicationContext, dao.nextSendOnce())
        return Result.success()
    }

    companion object {
        private const val NAME = "outbox-send"

        /** Plans the next delivery for [at] (the earliest message of the Outbox); null cancels it. */
        fun schedule(context: Context, at: Long?) {
            val manager = WorkManager.getInstance(context)
            if (at == null) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            manager.enqueueUniqueWork(
                NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<OutboxWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
