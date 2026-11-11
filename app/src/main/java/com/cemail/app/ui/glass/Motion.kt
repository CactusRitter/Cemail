package com.cemail.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

// Movement of the app (docs/DESIGN.md, "Movimiento"). Everything moves with springs, never with linear curves:
// [glassSpring] is the calm one (colours, sizes); the two below have a little overshoot, so things that appear or are
// pressed "settle" with a bounce. Android's "remove animations" setting still switches all of this off.

/** Lively spring with a visible bounce: for things that are pressed or pop up. */
fun <T> bounceSpring() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)

/** Softer bounce, for whole screens and large panels. */
fun <T> softBounceSpring() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/**
 * Press feedback: the element shrinks while a finger is on it and springs back, overshooting a little. Put it first in
 * the chain (before `glass` and `clickable`) so the whole button scales; it does not take the touch away from them.
 */
fun Modifier.pressScale(pressedScale: Float = 0.9f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, bounceSpring(), label = "pressScale")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                // Watching only (initial pass, nothing consumed): the click itself is handled as before.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                pressed = false
            }
        }
}

/**
 * Appears growing from [origin] (the centre, or the button a panel comes out of) up to its size, with a bounce, while
 * it fades in. It plays when [visible] turns true; [delayMillis] staggers several elements one after another.
 */
@Composable
fun Modifier.popIn(
    visible: Boolean = true,
    delayMillis: Int = 0,
    initialScale: Float = 0.5f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, bounceSpring())
        } else {
            progress.snapTo(0f)
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        val s = initialScale + (1f - initialScale) * p // above 1 while it overshoots
        scaleX = s
        scaleY = s
        alpha = p.coerceIn(0f, 1f)
        transformOrigin = origin
    }
}

/** A small "pop" every time [trigger] changes (not the first time it is shown): a star that was just marked, say. */
@Composable
fun Modifier.bumpOn(trigger: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) {
            first = false
        } else {
            scale.snapTo(0.55f)
            scale.animateTo(1f, bounceSpring())
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Where messages sit on screen, so the reader can unfold out of the card that was tapped and fold back into it.
 * The list reports every card it draws ([positions]) and sets [origin] when one is tapped; the reader takes it
 * ([consume]) once per opening.
 */
object ReadTransition {
    @Volatile var origin: Rect? = null

    /** True while the open reader came out of a card, so closing it folds back instead of using the plain transition. */
    @Volatile var fromCard: Boolean = false

    /** The cards on screen now, by message id (each card removes itself when it leaves the list). */
    val positions = java.util.concurrent.ConcurrentHashMap<Long, Rect>()

    fun consume(): Rect? = origin.also {
        origin = null
        fromCard = it != null
    }
}

/**
 * The screen opens slowly out of [origin] (the tapped card) to fill the window: a rounded window that grows with a soft
 * spring while the content fades in and settles from a slightly smaller size. When [closing] it does the opposite and
 * folds back into the card of message [mailId] (where that card is now, or where it was). Without an origin (a
 * notification, say) nothing is changed.
 */
@Composable
fun Modifier.expandFrom(origin: Rect?, mailId: Long, closing: Boolean): Modifier {
    if (origin == null) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 260f)) // a little quicker than opening
        } else {
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 170f))
        }
    }
    val corner = with(LocalDensity.current) { 22.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        val open = p.coerceIn(0f, 1f) // the spring may overshoot a little: the window never goes beyond the screen
        val card = ReadTransition.positions[mailId] ?: origin // read each frame: the list may have moved meanwhile
        clip = true
        shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Rounded(
                    RoundRect(
                        left = lerp(card.left, 0f, open),
                        top = lerp(card.top, 0f, open),
                        right = lerp(card.right, size.width, open),
                        bottom = lerp(card.bottom, size.height, open),
                        cornerRadius = CornerRadius(lerp(corner, 0f, open)),
                    ),
                )
        }
        alpha = (open * 3f).coerceIn(0f, 1f)
        val scale = 0.94f + 0.06f * p.coerceIn(0f, 1.2f)
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, if (size.height > 0f) card.center.y / size.height else 0.5f)
    }
}
