package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brokenhuskysledteam.spacetradersio.ui.theme.terminalExtras

// Draws faint horizontal scanlines across the full composable area,
// mimicking the look of an old CRT monitor. Layer this in a Box on
// top of screen content. Non-interactive — passes touch through.
@Composable
fun ScanlineOverlay(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 4.dp,
    lineColor: Color = MaterialTheme.terminalExtras.gridLineColor
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val spacingPx = lineSpacing.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacingPx
        }
    }
}
