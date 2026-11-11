package com.cemail.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The only fixed colours in the app: the logos of the services an account can belong to must look like the real ones,
// so they cannot follow the theme. They are drawn here as simple shapes (no image files) and used only in the
// "Add account" screen. See docs/DESIGN.md.
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)

/** The multicolour "G": four arcs of a ring and the bar of the blue one. */
@Composable
fun GoogleLogo(size: Dp = 32.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.22f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        fun arc(color: Color, start: Float, sweep: Float) =
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        arc(GoogleRed, 200f, 100f)
        arc(GoogleYellow, 140f, 60f)
        arc(GoogleGreen, 50f, 90f)
        arc(GoogleBlue, -40f, 90f)
        // The bar of the G, from the middle to the right edge.
        val centre = this.size.height / 2
        drawRect(GoogleBlue, Offset(this.size.width * 0.5f, centre - stroke / 2), Size(this.size.width * 0.5f - inset * 0.2f, stroke))
    }
}

/** The four coloured squares. */
@Composable
fun MicrosoftLogo(size: Dp = 32.dp) {
    Canvas(Modifier.size(size)) {
        val gap = this.size.minDimension * 0.07f
        val square = (this.size.minDimension - gap) / 2
        val cell = Size(square, square)
        drawRect(Color(0xFFF25022), Offset(0f, 0f), cell)
        drawRect(Color(0xFF7FBA00), Offset(square + gap, 0f), cell)
        drawRect(Color(0xFF00A4EF), Offset(0f, square + gap), cell)
        drawRect(Color(0xFFFFB900), Offset(square + gap, square + gap), cell)
    }
}
