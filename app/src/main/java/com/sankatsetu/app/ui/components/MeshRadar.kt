package com.sankatsetu.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A pulsing radar sweep — three rings expanding outward from a solid
 * center dot, fading as they grow — replacing a static icon for the
 * mesh's "actively searching" state. This is the app's own core metaphor
 * (a Bluetooth radio listening for other radios) rendered as motion
 * instead of a frozen glyph, which is what a wireframe-feeling empty state
 * was missing: something that visibly *does something* while there's
 * nothing to show yet. See docs/adr/0018-ui-revamp.md's revision note.
 */
@Composable
fun MeshRadar(
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    ringCount: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "meshRadar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "radarProgress"
    )

    Canvas(modifier = modifier.size(diameter)) {
        val maxRadius = this.size.minDimension / 2f
        val centerDotRadius = maxRadius * 0.14f

        for (i in 0 until ringCount) {
            // Each ring is offset in phase so they appear as a continuous
            // outward pulse rather than ringCount rings moving in lockstep.
            val ringProgress = (progress + i.toFloat() / ringCount) % 1f
            val radius = centerDotRadius + (maxRadius - centerDotRadius) * ringProgress
            val alpha = (1f - ringProgress).coerceIn(0f, 1f) * 0.6f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        drawCircle(color = color, radius = centerDotRadius)
    }
}
