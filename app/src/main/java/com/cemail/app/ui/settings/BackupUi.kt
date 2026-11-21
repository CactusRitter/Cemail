package com.cemail.app.ui.settings

import com.ce.kit.ui.settings.ChoiceChip
import com.ce.kit.ui.SoftButton
import com.ce.kit.ui.settings.SettingsCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cemail.app.CemailApplication
import com.cemail.app.R
import com.cemail.app.data.backup.BackupAccount
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject
import com.ce.kit.ui.theme.CeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saving the backup to a file the user picks, and restoring one. */
class BackupLaunchers(val save: () -> Unit, val restore: () -> Unit, val dialog: @Composable () -> Unit)

/**
 * The two file pickers of the backup (a .zip, see data/backup/Backup.kt), and the question before restoring. The work
 * runs in the app's scope (a restore that changes the language recreates the screen); [onResult] gets the text to show
 * when it ends.
 */
@Composable
fun rememberBackupLaunchers(onResult: (Int) -> Unit): BackupLaunchers {
    val context = LocalContext.current
    val app = context.applicationContext as CemailApplication
    val report by rememberUpdatedState(onResult)
    // The backup that was picked and read, waiting for the user to confirm it.
    var picked by remember { mutableStateOf<JsonObject?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch(Dispatchers.Main) {
            val ok = runCatching { app.backup.export(app.contentResolver.openOutputStream(uri)!!) }
                .onFailure { com.ce.kit.log.AppLog.e("backup", "the backup could not be saved", it) }.isSuccess
            report(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch(Dispatchers.Main) {
            val backup = try {
                withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { app.backup.read(it) } }
            } catch (e: Exception) {
                com.ce.kit.log.AppLog.e("backup", "the backup could not be read", e)
                report(R.string.backup_failed)
                return@launch
            }
            if (backup == null) report(R.string.backup_invalid) else picked = backup
        }
    }
    // Not drawn here: a GlassDialog fills the box it is in, so the screen that owns the launchers calls
    // [BackupLaunchers.dialog] as the last child of its full-screen Box.
    val dialog: @Composable () -> Unit = {
      picked?.let { backup ->
        com.ce.kit.ui.GlassDialog(
            visible = true,
            title = stringResource(R.string.backup_restore_title),
            message = stringResource(R.string.backup_restore_body),
            confirmLabel = stringResource(R.string.backup_restore_confirm),
            onConfirm = {
                picked = null
                app.appScope.launch(Dispatchers.Main) {
                    val ok = runCatching { app.backup.restore(backup) }
                        .onFailure { com.ce.kit.log.AppLog.e("backup", "the backup could not be restored", it) }.isSuccess
                    report(if (ok) R.string.backup_restored else R.string.backup_failed)
                }
            },
            onDismiss = { picked = null },
        )
      }
    }
    // A date in the name tells the copies apart; ISO, because a local date can have slashes.
    val fileName = stringResource(R.string.backup_file, java.time.LocalDate.now().toString())
    return BackupLaunchers(
        save = { save.launch(fileName) },
        // Any file: a .zip is often typed loosely by the providers (Drive…) and would show greyed out; read() checks it.
        restore = { open.launch(arrayOf("*/*")) },
        dialog = dialog,
    )
}

/**
 * "Accounts to add": the accounts of a restored backup that are not on this device. Each one is signed in again with
 * its address already chosen (IMAP: the form comes filled in, only the password is missing); their settings are
 * already in place.
 */
@Composable
fun AccountsToAdd(accounts: List<BackupAccount>, onAdd: (BackupAccount) -> Unit, onForget: () -> Unit, modifier: Modifier = Modifier) {
    if (accounts.isEmpty()) return
    SettingsCard(modifier) {
        Text(stringResource(R.string.backup_accounts_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.backup_accounts_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        accounts.forEach { account ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        account.displayName ?: account.email,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.displayName != null) {
                        Text(
                            account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ChoiceChip(stringResource(R.string.backup_accounts_add), false) { onAdd(account) }
            }
        }
        SoftButton(stringResource(R.string.backup_accounts_forget), onForget)
    }
}

@Preview
@Composable
private fun AccountsToAddPreview() {
    CeTheme {
        AccountsToAdd(
            listOf(
                BackupAccount("1", "GMAIL", "ana@example.com", "Ana"),
                BackupAccount("imap:ana@example.org", "IMAP", "ana@example.org", null),
            ),
            onAdd = {},
            onForget = {},
        )
    }
}
