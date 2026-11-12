package com.cemail.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cemail.app.R

// Diseños en pruebas (rama styles-lab): otras familias visuales además del cristal esmerilado, cada una inspirada
// en clientes de correo conocidos. Cambian toda la app desde las piezas del sistema de diseño (glass, glassSegment,
// GlassBackground y el tema de Material), sin tocar cada pantalla. Ver docs/DESIGN.md, «Diseños en pruebas».

/** The whole look of the app. [GLASS] is the design shared with Celander and Cenotes; the others are being tried out. */
enum class Skin(@StringRes val label: Int) {
    /** Frosted glass over soft lights (the "Ce" apps). */
    GLASS(R.string.skin_glass),
    /** Gmail / Material 3 Expressive: solid tonal surfaces, rows as separate rounded tiles, no lines. */
    MODERN(R.string.skin_modern),
    /** Spark / Superhuman at night: pure black, hairline edges, nothing lit that does not need to be. */
    AMOLED(R.string.skin_amoled),
    /** Outlook Express on Windows 98: teal desktop, grey windows with bevelled edges, square corners. */
    RETRO(R.string.skin_retro),
    /** mutt / Pine: green phosphor on black, monospaced type with a glow, boxes drawn with lines. */
    TERMINAL(R.string.skin_terminal),
    /** Hey / newsletters: cream paper, ink, serif type, printed rules. */
    PAPER(R.string.skin_paper),
    /** Neo-brutalism (Gumroad): flat bold colours, thick black outlines and hard offset shadows. */
    BRUTAL(R.string.skin_brutal),
    ;

    /** Looks that only exist in one mode (true = always dark); null follows the light/dark setting. */
    val forcedDark: Boolean? get() = when (this) {
        AMOLED, TERMINAL -> true
        else -> null
    }

    /** Whether the clock and icons of the system bars must be light (the background is dark). */
    fun darkBars(dark: Boolean): Boolean = dark || this == RETRO
}

/** How the edge of a surface is drawn. */
enum class Edge { GLASS, NONE, LINE, BEVEL, DOUBLE }

/**
 * The shape and line rules of a look: corners, how rows of a group join, edges, dividers and shadows. Colours live in
 * [GlassColors] and the Material colour scheme, as for the glass.
 */
@Immutable
data class SkinLook(
    val skin: Skin,
    val dark: Boolean,
    /** A designed corner radius r becomes min(r × [cornerScale], [maxCorner]); 0 = square corners. */
    val cornerScale: Float = 1f,
    val maxCorner: Dp = 1000.dp,
    /** Rows of a group: the space between them (0 = they share one card) and the radius of their inner corners. */
    val groupGap: Dp = 0.dp,
    val innerCorner: Dp = 0.dp,
    val edge: Edge = Edge.GLASS,
    val edgeWidth: Dp = 1.dp,
    /** Edge of cards, and of floating things (buttons, pills, dialogs), which may be stronger. With [Edge.NONE], the
     *  outline of small controls only (null-like: transparent = none). */
    val edgeColor: Color = Color.Transparent,
    val strongEdgeColor: Color = Color.Transparent,
    /** Line between two rows of a group (null = none), how thick, how far from the sides, and whether it is dashed. */
    val divider: Color? = null,
    val dividerWidth: Dp = 1.dp,
    val dividerInset: Dp = 14.dp,
    val dashedDivider: Boolean = false,
    /** A solid shadow without blur, moved down and to the end by [hardShadow] (0 = none). */
    val hardShadow: Dp = 0.dp,
    val hardShadowColor: Color = Color.Transparent,
    /** The soft blurred shadow under raised things. */
    val softShadow: Boolean = true,
    /** Bevel colours (Windows 98): highlight, light, shadow, dark. */
    val bevel: List<Color> = emptyList(),
    /** Rows of the message list, when they differ from cards: their fill and the fill of a selected one. */
    val listFill: Color? = null,
    val listPinned: Color? = null,
    /** Faint horizontal lines over the background (a CRT screen). */
    val scanlines: Color? = null,
    /** Colours of the title bar of dialogs (Windows 98), start to end; empty = none. */
    val titleBar: List<Color> = emptyList(),
    /** Text and icons drawn straight on the background, when the usual colours would not read there (null = usual). */
    val backdropInk: Color? = null,
    /** Corner of the floating "write" button; null = round. */
    val fabCorner: Dp? = null,
) {
    private val keepsShapes get() = cornerScale == 1f && maxCorner >= 1000.dp

    /** A corner radius of the glass design, in this look. */
    fun radius(r: Dp): Dp = if (keepsShapes) r else minOf(r * cornerScale, maxCorner)

    /** A shape of the glass design, in this look (rounded corners scaled or squared). */
    fun shape(shape: Shape): Shape = if (keepsShapes) shape else SkinnedShape(shape, cornerScale, maxCorner)
}

/** [base] with its rounded corners multiplied by [scale] and capped at [max]. */
private data class SkinnedShape(val base: Shape, val scale: Float, val max: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outline = base.createOutline(size, layoutDirection, density)
        if (outline !is Outline.Rounded) return outline
        val cap = with(density) { max.toPx() }
        val rr = outline.roundRect
        if (scale == 0f || cap == 0f) return Outline.Rectangle(Rect(rr.left, rr.top, rr.right, rr.bottom))
        fun c(r: CornerRadius) = CornerRadius(minOf(r.x * scale, cap), minOf(r.y * scale, cap))
        return Outline.Rounded(
            RoundRect(
                left = rr.left, top = rr.top, right = rr.right, bottom = rr.bottom,
                topLeftCornerRadius = c(rr.topLeftCornerRadius), topRightCornerRadius = c(rr.topRightCornerRadius),
                bottomRightCornerRadius = c(rr.bottomRightCornerRadius), bottomLeftCornerRadius = c(rr.bottomLeftCornerRadius),
            ),
        )
    }
}

val LocalSkin = staticCompositionLocalOf { SkinLook(Skin.GLASS, dark = false) }

/** Everything one look needs: its rules, its surface colours, and the Material theme (colours, type, shapes). */
class SkinTheme(val look: SkinLook, val glass: GlassColors, val scheme: ColorScheme, val typography: Typography, val shapes: Shapes)

private fun tone(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Surface colours without glass: everything opaque, no lights behind. */
private fun flat(
    dark: Boolean, card: Color, chrome: Color, pinned: Color, menu: Color, background: Color, surface: Color,
    gradient: List<Color> = listOf(background, background), blobs: List<Blob> = emptyList(),
) = GlassColors(
    dark = dark, card = card, chrome = chrome, pinned = pinned, menu = menu, base = background,
    borderTop = Color.Transparent, borderBottom = Color.Transparent,
    shadow = Color.Black.copy(alpha = if (dark) 0.6f else 0.22f),
    gradient = gradient, blobs = blobs, background = background, surface = surface,
)

/** A Material colour scheme from the few colours a look decides; the rest follow from them. */
private fun scheme(
    dark: Boolean, primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    background: Color, surface: Color, onSurface: Color, onSurfaceVariant: Color, surfaceVariant: Color,
    outline: Color, outlineVariant: Color, tertiary: Color, error: Color, errorContainer: Color, onErrorContainer: Color,
    container: Color = surface,
): ColorScheme = if (dark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
        tertiary = tertiary, background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, error = error, onError = surface,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer, outline = outline, outlineVariant = outlineVariant,
        inverseSurface = onSurface, inverseOnSurface = surface, surfaceTint = Color.Transparent,
        surfaceBright = container, surfaceDim = background, surfaceContainer = container, surfaceContainerHigh = container,
        surfaceContainerHighest = container, surfaceContainerLow = container, surfaceContainerLowest = container,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
        tertiary = tertiary, background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, error = error, onError = surface,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer, outline = outline, outlineVariant = outlineVariant,
        inverseSurface = onSurface, inverseOnSurface = surface, surfaceTint = Color.Transparent,
        surfaceBright = container, surfaceDim = background, surfaceContainer = container, surfaceContainerHigh = container,
        surfaceContainerHighest = container, surfaceContainerLow = container, surfaceContainerLowest = container,
    )
}

private fun Typography.mapStyles(f: (TextStyle) -> TextStyle) = Typography(
    displayLarge = f(displayLarge), displayMedium = f(displayMedium), displaySmall = f(displaySmall),
    headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
    titleLarge = f(titleLarge), titleMedium = f(titleMedium), titleSmall = f(titleSmall),
    bodyLarge = f(bodyLarge), bodyMedium = f(bodyMedium), bodySmall = f(bodySmall),
    labelLarge = f(labelLarge), labelMedium = f(labelMedium), labelSmall = f(labelSmall),
)

/** A font installed on every Android phone, by its system name ("serif", "monospace", "sans-serif-smallcaps"...). */
private fun device(name: String) = FontFamily(
    Font(DeviceFontFamilyName(name), FontWeight.Normal),
    Font(DeviceFontFamilyName(name), FontWeight.Medium),
    Font(DeviceFontFamilyName(name), FontWeight.Bold),
)

private fun shapesOf(look: SkinLook) = Shapes(
    extraSmall = RoundedCornerShape(look.radius(4.dp)), small = RoundedCornerShape(look.radius(8.dp)),
    medium = RoundedCornerShape(look.radius(12.dp)), large = RoundedCornerShape(look.radius(16.dp)),
    extraLarge = RoundedCornerShape(look.radius(28.dp)),
)

/** The theme of [skin] (any but [Skin.GLASS], which [CemailTheme] builds) in light or [dark], tinted by [accent]. */
fun skinTheme(skin: Skin, dark: Boolean, accent: AccentPalette): SkinTheme {
    val hsl = hslOf(accent.light)
    val h = hsl[0]
    val s = hsl[1]
    return when (skin) {
        Skin.MODERN -> modern(dark, accent, h, s)
        Skin.AMOLED -> amoled(accent, h, s)
        Skin.RETRO -> retro(dark, h)
        Skin.TERMINAL -> terminal()
        Skin.PAPER -> paper(dark, h, s)
        Skin.GLASS, Skin.BRUTAL -> brutal(dark, h)
    }
}

private fun modern(dark: Boolean, a: AccentPalette, h: Float, s: Float): SkinTheme {
    val look = SkinLook(
        Skin.MODERN, dark, groupGap = 2.dp, innerCorner = 6.dp, edge = Edge.NONE, fabCorner = 18.dp,
        edgeColor = if (dark) tone(h, 0.12f, 0.32f) else tone(h, 0.2f, 0.8f),
    )
    val glass = if (dark) {
        flat(
            true, card = tone(h, s * 0.18f, 0.125f), chrome = tone(h, s * 0.22f, 0.17f), pinned = a.darkContainer,
            menu = tone(h, s * 0.2f, 0.15f), background = tone(h, s * 0.25f, 0.07f), surface = tone(h, s * 0.2f, 0.11f),
        )
    } else {
        flat(
            false, card = tone(h, s * 0.3f, 0.995f), chrome = tone(h, s * 0.5f, 0.915f), pinned = a.lightContainer,
            menu = tone(h, s * 0.4f, 0.975f), background = tone(h, s * 0.45f, 0.95f), surface = tone(h, s * 0.4f, 0.975f),
        )
    }
    val scheme = if (dark) {
        scheme(
            true, primary = a.dark, onPrimary = tone(h, s, 0.15f), primaryContainer = a.darkContainer,
            onPrimaryContainer = tone(h, s * 0.6f, 0.9f), background = glass.background, surface = glass.surface,
            onSurface = tone(h, 0.12f, 0.92f), onSurfaceVariant = tone(h, 0.1f, 0.72f), surfaceVariant = tone(h, s * 0.15f, 0.2f),
            outline = tone(h, 0.08f, 0.45f), outlineVariant = tone(h, 0.1f, 0.26f), tertiary = Color(0xFFFFC94D),
            error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6), container = glass.menu,
        )
    } else {
        scheme(
            false, primary = a.light, onPrimary = Color.White, primaryContainer = a.lightContainer,
            onPrimaryContainer = tone(h, s, 0.22f), background = glass.background, surface = glass.surface,
            onSurface = tone(h, 0.12f, 0.1f), onSurfaceVariant = tone(h, 0.08f, 0.38f), surfaceVariant = tone(h, s * 0.3f, 0.9f),
            outline = tone(h, 0.08f, 0.55f), outlineVariant = tone(h, 0.15f, 0.85f), tertiary = Color(0xFFE3A008),
            error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002), container = glass.menu,
        )
    }
    val type = Typography().mapStyles { st ->
        if (st.fontWeight == FontWeight.Medium) st.copy(fontWeight = FontWeight.SemiBold) else st
    }
    return SkinTheme(look, glass, scheme, type, shapesOf(look))
}

private fun amoled(a: AccentPalette, h: Float, s: Float): SkinTheme {
    val black = Color.Black
    val look = SkinLook(
        Skin.AMOLED, dark = true, cornerScale = 0.8f, maxCorner = 22.dp, edge = Edge.LINE,
        edgeColor = Color(0xFF242424), strongEdgeColor = Color(0xFF333333), divider = Color(0xFF1C1C1C), softShadow = false,
    )
    val glass = flat(
        true, card = Color(0xFF0B0B0B), chrome = Color(0xFF111111), pinned = lerp(black, a.darkContainer, 0.6f),
        menu = Color(0xFF101010), background = black, surface = Color(0xFF0A0A0A),
    )
    val scheme = scheme(
        true, primary = a.dark, onPrimary = black, primaryContainer = lerp(black, a.darkContainer, 0.85f),
        onPrimaryContainer = tone(h, s * 0.6f, 0.9f), background = black, surface = glass.surface,
        onSurface = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF8E8E93), surfaceVariant = Color(0xFF1A1A1A),
        outline = Color(0xFF4A4A4A), outlineVariant = Color(0xFF262626), tertiary = Color(0xFFFFC94D),
        error = Color(0xFFFF6B6B), errorContainer = Color(0xFF3B0A0A), onErrorContainer = Color(0xFFFFB4B4), container = glass.menu,
    )
    return SkinTheme(look, glass, scheme, Typography(), shapesOf(look))
}

private fun retro(dark: Boolean, h: Float): SkinTheme {
    val navy = tone(h, 1f, if (dark) 0.72f else 0.27f)
    val look = if (dark) {
        SkinLook(
            Skin.RETRO, true, cornerScale = 0f, edge = Edge.BEVEL, softShadow = false,
            bevel = listOf(Color(0xFF7A7A7A), Color(0xFF5A5A5A), Color(0xFF242424), Color(0xFF000000)),
            divider = Color(0xFF2E2E2E), dividerInset = 6.dp,
            listFill = Color(0xFF1C1C1C), listPinned = tone(h, 0.45f, 0.26f),
            titleBar = listOf(tone(h, 0.8f, 0.3f), tone(h, 0.65f, 0.5f)),
            backdropInk = Color(0xFFE6E6E6),
        )
    } else {
        SkinLook(
            Skin.RETRO, false, cornerScale = 0f, edge = Edge.BEVEL, softShadow = false,
            bevel = listOf(Color.White, Color(0xFFDFDFDF), Color(0xFF808080), Color(0xFF0A0A0A)),
            divider = Color(0xFFE4E4E4), dividerInset = 6.dp,
            listFill = Color.White, listPinned = tone(h, 0.55f, 0.86f),
            titleBar = listOf(navy, tone(h, 0.75f, 0.5f)),
            backdropInk = Color.White,
        )
    }
    val silver = if (dark) Color(0xFF3C3C3C) else Color(0xFFC0C0C0)
    val desktop = if (dark) Color(0xFF003434) else Color(0xFF008080)
    val glass = flat(
        dark, card = silver, chrome = silver, pinned = if (dark) Color(0xFF1C1C1C) else Color.White, menu = silver,
        background = desktop, surface = silver,
    )
    val scheme = scheme(
        dark, primary = navy, onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = if (dark) tone(h, 0.45f, 0.3f) else tone(h, 0.55f, 0.84f), onPrimaryContainer = navy,
        background = desktop, surface = silver, onSurface = if (dark) Color(0xFFEDEDED) else Color.Black,
        onSurfaceVariant = if (dark) Color(0xFFB8B8B8) else Color(0xFF3A3A3A), surfaceVariant = if (dark) Color(0xFF2A2A2A) else Color(0xFFD4D0C8),
        outline = if (dark) Color(0xFF7A7A7A) else Color(0xFF808080), outlineVariant = if (dark) Color(0xFF555555) else Color(0xFF808080),
        tertiary = if (dark) Color(0xFFFFD24D) else Color(0xFFA67C00),
        error = if (dark) Color(0xFFFF7070) else Color(0xFFC00000), errorContainer = if (dark) Color(0xFF4A1010) else Color(0xFFFFC8C8),
        onErrorContainer = if (dark) Color(0xFFFFC8C8) else Color(0xFF800000), container = silver,
    )
    val type = Typography().mapStyles { it.copy(letterSpacing = 0.sp) }
    return SkinTheme(look, glass, scheme, type, shapesOf(look))
}

private fun terminal(): SkinTheme {
    val phosphor = Color(0xFF4CF57E)
    val bg = Color(0xFF040805)
    val look = SkinLook(
        Skin.TERMINAL, true, cornerScale = 0f, edge = Edge.DOUBLE, softShadow = false,
        edgeColor = phosphor.copy(alpha = 0.45f), strongEdgeColor = phosphor.copy(alpha = 0.8f),
        divider = phosphor.copy(alpha = 0.3f), dashedDivider = true, dividerInset = 10.dp,
        scanlines = Color.Black.copy(alpha = 0.35f),
    )
    val card = Color(0xFF06100A)
    val glass = flat(
        true, card = card, chrome = bg, pinned = lerp(card, phosphor, 0.13f), menu = Color(0xFF030604),
        background = bg, surface = card, gradient = listOf(Color(0xFF07120B), Color(0xFF030604)),
        blobs = listOf(Blob(phosphor.copy(alpha = 0.07f), 0.5f, 0.42f, 0.85f)),
    )
    // One colour like a real phosphor screen: brighter green for what stands out (the accent is not used here).
    val primary = Color(0xFFB8FFCB)
    val scheme = scheme(
        true, primary = primary, onPrimary = Color.Black, primaryContainer = lerp(card, primary, 0.22f),
        onPrimaryContainer = primary, background = bg, surface = card, onSurface = phosphor,
        onSurfaceVariant = Color(0xFF2FA557), surfaceVariant = Color(0xFF0B1F12), outline = Color(0xFF1F7A3C),
        outlineVariant = Color(0xFF14502A), tertiary = Color(0xFFFFC34D), error = Color(0xFFFF5F5F),
        errorContainer = Color(0xFF3A0A0A), onErrorContainer = Color(0xFFFF9A9A), container = glass.menu,
    )
    val glow = Shadow(phosphor.copy(alpha = 0.55f), blurRadius = 9f)
    val type = Typography().mapStyles { it.copy(fontFamily = FontFamily.Monospace, shadow = glow, letterSpacing = 0.sp) }
    return SkinTheme(look, glass, scheme, type, shapesOf(look))
}

private fun paper(dark: Boolean, h: Float, s: Float): SkinTheme {
    val ink = if (dark) Color(0xFFECE4D6) else Color(0xFF26221C)
    val look = SkinLook(
        Skin.PAPER, dark, cornerScale = 0.22f, maxCorner = 6.dp, edge = Edge.LINE,
        edgeColor = ink.copy(alpha = 0.16f), strongEdgeColor = ink.copy(alpha = 0.8f),
        divider = ink.copy(alpha = 0.13f), dividerInset = 14.dp, softShadow = false,
    )
    val glass = if (dark) {
        flat(
            true, card = Color(0xFF23201B), chrome = Color(0xFF23201B), pinned = tone(h, s * 0.35f, 0.22f),
            menu = Color(0xFF262219), background = Color(0xFF1A1814), surface = Color(0xFF23201B),
            gradient = listOf(Color(0xFF1C1A16), Color(0xFF171511)),
        )
    } else {
        flat(
            false, card = Color(0xFFFBF8F2), chrome = Color(0xFFFBF8F2), pinned = tone(h, s * 0.6f, 0.91f),
            menu = Color(0xFFFBF8F2), background = Color(0xFFF2EDE3), surface = Color(0xFFFBF8F2),
            gradient = listOf(Color(0xFFF5F0E7), Color(0xFFEEE8DB)),
        )
    }
    val scheme = scheme(
        dark, primary = if (dark) tone(h, s * 0.7f, 0.76f) else tone(h, s * 0.9f, 0.36f),
        onPrimary = glass.card, primaryContainer = if (dark) tone(h, s * 0.35f, 0.26f) else tone(h, s * 0.5f, 0.88f),
        onPrimaryContainer = if (dark) tone(h, s * 0.6f, 0.88f) else tone(h, s, 0.25f),
        background = glass.background, surface = glass.surface, onSurface = ink,
        onSurfaceVariant = if (dark) Color(0xFFA89F8F) else Color(0xFF6E665A),
        surfaceVariant = if (dark) Color(0xFF302B24) else Color(0xFFE8E1D3),
        outline = if (dark) Color(0xFF6B6458) else Color(0xFFA0978A), outlineVariant = if (dark) Color(0xFF3A352D) else Color(0xFFDDD5C6),
        tertiary = if (dark) Color(0xFFE3B341) else Color(0xFFB07800),
        error = if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E), errorContainer = if (dark) Color(0xFF4A1A16) else Color(0xFFF7D8D3),
        onErrorContainer = if (dark) Color(0xFFFFD0C8) else Color(0xFF6B1410), container = glass.menu,
    )
    val smallCaps = device("sans-serif-smallcaps")
    val base = Typography()
    val serif = base.mapStyles { it.copy(fontFamily = FontFamily.Serif, letterSpacing = 0.sp) }
    val type = serif.copy(
        labelMedium = base.labelMedium.copy(fontFamily = smallCaps, letterSpacing = 0.6.sp),
        labelSmall = base.labelSmall.copy(fontFamily = smallCaps, letterSpacing = 0.6.sp),
        headlineSmall = serif.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = serif.titleLarge.copy(fontWeight = FontWeight.Bold),
    )
    return SkinTheme(look, glass, scheme, type, shapesOf(look))
}

private fun brutal(dark: Boolean, h: Float): SkinTheme {
    val line = if (dark) Color(0xFFF2F2F2) else Color(0xFF111111)
    val look = SkinLook(
        Skin.BRUTAL, dark, cornerScale = 0.5f, maxCorner = 14.dp, edge = Edge.LINE, edgeWidth = 2.dp,
        edgeColor = line, strongEdgeColor = line, divider = line, dividerWidth = 2.dp, dividerInset = 0.dp,
        hardShadow = 4.dp, hardShadowColor = if (dark) tone(h, 0.9f, 0.62f) else Color(0xFF111111), softShadow = false,
    )
    val yellow = Color(0xFFFFE14D)
    val glass = if (dark) {
        flat(
            true, card = Color(0xFF1E1E1E), chrome = Color(0xFF1E1E1E), pinned = tone(h, 0.6f, 0.32f),
            menu = Color(0xFF1E1E1E), background = tone(h, 0.35f, 0.12f), surface = Color(0xFF1E1E1E),
        )
    } else {
        flat(
            false, card = Color.White, chrome = Color.White, pinned = yellow, menu = Color.White,
            background = tone(h, 0.85f, 0.87f), surface = Color.White,
        )
    }
    val scheme = scheme(
        dark, primary = if (dark) tone(h, 0.9f, 0.74f) else tone(h, 0.85f, 0.4f), onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = if (dark) tone(h, 0.6f, 0.32f) else yellow, onPrimaryContainer = line,
        background = glass.background, surface = glass.surface, onSurface = if (dark) Color(0xFFF5F5F5) else Color(0xFF111111),
        onSurfaceVariant = if (dark) Color(0xFFC4C4C4) else Color(0xFF3A3A3A), surfaceVariant = if (dark) Color(0xFF2C2C2C) else Color(0xFFF1F1F1),
        outline = line, outlineVariant = line.copy(alpha = 0.35f), tertiary = if (dark) Color(0xFFFFD84D) else Color(0xFFE0A100),
        error = if (dark) Color(0xFFFF6B7A) else Color(0xFFD7263D), errorContainer = if (dark) Color(0xFF4A0F18) else Color(0xFFFFC2CA),
        onErrorContainer = line, container = glass.menu,
    )
    val heavy = Typography().mapStyles { st ->
        when (st.fontWeight) {
            FontWeight.Medium -> st.copy(fontWeight = FontWeight.Bold)
            FontWeight.Bold -> st.copy(fontWeight = FontWeight.Black)
            else -> st
        }
    }
    val type = heavy.copy(
        headlineSmall = heavy.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        titleLarge = heavy.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp),
    )
    return SkinTheme(look, glass, scheme, type, shapesOf(look))
}
