package com.cemail.app.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cemail.app.ui.theme.LocalGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// Mismos componentes de cristal que Cenotes (ver docs/DESIGN.md). Si cambian allí, se actualizan aquí.

/** Spring used for every state change in the UI ("springs instead of curves"). */
fun <T> glassSpring() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

enum class GlassKind { Card, Chrome, Pinned, Menu }

/**
 * Frosted glass surface: translucent fill (blurring whatever is behind when [hazeState] is given),
 * a bright edge that fades towards the bottom, and an optional soft shadow.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    kind: GlassKind = GlassKind.Card,
    elevation: Dp = 0.dp,
    hazeState: HazeState? = null,
): Modifier {
    val glass = LocalGlass.current
    val fill = when (kind) {
        GlassKind.Card -> glass.card
        GlassKind.Chrome -> glass.chrome
        GlassKind.Pinned -> glass.pinned
        GlassKind.Menu -> glass.menu
    }
    var m = this
    if (elevation > 0.dp) m = m.shadow(elevation, shape, ambientColor = glass.shadow, spotColor = glass.shadow)
    m = m.clip(shape)
    m = if (hazeState != null) {
        m.hazeEffect(hazeState) {
            blurRadius = 24.dp
            backgroundColor = glass.base
            tints = listOf(HazeTint(fill))
            noiseFactor = 0f
        }
    } else {
        m.background(fill)
    }
    return m.border(1.dp, Brush.verticalGradient(listOf(glass.borderTop, glass.borderBottom)), shape)
}

/** Soft gradient with blurred colour blobs that the glass surfaces float on. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalGlass.current.dark
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val base = if (dark) {
                    listOf(Color(0xFF141B33), Color(0xFF171A2B), Color(0xFF1D1825))
                } else {
                    listOf(Color(0xFFECE6F6), Color(0xFFF4EDF0), Color(0xFFEEF1EB))
                }
                drawRect(Brush.verticalGradient(base))
                fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                    val center = Offset(size.width * cx, size.height * cy)
                    val radius = size.maxDimension * r
                    drawCircle(
                        Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                }
                if (dark) {
                    blob(Color(0xFF1C4250).copy(alpha = 0.55f), 0.1f, 0.45f, 0.5f)
                    blob(accent.copy(alpha = 0.16f), 0.9f, 0.08f, 0.45f)
                    blob(Color(0xFF3A2440).copy(alpha = 0.5f), 0.85f, 0.95f, 0.5f)
                } else {
                    blob(accent.copy(alpha = 0.12f), 0.15f, 0.05f, 0.5f)
                    blob(Color(0xFFF7D9CF).copy(alpha = 0.7f), 0.95f, 0.55f, 0.45f)
                    blob(Color(0xFFDCEFE3).copy(alpha = 0.8f), 0.05f, 0.95f, 0.5f)
                }
            },
        content = content,
    )
}

/** Round floating glass button (56dp), like Celander's settings button. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    Box(
        modifier
            .size(56.dp)
            .glass(CircleShape, GlassKind.Chrome, elevation = 10.dp, hazeState = hazeState)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription)
    }
}

/** Floating glass pill holding a few actions or tabs. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(height)
            .glass(CircleShape, GlassKind.Chrome, elevation = 10.dp, hazeState = hazeState)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** An icon inside a [GlassPill]; [selected] gets the accent-tinted capsule behind it. */
@Composable
fun PillIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    selected: Boolean = false,
    width: Dp = 52.dp,
    /** Fixed icon colour; the background highlight is then not used. */
    tintOverride: Color? = null,
    /** false when something else (a sliding indicator) draws the highlight behind the icon. */
    showBackground: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected && showBackground) colors.primaryContainer else Color.Transparent, glassSpring(), label = "pillBg",
    )
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurface, glassSpring(), label = "pillTint",
    )
    Box(
        Modifier
            .size(width = width, height = 48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tintOverride ?: tint)
    }
}
