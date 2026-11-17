package com.cemail.app.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cemail.app.ui.theme.LocalSkin
import com.cemail.app.ui.theme.Skin

/**
 * The checkbox of a task. Most designs keep [default] (Material's box, or the icon of a preview); Retro 98 draws the
 * Windows 98 one instead: a sunken white square with a bevelled edge and a black tick. [onCheckedChange] null makes it
 * a picture only (note previews).
 */
@Composable
fun TaskCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    default: @Composable () -> Unit,
) {
    val skin = LocalSkin.current
    if (skin.skin != Skin.RETRO || skin.bevel.size < 4) {
        default()
        return
    }
    val (hi, light, shade, dark) = skin.bevel
    val fill = skin.listFill ?: Color.White
    val tick = MaterialTheme.colorScheme.onSurface
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Canvas(modifier.then(toggle).size(size)) {
        val px = kotlin.math.max(1f, kotlin.math.round(density))
        val w = this.size.width
        val h = this.size.height
        // Sunken edge: dark along the top and start, light along the bottom and end (two lines each), like a text field.
        fun frame(inset: Float, tl: Color, br: Color) {
            drawRect(tl, Offset(inset, inset), Size(w - 2 * inset, px))
            drawRect(tl, Offset(inset, inset), Size(px, h - 2 * inset))
            drawRect(br, Offset(inset, h - inset - px), Size(w - 2 * inset, px))
            drawRect(br, Offset(w - inset - px, inset), Size(px, h - 2 * inset))
        }
        drawRect(fill, Offset(2 * px, 2 * px), Size(w - 4 * px, h - 4 * px))
        frame(0f, shade, hi)
        frame(px, dark, light)
        if (checked) {
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.5f)
                lineTo(w * 0.42f, h * 0.68f)
                lineTo(w * 0.76f, h * 0.3f)
            }
            drawPath(path, tick, style = Stroke(width = w * 0.14f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
    }
}
