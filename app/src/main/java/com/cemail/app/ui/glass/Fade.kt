package com.cemail.app.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

// Glass V2: lists that scroll under floating glass fade out at the edge instead of ending in a hard line.

/** Fades content out over [height] at the top edge, for lists that scroll below floating controls. */
fun Modifier.fadingTopEdge(height: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fraction = (height.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(0f to Color.Transparent, fraction to Color.Black), blendMode = BlendMode.DstIn)
    }

/** Fades content out over [height] at the bottom edge, so a list that continues reads as continuing. */
fun Modifier.fadingBottomEdge(height: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fraction = (height.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(1f - fraction to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
