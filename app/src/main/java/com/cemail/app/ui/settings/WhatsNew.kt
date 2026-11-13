package com.cemail.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cemail.app.BuildConfig
import com.cemail.app.CemailApplication
import com.cemail.app.R
import com.cemail.app.update.ReleaseNotes

/** Settings → Updates: what this version brought, what the next one will bring, and the earlier ones. */
@Composable
internal fun WhatsNew() {
    val context = LocalContext.current
    val updates = (context.applicationContext as CemailApplication).updates
    val next by updates.available.collectAsState()
    val changelog = remember { context.resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() } }
    val language = context.resources.configuration.locales[0].language
    var earlier by remember { mutableStateOf(false) }
    val versions = remember(changelog) { ReleaseNotes.all(changelog) }

    next?.let { update ->
        val notes = ReleaseNotes.forLanguage(update.notes, language)
        if (notes.isNotEmpty()) NotesCard(stringResource(R.string.whatsnew_next, update.version), notes)
    }
    NotesCard(
        stringResource(R.string.whatsnew_current, BuildConfig.VERSION_NAME),
        ReleaseNotes.forVersion(changelog, BuildConfig.VERSION_NAME) ?: stringResource(R.string.whatsnew_none),
    )
    SettingsCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.whatsnew_earlier), earlier) { earlier = !earlier }
        }
        if (earlier) {
            versions.filter { it.first != BuildConfig.VERSION_NAME }.forEach { (version, notes) ->
                Text(version, style = MaterialTheme.typography.titleSmall)
                Text(notes.replace(Regex("(?m)^- "), "• "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotesCard(title: String, notes: String) {
    SettingsCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(notes.replace(Regex("(?m)^- "), "• "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
