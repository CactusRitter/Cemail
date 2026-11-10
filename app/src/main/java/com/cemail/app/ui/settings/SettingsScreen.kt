package com.cemail.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass

@Composable
fun SettingsScreen(settings: AppSettings, repo: SettingsRepository, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassPill(Modifier.fillMaxWidth()) {
            PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
            Text(
                stringResource(R.string.settings_title),
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_section_messages),
                Modifier.padding(start = 8.dp, top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SwitchRow(
                title = R.string.settings_images_title,
                body = R.string.settings_images_body,
                checked = settings.alwaysShowImages,
                onChange = repo::setAlwaysShowImages,
            )
            SwitchRow(
                title = R.string.settings_group_title,
                body = R.string.settings_group_body,
                checked = settings.groupByDate,
                onChange = repo::setGroupByDate,
            )
            Text(
                stringResource(R.string.settings_section_appearance),
                Modifier.padding(start = 8.dp, top = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), GlassKind.Card)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_transparency_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        NumberFormat.getPercentInstance().format(settings.transparency / 100.0),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.settings_transparency_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.transparency.toFloat(),
                    onValueChange = { repo.setTransparency(it.roundToInt()) },
                    valueRange = 0f..100f,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: Int, body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
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
