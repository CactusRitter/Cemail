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
)

/** Small app preferences, kept in SharedPreferences and exposed as a StateFlow (same approach as Cenotes). */
class SettingsRepository(context: Context) {
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
    )

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

    fun setSyncMinutes(value: Int) {
        prefs.edit().putInt(KEY_SYNC, value).apply()
        _state.value = _state.value.copy(syncMinutes = value)
    }

    private companion object {
        const val KEY_SYNC = "sync_minutes"
        const val KEY_IMAGES = "always_show_images"
        const val KEY_GROUP = "group_by_date"
        const val KEY_ACCENT = "accent"
        const val KEY_TRANSPARENCY = "transparency"
    }
}
