package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import com.brokenhuskysledteam.spacetradersio.ui.theme.TerminalGray
import com.brokenhuskysledteam.spacetradersio.ui.theme.terminalExtras

// Green-bordered button with transparent background and sharp corners.
// Disabled state dims the border to gray.
@Composable
fun TerminalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary else TerminalGray

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        border = BorderStroke(MaterialTheme.terminalExtras.borderWidth, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = Color.Transparent,
            disabledContentColor = TerminalGray,
            disabledContainerColor = Color.Transparent
        ),
        content = content
    )
}

// Convenience overload that renders uppercase text automatically.
@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TerminalButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text.uppercase())
    }
}
