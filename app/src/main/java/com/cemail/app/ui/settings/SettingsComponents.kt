package com.cemail.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.popIn
import com.ce.kit.ui.glass.pressScale

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



/** "15 minutes", "2 hours" or "Manual". */
@Composable
internal fun syncIntervalLabel(minutes: Int): String = when {
    minutes == SyncWorker.MANUAL -> stringResource(R.string.sync_manual)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.sync_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.sync_minutes, minutes, minutes)
}


