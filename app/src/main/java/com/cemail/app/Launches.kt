package com.cemail.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cemail.app.data.mail.MailBox
import kotlinx.coroutines.flow.MutableStateFlow

/** What the app was asked to do when it was opened from outside: the launcher menu, a widget or a notification. */
sealed interface LaunchRequest {
    /** A new message, from [accountId] or from the account on screen. */
    data class Compose(val accountId: String? = null) : LaunchRequest
    data class OpenBox(val box: MailBox, val accountId: String? = null) : LaunchRequest
    data object Search : LaunchRequest
    data class OpenMail(val accountId: String, val localId: Long) : LaunchRequest

    /** "Snooze" on a notification: the snooze choices for those messages, in the app. */
    data class Snooze(val accountId: String, val localIds: List<Long>) : LaunchRequest

    /** A message started by another app: a mailto: link, "Send email" or "Share" (with files to attach). */
    data class Write(
        val to: String = "",
        val cc: String = "",
        val bcc: String = "",
        val subject: String = "",
        val body: String = "",
        val files: List<Uri> = emptyList(),
    ) : LaunchRequest
}

/**
 * Turns intents into [LaunchRequest]s and back. The launcher shortcuts and the widgets both build their intents
 * here, and `MailApp` carries them out once the right account and mailbox are on screen.
 */
object Launches {
    const val ACTION_COMPOSE = "com.cemail.app.action.COMPOSE"
    const val ACTION_OPEN_BOX = "com.cemail.app.action.OPEN_BOX"
    const val ACTION_SEARCH = "com.cemail.app.action.SEARCH"
    const val ACTION_OPEN_MAIL = "com.cemail.app.action.OPEN_MAIL"
    const val ACTION_SNOOZE = "com.cemail.app.action.SNOOZE"
    private const val EXTRA_IDS = "ids"
    private const val EXTRA_ACCOUNT = "account"
    private const val EXTRA_BOX = "box"
    private const val EXTRA_MAIL = "mail"

    val pending = MutableStateFlow<LaunchRequest?>(null)

    fun handle(intent: Intent?) {
        val request = when (intent?.action) {
            ACTION_COMPOSE -> LaunchRequest.Compose(intent.getStringExtra(EXTRA_ACCOUNT))
            ACTION_OPEN_BOX -> LaunchRequest.OpenBox(
                MailBox.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BOX) } ?: MailBox.INBOX,
                intent.getStringExtra(EXTRA_ACCOUNT),
            )
            ACTION_SEARCH -> LaunchRequest.Search
            ACTION_OPEN_MAIL -> {
                val account = intent.getStringExtra(EXTRA_ACCOUNT)
                val mail = intent.getLongExtra(EXTRA_MAIL, -1L)
                if (account != null && mail >= 0) LaunchRequest.OpenMail(account, mail) else null
            }
            ACTION_SNOOZE -> {
                val account = intent.getStringExtra(EXTRA_ACCOUNT)
                val ids = intent.getLongArrayExtra(EXTRA_IDS)?.toList().orEmpty()
                if (account != null && ids.isNotEmpty()) LaunchRequest.Snooze(account, ids) else null
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> write(intent)
            else -> null
        } ?: return
        pending.value = request
    }

    /**
     * Reads what another app wants written: the mailto: address (with its subject, body, cc and bcc) and the usual
     * extras of "Send email" and "Share". Only files another app shares (content://) are taken, never paths into
     * Cemail's own files, so no app can slip one of Cemail's private files into a message.
     */
    private fun write(intent: Intent): LaunchRequest.Write? {
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data?.scheme != "mailto") return null
        val mailto = data?.takeIf { it.scheme == "mailto" }?.let { runCatching { android.net.MailTo.parse(it.toString()) }.getOrNull() }
        fun join(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(", ")
        val files = buildList {
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::addAll)
            } else {
                androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
            }
        }.filter { it.scheme == "content" && it.authority?.startsWith(BuildConfig.APPLICATION_ID) != true }
        return LaunchRequest.Write(
            to = join(mailto?.to, intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.joinToString(", ")),
            cc = join(mailto?.cc, intent.getStringArrayExtra(Intent.EXTRA_CC)?.joinToString(", ")),
            bcc = join(mailto?.headers?.get("bcc"), intent.getStringArrayExtra(Intent.EXTRA_BCC)?.joinToString(", ")),
            subject = mailto?.subject?.takeIf { it.isNotBlank() } ?: intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(),
            body = mailto?.body?.takeIf { it.isNotBlank() } ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),
            files = files,
        )
    }

    /** The intent that makes the app carry out [request] (each one is distinct, so widgets can tell them apart). */
    fun intent(context: Context, request: LaunchRequest): Intent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return when (request) {
            is LaunchRequest.Compose -> intent.setAction(ACTION_COMPOSE).putExtra(EXTRA_ACCOUNT, request.accountId)
                .setData(Uri.parse("cemail://compose/${request.accountId.orEmpty()}"))
            is LaunchRequest.OpenBox -> intent.setAction(ACTION_OPEN_BOX).putExtra(EXTRA_BOX, request.box.name)
                .putExtra(EXTRA_ACCOUNT, request.accountId)
                .setData(Uri.parse("cemail://box/${request.box.name}/${request.accountId.orEmpty()}"))
            LaunchRequest.Search -> intent.setAction(ACTION_SEARCH).setData(Uri.parse("cemail://search"))
            is LaunchRequest.OpenMail -> intent.setAction(ACTION_OPEN_MAIL).putExtra(EXTRA_ACCOUNT, request.accountId)
                .putExtra(EXTRA_MAIL, request.localId).setData(Uri.parse("cemail://mail/${request.localId}"))
            is LaunchRequest.Snooze -> intent.setAction(ACTION_SNOOZE).putExtra(EXTRA_ACCOUNT, request.accountId)
                .putExtra(EXTRA_IDS, request.localIds.toLongArray())
                .setData(Uri.parse("cemail://snooze/${request.accountId}/${request.localIds.joinToString("-")}"))
            // Only ever built by other apps; kept so this function covers every request.
            is LaunchRequest.Write -> intent.setAction(Intent.ACTION_SENDTO)
                .setData(Uri.parse("mailto:" + Uri.encode(request.to)))
        }
    }
}
