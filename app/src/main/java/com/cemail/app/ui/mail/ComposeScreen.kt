package com.cemail.app.ui.mail

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.AppLog
import com.cemail.app.R
import com.cemail.app.data.gmail.OutAttachment
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // The text is edited with formatting that shows at once (no **marks**). The draft keeps the plain text and
    // the HTML; the editor itself is filled only once, from the text the draft started with.
    val rich = rememberRichTextState()
    val currentDraft by rememberUpdatedState(draft)
    LaunchedEffect(rich) {
        // Addresses that arrive already filled in (a reply) show as pills from the start.
        currentDraft.let { vm.updateDraft(it.copy(to = committedRecipients(it.to), cc = committedRecipients(it.cc), bcc = committedRecipients(it.bcc))) }
        if (currentDraft.body.isNotEmpty()) rich.setText(currentDraft.body)
        snapshotFlow { rich.annotatedString }.collect {
            val html = rich.toMailHtml()
            vm.updateDraft(currentDraft.copy(body = rich.toText(), html = html))
        }
    }
    var showCopies by rememberSaveable { mutableStateOf(draft.cc.isNotBlank() || draft.bcc.isNotBlank()) }

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card)) {
                RecipientField(
                    label = stringResource(R.string.compose_to),
                    value = draft.to,
                    onValueChange = { vm.updateDraft(draft.copy(to = it)) },
                    trailing = {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { showCopies = !showCopies },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (showCopies) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                stringResource(R.string.compose_copies_toggle),
                            )
                        }
                    },
                )
                AnimatedVisibility(
                    visible = showCopies,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        RecipientField(
                            label = stringResource(R.string.compose_cc),
                            value = draft.cc,
                            onValueChange = { vm.updateDraft(draft.copy(cc = it)) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        RecipientField(
                            label = stringResource(R.string.compose_bcc),
                            value = draft.bcc,
                            onValueChange = { vm.updateDraft(draft.copy(bcc = it)) },
                        )
                    }
                }
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
                RichTextEditor(
                    state = rich,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    placeholder = { Text(stringResource(R.string.compose_body)) },
                    colors = RichTextEditorDefaults.richTextEditorColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
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
            if (draft.attachments.isNotEmpty() || draft.cc.isNotBlank() || draft.bcc.isNotBlank() || draft.html != null) {
                Text(
                    stringResource(R.string.compose_unsaved),
                    Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Top bar: back, attach, discard, send.
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassPill(Modifier.fillMaxWidth()) {
                PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { vm.closeCompose(me) })
                Box(Modifier.weight(1f))
                PillIcon(CemailIcons.Attachment, stringResource(R.string.compose_attach), { picker.launch("*/*") }, width = 44.dp)
                PillIcon(Icons.Filled.Delete, stringResource(R.string.compose_discard), vm::discardDraft, width = 44.dp)
                PillIcon(
                    Icons.AutoMirrored.Filled.Send, stringResource(R.string.compose_send), {
                        if (hasRecipientProblem(draft.to, draft.cc, draft.bcc)) {
                            Toast.makeText(context, R.string.compose_recipient_problem, Toast.LENGTH_LONG).show()
                        } else {
                            vm.send(me)
                        }
                    },
                    selected = true, width = 52.dp,
                )
            }
        }

        // Formatting toolbar, floating over the keyboard.
        Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            FormatBar(rich)
        }
    }
}

/** Bold, italic, underline, font, lists and "clear formatting" for the text being written. */
@Composable
private fun FormatBar(rich: RichTextState) {
    var fonts by remember { mutableStateOf(false) }
    val style = rich.currentSpanStyle
    GlassPill {
        PillIcon(
            CemailIcons.Bold, stringResource(R.string.format_bold),
            { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
            selected = style.fontWeight == FontWeight.Bold, width = 44.dp,
        )
        PillIcon(
            CemailIcons.Italic, stringResource(R.string.format_italic),
            { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
            selected = style.fontStyle == FontStyle.Italic, width = 44.dp,
        )
        PillIcon(
            CemailIcons.Underline, stringResource(R.string.format_underline),
            { rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
            selected = style.textDecoration?.contains(TextDecoration.Underline) == true, width = 44.dp,
        )
        Box {
            PillIcon(
                CemailIcons.Font, stringResource(R.string.format_font), { fonts = true },
                selected = style.fontFamily != null, width = 44.dp,
            )
            DropdownMenu(expanded = fonts, onDismissRequest = { fonts = false }) {
                FONTS.forEach { (label, family) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), fontFamily = family) },
                        onClick = {
                            fonts = false
                            rich.addSpanStyle(SpanStyle(fontFamily = family))
                        },
                    )
                }
            }
        }
        PillIcon(
            CemailIcons.ListBulleted, stringResource(R.string.format_bullets), { rich.toggleUnorderedList() },
            selected = rich.isUnorderedList, width = 44.dp,
        )
        PillIcon(
            CemailIcons.ListNumbered, stringResource(R.string.format_numbers), { rich.toggleOrderedList() },
            selected = rich.isOrderedList, width = 44.dp,
        )
        PillIcon(
            CemailIcons.ClearFormat, stringResource(R.string.format_clear),
            { rich.removeSpanStyle(rich.currentSpanStyle) }, width = 44.dp,
        )
    }
}

private val FONTS = listOf(
    R.string.font_sans to FontFamily.SansSerif,
    R.string.font_serif to FontFamily.Serif,
    R.string.font_mono to FontFamily.Monospace,
    R.string.font_cursive to FontFamily.Cursive,
)

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
