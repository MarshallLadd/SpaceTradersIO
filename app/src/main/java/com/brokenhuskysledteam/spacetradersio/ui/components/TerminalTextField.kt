package com.brokenhuskysledteam.spacetradersio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.brokenhuskysledteam.spacetradersio.ui.theme.TerminalBlack

/**
 * Pre-styled wrapper around Material 3's [OutlinedTextField] that enforces the terminal theme.
 *
 * **Pattern:** Pre-styled wrapper composable. Rather than passing a `colors`, `shape`, and
 * `label` transform at every call site, this wrapper bakes in all theme-specific styling once.
 * Callers only supply the parameters that genuinely vary between usages (the value, the callback,
 * the label string, and a handful of behavioural flags). This pattern is ideal when a design
 * system has a single canonical look for a Material component: wrap it once, use the wrapper
 * everywhere, and change the theme in one place.
 *
 * **In this project:** Every text input in the app (token import, callsign registration, etc.)
 * uses this wrapper so that the green-on-black bordered aesthetic is applied consistently without
 * copy-pasting the `OutlinedTextFieldDefaults.colors(...)` block across screens.
 *
 * The color configuration is extracted into a local `colors` variable rather than inlined
 * directly in the `OutlinedTextField` call. This keeps the call site readable and makes it easy
 * to scan which color slots are overridden without visually parsing a deeply nested argument list.
 *
 * @param value The current text content of the field, typically driven by a `StateFlow` in the
 *   associated ViewModel.
 * @param onValueChange Callback invoked on every keystroke with the updated text. Forward this
 *   directly to the ViewModel event handler.
 * @param label Short description of the field's purpose. Rendered uppercase inside the field's
 *   label slot so callers can pass naturally-cased strings (e.g. "Call Sign") and the terminal
 *   convention is enforced here.
 * @param modifier Modifier applied to the [OutlinedTextField], allowing callers to control
 *   external sizing and padding.
 * @param singleLine When true (default), the field collapses to a single line and hides the
 *   newline action. Set to false for multi-line inputs such as long token strings.
 * @param readOnly When true, the field displays text but does not accept input or show a cursor.
 *   Useful for read-only data display that still benefits from the styled border and label.
 * @param trailingIcon Optional composable rendered at the trailing end of the field. Common uses
 *   include a visibility-toggle icon for password fields or a copy-to-clipboard icon.
 */
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
    // Extracting colors into a local variable keeps the OutlinedTextField call below short
    // and scannable. The alternative — inlining all color overrides as named arguments — would
    // push the actual field parameters far down in the call, making them harder to find.
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.primary,
        // Unfocused text intentionally uses the same primary green so content remains legible
        // even when the field loses focus — important for token display fields.
        unfocusedTextColor = MaterialTheme.colorScheme.primary,
        // TerminalBlack is used instead of MaterialTheme.colorScheme.surface to ensure the
        // container is fully opaque black regardless of surface elevation tinting.
        focusedContainerColor = TerminalBlack,
        unfocusedContainerColor = TerminalBlack
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // Uppercase applied here, not at the call site, to enforce the terminal convention
        // without requiring each caller to remember to call .uppercase() on the label string.
        label = { Text(label.uppercase()) },
        singleLine = singleLine,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        colors = colors,
        // Sharp corners match the rest of the terminal component set.
        shape = RectangleShape,
        modifier = modifier
    )
}
