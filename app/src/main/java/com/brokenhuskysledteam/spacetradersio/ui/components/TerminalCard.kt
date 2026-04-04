package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.brokenhuskysledteam.spacetradersio.ui.theme.terminalExtras

// Bordered panel styled after the data cards in the inspiration images.
// Optional title renders as an uppercase header with a decorative divider
// extending to the right, like "MISSION INFO ─────────────".
@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = borderColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = borderColor,
                    thickness = MaterialTheme.terminalExtras.borderWidth
                )
            }
        }
        Surface(
            color = backgroundColor,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = MaterialTheme.terminalExtras.borderWidth,
                    color = borderColor,
                    shape = RectangleShape
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}
