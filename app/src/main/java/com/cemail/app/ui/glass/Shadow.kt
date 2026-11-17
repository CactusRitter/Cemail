package com.cemail.app.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Glass V2: the shadow of a glass surface is drawn ONLY outside its shape, so translucent glass is never darkened from below.

/** One layer of a drop shadow; [alpha] scales the shadow colour. */
data class ShadowLayer(val blur: Dp, val offsetY: Dp, val alpha: Float)

/**
 * Elevation scale. Each level pairs a tight contact shadow, which gives the edge its pop, with a wide
 * ambient one that lifts the whole surface off the background.
 */
object Elevation {
    /** Cards lifted off the content. */
    val raised = listOf(ShadowLayer(blur = 3.dp, offsetY = 1.dp, alpha = 0.7f), ShadowLayer(blur = 22.dp, offsetY = 10.dp, alpha = 0.9f))

    /** A small part lifted off its own control, like the selected segment; a hint of depth, no more. */
    val lifted = listOf(ShadowLayer(blur = 2.dp, offsetY = 1.dp, alpha = 0.6f), ShadowLayer(blur = 8.dp, offsetY = 3.dp, alpha = 0.55f))

    /** Controls floating above everything: pills, round buttons, snackbars, sheets. */
    val floating = listOf(ShadowLayer(blur = 4.dp, offsetY = 2.dp, alpha = 0.7f), ShadowLayer(blur = 30.dp, offsetY = 14.dp, alpha = 1f))

    /** The layers for a surface that floats [elevation] above its background (from 10 dp up: [floating]). */
    fun layersFor(elevation: Dp): List<ShadowLayer> = if (elevation >= 10.dp) floating else raised
}

/**
 * Layered shadow drawn only outside [shape], so translucent glass is not darkened from below.
 * Rendered by the hardware pipeline from API 28; older devices simply show no shadow.
 */
fun Modifier.softShadow(shape: Shape, color: Color, layers: List<ShadowLayer>): Modifier =
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val outline = path.asAndroidPath()
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.copy(alpha = 1f).toArgb()
        }
        onDrawBehind {
            if (color.alpha == 0f) return@onDrawBehind
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.clipPath(path, ClipOp.Difference)
                for (layer in layers) {
                    paint.setShadowLayer(layer.blur.toPx(), 0f, layer.offsetY.toPx(), color.copy(alpha = color.alpha * layer.alpha).toArgb())
                    canvas.nativeCanvas.drawPath(outline, paint)
                }
                canvas.restore()
            }
        }
    }
