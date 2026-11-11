package com.cemail.app.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cemail.app.ui.theme.CemailTheme

/**
 * The app's switch (docs/DESIGN.md, "Interruptor"): a small rounded track (40 × 24 dp) that fills with the accent
 * when on, and a round knob that springs across with a little bounce. Off, the track is a faint glass outline.
 * [onCheckedChange] null makes it a picture only (the whole row is the touch target, or the value is fixed).
 */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val track by animateColorAsState(if (checked) c.primary else c.surfaceVariant.copy(alpha = 0.6f), glassSpring(), label = "track")
    val knob by animateColorAsState(if (checked) c.onPrimary else c.outline, glassSpring(), label = "knob")
    val x by animateDpAsState(if (checked) 16.dp else 0.dp, bounceSpring(), label = "knobX")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(
        modifier
            .then(toggle)
            .size(width = 40.dp, height = 24.dp)
            .background(track, CircleShape)
            .border(1.dp, if (checked) c.primary else c.outline.copy(alpha = 0.5f), CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Knob: a little smaller when off, like a pressed-in button, and full size when on.
        Box(Modifier.offset(x = x).size(18.dp).padding(if (checked) 0.dp else 2.dp).background(knob, CircleShape))
    }
}

@Preview
@Composable
private fun GlassSwitchPreview() {
    CemailTheme {
        androidx.compose.foundation.layout.Row {
            GlassSwitch(checked = true, onCheckedChange = {})
            GlassSwitch(checked = false, onCheckedChange = {})
        }
    }
}
