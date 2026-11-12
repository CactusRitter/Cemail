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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.geometry.Size
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
    val glass = LocalGlass.current
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(glass.gradient))
                glass.blobs.forEach { blob ->
                    val center = Offset(size.width * blob.x, size.height * blob.y)
                    val radius = size.maxDimension * blob.radius
                    drawCircle(
                        Brush.radialGradient(listOf(blob.color, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
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
            .pressScale()
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
            .pressScale(0.85f)
            .size(width = width, height = 48.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tintOverride ?: tint)
    }
}

/** Where a row sits inside a group that shares one glass card. */
enum class GroupPos {
    Single, First, Middle, Last;

    val hasTop get() = this == Single || this == First
    val hasBottom get() = this == Single || this == Last

    fun shape(radius: Dp): Shape = RoundedCornerShape(
        topStart = if (hasTop) radius else 0.dp, topEnd = if (hasTop) radius else 0.dp,
        bottomStart = if (hasBottom) radius else 0.dp, bottomEnd = if (hasBottom) radius else 0.dp,
    )

    companion object {
        /** Position of item [index] in a list of [size]. */
        fun of(index: Int, size: Int) = when {
            size <= 1 -> Single
            index == 0 -> First
            index == size - 1 -> Last
            else -> Middle
        }
    }
}

/**
 * One row of a group drawn as a slice of a single frosted card: the rows of a group touch, the rounded corners
 * and the bright edge belong to the group as a whole, and a hairline separates one row from the next.
 */
@Composable
fun Modifier.glassSegment(pos: GroupPos, radius: Dp = 20.dp, kind: GlassKind = GlassKind.Card): Modifier {
    val glass = LocalGlass.current
    val fill = if (kind == GlassKind.Pinned) glass.pinned else glass.card
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val shape = pos.shape(radius)
    return this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            val r = radius.toPx()
            val stroke = 1.dp.toPx()
            val half = stroke / 2f
            val mid = androidx.compose.ui.graphics.lerp(glass.borderTop, glass.borderBottom, 0.5f)
            val brush = when (pos) {
                GroupPos.Single -> Brush.verticalGradient(listOf(glass.borderTop, glass.borderBottom), 0f, size.height)
                GroupPos.First -> Brush.verticalGradient(listOf(glass.borderTop, mid), 0f, size.height)
                GroupPos.Middle -> Brush.verticalGradient(listOf(mid, mid))
                GroupPos.Last -> Brush.verticalGradient(listOf(mid, glass.borderBottom), 0f, size.height)
            }
            // The frame is drawn on a rectangle taller than the row where the group goes on, so no line shows at the seam.
            val top = if (pos.hasTop) half else -2 * r
            val bottom = if (pos.hasBottom) size.height - half else size.height + 2 * r
            drawRoundRect(
                brush, Offset(half, top), Size(size.width - stroke, bottom - top),
                androidx.compose.ui.geometry.CornerRadius(r - half), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
            if (!pos.hasBottom) {
                drawLine(divider, Offset(14.dp.toPx(), size.height - half), Offset(size.width - 14.dp.toPx(), size.height - half), stroke)
            }
        }
}
