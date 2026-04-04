package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.brokenhuskysledteam.spacetradersio.ui.theme.TerminalBlack

// OutlinedTextField pre-styled for the terminal aesthetic: green borders,
// green text, black container, sharp corners. Label is rendered uppercase.
@Composable
fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.primary,
        unfocusedTextColor = MaterialTheme.colorScheme.primary,
        focusedContainerColor = TerminalBlack,
        unfocusedContainerColor = TerminalBlack
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase()) },
        singleLine = singleLine,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        colors = colors,
        shape = RectangleShape,
        modifier = modifier
    )
}
