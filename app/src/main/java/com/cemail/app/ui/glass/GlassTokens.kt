package com.cemail.app.ui.glass

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Glass V2 (docs/DESIGN.md): the opacity of every glass fill follows ONE curve of the transparency setting, calibrated so text keeps
// WCAG AA at the default (65 %). Cemail, Cenotes and Celander share this file.

/**
 * Glass derived from the transparency picked in Settings › Design: 0 means solid surfaces without
 * blur, 1 fully clear glass. Every opacity runs linearly from opaque to its value at the default
 * (which keeps text at WCAG AA, see [opacity]) and from there to nothing at 1, so the default matches
 * the former "standard" glass and 0.4 the former "subtle" one. The blur itself is fixed.
 */
object GlassTokens {
    /** Real backdrop blur needs RenderEffect (API 31). Below it the fills stay fuller so text stays readable. */
    val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    const val DEFAULT_TRANSPARENCY = 0.65f
    const val SUBTLE_TRANSPARENCY = 0.4f
    const val NOISE = 0.03f

    /** The same blur on every glass surface; the transparency only changes how much shows through. */
    val blur = 25.dp

    fun isSolid(transparency: Float): Boolean = transparency <= 0f

    fun blurRadius(transparency: Float): Dp = if (isSolid(transparency)) 0.dp else blur

    /**
     * Opacity of glass fills. Without blur (API < 31) surfaces stay much fuller up to the default so
     * text remains readable on the plain background.
     */
    fun fillAlpha(transparency: Float, dark: Boolean, blurActive: Boolean): Float = when {
        !blurActive -> opacity(transparency, if (dark) 0.870f else 0.883f)
        else -> opacity(transparency, if (dark) 0.519f else 0.584f)
    }

    /** Highlighted cards (selected, pinned): the card fill made fuller, so the accent tint reads on any background. */
    fun pinnedAlpha(transparency: Float, dark: Boolean, blurActive: Boolean): Float =
        (fillAlpha(transparency, dark, blurActive) + 0.25f * (1f - transparency)).coerceAtMost(1f)

    /** Sheets carry forms and longer text, so they let less of the content behind them through. */
    fun sheetFillAlpha(transparency: Float, dark: Boolean, blurActive: Boolean): Float = when {
        !blurActive -> opacity(transparency, if (dark) 0.935f else 0.948f)
        else -> opacity(transparency, if (dark) 0.805f else 0.844f)
    }

    /**
     * Opacity of home screen widgets, from their own widget transparency (not the app's glass). They
     * cannot blur the wallpaper; up to the default, secondary text stays at WCAG AA on a black or
     * white wallpaper, at 1 only the glass rim, sheen and shadow remain.
     */
    fun widgetAlpha(transparency: Float, dark: Boolean): Float {
        val atDefault = if (dark) 0.857f else 0.870f
        val t = transparency.coerceIn(0f, 1f)
        return if (t <= DEFAULT_TRANSPARENCY) {
            1f - t / DEFAULT_TRANSPARENCY * (1f - atDefault)
        } else {
            // From the default down to the floor, so the whole slider shows.
            atDefault + (WIDGET_MIN_ALPHA - atDefault) * (t - DEFAULT_TRANSPARENCY) / (1f - DEFAULT_TRANSPARENCY)
        }
    }

    /**
     * A widget cannot blur the wallpaper behind it, so even at 100 % its card keeps this much of its
     * color: without it text lies straight on whatever the wallpaper shows and cannot be read.
     */
    const val WIDGET_MIN_ALPHA = 0.50f

    /** Opacity of translucent tiles inside content (cells, cards) that never blur. */
    fun tileAlpha(transparency: Float, dark: Boolean): Float = opacity(transparency, if (dark) 0.480f else 0.5645f)

    /**
     * Opaque at 0, [atDefault] at [DEFAULT_TRANSPARENCY], nothing at 1, linear in between. Above the
     * default the user trades legibility for a clear view, so 100 % really means fully transparent.
     */
    private fun opacity(transparency: Float, atDefault: Float): Float {
        val t = transparency.coerceIn(0f, 1f)
        return if (t <= DEFAULT_TRANSPARENCY) {
            1f - t / DEFAULT_TRANSPARENCY * (1f - atDefault)
        } else {
            atDefault * (1f - t) / (1f - DEFAULT_TRANSPARENCY)
        }
    }
}
