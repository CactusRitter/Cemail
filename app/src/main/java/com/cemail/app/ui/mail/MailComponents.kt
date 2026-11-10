package com.cemail.app.ui.mail

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.mail.Mail
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.glassSpring
import com.cemail.app.ui.icons.CemailIcons

/** "14:32" for today, "Nov 9" otherwise: formatted by the platform for the current locale. */
@Composable
fun rememberTimeLabel(millis: Long): String {
    val context = LocalContext.current
    return if (DateUtils.isToday(millis)) {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/** Round avatar with the sender's initial, coloured from the theme (like Thunderbird's contact circles). */
@Composable
fun Avatar(name: String, selected: Boolean = false, size: Int = 44) {
    val c = MaterialTheme.colorScheme
    val palette = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.errorContainer to c.onErrorContainer,
    )
    val (bg, fg) = palette[(name.lowercase().hashCode() and 0x7fffffff) % palette.size]
    val fill by animateColorAsState(if (selected) c.primary else bg, glassSpring(), label = "avatarBg")
    Box(Modifier.size(size.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = c.onPrimary)
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                color = fg,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailRow(
    mail: Mail,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    onSwipeStart: () -> Unit,
    onSwipeEnd: () -> Unit,
    startIsArchive: Boolean,
    /** Colour of the account the message belongs to (unified inbox only). */
    stripe: Color? = null,
) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> onSwipeStart()
            SwipeToDismissBoxValue.EndToStart -> onSwipeEnd()
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false // the row springs back; if the message left the list it simply disappears
    })
    val shape = RoundedCornerShape(20.dp)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (toEnd) colors.primaryContainer else colors.errorContainer, shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (toEnd) {
                    Icon(
                        if (startIsArchive) CemailIcons.Archive else CemailIcons.Inbox,
                        stringResource(if (startIsArchive) R.string.action_archive else R.string.action_move_inbox),
                        tint = colors.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_trash), tint = colors.onErrorContainer)
                }
            }
        },
    ) {
        val kind = if (selected || !mail.read) GlassKind.Pinned else GlassKind.Card
        Row(
            Modifier
                .glass(shape, kind)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stripe != null) Box(Modifier.width(4.dp).height(44.dp).background(stripe, CircleShape))
            Box(Modifier.clickable(onClick = onLongClick)) { Avatar(mail.senderName, selected) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mail.senderName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (mail.read) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (mail.hasAttachment) {
                        Icon(
                            CemailIcons.Attachment, stringResource(R.string.attachment),
                            Modifier.padding(end = 6.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        rememberTimeLabel(mail.timeMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mail.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    mail.subject.ifBlank { stringResource(R.string.no_subject) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (mail.read) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        mail.preview,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                        stringResource(if (mail.starred) R.string.action_unstar else R.string.action_star),
                        Modifier.padding(start = 8.dp).size(22.dp).clickable(role = Role.Button, onClick = onToggleStar),
                        tint = if (mail.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Opaque-ish glass card used for dialogs and the undo bar. */
@Composable
fun Modifier.menuGlass(): Modifier = this.glass(RoundedCornerShape(24.dp), GlassKind.Menu, elevation = 12.dp)

