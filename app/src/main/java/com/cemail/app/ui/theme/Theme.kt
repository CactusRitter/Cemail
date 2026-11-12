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
    AURORA(R.string.style_aurora),
    CLASSIC(R.string.style_classic),
    NEUTRAL(R.string.style_neutral),
    SUNSET(R.string.style_sunset),
    OCEAN(R.string.style_ocean),
    NEON(R.string.style_neon),
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

/**
 * What one style looks like in one mode: the gradient and the lights of the background, the tints of the glass and the
 * page/surface colours. A light with a null colour takes the accent. Kept as a table so a new style is one entry.
 */
private class Look(
    val gradient: List<Long>,
    /** Lights: colour (null = the accent), alpha, x, y, radius. */
    val blobs: List<LightSpec>,
    val card: Long,
    val menu: Long,
    val base: Long,
    val background: Long,
    val surface: Long,
    val shadow: Long = 0xFF3A3060,
)

private class LightSpec(val color: Long?, val alpha: Float, val x: Float, val y: Float, val radius: Float)

private fun look(style: ThemeStyle, dark: Boolean): Look = if (dark) when (style) {
    ThemeStyle.CLASSIC -> Look(
        gradient = listOf(0xFF141B33, 0xFF171A2B, 0xFF1D1825),
        blobs = listOf(LightSpec(0xFF1C4250, 0.55f, 0.1f, 0.45f, 0.5f), LightSpec(null, 0.16f, 0.9f, 0.08f, 0.45f), LightSpec(0xFF3A2440, 0.5f, 0.85f, 0.95f, 0.5f)),
        card = 0xFF262C45, menu = 0xFF262B40, base = 0xFF171B2E, background = 0xFF141B33, surface = 0xFF1C2033,
    )
    ThemeStyle.NEUTRAL -> Look(
        gradient = listOf(0xFF1A1B1E, 0xFF151618, 0xFF111214),
        blobs = listOf(LightSpec(null, 0.12f, 0.9f, 0.05f, 0.5f), LightSpec(0xFFFFFFFF, 0.04f, 0.1f, 0.7f, 0.5f)),
        card = 0xFF2C2E33, menu = 0xFF26282C, base = 0xFF17181B, background = 0xFF151618, surface = 0xFF1E1F23,
    )
    ThemeStyle.AURORA -> Look(
        gradient = listOf(0xFF0B0E1D, 0xFF0D1022, 0xFF0A0C18),
        blobs = listOf(LightSpec(null, 0.6f, 0.05f, 0.1f, 0.7f), LightSpec(0xFF1FC28A, 0.4f, 1f, 0.45f, 0.6f), LightSpec(0xFF8B3AD2, 0.55f, 0.1f, 0.95f, 0.65f)),
        card = 0xFF1F2440, menu = 0xFF1B2038, base = 0xFF0E1122, background = 0xFF0C0F1E, surface = 0xFF14172B,
    )
    ThemeStyle.SUNSET -> Look(
        gradient = listOf(0xFF1F1220, 0xFF241219, 0xFF1A1014),
        blobs = listOf(LightSpec(0xFFFF7A59, 0.5f, 0.95f, 0.1f, 0.65f), LightSpec(0xFFE0407F, 0.5f, 0.05f, 0.55f, 0.6f), LightSpec(null, 0.4f, 0.9f, 0.95f, 0.6f)),
        card = 0xFF38222E, menu = 0xFF331E2A, base = 0xFF1E1218, background = 0xFF1B1017, surface = 0xFF2A1822,
    )
    ThemeStyle.OCEAN -> Look(
        gradient = listOf(0xFF081822, 0xFF0A1D2A, 0xFF07141C),
        blobs = listOf(LightSpec(0xFF1AA6B7, 0.5f, 0.95f, 0.1f, 0.65f), LightSpec(null, 0.5f, 0.05f, 0.5f, 0.6f), LightSpec(0xFF2A5BD7, 0.5f, 0.85f, 0.95f, 0.65f)),
        card = 0xFF16303A, menu = 0xFF142A33, base = 0xFF0B1C24, background = 0xFF08161E, surface = 0xFF10242E,
    )
    ThemeStyle.NEON -> Look(
        gradient = listOf(0xFF08060E, 0xFF0B0712, 0xFF06050A),
        blobs = listOf(LightSpec(0xFFFF2BD6, 0.45f, 0.95f, 0.08f, 0.6f), LightSpec(null, 0.5f, 0.05f, 0.55f, 0.55f), LightSpec(0xFF00E5FF, 0.4f, 0.85f, 0.97f, 0.6f)),
        card = 0xFF1A1426, menu = 0xFF18121F, base = 0xFF0A0710, background = 0xFF08060E, surface = 0xFF130E1C,
    )
} else when (style) {
    ThemeStyle.CLASSIC -> Look(
        gradient = listOf(0xFFECE6F6, 0xFFF4EDF0, 0xFFEEF1EB),
        blobs = listOf(LightSpec(null, 0.12f, 0.15f, 0.05f, 0.5f), LightSpec(0xFFF7D9CF, 0.7f, 0.95f, 0.55f, 0.45f), LightSpec(0xFFDCEFE3, 0.8f, 0.05f, 0.95f, 0.5f)),
        card = 0xFFFFFFFF, menu = 0xFFF9F7FC, base = 0xFFF1ECF4, background = 0xFFECE6F6, surface = 0xFFF7F5FA,
    )
    ThemeStyle.NEUTRAL -> Look(
        gradient = listOf(0xFFF4F5F7, 0xFFEFF0F2, 0xFFE9EBEE),
        blobs = listOf(LightSpec(null, 0.08f, 0.15f, 0.05f, 0.5f), LightSpec(0xFFFFFFFF, 0.9f, 0.9f, 0.5f, 0.5f)),
        card = 0xFFFFFFFF, menu = 0xFFF9FAFA, base = 0xFFEFF0F2, background = 0xFFEFF0F2, surface = 0xFFF6F7F8, shadow = 0xFF30343C,
    )
    ThemeStyle.AURORA -> Look(
        gradient = listOf(0xFFE9ECFA, 0xFFF3EBF7, 0xFFE6F3F2),
        blobs = listOf(LightSpec(null, 0.4f, 0.05f, 0.08f, 0.7f), LightSpec(0xFFF2A3C6, 0.55f, 1f, 0.45f, 0.6f), LightSpec(0xFF86DCCB, 0.6f, 0.1f, 0.95f, 0.65f)),
        card = 0xFFFFFFFF, menu = 0xFFF8F8FD, base = 0xFFEEF0FA, background = 0xFFE9ECFA, surface = 0xFFF5F6FC,
    )
    ThemeStyle.SUNSET -> Look(
        gradient = listOf(0xFFFBEAE4, 0xFFF9EBEC, 0xFFF6EBF3),
        blobs = listOf(LightSpec(0xFFFFB38A, 0.6f, 0.95f, 0.1f, 0.6f), LightSpec(0xFFF7A0C0, 0.6f, 0.05f, 0.5f, 0.6f), LightSpec(null, 0.35f, 0.9f, 0.95f, 0.6f)),
        card = 0xFFFFFFFF, menu = 0xFFFDF8F6, base = 0xFFFBEFEA, background = 0xFFFBEAE4, surface = 0xFFFDF4F1,
    )
    ThemeStyle.OCEAN -> Look(
        gradient = listOf(0xFFE4F3F8, 0xFFE8F0FA, 0xFFE3F6F3),
        blobs = listOf(LightSpec(0xFF7FD3E3, 0.6f, 0.95f, 0.1f, 0.6f), LightSpec(null, 0.35f, 0.05f, 0.5f, 0.6f), LightSpec(0xFF9DB8F2, 0.6f, 0.9f, 0.95f, 0.6f)),
        card = 0xFFFFFFFF, menu = 0xFFF6FBFD, base = 0xFFE8F3F7, background = 0xFFE4F3F8, surface = 0xFFF2F9FB,
    )
    ThemeStyle.NEON -> Look(
        gradient = listOf(0xFFF3ECFA, 0xFFF8ECF5, 0xFFEAF6FA),
        blobs = listOf(LightSpec(0xFFF5A0E8, 0.6f, 0.95f, 0.08f, 0.6f), LightSpec(null, 0.35f, 0.05f, 0.55f, 0.55f), LightSpec(0xFF8FE6F2, 0.6f, 0.85f, 0.97f, 0.6f)),
        card = 0xFFFFFFFF, menu = 0xFFFAF7FD, base = 0xFFF1ECF7, background = 0xFFF3ECFA, surface = 0xFFF8F5FC,
    )
}

fun glassColors(
    dark: Boolean,
    transparency: Int,
    accent: AccentPalette,
    style: ThemeStyle = ThemeStyle.AURORA,
): GlassColors {
    val t = transparency.coerceIn(0, 100) / 100f
    val cardAlpha = 0.12f + 0.8f * (1f - t) // 65 % → 0.40
    val chromeAlpha = (cardAlpha + 0.3f).coerceAtMost(0.92f)
    val pinnedAlpha = (cardAlpha + 0.25f).coerceAtMost(1f)
    val tint = if (dark) accent.dark else accent.light
    val look = look(style, dark)
    val card = Color(look.card)
    return GlassColors(
        dark = dark,
        card = card.copy(alpha = cardAlpha),
        chrome = card.copy(alpha = chromeAlpha),
        pinned = (if (dark) accent.darkContainer else accent.lightContainer).copy(alpha = pinnedAlpha),
        menu = Color(look.menu).copy(alpha = 0.97f),
        base = Color(look.base),
        borderTop = Color.White.copy(alpha = if (dark) 0.16f else 0.85f),
        borderBottom = Color.White.copy(alpha = if (dark) 0.04f else 0.25f),
        shadow = if (dark) Color.Black.copy(alpha = 0.5f) else Color(look.shadow).copy(alpha = if (style == ThemeStyle.NEUTRAL) 0.22f else 0.25f),
        gradient = look.gradient.map(::Color),
        blobs = look.blobs.map { Blob((it.color?.let(::Color) ?: tint).copy(alpha = it.alpha), it.x, it.y, it.radius) },
        background = Color(look.background),
        surface = Color(look.surface),
    )
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
