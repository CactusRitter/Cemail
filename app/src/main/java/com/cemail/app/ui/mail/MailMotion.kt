package com.cemail.app.ui.mail

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
import androidx.compose.ui.graphics.CompositingStrategy
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
import com.cemail.app.ui.glass.bounceSpring
import com.cemail.app.ui.glass.glassSpring

// Movimiento propio de Cemail (no se comparte con las otras apps): el lector que se despliega desde la tarjeta y el cambio de lista.
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
    // The corner of this look (square in Retro 98, small in Paper…), so the card does not turn round while it grows.
    val corner = with(LocalDensity.current) { com.cemail.app.ui.theme.LocalSkin.current.radius(22.dp).toPx() }
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

/**
 * One row of a list that is swapped for another (another account, folder or filter): rows leave one after another
 * ([leaving]) and the new ones come in one after another with a bounce ([entering]); [index] sets the order.
 * Rows that scroll into view later (neither flag set) just appear as usual.
 */
@Composable
fun Modifier.listTransition(index: Int, leaving: Boolean, entering: Boolean, epoch: Int): Modifier {
    val progress = remember { Animatable(if (entering) 0f else 1f) }
    LaunchedEffect(leaving, entering, epoch) {
        val step = index.coerceAtMost(10)
        when {
            leaving -> {
                delay(step * 20L)
                progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
            }
            entering -> {
                progress.snapTo(0f)
                delay(step * 38L)
                progress.animateTo(1f, bounceSpring())
            }
            progress.value != 1f -> progress.animateTo(1f, glassSpring())
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        val s = 0.8f + 0.2f * p
        scaleX = s
        scaleY = s
        alpha = p.coerceIn(0f, 1f)
        // Like popIn: with an offscreen buffer the size of the row, the hard shadow (it sticks out to the end) was cut off
        // while the row faded in, and then popped back when the animation ended.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}
