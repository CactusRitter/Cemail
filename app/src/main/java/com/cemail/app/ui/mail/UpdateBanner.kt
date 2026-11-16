package com.cemail.app.ui.mail

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
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
import com.cemail.app.ui.glass.GlassProgressBar
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.glassSpring
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale
import com.cemail.app.update.DownloadState

/**
 * At the top of the Inbox (and in Settings → Updates) when GitHub has a newer Cemail: download it, with its progress
 * shown here (no notification), then Android asks to install it. Or not now.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier, allowLater: Boolean = true) {
    val context = LocalContext.current
    val updates = (context.applicationContext as CemailApplication).updates
    val update by updates.available.collectAsStateWithLifecycle()
    val download by updates.download.collectAsStateWithLifecycle()
    val shown = update ?: return
    var needsPermission by remember(shown.version) { mutableStateOf(false) }
    // Only what belongs to the version on offer (a download of an older offer does not count).
    val state = download.takeIf {
        when (it) {
            is DownloadState.Downloading -> it.version == shown.version
            is DownloadState.Ready -> it.version == shown.version
            is DownloadState.Failed -> it.version == shown.version
            DownloadState.Idle -> true
        }
    } ?: DownloadState.Idle
    val start = {
        if (!updates.canInstall()) {
            // Granting the permission can restart the app, so it comes first: nothing is half-downloaded.
            needsPermission = updates.askToAllowInstall()
        } else {
            needsPermission = false
            updates.startDownload(shown)
        }
    }
    Column(
        modifier
            .popIn(initialScale = 0.9f)
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .glass(RoundedCornerShape(24.dp), GlassKind.Pinned)
            .animateContentSize(glassSpring())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.update_title, shown.version), style = MaterialTheme.typography.titleSmall)
        when (state) {
            is DownloadState.Downloading -> {
                val fraction = if (state.total > 0) state.bytes.toFloat() / state.total else 0f
                GlassProgressBar(fraction, Modifier.padding(vertical = 4.dp))
                Text(
                    if (state.total > 0) {
                        stringResource(
                            R.string.update_progress,
                            java.text.NumberFormat.getPercentInstance().format(fraction.toDouble()),
                            Formatter.formatShortFileSize(context, state.bytes),
                            Formatter.formatShortFileSize(context, state.total),
                        )
                    } else {
                        stringResource(R.string.update_progress_unknown, Formatter.formatShortFileSize(context, state.bytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 4.dp)) {
                    BannerButton(stringResource(R.string.update_cancel), main = false) { updates.cancelDownload() }
                }
            }
            is DownloadState.Ready -> {
                BannerText(stringResource(R.string.update_ready))
                // The installer opens by itself when the download ends; this opens it again (if it was cancelled, or the
                // app was not on screen then).
                Row(Modifier.padding(top = 4.dp)) {
                    BannerButton(stringResource(R.string.update_install)) { updates.install(shown) }
                }
            }
            is DownloadState.Failed -> {
                BannerText(
                    stringResource(if (state.incomplete) R.string.update_failed_incomplete else R.string.update_failed_network),
                    error = true,
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerButton(stringResource(R.string.update_retry), onClick = start)
                    if (allowLater) BannerButton(stringResource(R.string.update_later), main = false) { updates.setAside(shown) }
                }
            }
            DownloadState.Idle -> {
                BannerText(stringResource(if (needsPermission) R.string.update_allow else R.string.update_body))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerButton(stringResource(R.string.update_download), onClick = start)
                    if (allowLater) BannerButton(stringResource(R.string.update_later), main = false) { updates.setAside(shown) }
                }
            }
        }
    }
}

@Composable
private fun BannerText(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The banner's buttons: the main one is a glass pill in the accent colour, the other plain text. */
@Composable
private fun BannerButton(text: String, main: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .pressScale(0.94f)
            .then(if (main) Modifier.glass(CircleShape, GlassKind.Chrome) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (main) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
