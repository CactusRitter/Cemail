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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import com.cemail.app.ui.theme.GlassColors
import com.cemail.app.ui.theme.LocalGlass
import com.cemail.app.ui.theme.LocalSkin
import com.cemail.app.ui.theme.Skin
import com.cemail.app.ui.theme.SkinLook
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

// Glass components shared by the "Ce" apps (docs/DESIGN.md): the same values in Cemail, Cenotes and Celander.
// Glass V2: Haze 2 (hazeBlur over HazeInput.Sources), source layers (GlassLayers), the opacity curve of GlassTokens and a shadow drawn
// only outside the shape (softShadow).

/**
 * The haze state of the screen that hosts glass: every `glass()` without an explicit one blurs what that screen registered
 * with `Modifier.hazeSource(state, zIndex = …)`. Null (a screen without sources) means translucent fills without blur.
 */
val LocalHazeState = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * Haze source layers of a screen. Glass inside the content (cards) only blurs layers below its own, i.e. the background;
 * floating glass outside all sources blurs every layer.
 */
object GlassLayers {
    const val BACKGROUND = 0f
    const val CONTENT = 1f
}

/** The fill of [kind]: with the opacity the curve gives for glass that blurs ([blurred]) or only tints. */
fun GlassColors.fill(kind: GlassKind, blurred: Boolean): Color {
    val solid = when (kind) {
        GlassKind.Card, GlassKind.Chrome -> cardSolid
        GlassKind.Pinned -> pinnedSolid
        GlassKind.Menu -> menuSolid
    } ?: return when (kind) {
        GlassKind.Card -> card
        GlassKind.Chrome -> chrome
        GlassKind.Pinned -> pinned
        GlassKind.Menu -> menu
    }
    val blur = blurred && GlassTokens.blurSupported && !GlassTokens.isSolid(transparency)
    val alpha = when (kind) {
        GlassKind.Card, GlassKind.Chrome -> GlassTokens.fillAlpha(transparency, dark, blur)
        GlassKind.Pinned -> GlassTokens.pinnedAlpha(transparency, dark, blur)
        GlassKind.Menu -> GlassTokens.sheetFillAlpha(transparency, dark, blur)
    }
    return solid.copy(alpha = alpha)
}

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
    hazeState: HazeState? = LocalHazeState.current,
    // Solid ground under the translucent fill, for buttons that float over the content of a message.
    opaque: Boolean = false,
): Modifier {
    val glass = LocalGlass.current
    val blurred = hazeState != null
    val fill = glass.fill(kind, blurred)
    val skin = LocalSkin.current
    if (skin.skin != Skin.GLASS) return skinSurface(skin, skin.shape(shape), fill, kind, elevation, glass.shadow, if (opaque) MaterialTheme.colorScheme.surface else null)
    var m = this
    if (skin.lift) {
        // "3D" glass (Appearance → 3D shadow): a soft shadow that lifts the slab off the background.
        m = m.liftShadow(shape, skin.liftColor, liftDepth(kind, elevation))
    } else if (elevation > 0.dp) {
        m = m.softShadow(shape, glass.shadow, Elevation.layersFor(elevation))
    }
    m = m.clip(shape)
    if (opaque) m = m.background(MaterialTheme.colorScheme.surface)
    m = if (hazeState != null && GlassTokens.blurSupported && !GlassTokens.isSolid(glass.transparency)) {
        m.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(GlassTokens.blur)
                backgroundColor(glass.base)
                colorEffects(listOf(HazeColorEffect.tint(fill)))
                noiseFactor(GlassTokens.NOISE)
            },
        )
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
        m = m.softShadow(shape, shadow, Elevation.layersFor(elevation))
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

/**
 * The fill of an unread message in a group that shares one card: lighter than the card, like the highlight of a
 * message that has a card of its own. With [wallColorFor] it makes the unread row a step of the card, a little higher
 * than the rest (like a building on a 3D map), instead of a second frame inside it.
 */
@Composable
fun unreadFill(): Color {
    val skin = LocalSkin.current
    val c = MaterialTheme.colorScheme
    return if (skin.dark) androidx.compose.ui.graphics.lerp(c.surface, c.onSurface, 0.30f) else c.surface.copy(alpha = 0.94f)
}

/** Colour of the wall under a raised unread row: the 3D shadow's, or a darker shade of [fill]. Null in the bevelled Windows 98 look. */
@Composable
fun wallColorFor(fill: Color): Color? {
    val skin = LocalSkin.current
    if (skin.edge == Edge.BEVEL) return null
    if (skin.hardShadow > 0.dp) return skin.hardShadowColor
    return androidx.compose.ui.graphics.lerp(fill.copy(alpha = 1f), Color.Black, if (skin.dark) 0.45f else 0.24f)
}

/** Height of the wall under an unread row. */
private val WALL_HEIGHT = 3.dp

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWall(color: Color) {
    val h = WALL_HEIGHT.toPx()
    drawRect(color, Offset(0f, size.height - h), Size(size.width, h))
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

/** Round floating glass button (56dp), like the other Ce apps' settings button. */
@Composable
fun GlassIconButton(
    icon: ImageVector?,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = LocalHazeState.current,
    /** Icon colour (the usual content colour by default). */
    tint: Color = androidx.compose.material3.LocalContentColor.current,
    /** Anything else inside the button instead of [icon] (a number, a badge). */
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .pressScale()
            .size(56.dp)
            .glass(CircleShape, GlassKind.Chrome, elevation = 10.dp, hazeState = hazeState)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else if (icon != null) Icon(icon, contentDescription, tint = tint)
    }
}

/** Floating glass pill holding a few actions or tabs. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    hazeState: HazeState? = LocalHazeState.current,
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
fun Modifier.glassSegment(
    pos: GroupPos, radius: Dp = 20.dp, kind: GlassKind = GlassKind.Card,
    /** An unread row: a wall under it (the row stands a little higher), with [fillOverride] as its lighter fill. */
    wall: Boolean = false, fillOverride: Color? = null,
): Modifier {
    val glass = LocalGlass.current
    val skin = LocalSkin.current
    if (skin.skin != Skin.GLASS) {
        val pinned = kind == GlassKind.Pinned
        val fill = fillOverride ?: if (pinned) skin.listPinned ?: glass.pinned else skin.listFill ?: glass.card
        return skinSegment(skin, pos, radius, fill, pinned, if (wall) wallColorFor(fill) else null)
    }
    val fill = fillOverride ?: glass.fill(if (kind == GlassKind.Pinned) GlassKind.Pinned else GlassKind.Card, blurred = false)
    val wallColor = if (wall) wallColorFor(fill) else null
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val shape = pos.shape(radius)
    return this
        .then(if (skin.lift) Modifier.liftSegment(pos, radius, skin.liftColor, liftDepth(GlassKind.Card, 0.dp)) else Modifier)
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            wallColor?.let { drawWall(it) }
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
            if (!pos.hasBottom && wallColor == null) {
                drawLine(divider, Offset(14.dp.toPx(), size.height - half), Offset(size.width - 14.dp.toPx(), size.height - half), stroke)
            }
        }
}

/**
 * One row of a group in a look that is not glass. With a gap (Modern) every row is its own tile, with big corners
 * on the outside of the group and small ones where two rows meet; without one the rows share a card with the look's
 * edge (lines, bevel) and dividers, and the hard shadow, if any, runs down the whole group.
 */
private fun Modifier.skinSegment(skin: SkinLook, pos: GroupPos, radius: Dp, fill: Color, pinned: Boolean, wallColor: Color? = null): Modifier {
    val r = skin.radius(radius)
    if (skin.groupGap > 0.dp) {
        val inner = minOf(skin.innerCorner, r)
        val shape = RoundedCornerShape(
            topStart = if (pos.hasTop) r else inner, topEnd = if (pos.hasTop) r else inner,
            bottomStart = if (pos.hasBottom) r else inner, bottomEnd = if (pos.hasBottom) r else inner,
        )
        val spaced = if (pos.hasBottom) this else padding(bottom = skin.groupGap)
        val tile = spaced.skinSurface(skin, shape, fill, if (pinned) GlassKind.Pinned else GlassKind.Card, 0.dp, Color.Transparent)
        return if (wallColor == null) tile else tile.drawWithContent { drawContent(); drawWall(wallColor) }
    }
    val shape = pos.shape(r)
    var m = this
    if (skin.hardShadow > 0.dp) m = m.hardShadow(shape, skin.hardShadowColor, skin.hardShadow, pos.hasTop, pos.hasBottom)
    return m.clip(shape).background(fill).drawWithContent {
        drawContent()
        wallColor?.let { drawWall(it) }
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
        if (!pos.hasBottom && divider != null && wallColor == null) {
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

/** Kind of glass of the floating "write" / "new" / "reply" button: highlighted, except in looks where it must match the other buttons (Retro 98). */
@Composable
fun fabKind(): GlassKind = if (LocalSkin.current.fabPinned) GlassKind.Pinned else GlassKind.Chrome

/** Icon colour of that button: the accent when highlighted, the usual content colour otherwise. */
@Composable
fun fabTint(): Color = if (LocalSkin.current.fabPinned) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current

/** Settings → Appearance → Message list: on the right (tablets), with the floating buttons then on the left. */
val LocalListOnRight = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Where floating buttons and their panels sit: bottom right, or bottom left when the list is on the right. */
@Composable
fun floatingCorner(): Alignment = if (LocalListOnRight.current) Alignment.BottomStart else Alignment.BottomEnd

/**
 * The lift of a glass slab (Appearance → 3D shadow): a soft shadow that stays outside the shape and falls a little
 * below it, the way a sheet of glass stands off its background. Built from a few widening layers, so it needs no blur
 * (which older Androids do not draw).
 */
private fun Modifier.liftShadow(shape: Shape, color: Color, depth: Dp): Modifier = drawBehind {
    // Small controls (chips, buttons) lift less than cards, so a row of them does not look heavy.
    val scale = (size.height / density / LIFT_FULL_HEIGHT_DP).coerceIn(0.4f, 1f)
    val d = depth * scale
    val c = color.copy(alpha = color.alpha * (0.5f + 0.5f * scale))
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) { drawLift(path, c, d) }
}

/** [liftShadow] for a row of a group: the shadow runs round the whole group, never across the seams between rows. */
private fun Modifier.liftSegment(pos: GroupPos, radius: Dp, color: Color, depth: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    val reach = depth.toPx() * 3
    val top = if (pos.hasTop) 0f else -4 * r
    val bottom = if (pos.hasBottom) size.height else size.height + 4 * r
    val path = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, top, size.width, bottom, androidx.compose.ui.geometry.CornerRadius(r)))
    }
    clipRect(-reach, if (pos.hasTop) -reach else 0f, size.width + reach, if (pos.hasBottom) size.height + reach else size.height) {
        clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) { drawLift(path, color, depth) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLift(path: androidx.compose.ui.graphics.Path, color: Color, depth: Dp) {
    val d = depth.toPx()
    val layers = 8
    translate(0f, d * 0.45f) {
        drawPath(path, color.copy(alpha = color.alpha * 0.5f))
        for (i in layers downTo 1) {
            drawPath(
                path, color.copy(alpha = color.alpha * 0.16f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(d * i / layers * 2, join = androidx.compose.ui.graphics.StrokeJoin.Round),
            )
        }
    }
}

/** How far a glass surface lifts: dialogs stand highest, floating buttons next, then cards. */
private fun liftDepth(kind: GlassKind, elevation: Dp): Dp = when {
    kind == GlassKind.Menu -> 16.dp
    elevation > 0.dp -> 12.dp
    else -> 8.dp
}

/** From this height (dp) up, a surface gets the full lift; smaller ones get less (down to 40 %). */
private const val LIFT_FULL_HEIGHT_DP = 90f

// ---- Selection ----

/**
 * How a selected card is marked, in the style of the look: an outline in the accent colour as thick as the look says
 * ([SkinLook.selectedEdgeWidth]: thick in Brutalist, thin in the glass), with the corners of the card. Works without
 * [SelectionBadge] too (a mail "open" in a second pane). The hard shadow of the card stays whole: it is drawn outside.
 */
@Composable
fun Modifier.selectedOutline(shape: Shape): Modifier =
    border(LocalSkin.current.selectedEdgeWidth, MaterialTheme.colorScheme.primary, shape)

/** The ✓ badge of a selected card, from the badge tokens of the look (see [SkinLook.badgeFill]). */
@Composable
fun SelectionBadge(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    val shape = skin.shape(CircleShape)
    var m = modifier.size(size)
    // The hard shadow of the look: a solid copy of the badge moved down and to the end.
    if (skin.badgeShadow > 0.dp) {
        m = m.drawBehind {
            val d = skin.badgeShadow.toPx()
            translate(d, d) { drawOutline(shape.createOutline(this.size, layoutDirection, this), skin.hardShadowColor) }
        }
    }
    m = m.clip(shape).background(skin.badgeFill ?: colors.primary)
    if (skin.badgeEdgeWidth > 0.dp) m = m.border(skin.badgeEdgeWidth, skin.badgeEdge, shape)
    if (skin.badgeBevel && skin.bevel.size >= 4) {
        // Sunken like a Windows 98 check box: dark on top and left, light on the bottom and right.
        val (hi, light, shade, dark) = skin.bevel
        m = m.drawBehind {
            val w = 1.dp.toPx()
            val s = this.size
            fun line(c: Color, from: Offset, to: Offset) = drawLine(c, from, to, w)
            line(shade, Offset(0f, w / 2), Offset(s.width, w / 2)); line(shade, Offset(w / 2, 0f), Offset(w / 2, s.height))
            line(dark, Offset(w, w * 1.5f), Offset(s.width - w, w * 1.5f)); line(dark, Offset(w * 1.5f, w), Offset(w * 1.5f, s.height - w))
            line(hi, Offset(0f, s.height - w / 2), Offset(s.width, s.height - w / 2))
            line(hi, Offset(s.width - w / 2, 0f), Offset(s.width - w / 2, s.height))
            line(light, Offset(w, s.height - w * 1.5f), Offset(s.width - w, s.height - w * 1.5f))
            line(light, Offset(s.width - w * 1.5f, w), Offset(s.width - w * 1.5f, s.height - w))
        }
    }
    Box(m, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Check, null, tint = skin.badgeInk ?: colors.onPrimary, modifier = Modifier.size(size * 0.7f),
        )
    }
}

// ---- Top scrim ----

/**
 * A strip behind the status bar and the top row (title and round buttons) of a screen whose list scrolls under them, so
 * what passes below does not mix with the title, the buttons and the clock. Put it in the screen's Box AFTER the list and
 * BEFORE the top row, at the top, with [height] = the insets + the row + the gap the list leaves under it. It fades out
 * along its last 24 dp and never takes touches. Glass blurs what is under it ([hazeState], the one of the buttons) and
 * washes it with the background; the other looks have no blur, so they get a solid wash of their own background that
 * fades out. [visible] false (a list at rest) fades it away, so the screen looks as before until it scrolls.
 */
@Composable
fun TopScrim(height: Dp, modifier: Modifier = Modifier, hazeState: HazeState? = LocalHazeState.current, visible: Boolean = true) {
    val glass = LocalGlass.current
    val skin = LocalSkin.current
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, glassSpring(), label = "topScrim")
    if (alpha <= 0.01f) return
    val shaped = modifier
        .fillMaxWidth()
        .height(height)
        .graphicsLayer { this.alpha = alpha }
    if (skin.skin == Skin.GLASS && hazeState != null && GlassTokens.blurSupported) {
        Box(
            shaped.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle {
                    blurRadius(20.dp)
                    backgroundColor(glass.base)
                    colorEffects(listOf(HazeColorEffect.tint(glass.base.copy(alpha = 0.6f))))
                    noiseFactor(GlassTokens.NOISE)
                    progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
                },
            ),
        )
    } else {
        val wash = if (skin.skin == Skin.GLASS) glass.gradient.first() else glass.base
        Box(
            shaped.drawBehind {
                val fade = (24.dp.toPx() / size.height).coerceIn(0f, 1f)
                drawRect(Brush.verticalGradient(colorStops = arrayOf(0f to wash, (1f - fade) to wash, 1f to wash.copy(alpha = 0f))))
            },
        )
    }
}


/**
 * A translucent tile for content inside scrolling areas (cells, rows, cards): the same glass as [glass], but it never blurs and
 * has no edge, so a grid of them stays light. [tint] is painted over it (a selected cell, an error row).
 */
@Composable
fun Modifier.glassTile(shape: Shape, tint: Color? = null): Modifier {
    val glass = LocalGlass.current
    val skin = LocalSkin.current
    val tinted = if (tint != null) Modifier.background(tint) else Modifier
    val solid = glass.cardSolid
    if (skin.skin != Skin.GLASS || solid == null) return glass(shape, GlassKind.Card, hazeState = null).then(tinted)
    return clip(shape).background(solid.copy(alpha = GlassTokens.tileAlpha(glass.transparency, glass.dark))).then(tinted)
}
