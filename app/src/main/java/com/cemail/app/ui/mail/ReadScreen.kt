package com.cemail.app.ui.mail

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import com.cemail.app.R
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.mail.Mail
import com.cemail.app.ui.glass.GlassIconButton
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons

@Composable
fun ReadScreen(summary: Mail, vm: MailViewModel, alwaysShowImages: Boolean) {
    // The list only holds the start of each text: the whole message is read when it is opened. Flags
    // (read, star...) keep coming from the list so they stay current.
    val loaded by produceState<Mail?>(null, summary.id) { value = vm.full(summary.id) }
    val mail = loaded?.copy(
        read = summary.read, starred = summary.starred, pinned = summary.pinned, folder = summary.folder,
        snoozedUntil = summary.snoozedUntil, labelIds = summary.labelIds, senderName = summary.senderName,
    ) ?: summary
    // Attached files are listed from Gmail (needs a connection) and only for messages that have some.
    val attachments by produceState(emptyList<com.cemail.app.data.gmail.AttachmentInfo>(), summary.id) {
        value = if (summary.hasAttachment) vm.attachments(summary.id) else emptyList()
    }
    val context = LocalContext.current
    val one = setOf(mail.id)
    val quoteWhen = DateUtils.formatDateTime(
        context, mail.timeMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    fun reply(prefixRes: Int, to: String): Draft {
        val quoted = mail.body.lines().joinToString("\n") { "> $it" }
        return Draft(
            to = to,
            subject = context.getString(prefixRes, mail.subject),
            body = "\n\n" + context.getString(R.string.quote_header, quoteWhen, mail.senderName) + "\n" + quoted,
        )
    }

    // The sanitized page, computed once per message. Messages without an HTML part show their text.
    val html = mail.bodyHtml.orEmpty()
    // Pictures that travel inside the message (cid:) are not "remote": they are fetched from Gmail once and always shown.
    val inline by produceState(emptyMap<String, String>(), mail.id, html) {
        value = if (HtmlSanitizer.hasInlineImages(html)) vm.inlineImages(mail.id) else emptyMap()
    }
    val hasRemoteImages = remember(mail.id, html) { html.isNotBlank() && HtmlSanitizer.hasRemoteImages(html) }
    var showImages by remember(mail.id, alwaysShowImages) { mutableStateOf(alwaysShowImages) }

    // The header's details (sender, date...) slide away exactly as much as the message has been scrolled,
    // until only the subject is left. The body keeps a fixed height meanwhile (its lower edge is simply
    // hidden while the details are showing), so scrolling never changes how far one can scroll.
    var scrollPx by remember(mail.id) { mutableIntStateOf(0) }
    val textScroll = rememberScrollState()
    var detailsPx by remember(mail.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val detailsDp = with(density) { detailsPx.toDp() }
    val hiddenPx = (if (html.isNotBlank()) scrollPx else textScroll.value).coerceIn(0, detailsPx)
    val progress = if (detailsPx > 0) hiddenPx / detailsPx.toFloat() else 0f
    // While the details go away the subject shrinks (it is scaled, so its lines never re-wrap) and the
    // sender's name fades in under it, in soft grey.
    var subjectPx by remember(mail.id) { mutableIntStateOf(0) }
    var namePx by remember(mail.id) { mutableIntStateOf(0) }
    val subjectScale = 1f - (1f - SUBJECT_MIN_SCALE) * progress
    val subjectShownPx = (subjectPx * subjectScale).roundToInt()
    val subjectMinPx = (subjectPx * SUBJECT_MIN_SCALE).roundToInt()
    val nameShownPx = (namePx * progress).roundToInt()
    // Extra room at the end of the message, so its last lines can always be scrolled into view.
    val bottomSpace = (detailsDp.value + with(density) { (subjectPx - subjectMinPx).toDp() }.value).roundToInt()
    val cleanPage = remember(mail.id, html, inline, bottomSpace) {
        if (html.isBlank()) null else HtmlSanitizer.page(HtmlSanitizer.clean(html, inline), bottomSpace, HtmlSanitizer.styles(html))
    }
    val fixedBody = Modifier.layout { measurable, constraints ->
        // The height the body has once the header is fully collapsed.
        val height = (constraints.maxHeight + (detailsPx - hiddenPx) + (subjectShownPx - subjectMinPx) + (nameShownPx - namePx))
            .coerceAtLeast(constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.dp, end = 12.dp, top = 88.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassKind.Card)
                    .padding(16.dp),
            ) {
                // Sender, date, notices and attachments: they slide away as the message is scrolled.
                Column(
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, (placeable.height - hiddenPx).coerceAtLeast(0)) { placeable.place(0, 0) }
                        }
                        .graphicsLayer { alpha = 1f - progress }
                        .onSizeChanged { detailsPx = it.height }
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(mail.senderName)
                    Column(Modifier.weight(1f)) {
                        Text(mail.senderName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            mail.senderEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${stringResource(R.string.to_me)} · $quoteWhen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                mail.snoozedUntil?.takeIf { mail.isSnoozed(System.currentTimeMillis()) }?.let {
                    val whenText = DateUtils.formatDateTime(
                        context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                    )
                    Text(
                        stringResource(R.string.snoozed_until, whenText),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (hasRemoteImages && !showImages) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.images_blocked),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.show_images),
                            Modifier.clickable(role = Role.Button) { showImages = true }.padding(8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (attachments.isNotEmpty()) AttachmentRow(mail.id, attachments, vm)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                if (cleanPage != null) {
                    HtmlBody(cleanPage, showImages, fixedBody.fillMaxSize()) { scrollPx = it }
                } else {
                    Box(
                        fixedBody.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card)
                            .verticalScroll(textScroll).padding(16.dp),
                    ) {
                        Text(mail.body, Modifier.padding(bottom = detailsDp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Top: one floating pill with the back button, the subject over the sender, and the menu button.
        var panel by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassPill(Modifier.fillMaxWidth(), height = 64.dp) {
                PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), vm::closeMail)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        mail.subject.ifBlank { stringResource(R.string.no_subject) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        mail.senderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PillIcon(Icons.Filled.MoreVert, stringResource(R.string.action_more), { panel = true })
            }
        }

        // The floating button is reply.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .size(60.dp)
                .glass(CircleShape, GlassKind.Pinned, elevation = 10.dp)
                .clickable(role = Role.Button) { vm.newDraft(reply(R.string.reply_prefix, mail.senderEmail)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(CemailIcons.Reply, stringResource(R.string.action_reply), tint = MaterialTheme.colorScheme.primary)
        }

        if (panel) {
            ActionPanel(
                onDismiss = { panel = false },
                reply = listOf(
                    PanelItem(CemailIcons.Reply, R.string.action_reply) { vm.newDraft(reply(R.string.reply_prefix, mail.senderEmail)) },
                    PanelItem(CemailIcons.ReplyAll, R.string.action_reply_all) { vm.newDraft(reply(R.string.reply_prefix, mail.senderEmail)) },
                    PanelItem(CemailIcons.Forward, R.string.action_forward) { vm.newDraft(reply(R.string.forward_prefix, "")) },
                ),
                organize = listOf(
                    PanelItem(CemailIcons.Archive, R.string.action_archive) { vm.archive(one) },
                    PanelItem(CemailIcons.Snooze, R.string.action_snooze) { vm.askSnooze(one) },
                    PanelItem(
                        if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                        if (mail.starred) R.string.action_unstar else R.string.action_star, selected = mail.starred,
                    ) { vm.toggleStar(one) },
                    PanelItem(CemailIcons.Pin, if (mail.pinned) R.string.action_unpin else R.string.action_pin, selected = mail.pinned) { vm.togglePin(one) },
                    PanelItem(Icons.Filled.Email, R.string.action_mark_unread) { vm.markRead(one, false) },
                    PanelItem(CemailIcons.Inbox, R.string.action_move_inbox) { vm.moveToInbox(one) },
                ),
                trash = PanelItem(Icons.Filled.Delete, R.string.action_trash, danger = true) { vm.trash(one) },
            )
        }
    }
}

private class PanelItem(
    val icon: ImageVector,
    val label: Int,
    val selected: Boolean = false,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Every action on the open message, in a glass panel just above the buttons: all the tiles are the same size,
 * in three columns, with a name under each icon, grouped into "Reply" and "Organize"; trashing sits alone at the
 * bottom. Tapping outside closes it.
 */
@Composable
private fun ActionPanel(onDismiss: () -> Unit, reply: List<PanelItem>, organize: List<PanelItem>, trash: PanelItem) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 96.dp, end = 12.dp, start = 12.dp)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                // Swallows taps so only the area around the panel closes it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PanelTitle(R.string.panel_group_reply)
            PanelRows(reply, onDismiss)
            PanelTitle(R.string.panel_group_organize)
            PanelRows(organize, onDismiss)
            PanelRows(listOf(trash), onDismiss, columns = 1)
        }
    }
}

@Composable
private fun PanelTitle(title: Int) {
    Text(
        stringResource(title),
        Modifier.padding(start = 6.dp, top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Tiles of one fixed size ([columns] per row), so every icon and label lines up. */
@Composable
private fun PanelRows(items: List<PanelItem>, onDismiss: () -> Unit, columns: Int = 3) {
    items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { item ->
                val tint = when {
                    item.danger -> MaterialTheme.colorScheme.error
                    item.selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    Modifier
                        .weight(1f)
                        .height(PANEL_TILE_HEIGHT)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (item.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) { onDismiss(); item.onClick() }
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, null, tint = tint)
                    Text(
                        stringResource(item.label),
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            repeat(columns - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

private val PANEL_TILE_HEIGHT = 84.dp

/**
 * Shows sanitized email HTML. JavaScript is off, files and content providers are unreachable, remote images
 * stay blocked until [showImages], and links open in the browser instead of inside the message view.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(page: String, showImages: Boolean, modifier: Modifier, onScroll: (Int) -> Unit) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // Emails are written for a white page, whatever the app theme is.
                setBackgroundColor(android.graphics.Color.WHITE)
                setOnScrollChangeListener { _, _, y, _, _ -> currentOnScroll(y) }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri: Uri = request.url
                        if (uri.scheme in setOf("http", "https", "mailto", "tel")) {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                // no app can open it
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { web ->
            web.settings.blockNetworkImage = !showImages
            val key = "$showImages|$page"
            if (web.tag != key) {
                web.tag = key
                web.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}



/** How small the subject gets once the header is collapsed (1 = its full size). */
private const val SUBJECT_MIN_SCALE = 0.72f
