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
