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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import com.cemail.app.ui.glass.ReadTransition
import com.cemail.app.ui.glass.expandFrom
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import com.cemail.app.ui.glass.bounceSpring
import com.cemail.app.ui.glass.glassSpring
import com.cemail.app.ui.glass.softBounceSpring
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
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
import com.cemail.app.ui.settings.SettingsRoute
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.graphics.graphicsLayer

/** Placeholder identity until Google sign-in (phase 1): messages "sent" in demo mode use this. */
const val DEMO_ME = "me@cemail.example"

private const val IMAP_PLAIN = "imap"
private const val IMAP_YAHOO = "yahoo"

private fun MailBox.icon(): ImageVector = when (this) {
    MailBox.INBOX -> CemailIcons.Inbox
    MailBox.STARRED -> Icons.Filled.Star
    MailBox.SNOOZED -> CemailIcons.Snooze
    MailBox.SENT -> Icons.AutoMirrored.Filled.Send
    MailBox.DRAFTS -> CemailIcons.Drafts
    MailBox.ARCHIVE -> CemailIcons.Archive
    MailBox.OUTBOX -> Icons.Filled.DateRange
    MailBox.SPAM -> Icons.Filled.Warning
    MailBox.TRASH -> Icons.Filled.Delete
}

@Composable
fun MailApp(vm: MailViewModel, accountVm: AccountViewModel, settings: AppSettings, settingsRepo: SettingsRepository) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsRoute by rememberSaveable { mutableStateOf(SettingsRoute.MAIN) }
    val acc by accountVm.state.collectAsStateWithLifecycle()
    // New messages are written from the selected account; in the unified view, from the default one of Settings.
    val defaultFrom = acc.accounts.firstOrNull { it.id == acc.selectedId }
        ?: acc.accounts.firstOrNull { it.id == settings.defaultAccountId }
        ?: acc.accounts.firstOrNull()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { accountVm.onSignInResult(it.data) }
    val startSignIn = { accountVm.startSignIn()?.let { launcher.launch(it) }; Unit }
    val microsoftLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { accountVm.onMicrosoftResult(it.data) }
    val startMicrosoft = { accountVm.startMicrosoftSignIn()?.let { microsoftLauncher.launch(it) }; Unit }
    // The form for an IMAP account: plain, or with Yahoo's steps (null = not shown).
    var imapForm by rememberSaveable { mutableStateOf<String?>(null) }
    imapForm?.let { kind ->
        com.cemail.app.ui.account.ImapSetupScreen(accountVm, yahoo = kind == IMAP_YAHOO) { imapForm = null }
        return
    }
    if (!acc.loaded) {
        GlassBackground {}
        return
    }
    if (acc.accounts.isEmpty() && !acc.demo) {
        SignInScreen(acc, startSignIn, accountVm::useDemo, onMicrosoft = startMicrosoft, onImap = { imapForm = IMAP_PLAIN }, onYahoo = { imapForm = IMAP_YAHOO })
        return
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    // The address the message being written goes out from (its chosen account, or the default one).
    val me = (acc.accounts.firstOrNull { it.id == draft?.fromAccountId } ?: defaultFrom)?.email ?: DEMO_ME
    val open = state.open
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // A request from outside (launcher menu, widget, another app): switch to the right account first, then carry it out.
    val context = androidx.compose.ui.platform.LocalContext.current
    val request by Launches.pending.collectAsStateWithLifecycle()
    LaunchedEffect(request, acc.selectedId, vm) {
        val r = request ?: return@LaunchedEffect
        val wanted = when (r) {
            is LaunchRequest.Compose -> r.accountId
            is LaunchRequest.OpenBox -> r.accountId
            // The unified inbox already shows every account's mail: it stays; another single account switches over.
            is LaunchRequest.OpenMail -> r.accountId.takeIf { acc.selectedId != null }
            is LaunchRequest.Snooze -> r.accountId.takeIf { acc.selectedId != null }
            LaunchRequest.Search -> null
            is LaunchRequest.Write -> null // written from the account on screen (or the default one)
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
            is LaunchRequest.Snooze -> {
                // From the notification: the app's own snooze choices; the notification has done its job.
                androidx.core.app.NotificationManagerCompat.from(context).cancel(r.accountId.hashCode())
                vm.closeMail()
                vm.askSnooze(r.localIds.toSet())
            }
            is LaunchRequest.Write -> {
                // A message being written is kept in Drafts first, never lost.
                if (state.draft != null) vm.closeCompose(me)
                vm.closeMail()
                vm.newDraft(Draft(to = r.to, cc = r.cc, bcc = r.bcc, subject = r.subject, body = r.body))
                if (r.files.isNotEmpty()) {
                    // The shared files are read now, while Android lets this app open them.
                    val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        r.files.mapNotNull { readAttachment(context, it) }
                    }
                    val problem = when {
                        files.size < r.files.size -> R.string.compose_attach_unreadable
                        !vm.attach(files) -> R.string.compose_attach_too_big
                        else -> null
                    }
                    problem?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        }
        Launches.pending.value = null
    }

    // A new account shows up in the list: leave the "add" screen for the accounts.
    val accountCount = acc.accounts.size
    LaunchedEffect(accountCount) { if (settingsRoute == SettingsRoute.ADD) settingsRoute = SettingsRoute.ACCOUNTS }

    // In the unified inbox every message carries a thin line in the colour of its account.
    val unifiedView = acc.accounts.size > 1 && acc.selectedId == null
    val colors = acc.accounts.associate { it.id to accountColor(it, acc.accounts) }
    val stripeOf: (Mail) -> Color? = { mail -> if (!unifiedView) null else colors[mail.accountId] }

    // The list keeps its place while a message is read; it goes back to the top only when what it shows changes.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // The quick button's filter lasts while the folder stays the same, also across an account switch.
    var listFilter by remember { mutableStateOf(ListFilter.NONE) }
    LaunchedEffect(state.box, state.labelId) { listFilter = ListFilter.NONE }
    // After a switch the new list arrives a moment later and the lazy list would follow the old first item: keep it at the top meanwhile.
    var pendingTop by remember { mutableStateOf(false) }
    LaunchedEffect(state.query) {
        pendingTop = true
        listState.scrollToItem(0)
        kotlinx.coroutines.delay(1500)
        pendingTop = false
    }
    LaunchedEffect(state.pinned, state.others) { if (pendingTop) listState.scrollToItem(0) }

    // How long the "Undo" bar stays after an action follows Settings.
    LaunchedEffect(vm, settings.swipe.undoSeconds) { vm.actionUndoMillis = settings.swipe.undoSeconds * 1000L }

    // While the app is on screen, look for new mail every minute (in the background SyncWorker does it).
    val lifecycleOwner = LocalLifecycleOwner.current
    // "Manual" turns every automatic check off (an account with its own interval counts on its own).
    val autoCheck = if (acc.accounts.isEmpty()) settings.syncMinutes > 0 else acc.accounts.any { settings.syncMinutesFor(it.id) > 0 }
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
    BackHandler(enabled = showSettings && !drawer.isOpen) {
        // Back walks up Settings (account → accounts → main) and closes it from the main screen.
        SettingsRoute.parent(settingsRoute)?.let { settingsRoute = it } ?: run { showSettings = false }
    }
    BackHandler(enabled = state.draft != null) { vm.closeCompose(me) }
    BackHandler(enabled = state.draft == null && state.open != null) { vm.closeMail() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = state.draft == null && state.open == null && state.selection.isEmpty() && state.searching) { vm.setSearching(false) }
    // In any folder or label, Back goes to the Inbox first (and leaves the app only from there).
    BackHandler(
        enabled = !drawer.isOpen && !showSettings && state.draft == null && state.open == null && state.selection.isEmpty() &&
            !state.searching && (state.box != MailBox.INBOX || state.labelId != null),
    ) { vm.selectBox(MailBox.INBOX) }

    GlassBackground {
        // With the list on the right (wide screens), the menu slides in from the right too: the drawer is laid out
        // mirrored, and the menu and the screens inside it get the real direction back.
        val direction = LocalLayoutDirection.current
        val drawerDirection = if (!com.cemail.app.ui.glass.LocalListOnRight.current) direction
            else if (direction == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides drawerDirection) {
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
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DrawerContent(state, acc, unified = unifiedView, revealed = drawer.targetValue == DrawerValue.Open, onOpenSettings = {
                        settingsRoute = SettingsRoute.MAIN
                        showSettings = true
                        scope.launch { drawer.close() }
                    }, onSelectAccount = { id ->
                        // The menu stays open: the labels of the chosen account load in it.
                        accountVm.select(id)
                    }, onToggleLabel = vm::toggleExpanded, onPickLabel = { id ->
                        vm.selectLabel(id)
                        scope.launch { drawer.close() }
                    }, onPick = { box ->
                        vm.selectBox(box)
                        scope.launch { drawer.close() }
                    })
                    }
                }
            },
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
            // The data of a screen that is on its way out must outlive the state that closed it.
            var lastDraft by remember { mutableStateOf(draft) }
            if (draft != null) lastDraft = draft
            var lastOpen by remember { mutableStateOf(open) }
            if (open != null) lastOpen = open
            val listScreen: @Composable () -> Unit = {
                ListScreen(state, vm, filter = listFilter, quickButton = { haze -> QuickButton(settings.quickAction, listFilter, { listFilter = it }, acc, { id -> accountVm.select(id) }, { vm.setSearching(true) }, haze) }, grouped = settings.groupByDate && state.query.isBlank(), groupCards = settings.groupCards, stripeOf = stripeOf, showSenderCircle = settings.showSenderCircle, swipe = settings.swipe, listState = listState, onOpenDrawer = { scope.launch { drawer.open() } })
            }
            // Tablets and open foldables (wide windows): the list and the open message side by side, like Gmail.
            val twoPane = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH
            val screen = when {
                draft != null -> Screen.COMPOSE
                showSettings -> Screen.SETTINGS
                open != null && !twoPane -> Screen.READ
                else -> Screen.LIST
            }
            Box(Modifier.fillMaxSize()) {
                // Moving between screens is animated: the new one grows in from a little smaller with a soft bounce
                // (and the other fades away); going back does the opposite, so it feels like depth.
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (initialState == Screen.READ && targetState == Screen.LIST && ReadTransition.fromCard) {
                            // Closing is the opposite: the mail folds back into its card (see expandFrom) and the list shows
                            // up behind it. The mail has to stay until it has folded, hence the long (almost invisible) exit.
                            (fadeIn(tween(300)) togetherWith fadeOut(tween(480), targetAlpha = 0.98f)).apply { targetContentZIndex = -1f }
                        } else if (initialState == Screen.LIST && targetState == Screen.READ && ReadTransition.origin != null) {
                            // The mail unfolds out of the card that was tapped (see expandFrom); the list just fades behind it.
                            EnterTransition.None togetherWith fadeOut(tween(380, delayMillis = 60))
                        } else if (targetState.depth >= initialState.depth) {
                            (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 0.9f)) togetherWith
                                (fadeOut(glassSpring()) + scaleOut(glassSpring(), targetScale = 1.04f))
                        } else {
                            (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 1.06f)) togetherWith
                                (fadeOut(glassSpring()) + scaleOut(glassSpring(), targetScale = 0.9f))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.COMPOSE -> Centered { lastDraft?.let { ComposeScreen(it, vm, acc.accounts, defaultFrom) } }
                        Screen.SETTINGS -> Dockable(settings.settingsDock, settingsRepo::setSettingsDock) {
                            SettingsScreen(
                                settings, settingsRepo, acc.accounts, settingsRoute, { settingsRoute = it }, accountVm::remove,
                                onAddGoogle = startSignIn, onAddMicrosoft = startMicrosoft, onAddImap = { imapForm = IMAP_PLAIN }, onAddYahoo = { imapForm = IMAP_YAHOO },
                            ) { showSettings = false }
                        }
                        Screen.LIST -> if (twoPane) TwoPane(
                            list = { listScreen() },
                            open = open,
                            reading = { ReadScreen(it, vm, settings.alwaysShowImages, state.labels, state.thread, darkMail = settings.darkMail) },
                        ) else Centered { listScreen() }
                        Screen.READ -> Centered { lastOpen?.let {
                            // Taken once per opening: the card that was tapped is where the mail grows from.
                            val origin = remember { ReadTransition.consume() }
                            // Leaving towards the list: the mail folds back into its card.
                            val closing = transition.targetState == EnterExitState.PostExit && screen == Screen.LIST
                            Box(Modifier.fillMaxSize().expandFrom(origin, it.id, closing)) {
                                ReadScreen(it, vm, settings.alwaysShowImages, state.labels, state.thread, darkMail = settings.darkMail)
                            }
                        } }
                    }
                }
                UndoBar(state.undo, vm, Modifier.align(Alignment.BottomCenter))
            }
            }
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
    /** true while the menu is open: its rows pop in one after another each time. */
    revealed: Boolean,
    onOpenSettings: () -> Unit,
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
        // Settings is a small button in the top corner of the menu: always reachable, without taking a row.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .pressScale(0.85f)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.settings_title), onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AccountSwitcher(acc, onSelectAccount, revealed)
        HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
        boxes.forEachIndexed { index, box ->
            val selected = box == state.box && state.labelId == null
            val unread = state.unreadByBox[box] ?: 0
            Row(
                Modifier
                    .popIn(revealed, delayMillis = (accounts.size + 1 + index) * 22, initialScale = 0.8f)
                    .pressScale(0.96f)
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
        delay(undo.durationMillis)
        vm.dismissUndo(id)
    }
    // The last event stays while the bar slides out, so it does not go blank on its way down.
    var shown by remember { mutableStateOf(undo) }
    if (undo != null) shown = undo
    AnimatedVisibility(
        visible = undo != null,
        modifier = modifier,
        enter = slideInVertically(bounceSpring()) { it } + fadeIn(glassSpring()) +
            scaleIn(bounceSpring(), initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = slideOutVertically(glassSpring()) { it / 2 } + fadeOut(glassSpring()) + scaleOut(glassSpring(), targetScale = 0.9f),
    ) {
        shown?.let { UndoRow(it, vm) }
    }
}

@Composable
private fun UndoRow(undo: UndoEvent, vm: MailViewModel) {
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            .glass(RoundedCornerShape(50), GlassKind.Menu, elevation = 12.dp)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(undo.message), style = MaterialTheme.typography.bodyMedium)
        if (undo.previous.isNotEmpty() || undo.cancelSend != null) {
            Text(
                stringResource(R.string.action_undo),
                Modifier
                    .clickable(role = Role.Button, onClick = vm::undo)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SnoozeDialog(onPick: (SnoozeOption) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.popIn(initialScale = 0.75f).fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(vertical = 16.dp),
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

/** What is on top; [depth] says how "deep" it is, to choose the direction of the transition. */
private enum class Screen(val depth: Int) { LIST(0), READ(1), SETTINGS(1), COMPOSE(2) }

/** From this window width (dp) the list and the open message are shown side by side: tablets, open foldables. */
internal const val TWO_PANE_MIN_WIDTH = 840

/** Widest a single screen grows: on a tablet or an open foldable it stays centred instead of stretching. */
private val MAX_CONTENT_WIDTH = 720.dp

/** [content] centred, no wider than [MAX_CONTENT_WIDTH] (on a phone it simply fills the screen). */
@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) { content() }
    }
}

/**
 * The list on the start side (or the end side, as chosen in Settings) and the open message beside it (wide windows). The list takes about a third of the
 * width (between 360 and 480 dp); with no message open, the reading side says where to tap.
 */
@Composable
private fun TwoPane(list: @Composable () -> Unit, open: Mail?, reading: @Composable (Mail) -> Unit) {
    // The card-to-message unfolding of phones does not apply here: the message has its own side.
    LaunchedEffect(open?.id) { ReadTransition.consume() }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.36f).coerceIn(360.dp, 480.dp)
        // "Message list: right" (Settings): mirrored, the list on the end side.
        val listOnRight = com.cemail.app.ui.glass.LocalListOnRight.current
        Row(Modifier.fillMaxSize()) {
            if (!listOnRight) Box(Modifier.width(listWidth).fillMaxHeight()) { list() }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = open,
                    contentKey = { it?.id },
                    transitionSpec = {
                        (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 0.96f)) togetherWith fadeOut(glassSpring())
                    },
                    label = "readingPane",
                ) { mail ->
                    if (mail != null) {
                        reading(mail)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(CemailIcons.Mail, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    stringResource(R.string.pane_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = com.cemail.app.ui.glass.onBackdrop(MaterialTheme.colorScheme.onSurfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
            if (listOnRight) Box(Modifier.width(listWidth).fillMaxHeight()) { list() }
        }
    }
}

/**
 * Settings on a wide screen: a column that sits on the left, in the centre or on the right, and stays where it was
 * left. Dragging in the empty space beside it (never inside the cards) moves it one step that way: from the centre to
 * a side, and from a side back to the centre. It follows the finger and settles with a spring. Faint handles show
 * where it can go; they are drawn with the design's own surface (glass, bevel, outline…), so they match every look.
 * Narrower windows (phones) just centre it.
 */
@Composable
private fun Dockable(dock: Int, onDock: (Int) -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val column = minOf(maxWidth, MAX_CONTENT_WIDTH)
        val free = maxWidth - column
        if (maxWidth < TWO_PANE_MIN_WIDTH.dp || free < 120.dp) {
            Centered(content)
            return@BoxWithConstraints
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val freePx = with(density) { free.toPx() }
        val columnPx = with(density) { column.toPx() }
        fun restX(d: Int) = freePx * (d + 1) / 2f
        val x = remember { androidx.compose.animation.core.Animatable(restX(dock)) }
        var pulled by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LaunchedEffect(dock, freePx) { x.animateTo(restX(dock), com.cemail.app.ui.glass.bounceSpring()) }
        // The empty space: the column lies on top of it, so a drag that starts on a card never reaches it.
        Box(
            Modifier.fillMaxSize().pointerInput(dock, freePx) {
                val step = 56.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val next = when {
                            pulled > step -> (dock + 1).coerceAtMost(1)
                            pulled < -step -> (dock - 1).coerceAtLeast(-1)
                            else -> dock
                        }
                        pulled = 0f
                        if (next != dock) onDock(next) else scope.launch { x.animateTo(restX(dock), com.cemail.app.ui.glass.bounceSpring()) }
                    },
                    onDragCancel = {
                        pulled = 0f
                        scope.launch { x.animateTo(restX(dock), com.cemail.app.ui.glass.bounceSpring()) }
                    },
                ) { change, amount ->
                    change.consume()
                    pulled += amount
                    // The column follows the finger a little more slowly than it moves: it feels held by a spring.
                    scope.launch { x.snapTo((restX(dock) + pulled * 0.6f).coerceIn(0f, freePx)) }
                }
            },
        )
        val gap = with(density) { 14.dp.toPx() }
        val arrowWidth = with(density) { ARROW_WIDTH.toPx() }
        // While pulling, the arrow on the side being pulled to grows a little.
        val grow = (kotlin.math.abs(pulled) / with(density) { 300.dp.toPx() }).coerceAtMost(0.35f)
        // An arrow on each side the column can still move to, pointing that way.
        if (dock > -1) MoveArrow(x.value - gap - arrowWidth, pointsRight = false, scale = 1f + if (pulled < 0f) grow else 0f)
        if (dock < 1) MoveArrow(x.value + columnPx + gap, pointsRight = true, scale = 1f + if (pulled > 0f) grow else 0f)
        Box(
            Modifier
                .absoluteOffset { androidx.compose.ui.unit.IntOffset(x.value.roundToIntSafe(), 0) }
                .width(column)
                .fillMaxHeight(),
        ) { content() }
    }
}

/** Size of the arrows beside Settings: an equilateral triangle (width = height × √3 / 2). */
private val ARROW_HEIGHT = 30.dp
private val ARROW_WIDTH = 26.dp

/**
 * The arrow beside the Settings column that tells it can move that way: an equilateral triangle without its back side
 * (a chevron), made of the design's own floating surface, so it is frosted glass, a bevel, an outline… as the look is.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MoveArrow(left: Float, pointsRight: Boolean, scale: Float) {
    Box(
        Modifier
            .align(androidx.compose.ui.AbsoluteAlignment.CenterLeft)
            .absoluteOffset { androidx.compose.ui.unit.IntOffset(left.roundToIntSafe(), 0) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = ARROW_WIDTH, height = ARROW_HEIGHT)
            .glass(ChevronShape(pointsRight), GlassKind.Chrome),
    )
}

/** A chevron (› or ‹): the two sides of an equilateral triangle that meet at its tip, about a third as thick as wide. */
private class ChevronShape(private val pointsRight: Boolean) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val w = size.width
        val h = size.height
        val t = w * 0.36f // thickness of each arm, measured across
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(t, 0f)
            lineTo(w, h / 2f)
            lineTo(t, h)
            lineTo(0f, h)
            lineTo(w - t, h / 2f)
            close()
            if (!pointsRight) {
                transform(androidx.compose.ui.graphics.Matrix().apply { scale(-1f, 1f); translate(-w, 0f) })
            }
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is ChevronShape && other.pointsRight == pointsRight
    override fun hashCode() = pointsRight.hashCode()
}

private fun Float.roundToIntSafe(): Int = if (isNaN()) 0 else kotlin.math.round(this).toInt()
