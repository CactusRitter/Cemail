package com.cemail.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.cemail.app.R

/**
 * The colours that mark accounts (unified inbox, pictures, the account list): eight vivid ones, apart from the app's
 * calmer accent colours so an account stands out at a glance. [light] is for light themes, [dark] a brighter one for
 * dark themes.
 */
enum class AccountColor(@StringRes val label: Int, val light: Color, val dark: Color) {
    RED(R.string.account_color_red, Color(0xFFE53935), Color(0xFFFF5A52)),
    ORANGE(R.string.account_color_orange, Color(0xFFF57C00), Color(0xFFFF9F43)),
    YELLOW(R.string.account_color_yellow, Color(0xFFF5C400), Color(0xFFFFE04D)),
    GREEN(R.string.account_color_green, Color(0xFF00B84A), Color(0xFF3FE07A)),
    CYAN(R.string.account_color_cyan, Color(0xFF00ACC9), Color(0xFF33E1FF)),
    BLUE(R.string.account_color_blue, Color(0xFF2962FF), Color(0xFF5C8DFF)),
    PURPLE(R.string.account_color_purple, Color(0xFF7C3AED), Color(0xFFA98BFF)),
    MAGENTA(R.string.account_color_magenta, Color(0xFFD81BBF), Color(0xFFFF5CE1)),
    ;

    /** Soft tints of the same colour, behind an account's initial. */
    val lightContainer: Color get() = lerp(light, Color.White, 0.8f)
    val darkContainer: Color get() = lerp(dark, Color.Black, 0.7f)

    companion object {
        /** The colour an account gets by its place in the list when none was chosen: the four most different first. */
        val automatic = listOf(RED, BLUE, GREEN, YELLOW, CYAN, MAGENTA, ORANGE, PURPLE)

        /** A stored colour; older versions stored one of the app's accents, which becomes the nearest vivid colour. */
        fun fromStored(name: String): AccountColor? = entries.firstOrNull { it.name == name } ?: when (name) {
            "INDIGO" -> BLUE
            "TEAL" -> CYAN
            "AMBER" -> YELLOW
            "CORAL" -> RED
            "PINK" -> MAGENTA
            "VIOLET" -> PURPLE
            else -> null
        }
    }
}
