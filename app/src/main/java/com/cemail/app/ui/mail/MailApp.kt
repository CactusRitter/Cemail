package com.cemail.app.ui.mail

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import com.cemail.app.ui.account.AccountUiState
import com.cemail.app.ui.account.AccountViewModel
import com.cemail.app.ui.account.SignInScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.remember
import com.cemail.app.R
import com.cemail.app.LaunchRequest
import com.cemail.app.Launches
import androidx.compose.foundation.layout.size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.ui.settings.SettingsScreen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailBox
import com.cemail.app.ui.glass.GlassBackground
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Placeholder identity until Google sign-in (phase 1): messages "sent" in demo mode use this. */
const val DEMO_ME = "me@cemail.example"

private fun MailBox.icon(): ImageVector = when (this) {
    MailBox.INBOX -> CemailIcons.Inbox
    MailBox.STARRED -> Icons.Filled.Star
    MailBox.SNOOZED -> CemailIcons.Snooze
    MailBox.SENT -> Icons.AutoMirrored.Filled.Send
    MailBox.DRAFTS -> CemailIcons.Drafts
    MailBox.ARCHIVE -> CemailIcons.Archive
    MailBox.TRASH -> Icons.Filled.Delete
}

@Composable
fun MailApp(vm: MailViewModel, accountVm: AccountViewModel, settings: AppSettings, settingsRepo: SettingsRepository) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val acc by accountVm.state.collectAsStateWithLifecycle()
    // New messages are written from the selected account (the first one in the unified view).
    val me = (acc.accounts.firstOrNull { it.id == acc.selectedId } ?: acc.accounts.firstOrNull())?.email ?: DEMO_ME
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { accountVm.onSignInResult(it.data) }
    val startSignIn = { accountVm.startSignIn()?.let { launcher.launch(it) }; Unit }
    if (!acc.loaded) {
        GlassBackground {}
        return
    }
    if (acc.accounts.isEmpty() && !acc.demo) {
        SignInScreen(acc, startSignIn, accountVm::useDemo)
        return
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val open = state.open
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // A request from outside (launcher menu, widget): switch to the right account first, then carry it out.
    val request by Launches.pending.collectAsStateWithLifecycle()
    LaunchedEffect(request, acc.selectedId, vm) {
        val r = request ?: return@LaunchedEffect
        val wanted = when (r) {
            is LaunchRequest.Compose -> r.accountId
            is LaunchRequest.OpenBox -> r.accountId
            is LaunchRequest.OpenMail -> r.accountId
            LaunchRequest.Search -> null
        }
        val several = acc.accounts.size > 1
        if (wanted != null && acc.accounts.any { it.id == wanted } && acc.selectedId != wanted) {
            accountVm.select(wanted)
            return@LaunchedEffect // runs again with the new account's ViewModel
        }
        if (r is LaunchRequest.OpenBox && wanted == null && several && acc.selectedId != null) {
            accountVm.select(null)
            return@LaunchedEffect
        }
        showSettings = false
        when (r) {
            is LaunchRequest.Compose -> vm.newDraft()
            is LaunchRequest.OpenBox -> { vm.closeMail(); vm.selectBox(r.box) }
            LaunchRequest.Search -> { vm.closeMail(); vm.setSearching(true) }
            is LaunchRequest.OpenMail -> vm.open(r.localId)
        }
        Launches.pending.value = null
    }

    // In the unified inbox every message carries a thin line in the colour of its account.
    val palette = accountPalette()
    val unifiedView = acc.accounts.size > 1 && acc.selectedId == null
    val stripeOf: (Mail) -> Color? = { mail ->
        if (!unifiedView) null else acc.accounts.indexOfFirst { it.id == mail.accountId }.takeIf { it >= 0 }?.let { palette[it % palette.size] }
    }

    // While the app is on screen, look for new mail every minute (in the background SyncWorker does it).
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoCheck = settings.syncMinutes > 0 // "Manual" turns every automatic check off
    LaunchedEffect(vm, lifecycleOwner, autoCheck) {
        if (!autoCheck) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(POLL_MILLIS)
                vm.poll()
            }
        }
    }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = showSettings && !drawer.isOpen) { showSettings = false }
    BackHandler(enabled = state.draft != null) { vm.closeCompose(me) }
    BackHandler(enabled = state.draft == null && state.open != null) { vm.closeMail() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isEmpty() && state.searching) { vm.setSearching(false) }

    GlassBackground {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = state.draft == null && state.open == null,
            drawerContent = {
                // The sheet itself is transparent and full height: a tap on it (outside the glass menu) closes the drawer.
                ModalDrawerSheet(
                    modifier = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { scope.launch { drawer.close() } },
                    drawerContainerColor = Color.Transparent,
                    windowInsets = WindowInsets(0),
                ) {
                    DrawerContent(state, acc, unified = unifiedView, onOpenSettings = {
                        showSettings = true
                        scope.launch { drawer.close() }
                    }, onAddAccount = startSignIn, onSelectAccount = { id ->
                        accountVm.select(id)
                        scope.launch { drawer.close() }
                    }, onToggleLabel = vm::toggleExpanded, onPickLabel = { id ->
                        vm.selectLabel(id)
                        scope.launch { drawer.close() }
                    }, onPick = { box ->
                        vm.selectBox(box)
                        scope.launch { drawer.close() }
                    })
                }
            },
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    draft != null -> ComposeScreen(draft, vm, me)
                    showSettings -> SettingsScreen(settings, settingsRepo, acc.accounts, accountVm::remove) { showSettings = false }
                    open != null -> ReadScreen(open, vm, settings.alwaysShowImages, state.labels, state.thread)
                    else -> ListScreen(state, vm, grouped = settings.groupByDate && state.query.isBlank(), stripeOf = stripeOf, showSenderCircle = settings.showSenderCircle, onOpenDrawer = { scope.launch { drawer.open() } })
                }
                UndoBar(state.undo, vm, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    state.snoozeTargets?.let { SnoozeDialog(onPick = vm::snooze, onDismiss = vm::dismissSnooze) }
}

@Composable
private fun DrawerContent(
    state: MailUiState,
    acc: AccountUiState,
    unified: Boolean,
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onSelectAccount: (String?) -> Unit,
    onPickLabel: (String) -> Unit,
    onToggleLabel: (String) -> Unit,
    onPick: (MailBox) -> Unit,
) {
    val accounts = acc.accounts
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .fillMaxWidth(0.82f)
            // Swallows taps on the menu so only the area around it closes the drawer.
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Settings sits at the top of the menu, so it never needs a scroll to be reached.
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(50), GlassKind.Card)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Filled.Settings, null)
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.bodyLarge)
        }
        AccountSwitcher(acc, onSelectAccount, onAddAccount)
        if (accounts.isEmpty()) {
            Text(
                stringResource(R.string.demo_banner),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // All accounts together: only what makes sense across accounts. Drafts, sent, snoozed, archive, trash
        // and labels belong to each account, so they appear once an account is chosen.
        val boxes = if (unified) MailBox.entries.filter { it == MailBox.INBOX || it == MailBox.STARRED } else MailBox.entries
        boxes.forEach { box ->
            val selected = box == state.box && state.labelId == null
            val unread = state.unreadByBox[box] ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(50), if (selected) GlassKind.Pinned else GlassKind.Card)
                    .clickable(role = Role.Button) { onPick(box) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(box.icon(), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(box.labelRes()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (unread > 0 && box != MailBox.STARRED) {
                    Text(unread.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (!unified) LabelSection(state, onPickLabel, onToggleLabel)
    }
}

/** Spark-style bar: tells what just happened and offers Undo for 5 seconds. */
@Composable
private fun UndoBar(undo: UndoEvent?, vm: MailViewModel, modifier: Modifier) {
    LaunchedEffect(undo?.id) {
        val id = undo?.id ?: return@LaunchedEffect
        delay(5_000)
        vm.dismissUndo(id)
    }
    if (undo == null) return
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            .glass(RoundedCornerShape(50), GlassKind.Menu, elevation = 12.dp)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(undo.message), style = MaterialTheme.typography.bodyMedium)
        if (undo.previous.isNotEmpty()) {
            Text(
                stringResource(R.string.action_undo),
                Modifier
                    .clickable(role = Role.Button, onClick = vm::undo)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Unit
        }
    }
}

@Composable
private fun SnoozeDialog(onPick: (SnoozeOption) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.snooze_title), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            SnoozeOption.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(option) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(CemailIcons.Snooze, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** How often new mail is looked for while the app is on screen. */
private const val POLL_MILLIS = 60_000L
