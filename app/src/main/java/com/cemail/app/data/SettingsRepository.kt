package com.cemail.app.data

import android.content.Context
import com.cemail.app.ui.theme.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val accent: Accent = Accent.INDIGO,
    /** 0 = solid glass, 100 = fully see-through. */
    val transparency: Int = 65,
    /** Load remote images in messages without asking (off by default: they can be tracking pixels). */
    val alwaysShowImages: Boolean = false,
    /** Split the message list into Today, Yesterday, Last week and Older sections. */
    val groupByDate: Boolean = true,
    /** Minutes between automatic checks for new mail; 0 = manual (never on its own). */
    val syncMinutes: Int = 15,
    /** Round picture (the initial) next to each message in the list. */
    val showSenderCircle: Boolean = true,
    /** Show a notification for mail that arrives while the app is closed. */
    val notifications: Boolean = true,
    /** Seconds a sent message waits so it can still be undone; 0 = sent at once. */
    val undoSendSeconds: Int = 5,
    /** Per account (by id): minutes between automatic checks; missing = the general interval above. */
    val accountSync: Map<String, Int> = emptyMap(),
    /** Per account (by id): the user labels that are not synchronised (nor shown in the menu). */
    val hiddenLabels: Map<String, Set<String>> = emptyMap(),
    /** Swiping a message from left to right, from right to left, and how far it has to go. */
    val swipe: SwipeConfig = SwipeConfig(),
) {
    /** The interval that applies to this account: its own, or the general one. */
    fun syncMinutesFor(accountId: String): Int = accountSync[accountId] ?: syncMinutes
}

/** Small app preferences, kept in SharedPreferences and exposed as a StateFlow (same approach as Cenotes). */
class SettingsRepository(context: Context) {
    private val signatureDir = java.io.File(context.filesDir, "signatures")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        accent = prefs.getString(KEY_ACCENT, null)
            ?.let { name -> Accent.entries.firstOrNull { it.name == name } } ?: Accent.INDIGO,
        transparency = prefs.getInt(KEY_TRANSPARENCY, 65),
        alwaysShowImages = prefs.getBoolean(KEY_IMAGES, false),
        groupByDate = prefs.getBoolean(KEY_GROUP, true),
        syncMinutes = prefs.getInt(KEY_SYNC, 15),
        showSenderCircle = prefs.getBoolean(KEY_CIRCLE, true),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        undoSendSeconds = prefs.getInt(KEY_UNDO_SEND, 5),
        accountSync = prefs.all.filterKeys { it.startsWith(KEY_ACCOUNT_SYNC) }.mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(KEY_ACCOUNT_SYNC) to it } }.toMap(),
        swipe = SwipeConfig(
            right = prefs.getString(KEY_SWIPE_RIGHT, null)?.let { n -> SwipeAction.entries.firstOrNull { it.name == n } } ?: SwipeAction.ARCHIVE,
            left = prefs.getString(KEY_SWIPE_LEFT, null)?.let { n -> SwipeAction.entries.firstOrNull { it.name == n } } ?: SwipeAction.TRASH,
            distance = prefs.getString(KEY_SWIPE_DISTANCE, null)?.let { n -> SwipeDistance.entries.firstOrNull { it.name == n } } ?: SwipeDistance.NORMAL,
        ),
        hiddenLabels = prefs.all.filterKeys { it.startsWith(KEY_HIDDEN_LABELS) }.mapNotNull { (k, v) -> (v as? Set<*>)?.let { k.removePrefix(KEY_HIDDEN_LABELS) to it.filterIsInstance<String>().toSet() } }.toMap(),
    )


    /** The signature added to new messages written from [email] (empty = none). */
    fun signature(email: String): String = prefs.getString("signature_${email.lowercase()}", "").orEmpty()

    fun setSignature(email: String, text: String) {
        prefs.edit().putString("signature_${email.lowercase()}", text).apply()
    }

    private fun signatureFile(email: String) =
        java.io.File(signatureDir, email.lowercase().filter { it.isLetterOrDigit() || it == '@' || it == '.' } + ".html")

    /** The account's HTML signature (imported from a message), or null. */
    fun signatureHtml(email: String): String? = signatureFile(email).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun setSignatureHtml(email: String, html: String?) {
        signatureDir.mkdirs()
        val file = signatureFile(email)
        if (html.isNullOrBlank()) file.delete() else file.writeText(html)
    }
    fun setNotifications(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
        _state.value = _state.value.copy(notifications = value)
    }

    /** Whether the system permission dialog was already shown (so it is not repeated on every start). */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
        _state.value = _state.value.copy(accent = accent)
    }

    fun setTransparency(value: Int) {
        prefs.edit().putInt(KEY_TRANSPARENCY, value).apply()
        _state.value = _state.value.copy(transparency = value)
    }

    fun setAlwaysShowImages(value: Boolean) {
        prefs.edit().putBoolean(KEY_IMAGES, value).apply()
        _state.value = _state.value.copy(alwaysShowImages = value)
    }

    fun setGroupByDate(value: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP, value).apply()
        _state.value = _state.value.copy(groupByDate = value)
    }

    /** [minutes] null = follow the general interval. */
    fun setAccountSync(accountId: String, minutes: Int?) {
        prefs.edit().apply { if (minutes == null) remove(KEY_ACCOUNT_SYNC + accountId) else putInt(KEY_ACCOUNT_SYNC + accountId, minutes) }.apply()
        val map = _state.value.accountSync.toMutableMap().apply { if (minutes == null) remove(accountId) else put(accountId, minutes) }
        _state.value = _state.value.copy(accountSync = map)
    }

    fun setHiddenLabels(accountId: String, ids: Set<String>) {
        prefs.edit().apply { if (ids.isEmpty()) remove(KEY_HIDDEN_LABELS + accountId) else putStringSet(KEY_HIDDEN_LABELS + accountId, ids) }.apply()
        val map = _state.value.hiddenLabels.toMutableMap().apply { if (ids.isEmpty()) remove(accountId) else put(accountId, ids) }
        _state.value = _state.value.copy(hiddenLabels = map)
    }

    /** When the background worker last checked this account (so each account keeps its own pace). */
    fun lastBackgroundCheck(accountId: String): Long = prefs.getLong(KEY_LAST_BG + accountId, 0L)
    fun setLastBackgroundCheck(accountId: String, millis: Long) = prefs.edit().putLong(KEY_LAST_BG + accountId, millis).apply()

    fun setSwipeRight(action: SwipeAction) {
        prefs.edit().putString(KEY_SWIPE_RIGHT, action.name).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(right = action))
    }

    fun setSwipeLeft(action: SwipeAction) {
        prefs.edit().putString(KEY_SWIPE_LEFT, action.name).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(left = action))
    }

    fun setSwipeDistance(distance: SwipeDistance) {
        prefs.edit().putString(KEY_SWIPE_DISTANCE, distance.name).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(distance = distance))
    }

    fun setSyncMinutes(value: Int) {
        prefs.edit().putInt(KEY_SYNC, value).apply()
        _state.value = _state.value.copy(syncMinutes = value)
    }

    fun setUndoSendSeconds(value: Int) {
        prefs.edit().putInt(KEY_UNDO_SEND, value).apply()
        _state.value = _state.value.copy(undoSendSeconds = value)
    }

    fun setShowSenderCircle(value: Boolean) {
        prefs.edit().putBoolean(KEY_CIRCLE, value).apply()
        _state.value = _state.value.copy(showSenderCircle = value)
    }

    private companion object {
        const val KEY_SWIPE_RIGHT = "swipe_right"
        const val KEY_SWIPE_LEFT = "swipe_left"
        const val KEY_SWIPE_DISTANCE = "swipe_distance"
        const val KEY_ACCOUNT_SYNC = "account_sync_"
        const val KEY_HIDDEN_LABELS = "hidden_labels_"
        const val KEY_LAST_BG = "last_bg_check_"
        const val KEY_CIRCLE = "show_sender_circle"
        const val KEY_UNDO_SEND = "undo_send_seconds"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_ASKED = "asked_notification_permission"
        const val KEY_SYNC = "sync_minutes"
        const val KEY_IMAGES = "always_show_images"
        const val KEY_GROUP = "group_by_date"
        const val KEY_ACCENT = "accent"
        const val KEY_TRANSPARENCY = "transparency"
    }
}
