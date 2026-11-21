package com.cemail.app.ui.mail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import com.ce.kit.ui.AccentButton
import com.ce.kit.ui.SoftButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cemail.app.R
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.glassSpring
import com.ce.kit.ui.glass.popIn
import com.ce.kit.ui.glass.softBounceSpring
import com.ce.kit.ui.theme.LocalSkin
import java.util.Calendar
import java.util.TimeZone

/**
 * Picking a date and then a time in the Ce design, instead of Android's own grey dialogs: one glass card (the same as
 * every menu of the app) with the calendar first and the clock after it. The calendar and the clock are Material's,
 * drawn on the card with the theme's colours, so they follow each design (Glass, Retro 98, Paper…) and light or dark.
 * Days before today cannot be picked. [onPick] gets the chosen moment in milliseconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeDialog(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val start = remember { Calendar.getInstance().apply { initial?.let { timeInMillis = it } } }
    // Material's calendar works with UTC midnights: today's local date, written as a UTC midnight.
    fun utcMidnight(c: Calendar): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val today = remember { utcMidnight(Calendar.getInstance()) }
    val date = rememberDatePickerState(
        initialSelectedDateMillis = maxOf(utcMidnight(start), today),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            override fun isSelectableYear(year: Int) = year >= Calendar.getInstance().get(Calendar.YEAR)
        },
    )
    val time = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var onTime by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val glass = Color.Transparent // the card is the glass: the pickers draw no surface of their own

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .popIn(initialScale = 0.75f)
                .padding(16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(LocalSkin.current.radius(28.dp)), GlassKind.Menu, elevation = 12.dp)
                .padding(vertical = 16.dp),
        ) {
            Text(
                stringResource(if (onTime) R.string.pick_time else R.string.pick_date),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            AnimatedContent(
                targetState = onTime,
                transitionSpec = { (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 0.94f)) togetherWith fadeOut(glassSpring()) },
                label = "dateTime",
            ) { timeStep ->
                if (!timeStep) {
                    DatePicker(
                        state = date,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        colors = DatePickerDefaults.colors(
                            containerColor = glass,
                            selectedDayContainerColor = c.primary,
                            selectedDayContentColor = c.onPrimary,
                            todayDateBorderColor = c.primary,
                            todayContentColor = c.primary,
                        ),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TimePicker(
                            state = time,
                            colors = TimePickerDefaults.colors(
                                containerColor = glass,
                                clockDialColor = c.onSurface.copy(alpha = 0.08f),
                                selectorColor = c.primary,
                                timeSelectorSelectedContainerColor = c.primaryContainer,
                                timeSelectorSelectedContentColor = c.onPrimaryContainer,
                                timeSelectorUnselectedContainerColor = c.onSurface.copy(alpha = 0.08f),
                                periodSelectorSelectedContainerColor = c.primaryContainer,
                            ),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SoftButton(
                    stringResource(if (onTime) R.string.back else R.string.cancel),
                    { if (onTime) onTime = false else onDismiss() },
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                Box(Modifier.width(8.dp))
                AccentButton(
                    stringResource(if (onTime) R.string.pick_set else R.string.pick_next),
                    enabled = date.selectedDateMillis != null,
                    onClick = {
                        if (!onTime) {
                            onTime = true
                        } else {
                            val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date.selectedDateMillis ?: today }
                            val at = Calendar.getInstance().apply {
                                clear()
                                set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), time.hour, time.minute)
                            }.timeInMillis
                            onPick(at)
                        }
                    },
                )
            }
        }
    }
}
