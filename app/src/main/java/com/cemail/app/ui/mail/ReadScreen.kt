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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import android.widget.Toast
import kotlinx.coroutines.launch
import com.cemail.app.R
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.mail.Folder
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val quoteWhen = DateUtils.formatDateTime(
        context, mail.timeMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    // Starts a reply, reply all or forward: quoted text and subject here, recipients and thread ids in the ViewModel.
    fun reply(all: Boolean = false, forward: Boolean = false) {
        val quoted = mail.body.lines().joinToString("\n") { "> $it" }
        val prefixRes = if (forward) R.string.forward_prefix else R.string.reply_prefix
        // "Re: Re: x" never happens: a subject that already starts with a reply/forward prefix is kept as it is.
        val subject = if (SUBJECT_PREFIX.containsMatchIn(mail.subject)) mail.subject else context.getString(prefixRes, mail.subject)
        vm.startReply(
            Draft(
                to = if (forward) "" else mail.senderEmail,
                subject = subject,
                body = "\n\n" + context.getString(R.string.quote_header, quoteWhen, mail.senderName) + "\n" + quoted,
            ),
            mail.id, all, forward,
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

    // Extra room at the end of the message, so its last lines can be scrolled clear of the reply button.
    val cleanPage = remember(mail.id, html, inline) {
        if (html.isBlank()) null else HtmlSanitizer.page(HtmlSanitizer.clean(html, inline), 76, HtmlSanitizer.styles(html))
    }
    val snoozedUntil = mail.snoozedUntil?.takeIf { mail.isSnoozed(System.currentTimeMillis()) }
    val notices = snoozedUntil != null || (hasRemoteImages && !showImages)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.dp, end = 12.dp, top = 88.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The sender and subject are in the pill above; this card only appears when there is something to
            // say or do before reading: a snooze reminder, blocked images, or attached files.
            if (notices) {
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    snoozedUntil?.let {
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
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                if (cleanPage != null) {
                    HtmlBody(cleanPage, showImages, Modifier.fillMaxSize()) { }
                } else {
                    Box(
                        Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card)
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        // The extra room at the end keeps the last lines clear of the reply button.
                        Text(mail.body, Modifier.padding(bottom = 72.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Top: one floating pill with the back button, the subject over the sender, and the menu button.
        var panel by remember { mutableStateOf(false) }
        var attachPanel by remember { mutableStateOf(false) }
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
                        "${mail.senderName} · $quoteWhen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (attachments.isNotEmpty()) {
                    Box {
                        PillIcon(CemailIcons.Attachment, stringResource(R.string.attachments_label), { attachPanel = true })
                        Text(
                            attachments.size.toString(),
                            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Bottom corner: the menu button over the reply button.
        Column(
            Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .glass(CircleShape, GlassKind.Chrome, elevation = 10.dp)
                    .clickable(role = Role.Button) { panel = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
            }
            Box(
                Modifier
                    .size(60.dp)
                    .glass(CircleShape, GlassKind.Pinned, elevation = 10.dp)
                    .clickable(role = Role.Button) { reply() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(CemailIcons.Reply, stringResource(R.string.action_reply), tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (attachPanel) AttachmentsPopup(mail.id, attachments, vm) { attachPanel = false }
        if (panel) {
            ActionGrid(
                onDismiss = { panel = false },
                items = listOf(
                    PanelItem(CemailIcons.Reply, R.string.action_reply) { reply() },
                    PanelItem(CemailIcons.ReplyAll, R.string.action_reply_all) { reply(all = true) },
                    PanelItem(CemailIcons.Forward, R.string.action_forward) { reply(forward = true) },
                    // The same slot: archive from the inbox, or bring the message back to it from anywhere else.
                    if (mail.folder == Folder.INBOX) {
                        PanelItem(CemailIcons.Archive, R.string.action_archive) { vm.archive(one) }
                    } else {
                        PanelItem(CemailIcons.Inbox, R.string.action_move_inbox) { vm.moveToInbox(one) }
                    },
                    PanelItem(CemailIcons.Snooze, R.string.action_snooze) { vm.askSnooze(one) },
                    PanelItem(
                        if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                        if (mail.starred) R.string.action_unstar else R.string.action_star, selected = mail.starred,
                    ) { vm.toggleStar(one) },
                    PanelItem(CemailIcons.Pin, if (mail.pinned) R.string.action_unpin else R.string.action_pin, selected = mail.pinned) { vm.togglePin(one) },
                    PanelItem(Icons.Filled.Email, R.string.action_mark_unread) { vm.markRead(one, false) },
                    PanelItem(CemailIcons.Download, R.string.action_save_eml) {
                        scope.launch {
                            val file = vm.emlFile(mail.id)
                            if (file == null) {
                                Toast.makeText(context, R.string.eml_failed, Toast.LENGTH_LONG).show()
                            } else {
                                shareFile(context, file, "message/rfc822")
                            }
                        }
                    },
                    PanelItem(Icons.Filled.Delete, R.string.action_trash, danger = true) { vm.trash(one) },
                ),
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
 * Every action on the open message in a small square glass window of icons (no text), 3 x 3, that opens above the
 * menu button: reply, reply all, forward / archive, snooze, star / pin, unread, trash. Tapping outside closes it.
 */
@Composable
private fun ActionGrid(onDismiss: () -> Unit, items: List<PanelItem>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 156.dp, end = 16.dp)
                // Swallows taps so only the area around the window closes it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { item ->
                        val tint = when {
                            item.danger -> MaterialTheme.colorScheme.error
                            item.selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (item.selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                )
                                .clickable(role = Role.Button) { onDismiss(); item.onClick() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, stringResource(item.label), tint = tint)
                        }
                    }
                }
            }
        }
    }
}

/** The attached files of the open message, in a small glass card that drops down from the paperclip. */
@Composable
private fun AttachmentsPopup(messageId: Long, attachments: List<com.cemail.app.data.gmail.AttachmentInfo>, vm: MailViewModel, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 80.dp, end = 12.dp, start = 12.dp)
                .widthIn(max = 320.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
                .padding(12.dp),
        ) {
            AttachmentRow(messageId, attachments, vm)
        }
    }
}

/**
 * Shows sanitized email HTML. JavaScript is off, files and content providers are unreachable, remote images
 * stay blocked until [showImages], and links open in the browser instead of inside the message view.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlBody(page: String, showImages: Boolean, modifier: Modifier, onScroll: (Int) -> Unit) {
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




/** Subjects that already start with a reply or forward prefix (in English, Spanish, German, French). */
private val SUBJECT_PREFIX = Regex("""^\s*(re|rv|aw|sv|fwd?|wg|tr)\s*:""", RegexOption.IGNORE_CASE)
