package com.cemail.app.ui.mail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ce.kit.ui.SoftButton
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.popIn
import com.cemail.app.R
import com.cemail.app.data.mail.Appointments
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Ics
import com.cemail.app.data.mail.IcsEvent
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.TextDates
import java.time.ZoneId
import java.util.Formatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An event of a message and the whole .ics file it came from (handed over to Celander); null if read from the text. */
class Invite(val event: IcsEvent, val ics: String?)

private const val CELANDER = "com.celander.app"
private const val CELANDER_ICS = "com.celander.app.extra.ICS"
private const val MAX_ICS_EXTRA = 100 * 1024
private const val SHOWN = 3

/**
 * The events of a message, once they have been fetched and read; empty meanwhile. Its .ics invitations, or, when it
 * brings none, the appointments written in its text.
 */
@Composable
fun rememberInvites(mail: Mail?, vm: MailViewModel): List<Invite> {
    val invites by produceState(emptyList<Invite>(), mail?.id) {
        value = emptyList()
        if (mail == null) return@produceState
        val texts = vm.calendarTexts(mail.id)
        value = if (texts.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                texts.flatMap { text -> Ics.parse(text).map { Invite(it, text) } }
                    .distinctBy { listOf(it.event.uid, it.event.title, it.event.startMillis, it.event.cancelled) }
            }
        } else {
            fromText(mail, vm).map { Invite(it, null) }
        }
    }
    return invites
}

private suspend fun fromText(mail: Mail, vm: MailViewModel): List<IcsEvent> {
    if (mail.folder == Folder.SPAM || mail.folder == Folder.TRASH) return emptyList()
    val events = withContext(Dispatchers.Default) {
        val text = Appointments.ownText(mail.body)
        val language = Appointments.language(text, Locale.getDefault().language)
        val found = TextDates.find(text, language, mail.timeMillis)
        Appointments.suggestions(text, mail.subject, mail.timeMillis, found, ZoneId.systemDefault())
    }
    // The server is only asked when there is something to suggest. If it cannot say, nothing is suggested.
    return if (events.isNotEmpty() && vm.isMailingList(mail.id) == false) events else emptyList()
}

/** The events a message invites to, in a glass card above its text. */
@Composable
fun InviteCard(invites: List<Invite>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val celander = remember { isInstalled(context, CELANDER) }
    Column(
        modifier
            .popIn(initialScale = 0.9f)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), GlassKind.Card)
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (invites.firstOrNull()?.ics == null) {
            Text(
                stringResource(R.string.invite_from_text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        invites.take(SHOWN).forEachIndexed { n, invite ->
            if (n > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            val event = invite.event
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (event.cancelled) {
                    Text(
                        stringResource(R.string.invite_cancelled),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    event.title.ifBlank { stringResource(R.string.no_subject) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenText = whenText(context, event)
                Text(
                    if (event.allDay) stringResource(R.string.invite_all_day, whenText) else whenText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (event.rrule != null) {
                    Text(
                        stringResource(R.string.invite_repeats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.location.isNotBlank()) {
                    Text(
                        event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!event.cancelled) {
                SoftButton(
                    stringResource(R.string.invite_add),
                    { start(context, insertIntent(invite), R.string.invite_no_app) },
                    icon = Icons.Filled.DateRange,
                )
            } else if (celander) {
                SoftButton(
                    stringResource(R.string.invite_open_celander),
                    { start(context, insertIntent(invite).setPackage(CELANDER), R.string.invite_celander_failed) },
                    icon = Icons.Filled.DateRange,
                )
            }
        }
        val more = invites.size - SHOWN
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.invite_more, more, more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Date and time in the phone's zone; whole days are dates only (and are stored as UTC days). */
private fun whenText(context: Context, event: IcsEvent): String {
    var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
    if (!event.allDay) flags = flags or DateUtils.FORMAT_SHOW_TIME
    val zone = if (event.allDay) "UTC" else java.util.TimeZone.getDefault().id
    val out = Formatter(StringBuilder(), Locale.getDefault())
    return DateUtils.formatDateRange(context, out, event.startMillis, event.endMillis, flags, zone).toString()
}

/** The standard "new event" request every calendar app understands; Celander also reads the whole file. */
private fun insertIntent(invite: Invite): Intent {
    val event = invite.event
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
        .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
    event.rrule?.let { intent.putExtra(CalendarContract.Events.RRULE, it) }
    invite.ics?.takeIf { it.toByteArray().size <= MAX_ICS_EXTRA }?.let { intent.putExtra(CELANDER_ICS, it) }
    return intent
}

private fun start(context: Context, intent: Intent, failure: Int) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    }
}

private fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
