package com.cemail.app.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cemail.app.ui.theme.CemailTheme
import com.cemail.app.ui.theme.LocalSkin

/**
 * The app's progress bar: the track of [GlassSlider] (12 dp, the accent faintly behind, the accent filling it) without
 * the knob, in the shape of the current look (round in glass, square in Retro 98). [fraction] is 0..1; it moves to a
 * new value with the glass spring instead of jumping.
 */
@Composable
fun GlassProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = LocalSkin.current.shape(CircleShape)
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), glassSpring(), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }
            .clip(shape)
            .background(primary.copy(alpha = 0.16f)),
    ) {
        if (shown > 0f) Box(Modifier.fillMaxWidth(shown.coerceAtLeast(0.03f)).fillMaxHeight().clip(shape).background(primary))
    }
}

@Preview
@Composable
private fun GlassProgressBarPreview() {
    CemailTheme { GlassProgressBar(0.45f) }
}
