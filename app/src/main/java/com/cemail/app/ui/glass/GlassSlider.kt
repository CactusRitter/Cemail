package com.cemail.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cemail.app.ui.theme.CemailTheme

/**
 * The app's slider (docs/DESIGN.md, "Deslizador"), like Celander's: a rounded 12 dp track and a round knob with a
 * white ring and a soft shadow. By default the track fills with the accent up to the knob; [track] paints the whole
 * track instead (the rainbow of "Custom colour"), and [knobColor] is the knob's centre. [value] null shows no knob.
 * Tap anywhere on it or drag along it.
 */
@Composable
fun GlassSlider(
    value: Float?,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    track: Brush? = null,
    knobColor: Color = MaterialTheme.colorScheme.primary,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = value?.let { ((it - valueRange.start) / span).coerceIn(0f, 1f) }
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value ?: valueRange.start, valueRange)
                setProgress { target -> onValueChange(target.coerceIn(valueRange)); true }
            },
    ) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val barWidth = maxWidth
        val pick: (Float) -> Unit = { x -> onValueChange(valueRange.start + (x / width).coerceIn(0f, 1f) * span) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(width, valueRange) { detectTapGestures { pick(it.x) } }
                .pointerInput(width, valueRange) { detectHorizontalDragGestures { change, _ -> pick(change.position.x) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (track != null) {
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(track))
            } else {
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(primary.copy(alpha = 0.16f)))
                if (fraction != null) {
                    Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.03f)).height(12.dp).clip(CircleShape).background(primary))
                }
            }
            if (fraction != null) {
                val knob = 28.dp
                val x = with(LocalDensity.current) { (fraction * width).toDp() } - knob / 2
                Box(
                    Modifier
                        .offset(x = x.coerceIn(0.dp, barWidth - knob))
                        .size(knob)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                        .background(knobColor, CircleShape),
                )
            }
        }
    }
}

@Preview
@Composable
private fun GlassSliderPreview() {
    CemailTheme { GlassSlider(0.65f, {}) }
}
