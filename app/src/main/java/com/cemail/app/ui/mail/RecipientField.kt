package com.cemail.app.ui.mail

import android.util.Patterns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.cemail.app.data.mail.Suggestion
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import com.cemail.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The addresses in a recipients string (separated by commas or semicolons), trimmed, without empties. */
fun cleanRecipients(raw: String): List<String> = raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

/** [cleanRecipients] joined back, with the trailing separator that marks "nothing being typed". */
fun committedRecipients(raw: String): String =
    cleanRecipients(raw).let { if (it.isEmpty()) "" else it.joinToString(", ") + ", " }

fun isValidAddress(address: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(address).matches()

/**
 * A recipients field: every address becomes a pill (with an x to remove it) as soon as it is finished with a
 * comma, space, Enter or by leaving the field; Backspace on an empty field takes the last pill back for editing.
 * The value is one string, "a@x.com, b@y.com, " plus whatever is still being typed after the last comma.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecipientField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    /** Addresses to offer for what is being typed (empty = none). */
    suggest: suspend (String) -> List<Suggestion> = { emptyList() },
) {
    // The latest value is kept here as well, so fast typing never works from a stale one.
    var current by remember { mutableStateOf(value) }
    // Values this field itself sent up: when they come back (late, after newer typing) they must not win.
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(value) { if (value !in sent) current = value }
    fun emit(next: String) {
        current = next
        sent.addLast(next)
        if (sent.size > 100) sent.removeFirst()
        onValueChange(next)
    }
    val parts = current.split(',', ';')
    val chips = parts.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
    val typing = parts.last().trimStart()

    fun build(list: List<String>, typed: String): String =
        list.joinToString(", ") + (if (list.isNotEmpty()) ", " else "") + typed

    var focused by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(emptyList<Suggestion>()) }
    LaunchedEffect(typing, focused) {
        options = if (focused && typing.length >= 2) {
            delay(120) // not at every key
            suggest(typing).filter { s -> chips.none { it.equals(s.email, ignoreCase = true) } }
        } else {
            emptyList()
        }
    }
    Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.padding(top = 16.dp).widthIn(min = 40.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            Modifier.weight(1f).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEachIndexed { index, address ->
                val ok = isValidAddress(address)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (ok) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        address,
                        Modifier.widthIn(max = 220.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) {
                            emit(build(chips.filterIndexed { i, _ -> i != index }, typing))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.recipient_remove, address), Modifier.size(16.dp)) }
                }
            }
            BasicTextField(
                value = TextFieldValue(typing, TextRange(typing.length)),
                onValueChange = { field ->
                    val text = field.text
                    // A separator ends the address: it turns into a pill and the field is empty again.
                    val end = text.indexOfFirst { it == ',' || it == ';' || it == ' ' || it == '\n' }
                    if (end >= 0) {
                        val done = text.substring(0, end).trim()
                        val rest = text.substring(end + 1).trimStart(',', ';', ' ', '\n')
                        emit(build(chips + listOfNotNull(done.takeIf { it.isNotEmpty() }), rest))
                    } else {
                        emit(build(chips, text))
                    }
                },
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .onFocusChanged {
                        // Leaving the field finishes the address being typed.
                        if (focused && !it.isFocused && typing.isNotBlank()) emit(build(chips + typing.trim(), ""))
                        focused = it.isFocused
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && typing.isEmpty() && chips.isNotEmpty()) {
                            emit(build(chips.dropLast(1), chips.last()))
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // No autocorrect: the keyboard must not "fix" an address while it is typed.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    if (typing.isNotBlank()) emit(build(chips + typing.trim(), ""))
                }),
            )
        }
        trailing()
    }
    options.forEach { s ->
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                options = emptyList()
                emit(build(chips + s.email, ""))
            }.padding(start = 56.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            if (s.name.isNotBlank()) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                s.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    }
}

/** true if there is no recipient in "To" or any address anywhere is not valid. */
fun hasRecipientProblem(to: String, cc: String, bcc: String): Boolean =
    cleanRecipients(to).isEmpty() || (cleanRecipients(to) + cleanRecipients(cc) + cleanRecipients(bcc)).any { !isValidAddress(it) }
