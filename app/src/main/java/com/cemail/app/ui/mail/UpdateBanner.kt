package com.cemail.app.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cemail.app.CemailApplication
import com.cemail.app.R
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale

/** At the top of the Inbox when GitHub has a newer Cemail: download it (Android then asks to install) or not now. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier, allowLater: Boolean = true) {
    val context = LocalContext.current
    val updates = (context.applicationContext as CemailApplication).updates
    val update by updates.available.collectAsStateWithLifecycle()
    val shown = update ?: return
    var started by remember(shown.version) { mutableStateOf(false) }
    var failed by remember(shown.version) { mutableStateOf(false) }
    var needsPermission by remember(shown.version) { mutableStateOf(false) }
    val title = stringResource(R.string.update_downloading, shown.version)
    Column(
        modifier
            .popIn(initialScale = 0.9f)
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .glass(RoundedCornerShape(24.dp), GlassKind.Pinned)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.update_title, shown.version), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                when {
                    failed -> R.string.update_failed
                    needsPermission -> R.string.update_allow
                    started -> R.string.update_started
                    else -> R.string.update_body
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (started) {
            // Once it is downloaded, the installer can be opened again (if it was cancelled, or Android first sent the
            // user to allow installing apps from Cemail).
            Text(
                stringResource(R.string.update_install),
                Modifier
                    .padding(top = 4.dp)
                    .pressScale(0.94f)
                    .glass(CircleShape, GlassKind.Chrome)
                    .clickable(role = Role.Button) { updates.install(shown) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.update_download),
                    Modifier
                        .pressScale(0.94f)
                        .glass(CircleShape, GlassKind.Chrome)
                        .clickable(role = Role.Button) {
                            if (!updates.canInstall()) {
                                // Granting the permission can restart the app, so it comes first: nothing is half-downloaded.
                                needsPermission = updates.askToAllowInstall()
                                failed = !needsPermission
                            } else {
                                needsPermission = false
                                started = updates.download(shown, title)
                                failed = !started
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (allowLater) Text(
                    stringResource(R.string.update_later),
                    Modifier
                        .pressScale(0.94f)
                        .clickable(role = Role.Button) { updates.setAside(shown) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
