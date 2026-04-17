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

/**
 * Bordered panel styled after the data cards in the retro terminal inspiration images.
 *
 * **Pattern:** Slot-based composable component. The [content] parameter is a lambda with a
 * [ColumnScope] receiver rather than a fixed set of child parameters. This allows callers to
 * place any number of arbitrarily-typed children inside the card without requiring a new overload
 * for every combination. The scoped receiver ([ColumnScope]) is intentional: it exposes layout
 * helpers like `weight()` that only make sense inside a `Column`, so the compiler enforces
 * correct usage at the call site.
 *
 * **In this project:** Every screen that groups related data — agent stats, ship details,
 * navigation info — wraps that content in a `TerminalCard` to achieve the consistent
 * bordered-panel look defined by the terminal theme. The optional [title] produces an
 * "UPPERCASE LABEL ─────────────" header matching the style visible in the inspiration images.
 *
 * @param modifier Modifier applied to the outermost [Column], allowing the caller to control
 *   size, padding, and alignment from outside the component.
 * @param title Optional header text. When non-null, an uppercase label and a [HorizontalDivider]
 *   extending to the right are rendered above the bordered surface. When null, no header is drawn
 *   and the bordered surface starts immediately.
 * @param borderColor Color used for both the outer border and the title/divider. Defaults to
 *   `MaterialTheme.colorScheme.primary` (terminal green), but screens can pass a different hue
 *   to create visual hierarchy between cards.
 * @param backgroundColor Fill color of the [Surface] behind the content. Defaults to
 *   `MaterialTheme.colorScheme.surface` (near-black in the terminal theme).
 * @param content Slot lambda executed inside an inner [Column] that is padded inside the border.
 *   The [ColumnScope] receiver lets children use `Modifier.weight()` to distribute vertical space.
 */
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
                // Uppercase transform is applied here rather than at the call site so that
                // callers can pass naturally-cased strings without remembering the convention.
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = borderColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                // weight(1f) stretches the divider to fill all remaining horizontal space,
                // creating the "LABEL ─────" visual without needing a fixed width.
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = borderColor,
                    thickness = MaterialTheme.terminalExtras.borderWidth
                )
            }
        }
        // RectangleShape gives the sharp, 0-radius corners that are a core part of the
        // terminal aesthetic. Rounded corners would break the visual language.
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
