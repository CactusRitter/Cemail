package com.cemail.app.ui.settings

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
import com.cemail.app.data.backup.RestoreResult
import com.cemail.app.ui.theme.CemailTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Saving the backup to a file the user picks, and restoring one. */
class BackupLaunchers(val save: () -> Unit, val restore: () -> Unit)

/**
 * The two file pickers of the backup. The work runs in the app's scope (a restore that changes the language recreates
 * the screen); [onResult] gets the text to show when it ends.
 */
@Composable
fun rememberBackupLaunchers(onResult: (Int) -> Unit): BackupLaunchers {
    val context = LocalContext.current
    val app = context.applicationContext as CemailApplication
    val report by rememberUpdatedState(onResult)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch(Dispatchers.Main) {
            val ok = runCatching {
                val text = app.backup.export()
                withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
            }.onFailure { com.cemail.app.AppLog.e("backup", "the backup could not be saved", it) }.isSuccess
            report(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        app.appScope.launch(Dispatchers.Main) {
            val text = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            report(
                when (text?.let { app.backup.restore(it) }) {
                    null -> R.string.backup_failed
                    RestoreResult.RESTORED -> R.string.backup_restored
                    RestoreResult.INVALID -> R.string.backup_invalid
                    RestoreResult.TOO_NEW -> R.string.backup_too_new
                },
            )
        }
    }
    // A date in the name tells the copies apart; ISO, because a local date can have slashes.
    val fileName = stringResource(R.string.backup_file, java.time.LocalDate.now().toString())
    return BackupLaunchers(
        save = { save.launch(fileName) },
        restore = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
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
        Text(
            stringResource(R.string.backup_accounts_forget),
            Modifier.clickable(role = Role.Button, onClick = onForget).padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
private fun AccountsToAddPreview() {
    CemailTheme {
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
