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
import androidx.compose.runtime.mutableStateOf
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
fun ReadScreen(mail: Mail, vm: MailViewModel) {
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
    val cleanPage = remember(mail.id, html) { if (html.isBlank()) null else HtmlSanitizer.page(HtmlSanitizer.clean(html)) }
    val hasRemoteImages = remember(mail.id, html) { html.isNotBlank() && HtmlSanitizer.hasRemoteImages(html) }
    var showImages by remember(mail.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(mail.subject.ifBlank { stringResource(R.string.no_subject) }, style = MaterialTheme.typography.titleLarge)
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
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                if (cleanPage != null) {
                    HtmlBody(cleanPage, showImages, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(mail.body, style = MaterialTheme.typography.bodyLarge)
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
private fun HtmlBody(page: String, showImages: Boolean, modifier: Modifier) {
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
