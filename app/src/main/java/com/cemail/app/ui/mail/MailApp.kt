package com.cemail.app.ui.mail

import androidx.activity.compose.BackHandler
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
import com.cemail.app.R
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
fun MailApp(vm: MailViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val open = state.open
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = state.draft != null) { vm.closeCompose(DEMO_ME) }
    BackHandler(enabled = state.draft == null && state.open != null) { vm.closeMail() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isEmpty() && state.searching) { vm.setSearching(false) }

    GlassBackground {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = state.draft == null && state.open == null,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color.Transparent, windowInsets = WindowInsets(0)) {
                    DrawerContent(state, onPick = { box ->
                        vm.selectBox(box)
                        scope.launch { drawer.close() }
                    })
                }
            },
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    draft != null -> ComposeScreen(draft, vm, DEMO_ME)
                    open != null -> ReadScreen(open, vm)
                    else -> ListScreen(state, vm, onOpenDrawer = { scope.launch { drawer.open() } })
                }
                UndoBar(state.undo, vm, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    state.snoozeTargets?.let { SnoozeDialog(onPick = vm::snooze, onDismiss = vm::dismissSnooze) }
}

@Composable
private fun DrawerContent(state: MailUiState, onPick: (MailBox) -> Unit) {
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
            .fillMaxWidth(0.82f)
            .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(stringResource(R.string.demo_account), size = 40)
            Column(Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.demo_account), style = MaterialTheme.typography.titleSmall)
                Text(DEMO_ME, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.demo_banner),
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MailBox.entries.forEach { box ->
            val selected = box == state.box
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
