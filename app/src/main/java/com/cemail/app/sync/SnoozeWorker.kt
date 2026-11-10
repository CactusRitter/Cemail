package com.cemail.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cemail.app.AppLog
import com.cemail.app.CemailApplication
import com.cemail.app.data.mail.toMail
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Brings snoozed messages back at the time the user chose: they return to the Inbox as unread and a notification
 * says so. One work request is kept for the earliest pending snooze; when it runs it schedules the next one.
 */
class SnoozeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CemailApplication
        val dao = app.database.messages()
        val due = dao.dueSnoozed(System.currentTimeMillis())
        if (due.isNotEmpty()) {
            dao.wake(due.map { it.localId })
            AppLog.i("snooze", "${due.size} snoozed message(s) are back in the Inbox")
            if (app.notifier.allowed() && app.settings.state.value.notifications) {
                val accounts = app.accountRepository.accounts.first()
                for ((accountId, group) in due.groupBy { it.accountId }) {
                    val label = accounts.firstOrNull { it.id == accountId }?.email?.takeIf { accounts.size > 1 }
                    app.notifier.showSnoozed(label, group.map { it.toMail() })
                }
            }
        }
        // Whatever is still snoozed gets its own wake-up.
        schedule(applicationContext, dao.nextSnoozeOnce())
        return Result.success()
    }

    companion object {
        private const val NAME = "snooze-wake"

        /** Plans the wake-up for [at] (the earliest snooze), replacing any earlier plan; null cancels it. */
        fun schedule(context: Context, at: Long?) {
            val manager = WorkManager.getInstance(context)
            if (at == null) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0)
            manager.enqueueUniqueWork(
                NAME, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SnoozeWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        }
    }
}
