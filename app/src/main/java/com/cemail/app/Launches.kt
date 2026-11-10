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
            else -> null
        } ?: return
        pending.value = request
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
        }
    }
}
