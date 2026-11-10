package com.cemail.app.ui.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.cemail.app.AppLog
import com.cemail.app.data.gmail.OutAttachment
import com.cemail.app.ui.icons.CemailIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass

@Composable
fun ComposeScreen(draft: Draft, vm: MailViewModel, me: String) {
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            // Files are read here, off the main thread, and kept in memory until the message is sent.
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { readAttachment(context, it) } }
            if (!vm.attach(files)) Toast.makeText(context, R.string.compose_attach_too_big, Toast.LENGTH_LONG).show()
        }
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card)) {
                TextField(
                    value = draft.to,
                    onValueChange = { vm.updateDraft(draft.copy(to = it)) },
                    label = { Text(stringResource(R.string.compose_to)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = colors,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextField(
                    value = draft.subject,
                    onValueChange = { vm.updateDraft(draft.copy(subject = it)) },
                    label = { Text(stringResource(R.string.compose_subject)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = colors,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextField(
                    value = draft.body,
                    onValueChange = { vm.updateDraft(draft.copy(body = it)) },
                    placeholder = { Text(stringResource(R.string.compose_body)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    colors = colors,
                )
            }
            draft.attachments.forEach { file ->
                Row(
                    Modifier.fillMaxWidth().glass(CircleShape, GlassKind.Card).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(CemailIcons.Attachment, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Formatter.formatShortFileSize(context, file.size.toLong()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { vm.detach(file) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.compose_attach_remove)) }
                }
            }
            if (draft.attachments.isNotEmpty()) {
                Text(
                    stringResource(R.string.compose_attach_unsaved),
                    Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassPill(Modifier.fillMaxWidth()) {
                PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { vm.closeCompose(me) })
                Box(Modifier.weight(1f))
                PillIcon(CemailIcons.Attachment, stringResource(R.string.compose_attach), { picker.launch("*/*") }, width = 44.dp)
                PillIcon(Icons.Filled.Delete, stringResource(R.string.compose_discard), vm::discardDraft, width = 44.dp)
                PillIcon(
                    Icons.AutoMirrored.Filled.Send, stringResource(R.string.compose_send), { vm.send(me) },
                    selected = true, width = 52.dp,
                )
            }
        }
    }
}

/** Reads a file chosen with the system picker (name, type and bytes); null if it cannot be read. */
private fun readAttachment(context: Context, uri: Uri): OutAttachment? = try {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment ?: "file"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("empty")
    OutAttachment(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
} catch (e: Exception) {
    AppLog.e("attach", "could not read the chosen file", e)
    null
}
