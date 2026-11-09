package com.cemail.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.cemail.app.R
import com.cemail.app.data.AppSettings

// Diseño compartido con las apps "Ce" (Celander, Cenotes): cristal esmerilado sobre un fondo
// suave de lavanda/índigo. Ver docs/DESIGN.md. Los valores son los mismos que en Cenotes.

/** Curated accent colours, each tuned for light and dark (same idea as Celander's calendar colours). */
enum class Accent(
    @StringRes val label: Int,
    val light: Color,
    val dark: Color,
    val lightContainer: Color,
    val darkContainer: Color,
) {
    INDIGO(R.string.accent_indigo, Color(0xFF4A4FD8), Color(0xFFA9ADFF), Color(0xFFE1E2FB), Color(0xFF2C3275)),
    VIOLET(R.string.accent_violet, Color(0xFF7B4FD6), Color(0xFFC7AEFF), Color(0xFFECE2FB), Color(0xFF45307A)),
    BLUE(R.string.accent_blue, Color(0xFF1F6FD1), Color(0xFF9CC7FF), Color(0xFFDCEAFB), Color(0xFF1D3F6E)),
    GREEN(R.string.accent_green, Color(0xFF2E8B57), Color(0xFF8FD9AE), Color(0xFFDDF1E5), Color(0xFF1E4D35)),
    CORAL(R.string.accent_coral, Color(0xFFD9534F), Color(0xFFFFB0A8), Color(0xFFFBE1DE), Color(0xFF6E2A27)),
    AMBER(R.string.accent_amber, Color(0xFFB7791F), Color(0xFFF5C77E), Color(0xFFF8EBD3), Color(0xFF5E4214)),
}

/** Colours for the frosted-glass surfaces; their opacity follows the transparency setting. */
@Immutable
data class GlassColors(
    val dark: Boolean,
    /** Cards (message rows, settings cards), sitting on the background. */
    val card: Color,
    /** Floating buttons and pills, blurred over scrolling content. */
    val chrome: Color,
    /** Highlighted cards (e.g. unread / selected), tinted with the accent. */
    val pinned: Color,
    /** Dialogs, menus and snackbars: nearly opaque so text stays readable. */
    val menu: Color,
    /** Opaque fallback colour behind the blur (used where blur isn't available). */
    val base: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val shadow: Color,
)

fun glassColors(dark: Boolean, transparency: Int, accent: Accent): GlassColors {
    val t = transparency.coerceIn(0, 100) / 100f
    val cardAlpha = 0.12f + 0.8f * (1f - t) // 65 % → 0.40
    val chromeAlpha = (cardAlpha + 0.3f).coerceAtMost(0.92f)
    val pinnedAlpha = (cardAlpha + 0.25f).coerceAtMost(1f)
    return if (dark) {
        GlassColors(
            dark = true,
            card = Color(0xFF262C45).copy(alpha = cardAlpha),
            chrome = Color(0xFF262C45).copy(alpha = chromeAlpha),
            pinned = accent.darkContainer.copy(alpha = pinnedAlpha),
            menu = Color(0xFF262B40).copy(alpha = 0.97f),
            base = Color(0xFF171B2E),
            borderTop = Color.White.copy(alpha = 0.16f),
            borderBottom = Color.White.copy(alpha = 0.04f),
            shadow = Color.Black.copy(alpha = 0.5f),
        )
    } else {
        GlassColors(
            dark = false,
            card = Color.White.copy(alpha = cardAlpha),
            chrome = Color.White.copy(alpha = chromeAlpha),
            pinned = accent.lightContainer.copy(alpha = pinnedAlpha),
            menu = Color(0xFFF9F7FC).copy(alpha = 0.97f),
            base = Color(0xFFF1ECF4),
            borderTop = Color.White.copy(alpha = 0.85f),
            borderBottom = Color.White.copy(alpha = 0.25f),
            shadow = Color(0xFF3A3060).copy(alpha = 0.25f),
        )
    }
}

val LocalGlass = staticCompositionLocalOf { glassColors(dark = false, transparency = 65, accent = Accent.INDIGO) }

@Composable
fun CemailTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val accent = settings.accent
    val colors = if (dark) {
        darkColorScheme(
            primary = accent.dark,
            onPrimary = Color(0xFF11132B),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = Color(0xFFE6E5F7),
            surface = Color(0xFF1C2033),
            onSurface = Color(0xFFF2F1F7),
            onSurfaceVariant = Color(0xFFB5B3C2),
            background = Color(0xFF141B33),
            error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.light,
            surface = Color(0xFFF7F5FA),
            onSurface = Color(0xFF1B1B21),
            onSurfaceVariant = Color(0xFF5D5B66),
            background = Color(0xFFECE6F6),
            error = Color(0xFFC62828),
        )
    }
    val glass = glassColors(dark, settings.transparency, accent)
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalGlass provides glass,
            LocalContentColor provides colors.onSurface,
            content = content,
        )
    }
}
