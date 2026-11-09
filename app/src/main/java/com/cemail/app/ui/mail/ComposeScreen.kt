package com.cemail.app.ui.mail

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
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            GlassPill(Modifier.fillMaxWidth()) {
                PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { vm.closeCompose(me) })
                Box(Modifier.weight(1f))
                PillIcon(Icons.Filled.Delete, stringResource(R.string.compose_discard), vm::discardDraft, width = 44.dp)
                PillIcon(
                    Icons.AutoMirrored.Filled.Send, stringResource(R.string.compose_send), { vm.send(me) },
                    selected = true, width = 52.dp,
                )
            }
        }
    }
}
