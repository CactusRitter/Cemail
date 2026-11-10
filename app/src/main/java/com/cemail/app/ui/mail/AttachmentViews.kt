package com.cemail.app.ui.mail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cemail.app.R
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons
import java.io.File
import kotlinx.coroutines.launch

/**
 * The files attached to the open message, as a row of glass chips. Tapping one downloads it (once) and opens it
 * with whatever app handles that type; the share icon sends it to any other app (Drive, Files, Telegram...).
 */
@Composable
fun AttachmentRow(messageId: Long, attachments: List<AttachmentInfo>, vm: MailViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(messageId) { mutableStateOf<String?>(null) }

    fun fetch(a: AttachmentInfo, then: (File) -> Unit) {
        if (busy != null) return
        busy = a.attachmentId
        scope.launch {
            val file = vm.attachmentFile(messageId, a)
            busy = null
            if (file == null) Toast.makeText(context, R.string.attachment_download_failed, Toast.LENGTH_LONG).show() else then(file)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.attachments_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { a ->
                Row(
                    Modifier.glass(CircleShape, GlassKind.Pinned).clip(CircleShape).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.clickable(role = Role.Button) { fetch(a) { openFile(context, it, a.mimeType) } }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (busy == a.attachmentId) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(CemailIcons.Attachment, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.widthIn(max = 150.dp)) {
                            Text(a.filename, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Formatter.formatShortFileSize(context, a.size.toLong()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(
                        Modifier.size(44.dp).clickable(role = Role.Button) { fetch(a) { shareFile(context, it, a.mimeType) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Share, stringResource(R.string.attachment_share), Modifier.size(20.dp)) }
                }
            }
        }
    }
}

private fun uriFor(context: Context, file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun openFile(context: Context, file: File, mime: String) {
    val open = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(context, file), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(open)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.attachment_open_failed, Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(context: Context, file: File, mime: String) {
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
