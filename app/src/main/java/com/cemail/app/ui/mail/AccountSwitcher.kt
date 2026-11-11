package com.cemail.app.ui.mail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.ui.account.AccountUiState
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale

/** The colours that tell accounts apart in the unified inbox, taken from the theme (they follow the accent). */
@Composable
fun accountPalette(): List<Color> {
    val c = MaterialTheme.colorScheme
    return listOf(c.primary, c.tertiary, c.secondary, c.error)
}

/**
 * Top of the drawer: every account is always in sight, one row each with its colour, and "All accounts" (the unified
 * inbox) closes the list. Tapping a row switches to that account and keeps the menu open, so its folders and labels
 * (which load below) can be picked right away. Adding and removing accounts is in Settings.
 */
@Composable
fun AccountSwitcher(acc: AccountUiState, onSelect: (String?) -> Unit, revealed: Boolean) {
    val accounts = acc.accounts
    val palette = accountPalette()
    val several = accounts.size > 1
    if (accounts.isEmpty()) {
        AccountRow(stringResource(R.string.demo_account), DEMO_ME, color = null, selected = true, onClick = {}, modifier = Modifier.popIn(revealed, 0, 0.8f))
        return
    }
    accounts.forEachIndexed { index, account ->
        AccountRow(
            title = account.displayName ?: account.email,
            subtitle = account.email,
            color = palette[index % palette.size],
            selected = !several || acc.selectedId == account.id,
            onClick = { onSelect(account.id) },
            modifier = Modifier.popIn(revealed, index * 22, 0.8f),
        )
    }
    if (several) {
        AccountRow(
            title = stringResource(R.string.account_all),
            subtitle = stringResource(R.string.account_all_body),
            color = null,
            selected = acc.selectedId == null,
            onClick = { onSelect(null) },
            modifier = Modifier.popIn(revealed, accounts.size * 22, 0.8f),
        )
    }
}

/** One account: a ring in its colour, its name over its address, and a check when it is the one on screen. */
@Composable
private fun AccountRow(title: String, subtitle: String, color: Color?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .pressScale(0.96f)
            .fillMaxWidth()
            .then(if (selected) Modifier.glass(shape, GlassKind.Pinned) else Modifier.clip(shape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).border(2.dp, color ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != title) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
