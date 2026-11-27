package com.cemail.app.ui.mail

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.ce.kit.ui.glass.popIn
import com.ce.kit.ui.glass.pressScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.res.pluralStringResource
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailBox
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import com.ce.kit.ui.glass.GlassIconButton
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.GlassPill
import com.ce.kit.ui.glass.PillIcon
import com.ce.kit.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons

@Composable
fun ReadScreen(
    summary: Mail,
    vm: MailViewModel,
    alwaysShowImages: Boolean,
    labels: List<Label> = emptyList(),
    thread: List<Mail> = emptyList(),
    darkMail: Boolean = true,
    accounts: List<com.cemail.app.data.db.AccountEntity> = emptyList(),
) {
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
    // Invitations (.ics) travel in their own part: asked once per message and then kept on the phone.
    val invites = rememberInvites(summary.id, vm)
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val one = setOf(mail.id)
    // The labels of the account that owns the message (in the unified inbox the drawer shows none).
    val accountLabels by produceState(labels, summary.accountId) { value = vm.userLabels(summary.accountId).ifEmpty { labels } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val quoteWhen = DateUtils.formatDateTime(
        context, mail.timeMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    // The sanitized page, computed once per message. Messages without an HTML part show their text.
    val html = mail.bodyHtml.orEmpty()
    // Pictures that travel inside the message (cid:) are not "remote": they are fetched from Gmail once and always shown.
    val inline by produceState(emptyMap<String, String>(), mail.id, html) {
        value = if (HtmlSanitizer.hasInlineImages(html)) vm.inlineImages(mail.id) else emptyMap()
    }
    val hasRemoteImages = remember(mail.id, html) { html.isNotBlank() && HtmlSanitizer.hasRemoteImages(html) }
    var showImages by remember(mail.id, alwaysShowImages) { mutableStateOf(alwaysShowImages) }
    // With a dark theme the message is darkened too (Settings → Messages), unless it is switched to its original colours
    // from the menu (only for this message). Needs a WebView that can darken pages (any recent one).
    val darkTheme = com.ce.kit.ui.theme.LocalGlass.current.dark
    val canDarken = remember { WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) }
    var darkChoice by remember(mail.id) { mutableStateOf<Boolean?>(null) }
    val darken = darkTheme && canDarken && (darkChoice ?: darkMail)

    // Starts a reply, reply all or forward: quoted text and subject here, recipients and thread ids in the ViewModel.
    fun reply(all: Boolean = false, forward: Boolean = false) {
        val quoted = mail.body.lines().joinToString("\n") { "> $it" }
        val header = resources.getString(R.string.quote_header, quoteWhen, mail.senderName)
        val prefixRes = if (forward) R.string.forward_prefix else R.string.reply_prefix
        // "Re: Re: x" never happens: a subject that already starts with a reply/forward prefix is kept as it is.
        val subject = if (SUBJECT_PREFIX.containsMatchIn(mail.subject)) mail.subject else resources.getString(prefixRes, mail.subject)
        fun open(quote: String?, css: String) = vm.startReply(
            Draft(
                to = if (forward) "" else mail.senderEmail,
                subject = subject,
                body = if (quote == null) "\n\n" + header + "\n" + quoted else "",
                fromAccountId = mail.accountId.takeIf { it.isNotBlank() },
                quoteHtml = quote, quoteCss = css, quoteHeader = header, quoteText = quoted, quoteShowImages = showImages,
            ),
            mail.id, all, forward,
        )
        // The original is quoted as the mail it is (pictures and layout), not as plain text.
        if (html.isBlank()) {
            open(null, "")
        } else {
            scope.launch {
                val pictures = if (HtmlSanitizer.hasInlineImages(html)) inline.ifEmpty { vm.inlineImages(mail.id) } else emptyMap()
                val (quote, css) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    HtmlSanitizer.clean(html, pictures) to HtmlSanitizer.styles(html)
                }
                open(quote, css)
            }
        }
    }


    // Extra room at the end of the message, so its last lines can be scrolled clear of the reply button. Cleaning a big
    // newsletter takes a while: it is done away from the main thread so the screen keeps opening smoothly meanwhile.
    val cleanPage by produceState<String?>(null, mail.id, html, inline) {
        value = if (html.isBlank()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            HtmlSanitizer.page(HtmlSanitizer.clean(html, inline), 88, HtmlSanitizer.styles(html))
        }
    }
    // The web view (costly to create) is put in place only after the opening animation, which runs with the plain text.
    var settled by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(SETTLE_MILLIS)
        settled = true
    }
    val snoozedUntil = mail.snoozedUntil?.takeIf { mail.isSnoozed(System.currentTimeMillis()) }
    val notices = snoozedUntil != null || (hasRemoteImages && !showImages)
    var labelPanel by remember { mutableStateOf(false) }
    // Where the message is: its folder (not for the Inbox, the usual place) and the account's labels it has.
    val folderBox = mail.folder.takeIf { it != Folder.INBOX }?.toBox()
    val mailLabels = accountLabels.filter { it.id in mail.labelIds }.sortedBy { it.name.lowercase() }

    var panel by remember { mutableStateOf(false) }
    var infoPanel by remember { mutableStateOf(false) }
    var attachPanel by remember { mutableStateOf(false) }
    // The page is a native WebView: on some phones it is drawn above the Compose popups (menu, attachments, labels)
    // that open over it: so they are drawn in their own window (GlassOverlayWindow), which is always above the page..

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.dp, end = 12.dp, top = 88.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (folderBox != null || mailLabels.isNotEmpty()) {
                PlaceChips(
                    folderBox, mailLabels,
                    onFolder = { box -> vm.closeMail(); vm.selectBox(box) },
                    onLabels = { labelPanel = true },
                )
            }
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
            if (invites.isNotEmpty()) InviteCard(invites)
            if (thread.isNotEmpty()) ThreadCard(thread, vm)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(com.ce.kit.ui.theme.LocalSkin.current.shape(RoundedCornerShape(24.dp))),
            ) {
                val page = cleanPage
                when {
                    // Still reading it from the phone (a moment): an empty card, never a flash of the wrong content.
                    loaded == null -> Box(Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card))
                    // A message with HTML is not shown as plain text first: the card stays empty until the page is
                    // drawn, then the page fades in over it.
                    html.isNotBlank() -> {
                        var shown by remember(mail.id) { mutableStateOf(false) }
                        val alpha by androidx.compose.animation.core.animateFloatAsState(
                            if (shown) 1f else 0f, com.ce.kit.ui.glass.glassSpring(), label = "page",
                        )
                        Box(Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card))
                        if (page != null && settled) {
                            Box(Modifier.fillMaxSize().hiddenUnderBottomPill()) {
                                HtmlBody(page, showImages, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, darken = darken, onShown = { shown = true }) { }
                            }
                            // Brutalist: the black frame of the card, which is there before the page loads, stays on top of the page.
                            val look = com.ce.kit.ui.theme.LocalSkin.current
                            if (look.skin == com.ce.kit.ui.theme.Skin.BRUTAL) {
                                Box(Modifier.fillMaxSize().border(look.edgeWidth, look.edgeColor, look.shape(RoundedCornerShape(24.dp))))
                            }
                        }
                    }
                    else -> Box(
                        Modifier.fillMaxSize().glass(RoundedCornerShape(24.dp), GlassKind.Card)
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                    ) {
                        // The extra room at the end keeps the last lines clear of the action pill.
                        Text(mail.body, Modifier.padding(bottom = 88.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Top: one floating pill with the back button, the subject over the sender, and the menu button.
        // Looked up when the menu opens (a connection may be needed), so opening a message costs nothing extra.
        val unsubscribe by produceState<com.cemail.app.data.mail.UnsubscribeInfo?>(null, summary.id, panel) {
            value = if (panel) vm.unsubscribeInfo(summary.id) else null
        }
        val muted by produceState(false, summary.id, panel) { value = panel && vm.isMuted(summary.id) }
        var unsubscribing by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassPill(Modifier.fillMaxWidth(), height = 64.dp, floor = PILL_FLOOR) {
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
                PillIcon(Icons.Filled.MoreVert, stringResource(R.string.action_more), { panel = true })
            }
        }

        // Bottom: one glass pill with the main actions (icon and name), floating over the message. The message keeps
        // extra room at its end (88 dp), so scrolled to the end its last line rests just above the pill.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .popIn(delayMillis = 60, initialScale = 0.9f),
        ) {
            // The message is a WebView, which the glass cannot blur: the pills keep a floor of opacity so its text never shows through.
            GlassPill(Modifier.fillMaxWidth(), height = 64.dp, floor = PILL_FLOOR) {
                PillAction(CemailIcons.Reply, R.string.pill_reply) { reply() }
                PillAction(CemailIcons.ReplyAll, R.string.pill_reply_all) { reply(all = true) }
                PillAction(CemailIcons.Forward, R.string.pill_forward) { reply(forward = true) }
                // The same slot: archive from the inbox, or bring the message back to it from anywhere else.
                if (mail.folder == Folder.INBOX) {
                    PillAction(CemailIcons.Archive, R.string.pill_archive) { vm.archive(one) }
                } else {
                    PillAction(CemailIcons.Inbox, R.string.pill_inbox) { vm.moveToInbox(one) }
                }
                PillAction(Icons.Filled.Delete, R.string.pill_trash, danger = true) { vm.trash(one) }
            }
        }

        if (unsubscribing) unsubscribe?.let { info ->
            UnsubscribeDialog(mail.senderName, mail.accountId, info, vm, onDismiss = { unsubscribing = false })
        }
        if (attachPanel) AttachmentsPopup(mail.id, attachments, vm) { attachPanel = false }
        if (labelPanel) LabelsPopup(listOf(mail), mapOf(mail.accountId to accountLabels), vm, onDismiss = { labelPanel = false })
        if (infoPanel) {
            val whenFull = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL, java.text.DateFormat.MEDIUM, java.util.Locale.getDefault())
                .format(java.util.Date(mail.timeMillis))
            val account = accounts.firstOrNull { it.id == mail.accountId }?.email ?: mail.accountId.ifBlank { "-" }
            val sender = if (mail.senderName.isNotBlank() && mail.senderName != mail.senderEmail) "${mail.senderName} <${mail.senderEmail}>" else mail.senderEmail
            val lines = buildList {
                add(stringResource(R.string.info_account) + ": " + account)
                add(stringResource(R.string.info_from) + ": " + sender)
                add(stringResource(R.string.info_date) + ": " + whenFull)
                add(stringResource(R.string.info_folder) + ": " + stringResource(mail.folder.toBox().labelRes()))
                if (mailLabels.isNotEmpty()) add(stringResource(R.string.info_labels) + ": " + mailLabels.joinToString { it.name })
                if (attachments.isNotEmpty()) add(stringResource(R.string.info_attachments) + ": " + attachments.joinToString { it.filename })
            }
            com.ce.kit.ui.GlassDialog(
                visible = true,
                title = stringResource(R.string.action_info),
                message = lines.joinToString("\n\n"),
                confirmLabel = stringResource(R.string.close),
                onConfirm = { infoPanel = false },
                onDismiss = { infoPanel = false },
            )
        }
        if (panel) {
            MoreMenu(
                onDismiss = { panel = false },
                items = listOfNotNull(
                    PanelItem(Icons.Filled.Info, R.string.action_info) { infoPanel = true },
                    PanelItem(CemailIcons.Snooze, R.string.action_snooze) { vm.askSnooze(one) },
                    PanelItem(
                        if (mail.starred) Icons.Filled.Star else CemailIcons.StarOutline,
                        if (mail.starred) R.string.action_unstar else R.string.action_star, selected = mail.starred,
                    ) { vm.toggleStar(one) },
                    PanelItem(CemailIcons.Pin, if (mail.pinned) R.string.action_unpin else R.string.action_pin, selected = mail.pinned) { vm.togglePin(one) },
                    PanelItem(Icons.Filled.Email, R.string.action_mark_unread) { vm.markRead(one, false) },
                    PanelItem(CemailIcons.Label, R.string.action_labels, selected = mail.labelIds.any { id -> accountLabels.any { it.id == id } }) {
                        labelPanel = true
                    },
                    // In the Spam folder the same button takes the message back out of it.
                    PanelItem(Icons.Filled.Warning, R.string.action_spam, selected = mail.folder == Folder.SPAM) {
                        if (mail.folder == Folder.SPAM) vm.moveToInbox(one) else vm.spam(one)
                    },
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
                    // Only offered when the message really has them: a mailing list's headers, and a conversation id.
                    if (unsubscribe != null) PanelItem(Icons.Filled.Close, R.string.action_unsubscribe) { unsubscribing = true } else null,
                    if (mail.threadId != null) {
                        PanelItem(Icons.Filled.Notifications, if (muted) R.string.action_unmute else R.string.action_mute, selected = muted) {
                            vm.setMuted(mail.id, !muted)
                        }
                    } else {
                        null
                    },
                    if (darkTheme && canDarken && html.isNotBlank()) {
                        PanelItem(CemailIcons.Contrast, if (darken) R.string.action_show_original else R.string.action_darken, selected = darken) { darkChoice = !darken }
                    } else {
                        null
                    },
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

/** One action of the bottom pill: its icon with a short name under it. */
@Composable
private fun RowScope.PillAction(icon: ImageVector, label: Int, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val name = stringResource(label)
    Column(
        Modifier
            .weight(1f)
            .pressScale(0.9f)
            .clip(com.ce.kit.ui.theme.LocalSkin.current.shape(RoundedCornerShape(24.dp)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The rest of the actions on the open message (the main ones are in the bottom pill): a glass list, icon and name
 * on each row, that opens under the top pill's menu button. Tapping outside closes it.
 */
@Composable
private fun MoreMenu(onDismiss: () -> Unit, items: List<PanelItem>) {
    com.ce.kit.ui.GlassOverlayWindow(onDismiss) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .popIn(initialScale = 0.5f, origin = TransformOrigin(1f, 0f))
                // Swallows taps so only the area around the window closes it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .width(284.dp)
                .glass(RoundedCornerShape(24.dp), GlassKind.Menu, elevation = 12.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            items.forEachIndexed { index, item ->
                val tint = when {
                    item.danger -> MaterialTheme.colorScheme.error
                    item.selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    Modifier
                        .popIn(delayMillis = 40 + index * 24, initialScale = 0.9f)
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onDismiss(); item.onClick() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(item.icon, null, tint = tint)
                    Text(stringResource(item.label), style = MaterialTheme.typography.bodyLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The rest of the conversation: one line that opens into the other messages; a tap opens one of them. */
@Composable
private fun ThreadCard(thread: List<Mail>, vm: MailViewModel) {
    var expanded by remember(thread.firstOrNull()?.threadId) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card),
    ) {
        Text(
            pluralStringResource(R.plurals.thread_more, thread.size, thread.size),
            Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }.padding(14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (expanded) {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                thread.forEach { m ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.open(m.id) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (m.folder == Folder.SENT) stringResource(R.string.thread_you) else m.senderName,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (m.read) FontWeight.Normal else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateUtils.formatDateTime(context, m.timeMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            m.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The attached files of the open message, in a small glass card that drops down from the paperclip. */
@Composable
private fun AttachmentsPopup(messageId: Long, attachments: List<com.cemail.app.data.gmail.AttachmentInfo>, vm: MailViewModel, onDismiss: () -> Unit) {
    com.ce.kit.ui.GlassOverlayWindow(onDismiss) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 80.dp, end = 12.dp, start = 12.dp)
                .popIn(initialScale = 0.3f, origin = TransformOrigin(1f, 0f))
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
fun HtmlBody(page: String, showImages: Boolean, modifier: Modifier, darken: Boolean = false, hidden: Boolean = false, onShown: () -> Unit = {}, onScroll: (Int) -> Unit) {
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnShown by rememberUpdatedState(onShown)
    val darkPage = MaterialTheme.colorScheme.surface.toArgb()
    // The WebView only darkens a page when its own theme is a dark one, so it gets one that says so (whatever the
    // system is set to), and is made again when this changes.
    key(darken) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val theme = if (darken) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar
            WebView(android.view.ContextThemeWrapper(ctx, theme)).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // Emails are written for a white page; with [darken] the WebView turns them dark (and the messages that
                // have their own dark version use it).
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darken)
                }
                setBackgroundColor(if (darken) darkPage else android.graphics.Color.WHITE)
                setOnScrollChangeListener { _, _, y, _, _ -> currentOnScroll(y) }
                webViewClient = object : WebViewClient() {
                    // The page is about to be on screen (drawn, with its embedded pictures): time to show it.
                    override fun onPageCommitVisible(view: WebView, url: String?) = currentOnShown()
                    override fun onPageFinished(view: WebView, url: String?) = currentOnShown() // in case the above never comes

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
            // [hidden]: a Compose popup is open over the page, which the native view would otherwise paint above.
            web.visibility = if (hidden) android.view.View.INVISIBLE else android.view.View.VISIBLE
            val key = "$showImages|$page"
            if (web.tag != key) {
                web.tag = key
                // Darkened on request: the "light only" a message may ask for is left out.
                web.loadDataWithBaseURL(null, if (darken) HtmlSanitizer.allowDark(page) else page, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
    }
}




/** Subjects that already start with a reply or forward prefix (in English, Spanish, German, French). */
private val SUBJECT_PREFIX = Regex("""^\s*(re|rv|aw|sv|fwd?|wg|tr)\s*:""", RegexOption.IGNORE_CASE)

/** Asks before leaving a mailing list, then does it the way the list allows: one click, its page, or a message. */
@Composable
private fun UnsubscribeDialog(sender: String, accountId: String, info: com.cemail.app.data.mail.UnsubscribeInfo, vm: MailViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val body = when {
        info.oneClick -> R.string.unsub_body_oneclick
        info.https != null -> R.string.unsub_body_web
        else -> R.string.unsub_body_mail
    }
    com.ce.kit.ui.GlassDialog(
        visible = true,
        title = stringResource(R.string.unsub_title, sender),
        message = stringResource(body),
        confirmLabel = stringResource(R.string.action_unsubscribe),
        onConfirm = {
            onDismiss()
            when {
                info.oneClick -> scope.launch {
                    val ok = com.cemail.app.data.mail.Unsubscribe.postOneClick(info.https.orEmpty())
                    Toast.makeText(context, if (ok) R.string.unsub_done else R.string.unsub_failed, Toast.LENGTH_LONG).show()
                }
                info.https != null -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.https)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.unsub_failed, Toast.LENGTH_LONG).show()
                }
                info.mailto != null -> vm.newDraft(Draft(to = info.mailto, subject = info.mailtoSubject ?: "unsubscribe", fromAccountId = accountId.takeIf { it.isNotBlank() }))
            }
        },
        onDismiss = onDismiss,
    )
}

/** The opening animation of the reader lasts about this long; the web view waits for it. */
private const val SETTLE_MILLIS = 520L

/** The list folder a message's folder is shown in. */
private fun Folder.toBox(): MailBox = when (this) {
    Folder.INBOX -> MailBox.INBOX
    Folder.SENT -> MailBox.SENT
    Folder.DRAFTS -> MailBox.DRAFTS
    Folder.ARCHIVE -> MailBox.ARCHIVE
    Folder.TRASH -> MailBox.TRASH
    Folder.SPAM -> MailBox.SPAM
    Folder.OUTBOX -> MailBox.OUTBOX
}

/**
 * Where the open message is, quietly: a row of small glass chips with its folder and its labels (folders and labels
 * are the same thing in the menu, so they look alike). The folder chip opens that folder; a label chip opens the
 * label picker to change them. Many chips scroll sideways; the row is drawn a bit wider than the column so the
 * scroll does not cut the chips' shadows at the sides.
 */
@Composable
private fun PlaceChips(box: MailBox?, labels: List<Label>, onFolder: (MailBox) -> Unit, onLabels: () -> Unit) {
    val labelsAction = stringResource(R.string.action_labels)
    Row(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = SIDE_ROOM.roundToPx()
                val wide = constraints.copy(minWidth = constraints.maxWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra)
                val placeable = measurable.measure(wide)
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
            }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SIDE_ROOM, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        box?.let { PlaceChip(it.icon(), stringResource(it.labelRes()), null) { onFolder(it) } }
        // Just the last part, like the menu ("La Caixa" for "Banks/La Caixa"); the whole name only when two of the
        // message's labels would otherwise read the same ("Work/Travel" and "Home/Travel").
        val shortNames = labels.map { it.name.substringAfterLast("/") }
        labels.forEachIndexed { i, label ->
            val short = shortNames[i]
            val text = if (shortNames.count { it == short } > 1) label.name.replace("/", " › ") else short
            PlaceChip(CemailIcons.Folder, text, labelsAction, onLabels)
        }
    }
}

private val SIDE_ROOM = 12.dp

@Composable
private fun PlaceChip(icon: ImageVector, text: String, clickLabel: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .pressScale(0.94f)
            .glass(CircleShape, GlassKind.Card)
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Alpha of the opacity floor under the reader's pills (a safety net: the page is already hidden under the bottom pill). */
private const val PILL_FLOOR = 0.3f

/** The page is fully hidden under the bottom pill (it is 64dp tall plus its margin) and fades in over the next 28dp above it. */
private val PAGE_HIDDEN_BOTTOM = 72.dp
private val PAGE_FADE_BOTTOM = 28.dp

private fun Modifier.hiddenUnderBottomPill(): Modifier = graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val hold = (PAGE_HIDDEN_BOTTOM.toPx() / h).coerceIn(0f, 1f)
        val ramp = ((PAGE_HIDDEN_BOTTOM + PAGE_FADE_BOTTOM).toPx() / h).coerceIn(hold, 1f)
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(0f to androidx.compose.ui.graphics.Color.Black, 1f - ramp to androidx.compose.ui.graphics.Color.Black, 1f - hold to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
