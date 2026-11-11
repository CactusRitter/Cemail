package com.cemail.app.ui.mail

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cemail.app.R
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.icons.CemailIcons
import java.util.Calendar

/** "Send later": the same quick choices as Snooze, plus a date and time of the user's own. */
@Composable
fun ScheduleSendDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun pickOwn() {
        val now = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val at = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                // A time in the past would send it at once: it is moved to a minute from now instead.
                onPick(maxOf(at, System.currentTimeMillis() + 60_000))
            }, now.get(Calendar.HOUR_OF_DAY), 0, android.text.format.DateFormat.is24HourFormat(context)).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.popIn(initialScale = 0.75f).fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(vertical = 16.dp)) {
            Text(stringResource(R.string.schedule_title), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            SnoozeOption.entries.forEach { option ->
                ScheduleRow(stringResource(option.label)) { onPick(option.at(System.currentTimeMillis())) }
            }
            ScheduleRow(stringResource(R.string.schedule_pick)) { pickOwn() }
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
