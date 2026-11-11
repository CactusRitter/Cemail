package com.cemail.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cemail.app.AppLog
import com.cemail.app.CrashLog
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.ui.mail.HtmlBody
import com.cemail.app.R
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.sync.SyncWorker
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.pressScale
import com.cemail.app.ui.mail.Avatar
import java.text.NumberFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    accounts: List<AccountEntity>,
    onRemoveAccount: (String) -> Unit,
    onAddGoogle: () -> Unit,
    onAddMicrosoft: () -> Unit,
    onAddImap: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var removing by remember { mutableStateOf<AccountEntity?>(null) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassPill(Modifier.fillMaxWidth()) {
            PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
            Text(
                stringResource(R.string.settings_title),
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- Accounts ---
            SectionTitle(R.string.settings_section_accounts)
            if (accounts.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_accounts),
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            accounts.forEach { account ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(CircleShape, GlassKind.Card)
                        .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(account.displayName ?: account.email, size = 40)
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.displayName ?: account.email,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextAction(R.string.settings_remove_account, MaterialTheme.colorScheme.error) { removing = account }
                }
            }
            // Adding an account lives here (not in the menu): one pill per kind of account.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.account_add_google to onAddGoogle,
                    R.string.account_add_microsoft to onAddMicrosoft,
                    R.string.account_add_other to onAddImap,
                ).forEach { (label, action) ->
                    Row(
                        Modifier.pressScale(0.94f).glass(CircleShape, GlassKind.Card).clickable(role = Role.Button, onClick = action).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // --- Signatures: one per account, added when a message is written from it. It can be plain text or HTML
            // imported from a message (.eml) or an .html file, which then shows exactly as it was designed. ---
            if (accounts.isNotEmpty()) {
                SectionTitle(R.string.settings_section_signature)
                var importFor by remember { mutableStateOf<String?>(null) }
                var version by remember { mutableIntStateOf(0) }
                val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    val email = importFor
                    importFor = null
                    if (uri == null || email == null) return@rememberLauncherForActivityResult
                    val html = readSignatureFile(context, uri)
                    if (html == null) {
                        Toast.makeText(context, R.string.settings_signature_import_failed, Toast.LENGTH_LONG).show()
                    } else {
                        repo.setSignatureHtml(email, html)
                        version++
                    }
                }
                accounts.forEach { account ->
                    var text by remember(account.email) { mutableStateOf(repo.signature(account.email)) }
                    val html = remember(account.email, version) { repo.signatureHtml(account.email) }
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(account.email, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (html != null) {
                            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp))) {
                                HtmlBody(HtmlSanitizer.page(html, 0, ""), true, Modifier.fillMaxSize()) { }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextAction(R.string.settings_signature_replace, MaterialTheme.colorScheme.primary) {
                                    importFor = account.email
                                    importer.launch("*/*")
                                }
                                TextAction(R.string.settings_signature_remove, MaterialTheme.colorScheme.error) {
                                    repo.setSignatureHtml(account.email, null)
                                    version++
                                }
                            }
                        } else {
                            androidx.compose.material3.TextField(
                                value = text,
                                onValueChange = { text = it; repo.setSignature(account.email, it) },
                                placeholder = { Text(stringResource(R.string.settings_signature_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                ),
                            )
                            TextAction(R.string.settings_signature_import, MaterialTheme.colorScheme.primary) {
                                importFor = account.email
                                importer.launch("*/*")
                            }
                        }
                    }
                }
            }

            // --- Messages ---
            SectionTitle(R.string.settings_section_messages)
            SwitchRow(
                title = R.string.settings_images_title,
                body = R.string.settings_images_body,
                checked = settings.alwaysShowImages,
                onChange = repo::setAlwaysShowImages,
            )
            SwitchRow(
                title = R.string.settings_group_title,
                body = R.string.settings_group_body,
                checked = settings.groupByDate,
                onChange = repo::setGroupByDate,
            )
            SwitchRow(
                title = R.string.settings_circle_title,
                body = R.string.settings_circle_body,
                checked = settings.showSenderCircle,
                onChange = repo::setShowSenderCircle,
            )

            // --- New mail (and, later, notifications) ---
            SectionTitle(R.string.settings_section_sync)
            val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            SwitchRow(
                title = R.string.settings_notifications_title,
                body = R.string.settings_notifications_body,
                checked = settings.notifications,
                onChange = { on ->
                    repo.setNotifications(on)
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (settings.notifications) {
                TextAction(R.string.settings_notifications_test, MaterialTheme.colorScheme.primary) {
                    (context.applicationContext as com.cemail.app.CemailApplication).notifier.show(
                        "test", null,
                        listOf(com.cemail.app.data.mail.Mail(
                            0, resources.getString(R.string.notif_test_sender), "", resources.getString(R.string.notif_test_subject),
                            resources.getString(R.string.notif_test_body), System.currentTimeMillis(),
                        )),
                    )
                }
            }
            if (settings.notifications && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Text(
                    stringResource(R.string.settings_notifications_blocked),
                    Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_sync_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_sync_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncWorker.INTERVALS.forEach { minutes ->
                        val selected = settings.syncMinutes == minutes
                        val label = when {
                            minutes == SyncWorker.MANUAL -> stringResource(R.string.sync_manual)
                            minutes % 60 == 0 -> pluralStringResource(R.plurals.sync_hours, minutes / 60, minutes / 60)
                            else -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
                        }
                        Text(
                            label,
                            Modifier
                                .pressScale(0.94f)
                                .glass(CircleShape, if (selected) GlassKind.Pinned else GlassKind.Card)
                                .clickable(role = Role.RadioButton) {
                                    repo.setSyncMinutes(minutes)
                                    SyncWorker.schedule(context, minutes)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_undo_send_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_undo_send_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 20, 30).forEach { seconds ->
                        val selected = settings.undoSendSeconds == seconds
                        Text(
                            if (seconds == 0) stringResource(R.string.undo_send_off) else stringResource(R.string.undo_send_seconds, seconds),
                            Modifier
                                .pressScale(0.94f)
                                .glass(CircleShape, if (selected) GlassKind.Pinned else GlassKind.Card)
                                .clickable(role = Role.RadioButton) { repo.setUndoSendSeconds(seconds) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // --- Appearance ---
            SectionTitle(R.string.settings_section_appearance)
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassKind.Card)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_transparency_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        NumberFormat.getPercentInstance().format(settings.transparency / 100.0),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.settings_transparency_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.transparency.toFloat(),
                    onValueChange = { repo.setTransparency(it.roundToInt()) },
                    valueRange = 0f..100f,
                )
            }

            // --- Diagnostics: the activity log can always be shared; the last crash shows only if there was one ---
            SectionTitle(R.string.settings_section_diagnostics)
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.settings_log_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_log_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shareTitle = stringResource(R.string.settings_log_share_title)
                    TextAction(R.string.settings_log_share, MaterialTheme.colorScheme.primary) { AppLog.share(context, shareTitle) }
                    val copiedLabel = stringResource(R.string.copied)
                    TextAction(R.string.copy, MaterialTheme.colorScheme.primary) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(copiedLabel, AppLog.text(context)))
                    }
                    TextAction(R.string.clear, MaterialTheme.colorScheme.onSurfaceVariant) {
                        AppLog.clear(context)
                        crash = null
                    }
                }
            }
            crash?.let { text ->
                Column(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.settings_crash_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_crash_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val copiedLabel = stringResource(R.string.copied)
                        TextAction(R.string.copy, MaterialTheme.colorScheme.primary) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(copiedLabel, text))
                        }
                        TextAction(R.string.clear, MaterialTheme.colorScheme.onSurfaceVariant) {
                            CrashLog.clear(context)
                            crash = null
                        }
                    }
                }
            }
        }
    }

    removing?.let { account ->
        Dialog(onDismissRequest = { removing = null }) {
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
                    TextAction(R.string.cancel, MaterialTheme.colorScheme.onSurface) { removing = null }
                    TextAction(R.string.settings_remove_account, MaterialTheme.colorScheme.error) {
                        removing = null
                        onRemoveAccount(account.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TextAction(label: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        stringResource(label),
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

@Composable
private fun SwitchRow(title: Int, body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), GlassKind.Card)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
