package com.cemail.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
private val YahooPurple = Color(0xFF6001D2)

/** Yahoo's mark: "Y!" in white on its purple, in a rounded square. ("Y!" is the logo, not text to translate.) */
@Composable
fun YahooLogo(size: Dp = 32.dp) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(size).background(YahooPurple, androidx.compose.foundation.shape.RoundedCornerShape(size * 0.28f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            "Y!",
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.46f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
    }
}

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
        // Angles run clockwise from 3 o'clock. The opening of the G is the upper right (315° to 360°): the red arc ends
        // before it and the blue one starts at the bar, so the ring does not close.
        arc(GoogleRed, 210f, 105f)
        arc(GoogleYellow, 150f, 60f)
        arc(GoogleGreen, 40f, 110f)
        arc(GoogleBlue, 0f, 40f)
        // The bar of the G, from the middle to the outer edge of the ring.
        val centre = this.size.height / 2
        drawRect(GoogleBlue, Offset(this.size.width * 0.5f, centre - stroke / 2), Size(this.size.width * 0.5f, stroke))
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
