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
import com.cemail.app.R
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.mail.Mail
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
                .padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassKind.Card)
                    .padding(16.dp),
            ) {
                Text(
                    mail.subject.ifBlank { stringResource(R.string.no_subject) },
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = if (subjectPx == 0) placeable.height else subjectShownPx
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            scaleX = subjectScale
                            scaleY = subjectScale
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                        }
                        .onSizeChanged { subjectPx = it.height },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    mail.senderName,
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, nameShownPx) { placeable.place(0, 0) }
                        }
                        .graphicsLayer { alpha = progress }
                        .onSizeChanged { namePx = it.height },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Everything but the subject: its height and opacity follow the scroll.
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

        // Top actions
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            var more by remember { mutableStateOf(false) }
            GlassPill(Modifier.fillMaxWidth()) {
                PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), vm::closeMail)
                Box(Modifier.weight(1f))
                PillIcon(CemailIcons.Archive, stringResource(R.string.action_archive), { vm.archive(one) }, width = 44.dp)
                PillIcon(Icons.Filled.Delete, stringResource(R.string.action_trash), { vm.trash(one) }, width = 44.dp)
                PillIcon(CemailIcons.Snooze, stringResource(R.string.action_snooze), { vm.askSnooze(one) }, width = 44.dp)
                PillIcon(
                    if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                    stringResource(if (mail.starred) R.string.action_unstar else R.string.action_star),
                    { vm.toggleStar(one) },
                    selected = mail.starred,
                    width = 44.dp,
                )
                Box {
                    PillIcon(Icons.Filled.MoreVert, stringResource(R.string.action_more), { more = true }, width = 44.dp)
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (mail.pinned) R.string.action_unpin else R.string.action_pin)) },
                            leadingIcon = { Icon(CemailIcons.Pin, null) },
                            onClick = { more = false; vm.togglePin(one) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_mark_unread)) },
                            leadingIcon = { Icon(Icons.Filled.Email, null) },
                            onClick = { more = false; vm.markRead(one, false) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_inbox)) },
                            leadingIcon = { Icon(CemailIcons.Inbox, null) },
                            onClick = { more = false; vm.moveToInbox(one) },
                        )
                    }
                }
            }
        }

        // Reply bar: three equal actions that always fit on one line.
        Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            GlassPill(Modifier.fillMaxWidth()) {
                ReplyAction(CemailIcons.Reply, R.string.action_reply) { vm.newDraft(reply(R.string.reply_prefix, mail.senderEmail)) }
                ReplyAction(CemailIcons.ReplyAll, R.string.action_reply_all) { vm.newDraft(reply(R.string.reply_prefix, mail.senderEmail)) }
                ReplyAction(CemailIcons.Forward, R.string.action_forward) { vm.newDraft(reply(R.string.forward_prefix, "")) }
            }
        }
    }
}

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

@Composable
private fun androidx.compose.foundation.layout.RowScope.ReplyAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .weight(1f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}


/** How small the subject gets once the header is collapsed (1 = its full size). */
private const val SUBJECT_MIN_SCALE = 0.72f
