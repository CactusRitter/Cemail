package com.cemail.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.sync.SyncWorker
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale

@Composable
internal fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun TextAction(label: Int, color: Color, onClick: () -> Unit) {
    Text(
        stringResource(label),
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/** A glass card for a group of related controls. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
internal fun SwitchRow(title: Int, body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), GlassKind.Card)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One choice among several, as a glass pill (the selected one is pinned and bold). */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .pressScale(0.94f)
            .glass(CircleShape, if (selected) GlassKind.Pinned else GlassKind.Card)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

/** "15 minutes", "2 hours" or "Manual". */
@Composable
internal fun syncIntervalLabel(minutes: Int): String = when {
    minutes == SyncWorker.MANUAL -> stringResource(R.string.sync_manual)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.sync_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
}

/** A round icon on the theme's container colour. */
@Composable
internal fun IconBubble(size: Dp = 48.dp, background: Color = MaterialTheme.colorScheme.primaryContainer, content: @Composable () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) { content() }
}

/**
 * A big, tappable row: icon, title, a line of explanation and an arrow. Rows of one screen pop in one after another
 * ([index] sets the order). Used for the sections of Settings, the accounts and the kinds of account to add.
 */
@Composable
internal fun BigRow(
    title: String,
    body: String?,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 14.dp,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier
            .popIn(true, index * 35, 0.9f)
            .pressScale(0.97f)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), GlassKind.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = verticalPadding, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
