package com.cemail.app.ui.settings

import com.ce.kit.ui.settings.BigRow
import com.ce.kit.ui.settings.IconBubble
import com.ce.kit.ui.settings.SwitchRow
import com.ce.kit.ui.settings.ChoiceChip
import com.ce.kit.ui.settings.SettingsCard
import com.ce.kit.ui.settings.AppearancePage as KitAppearancePage
import com.ce.kit.ui.settings.CeAppearanceActions
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ce.kit.log.AppLog
import com.ce.kit.log.CrashLog
import com.cemail.app.R
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.SwipeAction
import com.cemail.app.data.SwipeDistance
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.data.backup.BackupAccount
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ce.kit.ui.glass.GlassPill
import com.ce.kit.ui.glass.PillIcon
import com.ce.kit.ui.glass.glassSpring
import com.ce.kit.ui.glass.softBounceSpring
import com.cemail.app.ui.icons.CemailIcons
import com.cemail.app.ui.mail.swipeIcon
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.bounceSpring
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.pressScale
import com.cemail.app.ui.mail.shade
import com.ce.kit.ui.theme.Accent
import com.ce.kit.ui.theme.ThemeMode

/**
 * The screens of Settings. A route is a plain string so it survives a rotation (the caller keeps it) and the system
 * Back button can walk up the tree: details → list → main → closed.
 */
object SettingsRoute {
    const val MAIN = "main"
    const val ACCOUNTS = "accounts"
    const val ADD = "add"
    const val MESSAGES = "messages"
    const val GESTURES = "gestures"
    const val NOTIFICATIONS = "notifications"
    const val APPEARANCE = "appearance"
    const val DIAGNOSTICS = "diagnostics"
    const val BACKUP = "backup"
    const val UPDATES = "updates"
    private const val ACCOUNT = "account:"

    fun account(id: String) = ACCOUNT + id
    fun accountId(route: String): String? = route.removePrefix(ACCOUNT).takeIf { route.startsWith(ACCOUNT) }

    /** Where Back goes from [route]; null from the main screen (it closes Settings). */
    fun parent(route: String): String? = when {
        route == MAIN -> null
        route == ADD || route.startsWith(ACCOUNT) -> ACCOUNTS
        else -> MAIN
    }

    internal fun depth(route: String): Int = when {
        route == MAIN -> 0
        route == ADD || route.startsWith(ACCOUNT) -> 2
        else -> 1
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    accounts: List<AccountEntity>,
    route: String,
    onRoute: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddGoogle: () -> Unit,
    onAddMicrosoft: () -> Unit,
    onAddImap: () -> Unit,
    onAddYahoo: () -> Unit,
    /** Signs in again to an account of a restored backup. */
    onAddRestored: (BackupAccount) -> Unit,
    onClose: () -> Unit,
) {
    // Owned here, not in BackupPage: the restore question is a GlassDialog, which fills its parent, so it goes in the
    // full-screen Box below and not inside the scrolling page.
    var backupMessage by remember { mutableStateOf<Int?>(null) }
    val backupFiles = rememberBackupLaunchers { backupMessage = it }
    val goBack = { SettingsRoute.parent(route)?.let(onRoute) ?: onClose() }
    val account = SettingsRoute.accountId(route)?.let { id -> accounts.firstOrNull { it.id == id } }
    val title = when {
        account != null -> settings.lookOf(account.id).label ?: account.displayName ?: account.email
        route == SettingsRoute.ACCOUNTS -> stringResource(R.string.settings_section_accounts)
        route == SettingsRoute.ADD -> stringResource(R.string.add_account_title)
        route == SettingsRoute.MESSAGES -> stringResource(R.string.settings_section_messages)
        route == SettingsRoute.GESTURES -> stringResource(R.string.settings_gestures_title)
        route == SettingsRoute.NOTIFICATIONS -> stringResource(R.string.settings_notifications_page)
        route == SettingsRoute.APPEARANCE -> stringResource(R.string.settings_section_appearance)
        route == SettingsRoute.DIAGNOSTICS -> stringResource(R.string.settings_section_diagnostics)
        route == SettingsRoute.BACKUP -> stringResource(R.string.settings_section_backup)
        route == SettingsRoute.UPDATES -> stringResource(R.string.settings_section_updates)
        else -> stringResource(R.string.settings_title)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassPill(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), goBack)
            Text(
                title,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Going deeper grows the new screen in from a little smaller; going back does the opposite (like the app's screens).
        AnimatedContent(
            targetState = route,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (SettingsRoute.depth(targetState) >= SettingsRoute.depth(initialState)) {
                    (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(glassSpring(), targetScale = 1.03f))
                } else {
                    (fadeIn(glassSpring()) + scaleIn(softBounceSpring(), initialScale = 1.04f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(glassSpring(), targetScale = 0.94f))
                }.using(SizeTransform(clip = false))
            },
            label = "settingsPage",
        ) { target ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    // The side padding goes inside the scroll area: the cards' shadows spill into it instead of being cut at the edge.
                    .padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val targetAccount = SettingsRoute.accountId(target)?.let { id -> accounts.firstOrNull { it.id == id } }
                when {
                    target == SettingsRoute.MAIN -> MainPage(settings, accounts.size, onRoute)
                    target == SettingsRoute.ACCOUNTS -> AccountsPage(accounts, settings, repo, onRoute)
                    target == SettingsRoute.ADD -> AddAccountPage(onAddGoogle, onAddMicrosoft, onAddYahoo, onAddImap)
                    targetAccount != null -> AccountDetailPage(targetAccount, accounts, settings, repo) {
                        onRoute(SettingsRoute.ACCOUNTS)
                        onRemoveAccount(targetAccount.id)
                    }
                    target == SettingsRoute.MESSAGES -> MessagesPage(settings, repo)
                    target == SettingsRoute.GESTURES -> GesturesPage(settings, repo)
                    target == SettingsRoute.NOTIFICATIONS -> NotificationsPage(settings, repo)
                    target == SettingsRoute.APPEARANCE -> AppearancePage(settings, repo)
                    target == SettingsRoute.DIAGNOSTICS -> DiagnosticsPage()
                    target == SettingsRoute.BACKUP -> BackupPage(backupFiles, backupMessage, onAddRestored)
                    target == SettingsRoute.UPDATES -> UpdatesPage(settings, repo)
                    else -> Unit // an account that was just removed: the screen is on its way out
                }
            }
        }
    }
    backupFiles.dialog()
    }
}

// --- The main screen: one big row per section ---

@Composable
private fun MainPage(settings: AppSettings, accountCount: Int, onRoute: (String) -> Unit) {
    val summary = stringResource(
        R.string.settings_swipe_summary,
        stringResource(swipeChoiceLabel(settings.swipe.right)),
        stringResource(swipeChoiceLabel(settings.swipe.left)),
    )
    val rows: List<Triple<String, String, Pair<String, ImageVector>>> = listOf(
        Triple(stringResource(R.string.settings_section_accounts), pluralStringResource(R.plurals.accounts_count, accountCount, accountCount), SettingsRoute.ACCOUNTS to Icons.Filled.Person),
        Triple(stringResource(R.string.settings_section_appearance), stringResource(R.string.settings_row_appearance_body), SettingsRoute.APPEARANCE to CemailIcons.Palette),
        Triple(stringResource(R.string.settings_section_messages), stringResource(R.string.settings_row_messages_body), SettingsRoute.MESSAGES to Icons.Filled.Email),
        Triple(stringResource(R.string.settings_gestures_title), summary, SettingsRoute.GESTURES to Icons.AutoMirrored.Filled.ArrowForward),
        Triple(stringResource(R.string.settings_notifications_page), stringResource(R.string.settings_row_notifications_body), SettingsRoute.NOTIFICATIONS to Icons.Filled.Notifications),
        Triple(stringResource(R.string.settings_section_backup), stringResource(R.string.settings_row_backup_body), SettingsRoute.BACKUP to CemailIcons.Archive),
        Triple(stringResource(R.string.settings_section_updates), stringResource(R.string.settings_version, com.cemail.app.BuildConfig.VERSION_NAME), SettingsRoute.UPDATES to CemailIcons.Download),
        Triple(stringResource(R.string.settings_section_diagnostics), stringResource(R.string.settings_row_diagnostics_body), SettingsRoute.DIAGNOSTICS to Icons.Filled.Info),
    )
    rows.forEachIndexed { index, (title, body, target) ->
        BigRow(title, body, index, onClick = { onRoute(target.first) }) {
            IconBubble { Icon(target.second, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}

// --- Common settings pages ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessagesPage(settings: AppSettings, repo: SettingsRepository) {
    SwitchRow(R.string.settings_images_title, R.string.settings_images_body, settings.alwaysShowImages, repo::setAlwaysShowImages)
    SwitchRow(R.string.settings_dark_mail_title, R.string.settings_dark_mail_body, settings.darkMail, repo::setDarkMail)
    SwitchRow(R.string.settings_group_title, R.string.settings_group_body, settings.groupByDate, repo::setGroupByDate)
    SwitchRow(R.string.settings_circle_title, R.string.settings_circle_body, settings.showSenderCircle, repo::setShowSenderCircle)
    SettingsCard {
        Text(stringResource(R.string.settings_mark_selection_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_mark_selection_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.action_mark_read), settings.markSelectionRead) { repo.setMarkSelectionRead(true) }
            ChoiceChip(stringResource(R.string.action_mark_unread), !settings.markSelectionRead) { repo.setMarkSelectionRead(false) }
        }
    }
    SettingsCard {
        Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.quick_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                com.cemail.app.data.QuickAction.UNREAD to R.string.quick_action_unread,
                com.cemail.app.data.QuickAction.STARRED to R.string.quick_action_starred,
                com.cemail.app.data.QuickAction.ACCOUNT to R.string.quick_action_account,
                com.cemail.app.data.QuickAction.SEARCH to R.string.quick_action_search,
                com.cemail.app.data.QuickAction.NONE to R.string.quick_action_none,
            ).forEach { (action, label) ->
                ChoiceChip(stringResource(label), settings.quickAction == action) { repo.setQuickAction(action) }
            }
        }
    }
}

/** Which actions the two swipe directions do, and how far a message has to be dragged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GesturesPage(settings: AppSettings, repo: SettingsRepository) {
    @Composable
    fun directionCard(title: Int, body: Int, current: SwipeAction, onPick: (SwipeAction) -> Unit) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBubble(36.dp) { Icon(swipeIcon(current), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwipeAction.entries.forEach { action ->
                    ChoiceChip(stringResource(swipeChoiceLabel(action)), current == action) { onPick(action) }
                }
            }
        }
    }
    directionCard(R.string.settings_swipe_right_title, R.string.settings_swipe_right_body, settings.swipe.right, repo::setSwipeRight)
    directionCard(R.string.settings_swipe_left_title, R.string.settings_swipe_left_body, settings.swipe.left, repo::setSwipeLeft)
    SettingsCard {
        Text(stringResource(R.string.settings_swipe_distance_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_swipe_distance_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwipeDistance.entries.forEach { distance ->
                val label = when (distance) {
                    SwipeDistance.SHORT -> R.string.swipe_distance_short
                    SwipeDistance.NORMAL -> R.string.swipe_distance_normal
                    SwipeDistance.LONG -> R.string.swipe_distance_long
                }
                ChoiceChip(stringResource(label), settings.swipe.distance == distance) { repo.setSwipeDistance(distance) }
            }
        }
    }
    SwitchRow(R.string.settings_swipe_confirm_title, R.string.settings_swipe_confirm_body, settings.swipe.confirm, repo::setSwipeConfirm)
    SettingsCard {
        Text(stringResource(R.string.settings_swipe_undo_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_swipe_undo_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cemail.app.data.SwipeConfig.UNDO_CHOICES.forEach { seconds ->
                ChoiceChip(stringResource(R.string.undo_send_seconds, seconds), settings.swipe.undoSeconds == seconds) { repo.setUndoSeconds(seconds) }
            }
        }
    }
    Text(
        stringResource(R.string.settings_swipe_note),
        Modifier.padding(horizontal = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The name of an action in Settings (in the list it adapts to the message: "Mark as unread"...). */
internal fun swipeChoiceLabel(action: SwipeAction): Int = when (action) {
    SwipeAction.ARCHIVE -> R.string.action_archive
    SwipeAction.TRASH -> R.string.action_trash
    SwipeAction.TOGGLE_READ -> R.string.swipe_toggle_read
    SwipeAction.SPAM -> R.string.action_spam
    SwipeAction.STAR -> R.string.action_star
    SwipeAction.SNOOZE -> R.string.action_snooze
    SwipeAction.NONE -> R.string.swipe_none
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsPage(settings: AppSettings, repo: SettingsRepository) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (com.cemail.app.data.push.Push.available) {
        SwitchRow(R.string.settings_push_title, R.string.settings_push_body, settings.pushEnabled, repo::setPushEnabled)
    }
    SwitchRow(
        title = R.string.settings_notifications_title,
        body = R.string.settings_notifications_body,
        checked = settings.notifications,
        onChange = { on ->
            repo.setNotifications(on)
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
    if (settings.notifications) {
        TextAction(R.string.settings_notifications_test, MaterialTheme.colorScheme.primary) {
            (context.applicationContext as com.cemail.app.CemailApplication).notifier.show(
                "test", null,
                listOf(com.cemail.app.data.mail.Mail(
                    0, resources.getString(R.string.notif_test_sender), "", resources.getString(R.string.notif_test_subject),
                    resources.getString(R.string.notif_test_body), System.currentTimeMillis(),
                )),
            )
        }
    }
    if (settings.notifications && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        Text(
            stringResource(R.string.settings_notifications_blocked),
            Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    SettingsCard {
        Text(stringResource(R.string.settings_sync_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_sync_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cemail.app.sync.SyncWorker.INTERVALS.forEach { minutes ->
                // The background check is rescheduled by the app itself whenever this (or an account's own interval) changes.
                ChoiceChip(syncIntervalLabel(minutes), settings.syncMinutes == minutes) { repo.setSyncMinutes(minutes) }
            }
        }
    }
    SettingsCard {
        Text(stringResource(R.string.sync_window_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.sync_window_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 250, 500).forEach { size ->
                ChoiceChip(
                    if (size == 0) stringResource(R.string.sync_window_normal) else size.toString(),
                    settings.syncWindow == size,
                ) { repo.setSyncWindow(size) }
            }
        }
    }
    SettingsCard {
        Text(stringResource(R.string.settings_undo_send_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_undo_send_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 10, 20, 30).forEach { seconds ->
                ChoiceChip(
                    if (seconds == 0) stringResource(R.string.undo_send_off) else stringResource(R.string.undo_send_seconds, seconds),
                    settings.undoSendSeconds == seconds,
                ) { repo.setUndoSendSeconds(seconds) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(settings: AppSettings, repo: SettingsRepository) {
    val actions = remember(repo) {
        object : CeAppearanceActions {
            override fun setSkin(skin: com.ce.kit.ui.theme.Skin) = repo.setSkin(skin)
            override fun setThemeMode(mode: com.ce.kit.ui.theme.ThemeMode) = repo.setThemeMode(mode)
            override fun setDepth3d(on: Boolean) = repo.setDepth3d(on)
            override fun setThemeStyle(style: com.ce.kit.ui.theme.ThemeStyle) = repo.setThemeStyle(style)
            override fun setAccent(accent: com.ce.kit.ui.theme.Accent) = repo.setAccent(accent)
            override fun setCustomHue(hue: Float) = repo.setCustomHue(hue)
            override fun setTransparency(percent: Int) = repo.setTransparency(percent)
        }
    }
    KitAppearancePage(
        settings,
        actions,
        extra = {
            // Which side the list goes on: only where there are two panes (tablets, open foldables), not on phones.
            if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= com.cemail.app.ui.mail.TWO_PANE_MIN_WIDTH) SettingsCard {
                Text(stringResource(R.string.list_side_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.list_side_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(stringResource(R.string.list_side_left), !settings.listOnRight) { repo.setListOnRight(false) }
                    ChoiceChip(stringResource(R.string.list_side_right), settings.listOnRight) { repo.setListOnRight(true) }
                }
            }
        },
        styleExtra = {
            Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.cards_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.cards_grouped), settings.groupCards) { repo.setGroupCards(true) }
                ChoiceChip(stringResource(R.string.cards_single), !settings.groupCards) { repo.setGroupCards(false) }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        },
    )
}

/** The activity log can always be shared; the last crash shows only if there was one. */
@Composable
private fun DiagnosticsPage() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    SettingsCard {
        Text(stringResource(R.string.settings_log_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.settings_log_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shareTitle = stringResource(R.string.settings_log_share_title)
            TextAction(R.string.settings_log_share, MaterialTheme.colorScheme.primary) { AppLog.share(context, shareTitle) }
            val copiedLabel = stringResource(R.string.copied)
            TextAction(R.string.copy, MaterialTheme.colorScheme.primary) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(copiedLabel, AppLog.text(context)))
            }
            TextAction(R.string.clear, MaterialTheme.colorScheme.onSurfaceVariant) {
                AppLog.clear(context)
                crash = null
            }
        }
    }
    crash?.let { text ->
        SettingsCard {
            Text(stringResource(R.string.settings_crash_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.settings_crash_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val copiedLabel = stringResource(R.string.copied)
                TextAction(R.string.copy, MaterialTheme.colorScheme.primary) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(copiedLabel, text))
                }
                TextAction(R.string.clear, MaterialTheme.colorScheme.onSurfaceVariant) {
                    CrashLog.clear(context)
                    crash = null
                }
            }
        }
    }
}

/** Settings → Updates: this version, looking on GitHub for a newer one (by itself, or now). */
@Composable
private fun UpdatesPage(settings: AppSettings, repo: SettingsRepository) {
    val context = LocalContext.current
    val updates = (context.applicationContext as com.cemail.app.CemailApplication).updates
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // What "Look now" found: null = not asked yet; then a version, "up to date" or "could not check".
    var result by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val upToDate = stringResource(R.string.update_none, com.cemail.app.BuildConfig.VERSION_NAME)
    val failed = stringResource(R.string.update_check_failed)
    val foundText = stringResource(R.string.update_found)
    SwitchRow(R.string.settings_updates_title, R.string.settings_updates_body, settings.checkUpdates, repo::setCheckUpdates)
    com.cemail.app.ui.mail.UpdateBanner(allowLater = false)
    SettingsCard {
        Text(stringResource(R.string.settings_version, com.cemail.app.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleSmall)
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ChoiceChip(stringResource(if (checking) R.string.update_checking else R.string.settings_updates_now), false, enabled = !checking) {
            if (checking) return@ChoiceChip
            checking = true
            scope.launch {
                result = try {
                    if (updates.check(force = true) != null) foundText else upToDate
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed
                }
                checking = false
            }
        }
    }
    WhatsNew()
}

/**
 * Settings → Backup: settings, signatures, the list of accounts (without any password) and the muted, pinned and
 * snoozed messages, saved to a file the user picks (Drive, Downloads…) and brought back on this or another device
 * (see data/backup/Backup.kt). Below, the accounts of a restored backup that still have to be signed in again.
 */
@Composable
private fun BackupPage(files: BackupLaunchers, message: Int?, onAddRestored: (BackupAccount) -> Unit) {
    val backup = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.cemail.app.CemailApplication).backup
    val toAdd by backup.accountsToAdd.collectAsStateWithLifecycle(emptyList())
    SettingsCard {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.backup_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.backup_export), false, onClick = files.save)
            ChoiceChip(stringResource(R.string.backup_import), false, onClick = files.restore)
        }
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    }
    AccountsToAdd(toAdd, onAddRestored, backup::forgetAccountsToAdd)
}
