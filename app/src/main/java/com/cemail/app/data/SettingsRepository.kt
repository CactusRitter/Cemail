package com.cemail.app.data

import android.content.Context
import com.cemail.app.ui.theme.Accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class AppSettings(
    val accent: Accent = Accent.INDIGO,
    /** A colour of the free hue bar (0–360) instead of [accent]; null = the chosen accent. */
    val customHue: Float? = null,
    /** Light or dark: following the phone, or always one of them. */
    val themeMode: com.cemail.app.ui.theme.ThemeMode = com.cemail.app.ui.theme.ThemeMode.SYSTEM,
    /** Background and glass look (Classic, Neutral, Aurora). */
    val themeStyle: com.cemail.app.ui.theme.ThemeStyle = com.cemail.app.ui.theme.ThemeStyle.AURORA,
    /** The whole look of the app (glass, or one of the looks being tried out). */
    val skin: com.cemail.app.ui.theme.Skin = com.cemail.app.ui.theme.Skin.GLASS,
    /** Glass with a solid, unblurred shadow under every surface (the 3D look of Brutalist). */
    val depth3d: Boolean = false,
    /** Left-handed: floating buttons on the left and, on tablets, the list on the right. */
    val listOnRight: Boolean = false,
    /** Where Settings sits on a wide screen: -1 start, 0 centre, 1 end (moved by dragging beside it). */
    val settingsDock: Int = 0,
    /** 0 = solid glass, 100 = fully see-through. */
    val transparency: Int = 65,
    /** Load remote images in messages without asking (off by default: they can be tracking pixels). */
    val alwaysShowImages: Boolean = false,
    // In a dark theme, messages are shown with dark colours (the WebView darkens them) instead of on a white page.
    val darkMail: Boolean = true,
    /** Split the message list into Today, Yesterday, Last week and Older sections. */
    val groupByDate: Boolean = true,
    val groupCards: Boolean = true,
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
    /** Instant notifications through the push relay (only has an effect when the keys are set). */
    val pushEnabled: Boolean = true,
    /** The account (by id) new messages are sent from when the screen does not say which (the unified inbox); null = the first one. */
    val defaultAccountId: String? = null,
    /** Per account (by id): the name shown for it, the name it sends with and its colour. */
    val accountLooks: Map<String, AccountLook> = emptyMap(),
    /** Account ids in the order the user wants them (menu, quick switch, Settings); accounts not listed go last. */
    val accountOrder: List<String> = emptyList(),
    /** What the small button above "write" does. */
    val quickAction: QuickAction = QuickAction.UNREAD,
) {
    fun lookOf(accountId: String): AccountLook = accountLooks[accountId] ?: AccountLook()

    /** The app's colour in its four shades: the custom one, or the chosen accent. */
    val accentPalette: com.cemail.app.ui.theme.AccentPalette
        get() = customHue?.let { com.cemail.app.ui.theme.customPalette(it) } ?: accent.palette
    /** The interval that applies to this account: its own, or the general one. */
    fun syncMinutesFor(accountId: String): Int = accountSync[accountId] ?: syncMinutes
}

/**
 * How one account looks and signs: [label] replaces its name in the lists ("Gmail", "Outlook"…), [senderName] is the
 * name recipients see (null = the server's own) and [color] its colour (null = one of the theme's, by position).
 */
/** What the quick button above "write" does. */
enum class QuickAction { UNREAD, STARRED, ACCOUNT, SEARCH, NONE }

/** [this] sorted by the user's [order]; accounts the order does not know keep their own order after the others. */
fun <T> List<T>.orderedBy(order: List<String>, id: (T) -> String): List<T> =
    if (order.isEmpty()) this else sortedBy { a -> order.indexOf(id(a)).let { if (it < 0) Int.MAX_VALUE else it } }

data class AccountLook(val label: String? = null, val senderName: String? = null, val color: com.cemail.app.ui.theme.AccountColor? = null)

/** Small app preferences, kept in SharedPreferences and exposed as a StateFlow (same approach as Cenotes). */
class SettingsRepository(context: Context) {
    private val signatureDir = java.io.File(context.filesDir, "signatures")
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read() = AppSettings(
        accent = prefs.getString(KEY_ACCENT, null)
            ?.let { name -> Accent.entries.firstOrNull { it.name == name } } ?: Accent.INDIGO,
        customHue = prefs.getFloat(KEY_HUE, -1f).takeIf { it >= 0f },
        themeStyle = prefs.getString(KEY_STYLE, null)
            ?.let { n -> com.cemail.app.ui.theme.ThemeStyle.entries.firstOrNull { it.name == n } } ?: com.cemail.app.ui.theme.ThemeStyle.AURORA,
        themeMode = prefs.getString(KEY_THEME, null)
            ?.let { n -> com.cemail.app.ui.theme.ThemeMode.entries.firstOrNull { it.name == n } } ?: com.cemail.app.ui.theme.ThemeMode.SYSTEM,
        skin = prefs.getString(KEY_SKIN, null)?.let { n -> com.cemail.app.ui.theme.Skin.entries.firstOrNull { it.name == n } } ?: com.cemail.app.ui.theme.Skin.GLASS,
        depth3d = prefs.getBoolean(KEY_DEPTH, false),
        listOnRight = prefs.getBoolean(KEY_LIST_RIGHT, false),
        settingsDock = prefs.getInt(KEY_SETTINGS_DOCK, 0).coerceIn(-1, 1),
        transparency = prefs.getInt(KEY_TRANSPARENCY, 65),
        alwaysShowImages = prefs.getBoolean(KEY_IMAGES, false),
        darkMail = prefs.getBoolean(KEY_DARK_MAIL, true),
        groupByDate = prefs.getBoolean(KEY_GROUP, true),
        groupCards = prefs.getBoolean(KEY_GROUP_CARDS, true),
        syncMinutes = prefs.getInt(KEY_SYNC, 15),
        showSenderCircle = prefs.getBoolean(KEY_CIRCLE, true),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        undoSendSeconds = prefs.getInt(KEY_UNDO_SEND, 5),
        accountSync = prefs.all.filterKeys { it.startsWith(KEY_ACCOUNT_SYNC) }.mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(KEY_ACCOUNT_SYNC) to it } }.toMap(),
        pushEnabled = prefs.getBoolean(KEY_PUSH, true),
        defaultAccountId = prefs.getString(KEY_DEFAULT_ACCOUNT, null),
        accountOrder = prefs.getString(KEY_ACCOUNT_ORDER, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
        quickAction = prefs.getString(KEY_QUICK, null)?.let { n -> QuickAction.entries.firstOrNull { it.name == n } } ?: QuickAction.UNREAD,
        accountLooks = prefs.all.keys.mapNotNull { k ->
            listOf(KEY_ACCOUNT_LABEL, KEY_SENDER_NAME, KEY_ACCOUNT_COLOR).firstOrNull { k.startsWith(it) }?.let { k.removePrefix(it) }
        }.toSet().associateWith { id -> readLook(id) },
        swipe = SwipeConfig(
            right = prefs.getString(KEY_SWIPE_RIGHT, null)?.let { n -> SwipeAction.entries.firstOrNull { it.name == n } } ?: SwipeAction.ARCHIVE,
            left = prefs.getString(KEY_SWIPE_LEFT, null)?.let { n -> SwipeAction.entries.firstOrNull { it.name == n } } ?: SwipeAction.TRASH,
            distance = prefs.getString(KEY_SWIPE_DISTANCE, null)?.let { n -> SwipeDistance.entries.firstOrNull { it.name == n } } ?: SwipeDistance.NORMAL,
            confirm = prefs.getBoolean(KEY_SWIPE_CONFIRM, false),
            undoSeconds = prefs.getInt(KEY_UNDO_SECONDS, 5),
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

    /** One of the eight colours (the custom one is cleared). */
    fun setAccent(accent: Accent) {
        prefs.edit().putString(KEY_ACCENT, accent.name).remove(KEY_HUE).apply()
        _state.value = _state.value.copy(accent = accent, customHue = null)
    }

    /** A colour of the free hue bar (0–360). */
    fun setCustomHue(hue: Float) {
        prefs.edit().putFloat(KEY_HUE, hue).apply()
        _state.value = _state.value.copy(customHue = hue)
    }

    fun setThemeStyle(style: com.cemail.app.ui.theme.ThemeStyle) {
        prefs.edit().putString(KEY_STYLE, style.name).apply()
        _state.value = _state.value.copy(themeStyle = style)
    }

    /**
     * Every setting as a small JSON text, to carry them to another phone or tablet (Settings → Backup of settings):
     * appearance, gestures, notifications, the per-account names, colours, intervals and folders, and the signatures
     * (text and HTML). Never any password or access token: those live elsewhere and are not part of it.
     */
    fun exportJson(): String {
        val values = buildJsonObject {
            prefs.all.forEach { (key, value) ->
                if (key == KEY_ASKED || key.startsWith(KEY_LAST_BG)) return@forEach // this device's own bookkeeping
                when (value) {
                    is Boolean -> put(key, buildJsonObject { put("b", value) })
                    is Int -> put(key, buildJsonObject { put("i", value) })
                    is Long -> put(key, buildJsonObject { put("l", value) })
                    is Float -> put(key, buildJsonObject { put("f", value) })
                    is String -> put(key, buildJsonObject { put("s", value) })
                    is Set<*> -> put(key, buildJsonObject { put("set", JsonArray(value.filterIsInstance<String>().map(::JsonPrimitive))) })
                }
            }
        }
        val signatures = buildJsonObject {
            signatureDir.listFiles()?.filter { it.extension == "html" }?.forEach { put(it.nameWithoutExtension, it.readText()) }
        }
        return buildJsonObject {
            put("app", "cemail")
            put("format", 1)
            put("settings", values)
            put("signatures", signatures)
        }.toString()
    }

    /** Applies a backup made by [exportJson]; returns false (and changes nothing) if [text] is not one. */
    fun importJson(text: String): Boolean {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return false
        if (root["app"]?.jsonPrimitive?.contentOrNull != "cemail") return false
        val values = root["settings"] as? JsonObject ?: return false
        val edit = prefs.edit()
        values.forEach { (key, entry) ->
            val typed = entry as? JsonObject ?: return@forEach
            typed["b"]?.jsonPrimitive?.booleanOrNull?.let { edit.putBoolean(key, it) }
            typed["i"]?.jsonPrimitive?.intOrNull?.let { edit.putInt(key, it) }
            typed["l"]?.jsonPrimitive?.longOrNull?.let { edit.putLong(key, it) }
            typed["f"]?.jsonPrimitive?.floatOrNull?.let { edit.putFloat(key, it) }
            typed["s"]?.jsonPrimitive?.contentOrNull?.let { edit.putString(key, it) }
            (typed["set"] as? JsonArray)?.let { list -> edit.putStringSet(key, list.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()) }
        }
        edit.apply()
        (root["signatures"] as? JsonObject)?.forEach { (name, html) ->
            val safe = name.filter { it.isLetterOrDigit() || it == '@' || it == '.' }
            html.jsonPrimitive.contentOrNull?.let { signatureDir.mkdirs(); java.io.File(signatureDir, "$safe.html").writeText(it) }
        }
        _state.value = read()
        return true
    }

    fun setSkin(skin: com.cemail.app.ui.theme.Skin) {
        prefs.edit().putString(KEY_SKIN, skin.name).apply()
        _state.value = _state.value.copy(skin = skin)
    }

    fun setDepth3d(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEPTH, value).apply()
        _state.value = _state.value.copy(depth3d = value)
    }

    fun setSettingsDock(value: Int) {
        prefs.edit().putInt(KEY_SETTINGS_DOCK, value.coerceIn(-1, 1)).apply()
        _state.value = _state.value.copy(settingsDock = value.coerceIn(-1, 1))
    }

    fun setListOnRight(value: Boolean) {
        prefs.edit().putBoolean(KEY_LIST_RIGHT, value).apply()
        _state.value = _state.value.copy(listOnRight = value)
    }

    fun setThemeMode(mode: com.cemail.app.ui.theme.ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setTransparency(value: Int) {
        prefs.edit().putInt(KEY_TRANSPARENCY, value).apply()
        _state.value = _state.value.copy(transparency = value)
    }

    fun setDarkMail(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MAIL, value).apply()
        _state.value = _state.value.copy(darkMail = value)
    }

    fun setAlwaysShowImages(value: Boolean) {
        prefs.edit().putBoolean(KEY_IMAGES, value).apply()
        _state.value = _state.value.copy(alwaysShowImages = value)
    }

    fun setGroupCards(value: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_CARDS, value).apply()
        _state.value = _state.value.copy(groupCards = value)
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

    fun setPushEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH, value).apply()
        _state.value = _state.value.copy(pushEnabled = value)
    }

    private fun readLook(id: String) = AccountLook(
        label = prefs.getString(KEY_ACCOUNT_LABEL + id, null),
        senderName = prefs.getString(KEY_SENDER_NAME + id, null),
        color = prefs.getString(KEY_ACCOUNT_COLOR + id, null)?.let { com.cemail.app.ui.theme.AccountColor.fromStored(it) },
    )

    /** Saves how an account looks; blank texts and a null colour go back to the defaults. */
    fun setAccountLook(accountId: String, look: AccountLook) {
        fun put(key: String, value: String?) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key + accountId) else putString(key + accountId, value.trim())
        }.apply()
        put(KEY_ACCOUNT_LABEL, look.label)
        put(KEY_SENDER_NAME, look.senderName)
        put(KEY_ACCOUNT_COLOR, look.color?.name)
        _state.value = _state.value.copy(accountLooks = _state.value.accountLooks + (accountId to readLook(accountId)))
    }

    fun setAccountOrder(ids: List<String>) {
        prefs.edit().putString(KEY_ACCOUNT_ORDER, ids.joinToString(",")).apply()
        _state.value = _state.value.copy(accountOrder = ids)
    }

    fun setQuickAction(action: QuickAction) {
        prefs.edit().putString(KEY_QUICK, action.name).apply()
        _state.value = _state.value.copy(quickAction = action)
    }

    fun setDefaultAccount(accountId: String?) {
        prefs.edit().apply { if (accountId == null) remove(KEY_DEFAULT_ACCOUNT) else putString(KEY_DEFAULT_ACCOUNT, accountId) }.apply()
        _state.value = _state.value.copy(defaultAccountId = accountId)
    }

    fun setSwipeRight(action: SwipeAction) {
        prefs.edit().putString(KEY_SWIPE_RIGHT, action.name).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(right = action))
    }

    fun setSwipeLeft(action: SwipeAction) {
        prefs.edit().putString(KEY_SWIPE_LEFT, action.name).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(left = action))
    }

    fun setSwipeConfirm(value: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_CONFIRM, value).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(confirm = value))
    }

    fun setUndoSeconds(value: Int) {
        prefs.edit().putInt(KEY_UNDO_SECONDS, value).apply()
        _state.value = _state.value.copy(swipe = _state.value.swipe.copy(undoSeconds = value))
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
        const val KEY_PUSH = "push_enabled"
        const val KEY_DEFAULT_ACCOUNT = "default_account"
        const val KEY_ACCOUNT_ORDER = "account_order"
        const val KEY_QUICK = "quick_action"
        const val KEY_HUE = "accent_hue"
        const val KEY_THEME = "theme_mode"
        const val KEY_STYLE = "theme_style"
        const val KEY_SKIN = "skin"
        const val KEY_DEPTH = "depth_3d"
        const val KEY_LIST_RIGHT = "list_right"
        const val KEY_SETTINGS_DOCK = "settings_dock"
        const val KEY_ACCOUNT_LABEL = "account_label_"
        const val KEY_SENDER_NAME = "account_sender_"
        const val KEY_ACCOUNT_COLOR = "account_color_"
        const val KEY_SWIPE_CONFIRM = "swipe_confirm"
        const val KEY_UNDO_SECONDS = "undo_seconds"
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
        const val KEY_DARK_MAIL = "dark_mail"
        const val KEY_GROUP = "group_by_date"
        const val KEY_GROUP_CARDS = "group_cards"
        const val KEY_ACCENT = "accent"
        const val KEY_TRANSPARENCY = "transparency"
    }
}
