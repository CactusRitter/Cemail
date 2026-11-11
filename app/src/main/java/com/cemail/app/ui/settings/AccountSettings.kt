package com.cemail.app.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cemail.app.CemailApplication
import com.cemail.app.R
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.gmail.ParsedEml
import com.cemail.app.data.gmail.SignatureHtml
import com.cemail.app.sync.SyncWorker
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.pressScale
import com.cemail.app.ui.icons.CemailIcons
import com.cemail.app.ui.icons.GoogleLogo
import com.cemail.app.ui.icons.MicrosoftLogo
import com.cemail.app.ui.mail.Avatar
import com.cemail.app.ui.mail.HtmlBody
import kotlinx.coroutines.launch

/** The accounts, one big row each (tap for its own settings), and a single "Add" button after them. */
@Composable
internal fun AccountsPage(accounts: List<AccountEntity>, onRoute: (String) -> Unit) {
    if (accounts.isEmpty()) {
        Text(
            stringResource(R.string.settings_no_accounts),
            Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    accounts.forEachIndexed { index, account ->
        BigRow(
            title = account.displayName ?: account.email,
            body = stringResource(R.string.account_row_body, account.email, providerName(account)),
            index = index,
            onClick = { onRoute(SettingsRoute.account(account.id)) },
        ) { Avatar(account.displayName ?: account.email, size = 48) }
    }
    Row(
        Modifier
            .pressScale(0.95f)
            .glass(CircleShape, GlassKind.Pinned, elevation = 8.dp)
            .clickable(role = Role.Button) { onRoute(SettingsRoute.ADD) }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.accounts_add_button), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun providerName(account: AccountEntity): String = stringResource(
    when (account.provider) {
        "MICROSOFT" -> R.string.account_add_microsoft
        "IMAP" -> R.string.account_provider_imap
        else -> R.string.account_add_google
    },
)

/** The kinds of account that can be added: three big buttons with the logo of each service. */
@Composable
internal fun AddAccountPage(onGoogle: () -> Unit, onMicrosoft: () -> Unit, onImap: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    BigRow(stringResource(R.string.account_add_google), stringResource(R.string.add_google_body), 0, onGoogle, verticalPadding = 22.dp) {
        IconBubble(60.dp, surface) { GoogleLogo(32.dp) }
    }
    BigRow(stringResource(R.string.account_add_microsoft), stringResource(R.string.add_microsoft_body), 1, onMicrosoft, verticalPadding = 22.dp) {
        IconBubble(60.dp, surface) { MicrosoftLogo(30.dp) }
    }
    BigRow(stringResource(R.string.account_add_other), stringResource(R.string.add_imap_body), 2, onImap, verticalPadding = 22.dp) {
        IconBubble(60.dp) { Icon(CemailIcons.Mail, null, Modifier.padding(2.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
}

/** Everything that is set for one account: signature, how often it is checked, which labels sync, and removing it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountDetailPage(account: AccountEntity, settings: AppSettings, repo: SettingsRepository, onRemove: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CemailApplication
    var removing by remember { mutableStateOf(false) }

    // Header: who this is.
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(account.displayName ?: account.email, size = 52)
            Column(Modifier.weight(1f)) {
                Text(account.displayName ?: account.email, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(providerName(account), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // --- Signature: plain text, or HTML pasted from the clipboard or imported from a .eml/.html file ---
    SectionTitle(R.string.settings_section_signature)
    SignatureCard(account.email, repo)

    // --- How often this account is checked on its own ---
    SectionTitle(R.string.account_sync_title)
    SettingsCard {
        Text(stringResource(R.string.account_sync_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val own = settings.accountSync[account.id]
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.account_sync_default, syncIntervalLabel(settings.syncMinutes)), own == null) { repo.setAccountSync(account.id, null) }
            SyncWorker.INTERVALS.forEach { minutes ->
                ChoiceChip(syncIntervalLabel(minutes), own == minutes) { repo.setAccountSync(account.id, minutes) }
            }
        }
    }

    // --- Labels to sync (Gmail only: the other kinds of account have folders, which always sync) ---
    if (account.provider == "GMAIL") {
        SectionTitle(R.string.account_labels_title)
        val labels by produceState(emptyList(), account.id) { value = app.mailRepositoryFor(account.id).allLabels(account.id) }
        val hidden = settings.hiddenLabels[account.id].orEmpty()
        var changed by remember { mutableStateOf(false) }
        // Switching labels off or on is applied once, when leaving this screen: a full sync drops or brings back the messages.
        DisposableEffect(account.id) {
            onDispose {
                if (changed) app.appScope.launch { app.mailRepositoryFor(account.id).refresh(quick = false) }
            }
        }
        SettingsCard {
            Text(stringResource(R.string.account_labels_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (labels.isEmpty()) {
                Text(stringResource(R.string.account_labels_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.sortedBy { it.name.lowercase() }.forEach { label ->
                        val on = label.id !in hidden
                        Row(
                            Modifier
                                .pressScale(0.94f)
                                .glass(CircleShape, if (on) GlassKind.Pinned else GlassKind.Card)
                                .clickable(role = Role.Checkbox) {
                                    repo.setHiddenLabels(account.id, if (on) hidden + label.id else hidden - label.id)
                                    changed = true
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (on) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Remove ---
    TextAction(R.string.settings_remove_account, MaterialTheme.colorScheme.error) { removing = true }
    if (removing) {
        Dialog(onDismissRequest = { removing = false }) {
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_remove_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_remove_body, account.email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextAction(R.string.cancel, MaterialTheme.colorScheme.onSurface) { removing = false }
                    TextAction(R.string.settings_remove_account, MaterialTheme.colorScheme.error) {
                        removing = false
                        onRemove()
                    }
                }
            }
        }
    }
}

/**
 * The signature of one account. Without HTML it is a plain text box; with HTML it shows exactly as it was designed.
 * HTML comes from the clipboard (rich text or code, copied from anywhere) or from a .eml/.html file, and is cleaned
 * the same way as any other HTML in the app.
 */
@Composable
private fun SignatureCard(email: String, repo: SettingsRepository) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var text by remember(email, version) { mutableStateOf(repo.signature(email)) }
    val html = remember(email, version) { repo.signatureHtml(email) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val read = readSignatureFile(context, uri)
        if (read == null) {
            Toast.makeText(context, R.string.settings_signature_import_failed, Toast.LENGTH_LONG).show()
        } else {
            repo.setSignatureHtml(email, read)
            version++
        }
    }
    fun paste() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val rich = item?.htmlText
        val plain = item?.coerceToText(context)?.toString()
        val source = when {
            !rich.isNullOrBlank() -> rich
            !plain.isNullOrBlank() && '<' in plain && '>' in plain -> plain // HTML code copied as text
            else -> null
        }
        when {
            source != null -> {
                val clean = SignatureHtml.extract(ParsedEml(null, source, null, emptyMap()))
                if (clean == null) {
                    Toast.makeText(context, R.string.settings_signature_import_failed, Toast.LENGTH_LONG).show()
                } else {
                    repo.setSignatureHtml(email, clean)
                    version++
                }
            }
            !plain.isNullOrBlank() -> {
                repo.setSignatureHtml(email, null)
                repo.setSignature(email, plain)
                version++
            }
            else -> Toast.makeText(context, R.string.settings_signature_paste_empty, Toast.LENGTH_SHORT).show()
        }
    }
    SettingsCard {
        if (html != null) {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))) {
                HtmlBody(HtmlSanitizer.page(html, 0, ""), true, Modifier.fillMaxSize()) { }
            }
        } else {
            TextField(
                value = text,
                onValueChange = { text = it; repo.setSignature(email, it) },
                placeholder = { Text(stringResource(R.string.settings_signature_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        TextAction(R.string.settings_signature_paste, MaterialTheme.colorScheme.primary) { paste() }
        TextAction(R.string.settings_signature_import, MaterialTheme.colorScheme.primary) { importer.launch("*/*") }
        if (html != null) {
            TextAction(R.string.settings_signature_remove, MaterialTheme.colorScheme.error) {
                repo.setSignatureHtml(email, null)
                version++
            }
        }
    }
}
