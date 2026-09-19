package com.sankatsetu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sankatsetu.app.ui.theme.ConsoleReadoutStyle
import com.sankatsetu.app.ui.theme.PillShape

/**
 * One consistent status vocabulary across every tab (peer connection state,
 * IOU status, message delivery) — a tinted pill, monospace uppercase word,
 * never color alone (screen readers and colorblind users get the same word
 * everyone else does). Background is the status color at low opacity, not
 * a solid fill, so it reads as a tag rather than a loud alert — reserving
 * genuine visual weight for the rare moment color alone should draw the
 * eye. See docs/adr/0018-ui-revamp.md.
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ConsoleReadoutStyle,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
