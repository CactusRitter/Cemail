package com.cemail.app.ui.settings

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
import com.cemail.app.AppLog
import com.cemail.app.CrashLog
import com.cemail.app.R
import com.cemail.app.data.AppSettings
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.SwipeAction
import com.cemail.app.data.SwipeDistance
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glassSpring
import com.cemail.app.ui.glass.softBounceSpring
import com.cemail.app.ui.icons.CemailIcons
import com.cemail.app.ui.mail.swipeIcon
import java.text.NumberFormat
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.bounceSpring
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.pressScale
import com.cemail.app.ui.mail.shade
import com.cemail.app.ui.theme.Accent
import com.cemail.app.ui.theme.ThemeMode

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
    onClose: () -> Unit,
) {
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
        else -> stringResource(R.string.settings_title)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassPill(Modifier.fillMaxWidth()) {
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
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
                    else -> Unit // an account that was just removed: the screen is on its way out
                }
            }
        }
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
    SwitchRow(R.string.settings_group_title, R.string.settings_group_body, settings.groupByDate, repo::setGroupByDate)
    SwitchRow(R.string.settings_circle_title, R.string.settings_circle_body, settings.showSenderCircle, repo::setShowSenderCircle)
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
    // Like Celander's "Design": light or dark, the colour (eight, or any hue), and the glass.
    val glassLook = settings.skin == com.cemail.app.ui.theme.Skin.GLASS
    SettingsCard {
        Text(stringResource(R.string.skin_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.skin_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cemail.app.ui.theme.Skin.entries.forEach { skin ->
                ChoiceChip(stringResource(skin.label), settings.skin == skin) { repo.setSkin(skin) }
            }
        }
        if (settings.skin.forcedDark != null) {
            // Only one mode exists for this design: no light/dark choice to offer.
            Text(stringResource(R.string.skin_always_dark), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.theme_mode_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    ChoiceChip(stringResource(label), settings.themeMode == mode) { repo.setThemeMode(mode) }
                }
            }
        }
    }
    if (glassLook) SwitchRow(R.string.depth_title, R.string.depth_body, settings.depth3d, repo::setDepth3d)
    SettingsCard {
        if (glassLook) {
            Text(stringResource(R.string.style_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.style_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.cemail.app.ui.theme.ThemeStyle.entries.forEach { style ->
                    ChoiceChip(stringResource(style.label), settings.themeStyle == style) { repo.setThemeStyle(style) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Text(stringResource(R.string.cards_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.cards_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(stringResource(R.string.cards_grouped), settings.groupCards) { repo.setGroupCards(true) }
            ChoiceChip(stringResource(R.string.cards_single), !settings.groupCards) { repo.setGroupCards(false) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.accent_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.accent_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { accent ->
                AccentDot(accent.shade(), stringResource(accent.label), selected = settings.customHue == null && settings.accent == accent) {
                    repo.setAccent(accent)
                }
            }
        }
        Text(stringResource(R.string.accent_custom), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
        HueBar(settings.customHue, repo::setCustomHue)
    }
    if (glassLook) SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_transparency_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                NumberFormat.getPercentInstance().format(settings.transparency / 100.0),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(stringResource(R.string.settings_transparency_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        com.cemail.app.ui.glass.GlassSlider(
            value = settings.transparency.toFloat(),
            onValueChange = { repo.setTransparency(it.roundToInt()) },
            valueRange = 0f..100f,
        )
        // How the glass looks right now: coloured bars behind a floating pill.
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.background)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) // tinted like the app's background
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val c = MaterialTheme.colorScheme
                listOf(c.primary to 0.5f, c.error to 0.8f, Accent.TEAL.shade() to 0.35f, Accent.AMBER.shade() to 0.65f).forEach { (color, part) ->
                    Box(Modifier.fillMaxWidth(part).height(14.dp).clip(CircleShape).background(color.copy(alpha = 0.8f)))
                }
            }
            Text(
                stringResource(R.string.preview),
                Modifier.align(Alignment.Center).glass(CircleShape, GlassKind.Chrome, elevation = 6.dp).padding(horizontal = 22.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** One accent colour to pick: a round swatch, with a check and a ring when it is the one in use. */
@Composable
private fun AccentDot(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, bounceSpring(), label = "ring")
    Box(
        Modifier
            .pressScale(0.9f)
            .size(44.dp)
            .then(if (ring > 0.dp) Modifier.border(ring, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), CircleShape) else Modifier)
            .padding(ring + 2.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.RadioButton, onClickLabel = name, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, name, Modifier.size(20.dp), tint = Color.White)
    }
}

/**
 * Any colour: a rainbow bar to tap or drag along (like Celander's "Custom colour"). The knob shows the chosen hue;
 * without one (an accent of the list is in use) there is no knob.
 */
@Composable
private fun HueBar(hue: Float?, onHue: (Float) -> Unit) {
    // The same colours the custom accent will have, so the bar shows exactly what each point gives.
    val stops = remember { (0..24).map { com.cemail.app.ui.theme.customPalette((it * 15f).coerceAtMost(359.9f)).light } }
    com.cemail.app.ui.glass.GlassSlider(
        value = hue,
        onValueChange = onHue,
        valueRange = 0f..359.9f,
        track = Brush.horizontalGradient(stops),
        knobColor = hue?.let { com.cemail.app.ui.theme.customPalette(it).light } ?: Color.Transparent,
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
