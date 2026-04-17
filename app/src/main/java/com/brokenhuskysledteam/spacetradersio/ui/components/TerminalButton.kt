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

/**
 * Green-bordered button with a transparent background and sharp, 0-radius corners.
 *
 * **Pattern:** Dual-overload composable with a slot-based primary. This overload is the primary
 * definition: it accepts a [content] lambda with a [RowScope] receiver, giving callers full
 * flexibility to place icons, text, or any combination inside the button row. The second overload
 * (see below) is a convenience wrapper that reduces boilerplate for the common case of a plain
 * text label. All real logic — disabled-state colours, shape, border — lives here in the primary
 * overload. The convenience overload delegates to this one, so there is a single source of truth
 * for button behaviour and no risk of the two diverging. To apply this pattern in a new project:
 * write the flexible slot overload first, then add a convenience overload that calls it.
 *
 * **In this project:** Every action button on every screen (login, register, orbit, dock, etc.)
 * uses one of these two overloads. The uniform style is enforced by the component rather than
 * repeated at each call site.
 *
 * @param onClick Callback invoked when the button is tapped. Not called when [enabled] is false
 *   because [OutlinedButton] handles that internally.
 * @param modifier Modifier applied to the [OutlinedButton], allowing callers to set size or
 *   external padding without coupling that concern to the component internals.
 * @param enabled When false, the border and content colour both dim to [TerminalGray]. The
 *   disabled colours are provided explicitly here because Material's default disabled colours
 *   (semi-transparent on a white surface) would not be legible on the terminal's black background.
 * @param content Slot lambda executed inside the button's [RowScope]. The [RowScope] receiver
 *   allows children to use `Modifier.weight()` to distribute space across a mixed icon + text row.
 */
@Composable
fun TerminalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    // Compute border colour before the OutlinedButton call so we can pass a single resolved
    // Color to BorderStroke. ButtonDefaults handles content/container colours separately below.
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary else TerminalGray

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        // Sharp corners are a core part of the terminal aesthetic.
        shape = RectangleShape,
        border = BorderStroke(MaterialTheme.terminalExtras.borderWidth, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            // Transparent container keeps the black background visible through the button,
            // matching the retro terminal look where buttons are outlines, not filled shapes.
            containerColor = Color.Transparent,
            disabledContentColor = TerminalGray,
            disabledContainerColor = Color.Transparent
        ),
        content = content
    )
}

/**
 * Convenience overload that renders a single uppercase text label inside [TerminalButton].
 *
 * **Pattern:** Convenience wrapper calling the primary slot overload. This overload exists solely
 * to eliminate boilerplate at call sites that only need text. It contains no logic of its own —
 * it immediately delegates to the primary overload. Adding features (animations, loading states,
 * etc.) to the primary overload automatically benefits this wrapper too.
 *
 * **In this project:** Used on all screens where a button has a simple label (e.g. "LOGIN",
 * "REGISTER", "ORBIT"). Screens that need an icon + label use the slot overload directly.
 *
 * @param text Label string. Rendered uppercase inside the button to enforce the terminal
 *   convention without requiring the caller to remember to apply `.uppercase()` every time.
 * @param onClick Forwarded to the primary [TerminalButton] overload unchanged.
 * @param modifier Forwarded to the primary [TerminalButton] overload unchanged.
 * @param enabled Forwarded to the primary [TerminalButton] overload unchanged.
 */
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
