package com.cemail.app.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cemail.app.R
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.popIn
import com.cemail.app.ui.icons.CemailIcons

/** "Send later": the same quick choices as Snooze, plus a date and time of the user's own. */
@Composable
fun ScheduleSendDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        // The date and time of the user's own, in the Ce design. A time in the past would send it at once: it is moved
        // to a minute from now instead.
        DateTimeDialog(initial = null, onPick = { onPick(maxOf(it, System.currentTimeMillis() + 60_000)) }, onDismiss = { picking = false })
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.popIn(initialScale = 0.75f).fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(vertical = 16.dp)) {
            Text(stringResource(R.string.schedule_title), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            SnoozeOption.entries.forEach { option ->
                ScheduleRow(stringResource(option.label)) { onPick(option.at(System.currentTimeMillis())) }
            }
            ScheduleRow(stringResource(R.string.schedule_pick)) { picking = true }
        }
    }
}

@Composable
private fun ScheduleRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(CemailIcons.Snooze, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
