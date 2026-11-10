package com.cemail.app.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.ui.account.AccountUiState
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons

/** The colours that tell accounts apart in the unified inbox, taken from the theme (they follow the accent). */
@Composable
fun accountPalette(): List<Color> {
    val c = MaterialTheme.colorScheme
    return listOf(c.primary, c.tertiary, c.secondary, c.error)
}

/**
 * Top of the drawer. "All accounts" (the unified inbox) is always the first row. Below it, a pill with the
 * account being shown unfolds the list of accounts; tapping one switches to it and keeps the menu open, so its labels can be picked right away. Adding and removing accounts is in Settings.
 */
@Composable
fun AccountSwitcher(acc: AccountUiState, onSelect: (String?) -> Unit) {
    val accounts = acc.accounts
    val palette = accountPalette()
    val several = accounts.size > 1
    var open by rememberSaveable { mutableStateOf(false) }
    val current = accounts.firstOrNull { it.id == acc.selectedId } ?: accounts.singleOrNull()

    if (several) {
        AccountPill(
            title = stringResource(R.string.account_all), subtitle = stringResource(R.string.account_all_body),
            stripe = null, selected = acc.selectedId == null, expanded = null,
            onClick = { onSelect(null) },
        ) { AllAccountsIcon() }
    }

    // The pill that unfolds the accounts: it shows the one in use, or "Accounts" while unfolded.
    val countText = pluralStringResource(R.plurals.accounts_count, accounts.size, accounts.size)
    when {
        accounts.isEmpty() -> AccountPill(
            title = stringResource(R.string.demo_account), subtitle = DEMO_ME, stripe = null, selected = true,
            expanded = open, onClick = { open = !open },
        ) { Avatar(stringResource(R.string.demo_account), size = 40) }
        open || current == null -> AccountPill(
            title = stringResource(R.string.settings_section_accounts), subtitle = countText, stripe = null,
            selected = false, expanded = open, onClick = { open = !open },
        ) { AccountsIcon() }
        else -> AccountPill(
            title = current.displayName ?: current.email, subtitle = current.email,
            stripe = if (several) palette[accounts.indexOf(current) % palette.size] else null, selected = true,
            expanded = false, onClick = { open = !open },
        ) { Avatar(current.displayName ?: current.email, size = 40) }
    }

    if (!open) return
    accounts.forEachIndexed { index, account ->
        AccountPill(
            title = account.displayName ?: account.email, subtitle = account.email,
            stripe = if (several) palette[index % palette.size] else null,
            selected = account.id == current?.id && acc.selectedId != null || (account.id == current?.id && !several),
            expanded = null,
            onClick = { onSelect(account.id) },
        ) { Avatar(account.displayName ?: account.email, size = 40) }
    }
}

@Composable
private fun AllAccountsIcon() {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(CemailIcons.Inbox, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AccountsIcon() {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(CemailIcons.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [expanded] is null for a choice inside the list (no arrow), true/false for the pill that unfolds it. */
@Composable
private fun AccountPill(
    title: String,
    subtitle: String,
    stripe: Color?,
    selected: Boolean,
    expanded: Boolean?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(CircleShape, if (selected) GlassKind.Pinned else GlassKind.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        if (stripe != null) Box(Modifier.width(4.dp).height(28.dp).background(stripe, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expanded != null) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                stringResource(if (expanded) R.string.label_collapse else R.string.label_expand),
                Modifier.padding(end = 8.dp),
            )
        }
    }
}
