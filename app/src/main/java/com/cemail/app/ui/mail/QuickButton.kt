package com.cemail.app.ui.mail

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.QuickAction
import com.cemail.app.ui.account.AccountUiState
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale
import com.cemail.app.ui.icons.CemailIcons
import dev.chrisbanes.haze.HazeState

/** A filter applied on top of the list on screen (the quick button toggles it). */
enum class ListFilter { NONE, UNREAD, STARRED }

fun List<com.cemail.app.data.mail.Mail>.filteredBy(filter: ListFilter) = when (filter) {
    ListFilter.NONE -> this
    ListFilter.UNREAD -> filter { !it.read }
    ListFilter.STARRED -> filter { it.starred }
}

/**
 * The small round button above "write". What it does is chosen in Settings → Messages: filter unread or starred
 * messages (it lights up while the filter is on), cycle through "All accounts" and each account in the order of
 * Settings, or open the search.
 */
@Composable
fun QuickButton(
    action: QuickAction,
    filter: ListFilter,
    onFilter: (ListFilter) -> Unit,
    acc: AccountUiState,
    onSelectAccount: (String?) -> Unit,
    onSearch: () -> Unit,
    haze: HazeState,
) {
    if (action == QuickAction.NONE || (action == QuickAction.ACCOUNT && acc.accounts.size < 2)) return
    val context = LocalContext.current
    val active = (action == QuickAction.UNREAD && filter == ListFilter.UNREAD) || (action == QuickAction.STARRED && filter == ListFilter.STARRED)
    val label = stringResource(
        when (action) {
            QuickAction.UNREAD -> R.string.quick_unread
            QuickAction.STARRED -> R.string.quick_starred
            QuickAction.ACCOUNT -> R.string.quick_account
            else -> R.string.search
        },
    )
    // For the account switch: the name each stop is announced with.
    val allName = stringResource(R.string.account_all)
    val names = acc.accounts.associate { it.id to it.shownName() }
    val showing = R.string.quick_account_showing
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .popIn(delayMillis = 260, initialScale = 0.3f)
            .pressScale(0.88f)
            .size(48.dp)
            .glass(CircleShape, if (active) GlassKind.Pinned else GlassKind.Chrome, elevation = 8.dp, hazeState = haze)
            .clickable(role = Role.Button, onClickLabel = label) {
                when (action) {
                    QuickAction.UNREAD -> onFilter(if (filter == ListFilter.UNREAD) ListFilter.NONE else ListFilter.UNREAD)
                    QuickAction.STARRED -> onFilter(if (filter == ListFilter.STARRED) ListFilter.NONE else ListFilter.STARRED)
                    QuickAction.SEARCH -> onSearch()
                    QuickAction.ACCOUNT -> {
                        val stops = listOf<String?>(null) + acc.accounts.map { it.id }
                        val next = stops[(stops.indexOf(acc.selectedId) + 1) % stops.size]
                        onSelectAccount(next)
                        Toast.makeText(context, context.getString(showing, next?.let { names[it] } ?: allName), Toast.LENGTH_SHORT).show()
                    }
                    QuickAction.NONE -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val account = acc.accounts.firstOrNull { it.id == acc.selectedId }
        when {
            action == QuickAction.ACCOUNT && account != null -> AccountAvatar(account, acc.accounts, size = 30, ring = true)
            action == QuickAction.ACCOUNT -> Icon(CemailIcons.Inbox, label, tint = tint)
            action == QuickAction.UNREAD -> Icon(CemailIcons.Mail, label, tint = tint)
            action == QuickAction.STARRED -> Icon(Icons.Filled.Star, label, tint = tint)
            else -> Icon(Icons.Filled.Search, label, tint = tint)
        }
    }
}
