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

/**
 * One accent colour in its four shades: the colour itself on light surfaces, a lighter one for dark surfaces, and
 * the soft "container" tints behind selected things (light and dark). All four come from the same hue.
 */
@Immutable
data class AccentPalette(val light: Color, val dark: Color, val lightContainer: Color, val darkContainer: Color)

/** The four shades of [base] (its hue, with the lightness each use needs). */
fun paletteFrom(base: Color): AccentPalette {
    val hsl = hslOf(base)
    fun shade(saturation: Float, lightness: Float) = Color.hsl(hsl[0], saturation.coerceIn(0f, 1f), lightness)
    return AccentPalette(
        light = base,
        dark = shade(hsl[1] * 0.9f + 0.1f, 0.78f),
        lightContainer = shade(hsl[1] * 0.75f, 0.93f),
        darkContainer = shade(hsl[1] * 0.5f, 0.30f),
    )
}

/** Hue (0–360), saturation and lightness (0–1) of [color]; plain maths, so it also runs in unit tests. */
private fun hslOf(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return floatArrayOf(h % 360f, s, l)
}

/**
 * A colour of the free hue bar ("custom colour"), as calm as the curated ones: yellows and cyans are naturally much
 * brighter than blues at the same lightness, so they are darkened a little (yellow turns ochre, like Celander's).
 */
fun customPalette(hue: Float): AccentPalette {
    val h = hue.coerceIn(0f, 359.9f)
    fun bump(center: Float, width: Float) = (1f - kotlin.math.abs(h - center) / width).coerceAtLeast(0f)
    val lightness = 0.48f - 0.14f * bump(75f, 70f) - 0.08f * bump(175f, 45f)
    val saturation = 0.58f - 0.08f * bump(75f, 70f)
    return paletteFrom(Color.hsl(h, saturation, lightness))
}

/**
 * The accent colours: the same eight as Celander, in its order. They colour buttons, selections, the background and
 * the glass, and also mark accounts (Settings → the account). The names stay the stored ones (CORAL, AMBER…).
 */
enum class Accent(@StringRes val label: Int, base: Color) {
    INDIGO(R.string.accent_indigo, Color(0xFF4A54D6)),
    BLUE(R.string.accent_blue, Color(0xFF296DB5)),
    TEAL(R.string.accent_teal, Color(0xFF1C7B77)),
    GREEN(R.string.accent_green, Color(0xFF1D7F46)),
    AMBER(R.string.accent_amber, Color(0xFF8F6721)),
    CORAL(R.string.accent_coral, Color(0xFFB54529)),
    PINK(R.string.accent_pink, Color(0xFFBD2B65)),
    VIOLET(R.string.accent_violet, Color(0xFF8B3AD2)),
    ;

    val palette: AccentPalette = paletteFrom(base)
    val light: Color get() = palette.light
    val dark: Color get() = palette.dark
    val lightContainer: Color get() = palette.lightContainer
    val darkContainer: Color get() = palette.darkContainer
}

/** Light or dark: following the phone, or always one of them. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Whether the app is dark now, given [mode]. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The look of the background and of the glass tint, apart from light/dark and the accent. [CLASSIC] is the soft
 * lavender one; [NEUTRAL] is mist grey (light) or graphite (dark) with almost no colour; [AURORA] is a deep sky with
 * big vivid lights behind the glass. Each has a light and a dark version.
 */
enum class ThemeStyle(@StringRes val label: Int) {
    CLASSIC(R.string.style_classic),
    NEUTRAL(R.string.style_neutral),
    AURORA(R.string.style_aurora),
}

/** A soft round light of the background: [x], [y] are fractions of the screen, [radius] of its longest side. */
@Immutable
data class Blob(val color: Color, val x: Float, val y: Float, val radius: Float)

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
    /** The vertical gradient of the background, top to bottom, and the soft lights drawn over it. */
    val gradient: List<Color>,
    val blobs: List<Blob>,
    /** Page and surface colours of the Material scheme for this style. */
    val background: Color,
    val surface: Color,
)

fun glassColors(
    dark: Boolean,
    transparency: Int,
    accent: AccentPalette,
    style: ThemeStyle = ThemeStyle.CLASSIC,
): GlassColors {
    val t = transparency.coerceIn(0, 100) / 100f
    val cardAlpha = 0.12f + 0.8f * (1f - t) // 65 % → 0.40
    val chromeAlpha = (cardAlpha + 0.3f).coerceAtMost(0.92f)
    val pinnedAlpha = (cardAlpha + 0.25f).coerceAtMost(1f)
    val tint = if (dark) accent.dark else accent.light
    return if (dark) {
        val card = when (style) {
            ThemeStyle.CLASSIC -> Color(0xFF262C45)
            ThemeStyle.NEUTRAL -> Color(0xFF2C2E33)
            ThemeStyle.AURORA -> Color(0xFF1F2440)
        }
        val menu = when (style) {
            ThemeStyle.CLASSIC -> Color(0xFF262B40)
            ThemeStyle.NEUTRAL -> Color(0xFF26282C)
            ThemeStyle.AURORA -> Color(0xFF1B2038)
        }
        GlassColors(
            dark = true,
            card = card.copy(alpha = cardAlpha),
            chrome = card.copy(alpha = chromeAlpha),
            pinned = accent.darkContainer.copy(alpha = pinnedAlpha),
            menu = menu.copy(alpha = 0.97f),
            base = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFF171B2E)
                ThemeStyle.NEUTRAL -> Color(0xFF17181B)
                ThemeStyle.AURORA -> Color(0xFF0E1122)
            },
            borderTop = Color.White.copy(alpha = 0.16f),
            borderBottom = Color.White.copy(alpha = 0.04f),
            shadow = Color.Black.copy(alpha = 0.5f),
            gradient = when (style) {
                ThemeStyle.CLASSIC -> listOf(Color(0xFF141B33), Color(0xFF171A2B), Color(0xFF1D1825))
                ThemeStyle.NEUTRAL -> listOf(Color(0xFF1A1B1E), Color(0xFF151618), Color(0xFF111214))
                ThemeStyle.AURORA -> listOf(Color(0xFF0B0E1D), Color(0xFF0D1022), Color(0xFF0A0C18))
            },
            blobs = when (style) {
                ThemeStyle.CLASSIC -> listOf(
                    Blob(Color(0xFF1C4250).copy(alpha = 0.55f), 0.1f, 0.45f, 0.5f),
                    Blob(tint.copy(alpha = 0.16f), 0.9f, 0.08f, 0.45f),
                    Blob(Color(0xFF3A2440).copy(alpha = 0.5f), 0.85f, 0.95f, 0.5f),
                )
                ThemeStyle.NEUTRAL -> listOf(
                    Blob(tint.copy(alpha = 0.12f), 0.9f, 0.05f, 0.5f),
                    Blob(Color.White.copy(alpha = 0.04f), 0.1f, 0.7f, 0.5f),
                )
                ThemeStyle.AURORA -> listOf(
                    Blob(tint.copy(alpha = 0.6f), 0.05f, 0.1f, 0.7f),
                    Blob(Color(0xFF1FC28A).copy(alpha = 0.4f), 1f, 0.45f, 0.6f),
                    Blob(Color(0xFF8B3AD2).copy(alpha = 0.55f), 0.1f, 0.95f, 0.65f),
                )
            },
            background = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFF141B33)
                ThemeStyle.NEUTRAL -> Color(0xFF151618)
                ThemeStyle.AURORA -> Color(0xFF0C0F1E)
            },
            surface = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFF1C2033)
                ThemeStyle.NEUTRAL -> Color(0xFF1E1F23)
                ThemeStyle.AURORA -> Color(0xFF14172B)
            },
        )
    } else {
        val menu = when (style) {
            ThemeStyle.CLASSIC -> Color(0xFFF9F7FC)
            ThemeStyle.NEUTRAL -> Color(0xFFF9FAFA)
            ThemeStyle.AURORA -> Color(0xFFF8F8FD)
        }
        GlassColors(
            dark = false,
            card = Color.White.copy(alpha = cardAlpha),
            chrome = Color.White.copy(alpha = chromeAlpha),
            pinned = accent.lightContainer.copy(alpha = pinnedAlpha),
            menu = menu.copy(alpha = 0.97f),
            base = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFFF1ECF4)
                ThemeStyle.NEUTRAL -> Color(0xFFEFF0F2)
                ThemeStyle.AURORA -> Color(0xFFEEF0FA)
            },
            borderTop = Color.White.copy(alpha = 0.85f),
            borderBottom = Color.White.copy(alpha = 0.25f),
            shadow = when (style) {
                ThemeStyle.NEUTRAL -> Color(0xFF30343C).copy(alpha = 0.22f)
                else -> Color(0xFF3A3060).copy(alpha = 0.25f)
            },
            gradient = when (style) {
                ThemeStyle.CLASSIC -> listOf(Color(0xFFECE6F6), Color(0xFFF4EDF0), Color(0xFFEEF1EB))
                ThemeStyle.NEUTRAL -> listOf(Color(0xFFF4F5F7), Color(0xFFEFF0F2), Color(0xFFE9EBEE))
                ThemeStyle.AURORA -> listOf(Color(0xFFE9ECFA), Color(0xFFF3EBF7), Color(0xFFE6F3F2))
            },
            blobs = when (style) {
                ThemeStyle.CLASSIC -> listOf(
                    Blob(tint.copy(alpha = 0.12f), 0.15f, 0.05f, 0.5f),
                    Blob(Color(0xFFF7D9CF).copy(alpha = 0.7f), 0.95f, 0.55f, 0.45f),
                    Blob(Color(0xFFDCEFE3).copy(alpha = 0.8f), 0.05f, 0.95f, 0.5f),
                )
                ThemeStyle.NEUTRAL -> listOf(
                    Blob(tint.copy(alpha = 0.08f), 0.15f, 0.05f, 0.5f),
                    Blob(Color.White.copy(alpha = 0.9f), 0.9f, 0.5f, 0.5f),
                )
                ThemeStyle.AURORA -> listOf(
                    Blob(tint.copy(alpha = 0.4f), 0.05f, 0.08f, 0.7f),
                    Blob(Color(0xFFF2A3C6).copy(alpha = 0.55f), 1f, 0.45f, 0.6f),
                    Blob(Color(0xFF86DCCB).copy(alpha = 0.6f), 0.1f, 0.95f, 0.65f),
                )
            },
            background = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFFECE6F6)
                ThemeStyle.NEUTRAL -> Color(0xFFEFF0F2)
                ThemeStyle.AURORA -> Color(0xFFE9ECFA)
            },
            surface = when (style) {
                ThemeStyle.CLASSIC -> Color(0xFFF7F5FA)
                ThemeStyle.NEUTRAL -> Color(0xFFF6F7F8)
                ThemeStyle.AURORA -> Color(0xFFF5F6FC)
            },
        )
    }
}

val LocalGlass = staticCompositionLocalOf { glassColors(dark = false, transparency = 65, accent = Accent.INDIGO.palette) }

@Composable
fun CemailTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = settings.themeMode.isDark()
    val accent = settings.accentPalette
    val glass = glassColors(dark, settings.transparency, accent, settings.themeStyle)
    val colors = if (dark) {
        darkColorScheme(
            primary = accent.dark,
            onPrimary = Color(0xFF11132B),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = Color(0xFFE6E5F7),
            surface = glass.surface,
            onSurface = Color(0xFFF2F1F7),
            onSurfaceVariant = Color(0xFFB5B3C2),
            background = glass.background,
            error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.light,
            surface = glass.surface,
            onSurface = Color(0xFF1B1B21),
            onSurfaceVariant = Color(0xFF5D5B66),
            background = glass.background,
            error = Color(0xFFC62828),
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalGlass provides glass,
            LocalContentColor provides colors.onSurface,
            content = content,
        )
    }
}
