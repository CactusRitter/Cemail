package com.cemail.app.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cemail.app.ui.glass.GlassIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import com.cemail.app.ui.glass.bounceSpring
import com.cemail.app.ui.glass.glassSpring
import com.cemail.app.ui.glass.softBounceSpring
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.glass.pressScale
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import com.cemail.app.ui.theme.LocalGlass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.cemail.app.R
import com.cemail.app.data.mail.DateSection
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.MailBox
import androidx.compose.ui.graphics.Color
import com.cemail.app.data.mail.dateSectionOf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.cemail.app.ui.glass.GlassBackground
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

fun MailBox.labelRes(): Int = when (this) {
    MailBox.INBOX -> R.string.box_inbox
    MailBox.STARRED -> R.string.box_starred
    MailBox.SNOOZED -> R.string.box_snoozed
    MailBox.SENT -> R.string.box_sent
    MailBox.DRAFTS -> R.string.box_drafts
    MailBox.ARCHIVE -> R.string.box_archive
    MailBox.OUTBOX -> R.string.box_outbox
    MailBox.SPAM -> R.string.box_spam
    MailBox.TRASH -> R.string.box_trash
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: MailUiState,
    vm: MailViewModel,
    grouped: Boolean,
    stripeOf: (Mail) -> Color?,
    showSenderCircle: Boolean,
    swipe: com.cemail.app.data.SwipeConfig,
    /** Kept by the caller, so the list is where it was left after reading a message. */
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenDrawer: () -> Unit,
) {
    val haze = rememberHazeState()
    val selecting = state.selection.isNotEmpty()
    val startIsArchive = state.box != MailBox.ARCHIVE && state.box != MailBox.TRASH && state.box != MailBox.SPAM

    Box(Modifier.fillMaxSize()) {
        // The background is a blur source too (as in Cenotes), so the buttons over it get real frosted glass
        // instead of a flat white fill.
        GlassBackground(Modifier.hazeSource(haze)) {}
        val empty = state.pinned.isEmpty() && state.others.isEmpty()
        // Pull down to refresh: the indicator shows only for a refresh the user asked for, not for the automatic ones.
        val pullState = rememberPullToRefreshState()
        var pulled by remember { mutableStateOf(false) }
        // The list comes down with the finger (up to a bit past the threshold), holds there while it updates, and goes back.
        val thresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(pullState) {
            // A small tick when the pull gets far enough that letting go will update.
            snapshotFlow { pullState.distanceFraction >= 1f }.distinctUntilChanged().collect { reached ->
                if (reached) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        LaunchedEffect(pulled, state.syncing) {
            if (pulled && !state.syncing) {
                delay(700) // long enough to be seen, and for the sync to start
                if (!state.syncing) pulled = false
            }
        }
        LaunchedEffect(pulled) {
            if (pulled) {
                delay(20_000)
                pulled = false
            }
        }
        Box(
            Modifier.fillMaxSize().pullToRefresh(
                state = pullState,
                isRefreshing = pulled,
                enabled = !selecting && !state.searching,
                onRefresh = {
                    pulled = true
                    vm.refresh()
                },
            ),
        ) {
        // Each folder, label and account is a list of its own: its messages do not cross-fade with those of the one before
        // (that left half-faded cards of the Inbox drawn over the Trash). Moving or deleting inside a list is still animated.
        androidx.compose.runtime.key(vm, state.box, state.labelId) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pullState.distanceFraction.coerceAtMost(1.5f) * thresholdPx }
                .hazeSource(haze),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 80.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (empty) {
                item {
                    Column(
                        Modifier.fillParentMaxWidth().padding(top = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(CemailIcons.Inbox, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(if (state.query.isBlank()) R.string.empty_title else R.string.no_results),
                            Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (state.query.isBlank()) {
                            Text(
                                stringResource(R.string.empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            if ((state.box == MailBox.TRASH || state.box == MailBox.SPAM) && state.labelId == null && state.query.isBlank() && !empty) {
                item(key = "empty-folder") { EmptyFolderButton(state.box, vm) }
            }
            if (state.pinned.isNotEmpty()) {
                item(key = "h-pinned") { SectionHeader(stringResource(R.string.section_pinned)) }
                items(state.pinned, key = { it.id }) { mail -> MailItem(mail, state, vm, startIsArchive, swipe, stripeOf(mail), showSenderCircle, Modifier.animateItem(fadeInSpec = glassSpring(), placementSpec = softBounceSpring(), fadeOutSpec = glassSpring())) }
                if (state.others.isNotEmpty() && !grouped) item(key = "h-mail") { SectionHeader(stringResource(R.string.section_mail)) }
            }
            if (grouped) {
                val now = System.currentTimeMillis()
                val groups = state.others.groupBy { dateSectionOf(it.timeMillis, now) }
                DateSection.entries.forEach { section ->
                    val list = groups[section] ?: return@forEach
                    val collapsed = section in state.collapsedSections
                    item(key = "d-$section") { DateHeader(section, list.size, collapsed) { vm.toggleSection(section) } }
                    if (!collapsed) items(list, key = { it.id }) { mail -> MailItem(mail, state, vm, startIsArchive, swipe, stripeOf(mail), showSenderCircle, Modifier.animateItem(fadeInSpec = glassSpring(), placementSpec = softBounceSpring(), fadeOutSpec = glassSpring())) }
                }
            } else {
                items(state.others, key = { it.id }) { mail -> MailItem(mail, state, vm, startIsArchive, swipe, stripeOf(mail), showSenderCircle, Modifier.animateItem(fadeInSpec = glassSpring(), placementSpec = softBounceSpring(), fadeOutSpec = glassSpring())) }
            }
        }
        }
            // Under the top bar, where the pulled list comes down to.
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = pulled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 72.dp),
                containerColor = LocalGlass.current.menu,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Top bar: glass pill over the list (menu, title or search field, search).
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Selecting, searching and the normal header swap with a little pop instead of changing at once.
            val barMode = if (selecting) 0 else if (state.searching) 1 else 2
            AnimatedContent(
                targetState = barMode,
                transitionSpec = { (fadeIn(glassSpring()) + scaleIn(bounceSpring(), initialScale = 0.92f)) togetherWith fadeOut(glassSpring()) },
                label = "topBar",
            ) { mode ->
            when (mode) {
                0 -> SelectionBar(state, vm, haze)
                1 -> SearchBar(state, vm, haze)
                // Same header as Celander and Cenotes: round button on the left, the title in big bold
                // letters in the middle, and a pill with the actions on the right.
                // The title sits in the middle of the whole screen (not in the room left between two ends of different
                // widths) and gets the room that is left once the wider end has been taken from both sides.
                else -> CenteredHeader(
                    start = { GlassIconButton(Icons.Filled.Menu, stringResource(R.string.menu_open), onOpenDrawer, hazeState = haze) },
                    title = {
                        Text(
                            state.labelId?.let { id -> state.labels.firstOrNull { it.id == id }?.name?.substringAfterLast("/") }
                                ?: stringResource(state.box.labelRes()),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    // Refreshing is done by pulling the list down, so there is no button for it: search is a round button
                    // just like the menu one on the other side.
                    end = {
                        GlassIconButton(Icons.Filled.Search, stringResource(R.string.search), { vm.setSearching(true) }, hazeState = haze)
                    },
                )
            }
            }
        }

        // Compose button (Thunderbird has a floating "write" button).
        if (!selecting && !state.searching) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .popIn(delayMillis = 220, initialScale = 0.3f)
                    .pressScale(0.88f)
                    .size(60.dp)
                    .glass(CircleShape, GlassKind.Pinned, elevation = 10.dp, hazeState = haze)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.compose)) { vm.newDraft() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Create, stringResource(R.string.compose), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun MailItem(mail: Mail, state: MailUiState, vm: MailViewModel, startIsArchive: Boolean, swipe: com.cemail.app.data.SwipeConfig, stripe: Color?, showCircle: Boolean, modifier: Modifier = Modifier) {
    val one = setOf(mail.id)
    // With "confirm before acting" on, the swipe only asks; the action runs when it is confirmed.
    var pending by remember { mutableStateOf<com.cemail.app.data.SwipeAction?>(null) }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // Reports where this card is, so an opened mail can fold back into it.
    androidx.compose.runtime.DisposableEffect(mail.id) { onDispose { com.cemail.app.ui.glass.ReadTransition.positions.remove(mail.id) } }
    fun perform(action: com.cemail.app.data.SwipeAction) {
        when (action) {
            com.cemail.app.data.SwipeAction.ARCHIVE -> if (startIsArchive) vm.archive(one) else vm.moveToInbox(one)
            com.cemail.app.data.SwipeAction.TRASH -> vm.trash(one)
            com.cemail.app.data.SwipeAction.TOGGLE_READ -> vm.markRead(one, !mail.read)
            com.cemail.app.data.SwipeAction.SPAM -> vm.spam(one)
            com.cemail.app.data.SwipeAction.STAR -> vm.toggleStar(one, withUndo = true)
            com.cemail.app.data.SwipeAction.SNOOZE -> vm.askSnooze(one)
            com.cemail.app.data.SwipeAction.NONE -> Unit
        }
    }
    MailRow(
        mail = mail,
        selected = mail.id in state.selection,
        selectionMode = state.selection.isNotEmpty(),
        onClick = {
            if (state.selection.isNotEmpty()) {
                vm.toggleSelect(mail.id)
            } else if (mail.folder == Folder.DRAFTS || mail.folder == Folder.OUTBOX) {
                vm.editDraft(mail)
            } else {
                com.cemail.app.ui.glass.ReadTransition.origin = bounds // the mail grows out of this card
                vm.open(mail.id)
            }
        },
        onLongClick = { vm.toggleSelect(mail.id) },
        onToggleStar = { vm.toggleStar(one) },
        swipe = swipe,
        onSwipe = { action -> if (swipe.confirm) pending = action else perform(action) },
        startIsArchive = startIsArchive,
        stripe = stripe,
        showCircle = showCircle,
        modifier = modifier.onGloballyPositioned {
            bounds = it.boundsInRoot()
            bounds?.let { rect -> com.cemail.app.ui.glass.ReadTransition.positions[mail.id] = rect }
        },
    )
    pending?.let { action ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { pending = null }) {
            androidx.compose.foundation.layout.Column(
                Modifier.popIn(initialScale = 0.75f).fillMaxWidth().glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp).padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(swipeQuestion(action, startIsArchive, mail.read, mail.starred)),
                    style = MaterialTheme.typography.titleMedium,
                )
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    Text(
                        stringResource(R.string.swipe_no),
                        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { pending = null }.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.swipe_yes),
                        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { pending = null; perform(action) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SelectionBar(state: MailUiState, vm: MailViewModel, haze: dev.chrisbanes.haze.HazeState) {
    val ids = state.selection
    GlassPill(Modifier.fillMaxWidth(), hazeState = haze) {
        PillIcon(Icons.Filled.Close, stringResource(R.string.close), vm::clearSelection)
        Text(
            stringResource(R.string.selected_count, ids.size),
            Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        PillIcon(CemailIcons.Archive, stringResource(R.string.action_archive), { vm.archive(ids) }, width = 44.dp)
        PillIcon(Icons.Filled.Delete, stringResource(R.string.action_trash), { vm.trash(ids) }, width = 44.dp)
        PillIcon(CemailIcons.Snooze, stringResource(R.string.action_snooze), { vm.askSnooze(ids) }, width = 44.dp)
        PillIcon(CemailIcons.Pin, stringResource(R.string.action_pin), { vm.togglePin(ids) }, width = 44.dp)
        PillIcon(Icons.Filled.Email, stringResource(R.string.action_mark_unread), { vm.markRead(ids, false) }, width = 44.dp)
    }
}

@Composable
private fun SearchBar(state: MailUiState, vm: MailViewModel, haze: dev.chrisbanes.haze.HazeState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassPill(Modifier.fillMaxWidth(), hazeState = haze) {
        PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { vm.setSearching(false) })
        Box(Modifier.weight(1f).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (state.query.isEmpty()) {
                Text(
                    stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (state.query.isNotEmpty()) PillIcon(Icons.Filled.Close, stringResource(R.string.close), { vm.setQuery("") })
    }
}

/** Tappable header of a date section: name, how many messages, and an arrow that opens or closes it. */
@Composable
private fun DateHeader(section: DateSection, count: Int, collapsed: Boolean, onClick: () -> Unit) {
    val label = when (section) {
        DateSection.TODAY -> R.string.section_today
        DateSection.YESTERDAY -> R.string.section_yesterday
        DateSection.LAST_WEEK -> R.string.section_last_week
        DateSection.OLDER -> R.string.section_older
    }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            stringResource(if (collapsed) R.string.label_expand else R.string.label_collapse),
            Modifier.padding(horizontal = 8.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "Empty trash" / "Empty spam" at the top of those folders; it asks before deleting anything for good. */
@Composable
private fun EmptyFolderButton(box: MailBox, vm: MailViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val name = stringResource(box.labelRes())
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(if (box == MailBox.TRASH) R.string.empty_trash else R.string.empty_spam),
            Modifier
                .glass(CircleShape, GlassKind.Card)
                .clickable(role = Role.Button) { confirm = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.empty_confirm_title)) },
            text = { Text(stringResource(R.string.empty_confirm_body, name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false; vm.emptyFolder(box) }) {
                    Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** A header with the title exactly in the middle of the screen, a button at each end, and no overlap between them. */
@Composable
private fun CenteredHeader(start: @Composable () -> Unit, title: @Composable () -> Unit, end: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content = {
            Box { start() }
            Box { title() }
            Box { end() }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaced = measurables[0].measure(loose)
        val endPlaced = measurables[2].measure(loose)
        val gap = 8.dp.roundToPx()
        val side = maxOf(startPlaced.width, endPlaced.width)
        val titlePlaced = measurables[1].measure(
            loose.copy(maxWidth = (constraints.maxWidth - 2 * (side + gap)).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            startPlaced.placeRelative(0, (constraints.maxHeight - startPlaced.height) / 2)
            endPlaced.placeRelative(constraints.maxWidth - endPlaced.width, (constraints.maxHeight - endPlaced.height) / 2)
            titlePlaced.placeRelative((constraints.maxWidth - titlePlaced.width) / 2, (constraints.maxHeight - titlePlaced.height) / 2)
        }
    }
}
