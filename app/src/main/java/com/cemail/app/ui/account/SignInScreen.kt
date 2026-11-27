package com.cemail.app.ui.account

import androidx.compose.foundation.clickable
import com.ce.kit.ui.SoftButton
import com.ce.kit.ui.AccentButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.cemail.app.R
import com.cemail.app.data.backup.BackupAccount
import com.cemail.app.ui.settings.AccountsToAdd
import com.ce.kit.ui.glass.GlassBackground
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons

/** First screen when there is no account: one button to sign in with Google, or try the demo. */
@Composable
fun SignInScreen(
    state: AccountUiState,
    onSignIn: () -> Unit,
    onDemo: () -> Unit,
    onMicrosoft: () -> Unit,
    onImap: () -> Unit,
    onYahoo: () -> Unit = onImap,
    /** Opens a backup file (a new phone: settings first, then the accounts it lists). */
    onRestore: () -> Unit = {},
    /** What came of the last restore (or save), or null. */
    @StringRes restoreMessage: Int? = null,
    /** The accounts of a restored backup, still to be signed in again. */
    accountsToAdd: List<BackupAccount> = emptyList(),
    onAddRestored: (BackupAccount) -> Unit = {},
    onForgetRestored: () -> Unit = {},
) {
    // Centred on the screen; it scrolls when the accounts of a restored backup make it taller than the screen.
    GlassBackground { androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val screen = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = screen).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            AccountsToAdd(accountsToAdd, onAddRestored, onForgetRestored, Modifier.widthIn(max = 560.dp))
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().glass(RoundedCornerShape(32.dp), GlassKind.Card).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(CemailIcons.Mail, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.sign_in_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.signingIn) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    AccentButton(stringResource(R.string.sign_in_google), onSignIn, enabled = !state.signingIn)
                }
                state.error?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    stringResource(R.string.sign_in_unverified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                SoftButton(stringResource(R.string.sign_in_microsoft), { if (!state.signingIn) onMicrosoft() })
                SoftButton(stringResource(R.string.sign_in_yahoo), onYahoo)
                SoftButton(stringResource(R.string.sign_in_other), onImap)
                // The example-messages mode is for testing only: debug builds, never the released app.
                if (com.cemail.app.BuildConfig.DEBUG) SoftButton(stringResource(R.string.sign_in_demo), onDemo)
                SoftButton(stringResource(R.string.sign_in_restore), onRestore)
                restoreMessage?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    } }
}
