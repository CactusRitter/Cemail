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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import com.cemail.app.data.SwipeAction
import com.cemail.app.data.SwipeConfig
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.cemail.app.ui.glass.bumpOn
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.mail.Mail
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GroupPos
import com.cemail.app.ui.glass.glassSegment
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MailRow(
    mail: Mail,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleStar: () -> Unit,
    swipe: SwipeConfig,
    onSwipe: (SwipeAction) -> Unit,
    /** The message is in Archive, Trash or Spam: "archive" then means "back to the Inbox". */
    startIsArchive: Boolean,
    /** Colour of the account the message belongs to (unified inbox only). */
    stripe: Color? = null,
    /** false hides the round sender picture (Settings). */
    showCircle: Boolean = true,
    /** Place of the row inside the card its group shares. */
    pos: GroupPos = GroupPos.Single,
    /** true: the rows share a card, so an unread row is told apart by bold type only (no stronger fill). */
    inGroup: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipe(swipe.right)
                SwipeToDismissBoxValue.EndToStart -> onSwipe(swipe.left)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // the row springs back; if the message left the list it simply disappears
        },
        positionalThreshold = { total -> total * swipe.distance.fraction },
    )
    val skin = com.cemail.app.ui.theme.LocalSkin.current
    val radius = skin.radius(20.dp)
    val shape = pos.shape(radius)
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !selectionMode && swipe.right != SwipeAction.NONE,
        enableDismissFromEndToStart = !selectionMode && swipe.left != SwipeAction.NONE,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val colors = MaterialTheme.colorScheme
            val action = if (toEnd) swipe.right else swipe.left
            val danger = action == SwipeAction.TRASH || action == SwipeAction.SPAM
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (danger) colors.errorContainer else colors.primaryContainer, shape)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    swipeIcon(action, startIsArchive),
                    stringResource(swipeLabel(action, startIsArchive, mail.read, mail.starred)),
                    tint = if (danger) colors.onErrorContainer else colors.onPrimaryContainer,
                )
            }
        },
    ) {
        val kind = if (selected || (!mail.read && !inGroup)) GlassKind.Pinned else GlassKind.Card
        Row(
            Modifier
                .glassSegment(pos, 20.dp, kind)
                .then(if (stripe != null) Modifier.accountCorner(stripe, if (pos.hasTop) radius else 0.dp) else Modifier)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(Icons.Filled.Check, null, Modifier.padding(end = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        SenderTag(mail.senderName, mail.read, showCircle)
                    }
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
                        fontWeight = if (mail.read) FontWeight.Normal else FontWeight.Bold,
                        color = if (mail.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    mail.subject.ifBlank { stringResource(R.string.no_subject) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (mail.read) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mail.preview,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (mail.read) FontWeight.Normal else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                        stringResource(if (mail.starred) R.string.action_unstar else R.string.action_star),
                        Modifier.padding(start = 8.dp).size(22.dp).bumpOn(mail.starred).clickable(role = Role.Button, onClick = onToggleStar),
                        tint = if (mail.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The sender as a small label (unread: accent colour, read: neutral). With [asLabel] false it is plain text.
 * A long name is cut with an ellipsis so it never pushes the time away.
 */
@Composable
private fun SenderTag(name: String, read: Boolean, asLabel: Boolean) {
    val c = MaterialTheme.colorScheme
    if (!asLabel) {
        Text(
            name, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (read) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Text(
        name,
        Modifier
            .background(if (read) c.surfaceVariant.copy(alpha = 0.6f) else c.primary.copy(alpha = 0.16f), com.cemail.app.ui.theme.LocalSkin.current.shape(RoundedCornerShape(8.dp)))
            .padding(horizontal = 8.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (read) c.onSurfaceVariant else c.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Account colour in the card's top-start corner: a crescent that is thickest in the corner (about 4 dp) and
 * thins to nothing along the top (~110 dp) and down the side (~34 dp), over a soft glow of the same colour.
 * Used in the unified inbox only. Mirrored in right-to-left layouts.
 */
private fun Modifier.accountCorner(color: Color, corner: androidx.compose.ui.unit.Dp): Modifier = this.drawWithContent {
    drawContent()
    val d = density
    val rtl = layoutDirection == LayoutDirection.Rtl
    val r = corner.toPx()
    val rounded = r > 0f
    val crescent = if (!rounded) Path().apply {
        // Flat top edge (middle rows of a group): same visible size as the one hugging the rounded corner.
        moveTo(0f, 12f * d)
        lineTo(0f, 0f)
        lineTo(112f * d, 0f)
        cubicTo(72f * d, 0.5f * d, 40f * d, 2f * d, 22f * d, 5.5f * d)
        cubicTo(9f * d, 6f * d, 1.5f * d, 8f * d, 0f, 12f * d)
        close()
    } else Path().apply {
        moveTo(0f, 34f * d)
        lineTo(0f, r)
        if (r > 0f) arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(112f * d, 0f)
        cubicTo(72f * d, 0.5f * d, 40f * d, 2f * d, 22f * d, 5.5f * d)
        cubicTo(9f * d, 9f * d, 1.5f * d, 18f * d, 0f, 34f * d)
        close()
    }
    val fade = Brush.linearGradient(
        0f to color, 0.5f to color.copy(alpha = 0.6f), 1f to color.copy(alpha = 0f),
        start = Offset.Zero, end = Offset(120f * d, 18f * d),
    )
    val glow = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = Offset.Zero, radius = 110f * d)
    withTransform({ if (rtl) scale(-1f, 1f, pivot = Offset(size.width / 2f, 0f)) }) {
        drawRect(glow, size = Size(size.width, size.height))
        drawPath(crescent, fade)
    }
}


/** The icon shown behind a message while it is dragged (and in Settings). */
fun swipeIcon(action: SwipeAction, startIsArchive: Boolean = true) = when (action) {
    SwipeAction.ARCHIVE -> if (startIsArchive) CemailIcons.Archive else CemailIcons.Inbox
    SwipeAction.TRASH -> Icons.Filled.Delete
    SwipeAction.TOGGLE_READ -> CemailIcons.Mail
    SwipeAction.SPAM -> Icons.Filled.Warning
    SwipeAction.STAR -> Icons.Filled.Star
    SwipeAction.SNOOZE -> CemailIcons.Snooze
    SwipeAction.NONE -> Icons.Filled.Close
}

/** The name of an action; with the message at hand it says exactly what will happen ("Mark as unread"...). */
fun swipeLabel(action: SwipeAction, startIsArchive: Boolean = true, read: Boolean? = null, starred: Boolean? = null): Int = when (action) {
    SwipeAction.ARCHIVE -> if (startIsArchive) R.string.action_archive else R.string.action_move_inbox
    SwipeAction.TRASH -> R.string.action_trash
    SwipeAction.TOGGLE_READ -> if (read == true) R.string.action_mark_unread else R.string.action_mark_read
    SwipeAction.SPAM -> R.string.action_spam
    SwipeAction.STAR -> if (starred == true) R.string.action_unstar else R.string.action_star
    SwipeAction.SNOOZE -> R.string.action_snooze
    SwipeAction.NONE -> R.string.swipe_none
}

/** The short question asked before a swipe acts ("Archive?", "Delete?"...) when confirmation is on. */
fun swipeQuestion(action: SwipeAction, startIsArchive: Boolean = true, read: Boolean? = null, starred: Boolean? = null): Int = when (action) {
    SwipeAction.ARCHIVE -> if (startIsArchive) R.string.swipe_ask_archive else R.string.swipe_ask_inbox
    SwipeAction.TRASH -> R.string.swipe_ask_trash
    SwipeAction.TOGGLE_READ -> if (read == true) R.string.swipe_ask_unread else R.string.swipe_ask_read
    SwipeAction.SPAM -> R.string.swipe_ask_spam
    SwipeAction.STAR -> if (starred == true) R.string.swipe_ask_unstar else R.string.swipe_ask_star
    SwipeAction.SNOOZE -> R.string.swipe_ask_snooze
    SwipeAction.NONE -> R.string.swipe_none
}
