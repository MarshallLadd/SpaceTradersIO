package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.brokenhuskysledteam.spacetradersio.ui.theme.terminalExtras
import kotlin.math.roundToInt

// Retro-terminal style progress bar: a bordered rectangle with a filled
// portion proportional to [value] (0f = empty, 1f = full) and an optional
// label overlaid on top (e.g. "340/400" or "67%").
@Composable
fun TerminalProgressBar(
    value: Float,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val clampedValue = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .border(
                width = MaterialTheme.terminalExtras.borderWidth,
                color = color,
                shape = RectangleShape
            )
    ) {
        // Fill bar
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedValue)
                .height(22.dp)
                .background(color.copy(alpha = 0.25f))
        )
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp)
        )
    }
}
