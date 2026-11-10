package com.cemail.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cemail.app.MainActivity
import com.cemail.app.R
import com.cemail.app.data.mail.Mail

/**
 * Posts a notification for the mail that arrived while the app was not on screen: one per account, so
 * several accounts never hide each other. A single message shows its sender, subject and start of the text;
 * several show a list.
 */
class MailNotifier(private val context: Context) {

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.notif_channel_body)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** true if Android lets us show notifications (the runtime permission exists from Android 13). */
    fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** @param accountLabel the account's address, shown only when there is more than one account. */
    @SuppressLint("MissingPermission") // checked by allowed()
    fun show(accountId: String, accountLabel: String?, mails: List<Mail>) {
        if (mails.isEmpty() || !allowed()) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setWhen(mails.maxOf { it.timeMillis })
            .setSubText(accountLabel)
        val first = mails.first()
        if (mails.size == 1) {
            builder.setContentTitle(first.senderName)
                .setContentText(first.subject.ifBlank { context.getString(R.string.no_subject) })
                .setStyle(NotificationCompat.BigTextStyle().bigText(first.subject.ifBlank { context.getString(R.string.no_subject) } + "\n" + first.preview))
        } else {
            val title = context.resources.getQuantityString(R.plurals.notif_new_messages, mails.size, mails.size)
            val style = NotificationCompat.InboxStyle()
            mails.take(MAX_LINES).forEach { style.addLine("${it.senderName}  ${it.subject}") }
            if (mails.size > MAX_LINES) style.setSummaryText("+${mails.size - MAX_LINES}")
            builder.setContentTitle(title)
                .setContentText(mails.take(MAX_LINES).joinToString(", ") { it.senderName }.take(80))
                .setStyle(style)
        }
        NotificationManagerCompat.from(context).notify(accountId.hashCode(), builder.build())
    }

    private companion object {
        const val CHANNEL = "new_mail"
        const val MAX_LINES = 5
    }
}
