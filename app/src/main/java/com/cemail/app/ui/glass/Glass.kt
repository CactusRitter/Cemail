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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.addOutline
import com.cemail.app.ui.theme.Edge
import com.cemail.app.ui.theme.LocalGlass
import com.cemail.app.ui.theme.LocalSkin
import com.cemail.app.ui.theme.Skin
import com.cemail.app.ui.theme.SkinLook
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
    // Solid ground under the translucent fill, for buttons that float over the content of a message.
    opaque: Boolean = false,
): Modifier {
    val glass = LocalGlass.current
    val fill = when (kind) {
        GlassKind.Card -> glass.card
        GlassKind.Chrome -> glass.chrome
        GlassKind.Pinned -> glass.pinned
        GlassKind.Menu -> glass.menu
    }
    val skin = LocalSkin.current
    if (skin.skin != Skin.GLASS) return skinSurface(skin, skin.shape(shape), fill, kind, elevation, glass.shadow, if (opaque) MaterialTheme.colorScheme.surface else null)
    var m = this
    if (skin.hardShadow > 0.dp) {
        // "3D" glass: a solid shadow under every surface instead of the soft one (Appearance → 3D shadow).
        m = m.hardShadow(shape, skin.hardShadowColor, depthOf(skin, kind, elevation))
    } else if (elevation > 0.dp) {
        m = m.shadow(elevation, shape, ambientColor = glass.shadow, spotColor = glass.shadow)
    }
    m = m.clip(shape)
    if (opaque) m = m.background(MaterialTheme.colorScheme.surface)
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

/** A surface in one of the looks being tried out (not glass): solid fill, and the look's own edge and shadow. */
private fun Modifier.skinSurface(skin: SkinLook, shape: Shape, fill: Color, kind: GlassKind, elevation: Dp, shadow: Color, under: Color? = null): Modifier {
    var m = this
    val floating = kind != GlassKind.Card
    if (skin.hardShadow > 0.dp) {
        m = m.hardShadow(shape, skin.hardShadowColor, depthOf(skin, kind, elevation))
    } else if (skin.softShadow && elevation > 0.dp) {
        m = m.shadow(elevation * 0.6f, shape, ambientColor = shadow, spotColor = shadow)
    }
    m = m.clip(shape).then(if (under != null) Modifier.background(under) else Modifier).background(fill)
    return when (skin.edge) {
        Edge.GLASS -> m
        // No edge, but small controls (chips, buttons) sitting on a card of the same colour get a fine outline.
        Edge.NONE -> if (skin.edgeColor.alpha == 0f) m else m.drawWithContent {
            drawContent()
            if (kind == GlassKind.Card && size.height < 64.dp.toPx()) {
                drawOutline(shape.createOutline(size, layoutDirection, this), skin.edgeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(skin.edgeWidth.toPx()))
            }
        }
        Edge.LINE -> m.border(skin.edgeWidth, if (floating) skin.strongEdgeColor else skin.edgeColor, shape)
        Edge.BEVEL -> m.drawBehind {
            // Selected things are pressed in (sunken); a raised button with a shadow (write) stays raised.
            bevel(skin.bevel, raised = kind != GlassKind.Pinned || elevation > 0.dp)
            if (kind == GlassKind.Menu && skin.titleBar.size >= 2) {
                // The title bar of a Windows 98 window, as a strip along the top of dialogs.
                val inset = 3 * pixel()
                drawRect(
                    Brush.horizontalGradient(skin.titleBar), Offset(inset, inset),
                    Size(size.width - 2 * inset, 9.dp.toPx()),
                )
            }
        }
    }
}

/** One device pixel per dp, rounded, so thin lines stay sharp. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pixel(): Float = kotlin.math.max(1f, kotlin.math.round(density))

/**
 * Windows 98 edge: two lines on each side, light along the top and start and dark along the bottom and end (raised), or
 * the other way round (sunken, like a text field). [top] and [bottom] false leave those sides open (rows of a group).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bevel(c: List<Color>, raised: Boolean, top: Boolean = true, bottom: Boolean = true) {
    if (c.size < 4) return
    val w = pixel()
    val (hi, light, shade, dark) = c
    fun frame(inset: Float, tl: Color, br: Color) {
        val l = inset
        val r = size.width - inset
        val t = if (top) inset else 0f
        val b = if (bottom) size.height - inset else size.height
        if (top) drawRect(tl, Offset(l, t), Size(r - l, w))
        drawRect(tl, Offset(l, t), Size(w, b - t))
        if (bottom) drawRect(br, Offset(l, b - w), Size(r - l, w))
        drawRect(br, Offset(r - w, t), Size(w, b - t))
    }
    if (raised) {
        frame(0f, light, dark)
        frame(w, hi, shade)
    } else {
        frame(0f, shade, hi)
        frame(w, dark, light)
    }
}

/** How far the solid shadow drops: dialogs and floating buttons stand higher than cards. */
private fun depthOf(skin: SkinLook, kind: GlassKind, elevation: Dp): Dp = when {
    kind == GlassKind.Menu -> skin.hardShadow * 1.5f
    elevation > 0.dp -> skin.hardShadow * 1.25f
    else -> skin.hardShadow
}

/**
 * A solid shadow without blur: the shape again, moved [offset] down and towards the end, drawn only where it sticks out
 * (so it never shows through a see-through surface). A row inside a group passes [top] or [bottom] false where the
 * group goes on: the shadow then runs straight into the next row's instead of drawing its own end.
 */
private fun Modifier.hardShadow(shape: Shape, color: Color, offset: Dp, top: Boolean = true, bottom: Boolean = true): Modifier = drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    val dx = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) -o else o
    val own = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
    clipPath(own, androidx.compose.ui.graphics.ClipOp.Difference) {
        clipRect(-2 * o, -2 * o, size.width + 2 * o, if (bottom) size.height + 2 * o else size.height) {
            translate(dx, o) { drawOutline(outline, color) }
            if (!top) drawRect(color, Offset(dx, 0f), Size(size.width, o))
        }
    }
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
    val shape = LocalSkin.current.shape(CircleShape)
    Box(
        Modifier
            .pressScale(0.85f)
            .size(width = width, height = 48.dp)
            .clip(shape)
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
    val skin = LocalSkin.current
    if (skin.skin != Skin.GLASS) {
        val pinned = kind == GlassKind.Pinned
        val fill = if (pinned) skin.listPinned ?: glass.pinned else skin.listFill ?: glass.card
        return skinSegment(skin, pos, radius, fill, pinned)
    }
    val fill = if (kind == GlassKind.Pinned) glass.pinned else glass.card
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val shape = pos.shape(radius)
    return this
        .then(if (skin.hardShadow > 0.dp) Modifier.hardShadow(shape, skin.hardShadowColor, skin.hardShadow, pos.hasTop, pos.hasBottom) else Modifier)
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

/**
 * One row of a group in a look that is not glass. With a gap (Modern) every row is its own tile, with big corners
 * on the outside of the group and small ones where two rows meet; without one the rows share a card with the look's
 * edge (lines, bevel) and dividers, and the hard shadow, if any, runs down the whole group.
 */
private fun Modifier.skinSegment(skin: SkinLook, pos: GroupPos, radius: Dp, fill: Color, pinned: Boolean): Modifier {
    val r = skin.radius(radius)
    if (skin.groupGap > 0.dp) {
        val inner = minOf(skin.innerCorner, r)
        val shape = RoundedCornerShape(
            topStart = if (pos.hasTop) r else inner, topEnd = if (pos.hasTop) r else inner,
            bottomStart = if (pos.hasBottom) r else inner, bottomEnd = if (pos.hasBottom) r else inner,
        )
        val spaced = if (pos.hasBottom) this else padding(bottom = skin.groupGap)
        return spaced.skinSurface(skin, shape, fill, if (pinned) GlassKind.Pinned else GlassKind.Card, 0.dp, Color.Transparent)
    }
    val shape = pos.shape(r)
    var m = this
    if (skin.hardShadow > 0.dp) m = m.hardShadow(shape, skin.hardShadowColor, skin.hardShadow, pos.hasTop, pos.hasBottom)
    return m.clip(shape).background(fill).drawWithContent {
        drawContent()
        when (skin.edge) {
            Edge.BEVEL -> bevel(skin.bevel, raised = false, top = pos.hasTop, bottom = pos.hasBottom)
            Edge.LINE -> {
                val stroke = skin.edgeWidth.toPx()
                val half = stroke / 2f
                val rr = r.toPx()
                // As in the glass: the frame goes on past the seams, so no line shows where two rows meet.
                val top = if (pos.hasTop) half else -2 * rr - stroke
                val bottom = if (pos.hasBottom) size.height - half else size.height + 2 * rr + stroke
                drawRoundRect(
                    skin.edgeColor, Offset(half, top), Size(size.width - stroke, bottom - top),
                    androidx.compose.ui.geometry.CornerRadius(kotlin.math.max(rr - half, 0f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }
            Edge.GLASS, Edge.NONE -> Unit
        }
        val divider = skin.divider
        if (!pos.hasBottom && divider != null) {
            val w = skin.dividerWidth.toPx()
            val inset = skin.dividerInset.toPx()
            val y = size.height - w / 2f

            drawLine(divider, Offset(inset, y), Offset(size.width - inset, y), w)
        }
    }
}

/** Colour for text and icons drawn straight on the background: [default], unless the look needs another (Retro's teal desktop). */
@Composable
fun onBackdrop(default: Color): Color = LocalSkin.current.backdropInk ?: default

/** Settings → Appearance → Message list: on the right (tablets), with the floating buttons then on the left. */
val LocalListOnRight = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Where floating buttons and their panels sit: bottom right, or bottom left when the list is on the right. */
@Composable
fun floatingCorner(): Alignment = if (LocalListOnRight.current) Alignment.BottomStart else Alignment.BottomEnd
