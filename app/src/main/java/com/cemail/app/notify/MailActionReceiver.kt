package com.cemail.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.cemail.app.AppLog
import com.cemail.app.CemailApplication
import com.cemail.app.data.mail.Folder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Handles the "Read" and "Delete" buttons of a new-mail notification, without opening the app. */
class MailActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CemailApplication
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT) ?: return
        val ids = intent.getLongArrayExtra(EXTRA_IDS)?.toSet() ?: return
        val action = intent.action ?: return
        NotificationManagerCompat.from(context).cancel(accountId.hashCode())
        if (accountId == TEST_ACCOUNT) return
        val pending = goAsync()
        app.appScope.launch {
            try {
                val repo = app.mailRepositoryFor(accountId)
                when (action) {
                    ACTION_READ -> repo.update(ids) { it.copy(read = true) }
                    ACTION_DELETE -> repo.update(ids) { it.copy(folder = Folder.TRASH, snoozedUntil = null) }
                }
                AppLog.i("notif", "$action on ${ids.size} message(s)")
                // The change is written and sent to Gmail in the background: give it a moment before the receiver ends.
                delay(4_000)
            } catch (e: Exception) {
                AppLog.e("notif", "$action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_READ = "com.cemail.app.action.READ"
        const val ACTION_DELETE = "com.cemail.app.action.DELETE"
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_IDS = "ids"
        const val TEST_ACCOUNT = "test"
    }
}
