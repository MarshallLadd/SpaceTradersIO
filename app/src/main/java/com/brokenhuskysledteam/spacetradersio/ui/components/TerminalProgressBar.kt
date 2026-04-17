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

/**
 * Retro-terminal style progress bar: a bordered rectangle with a filled portion proportional to
 * [value] and an optional text label centred on top.
 *
 * **Pattern:** Declarative layout-based progress bar (no custom Canvas drawing). Rather than
 * using `Canvas.drawRect`, this component layers two `Box` composables inside a parent `Box`:
 * one `Box` is the full-width border shell, and a nested inner `Box` uses `fillMaxWidth(fraction)`
 * to occupy exactly `value * 100%` of the available width. This is the simplest correct approach
 * in Compose when the filled portion is axis-aligned and rectangular — no coordinate arithmetic
 * is required because the layout system handles the width calculation.
 *
 * The filled portion width is therefore calculated implicitly: `fillMaxWidth(clampedValue)` tells
 * Compose to measure the inner box at `clampedValue` (0.0–1.0) of the parent's measured width.
 * For example, a `value` of 0.67f fills 67% of the bar width regardless of screen density or
 * the parent's actual pixel size.
 *
 * **In this project:** Used on the ship detail screen to display hull integrity, fuel level, and
 * cargo capacity as compact single-line indicators that fit inside a [TerminalCard] without
 * requiring extra height.
 *
 * @param value Progress fraction in the range [0.0, 1.0]. Values outside this range are clamped
 *   by [coerceIn] before use, so callers do not need to guard against out-of-range inputs
 *   (e.g. a fuel value that temporarily exceeds capacity due to a rounding mismatch in the API).
 * @param label Text drawn centred over the bar. Typical values are "340/400" (absolute) or
 *   "67%" (percentage). The label is always visible regardless of [value], so even an empty
 *   bar still shows its label.
 * @param modifier Modifier applied to the outer border [Box]. Use this to control horizontal
 *   padding, max-width constraints, or additional decoration.
 * @param color Tint used for the border, the fill background (at reduced alpha), and the label
 *   text. Defaults to `MaterialTheme.colorScheme.primary` (terminal green). Passing a different
 *   colour allows callers to signal warning (amber) or critical (red) states.
 */
@Composable
fun TerminalProgressBar(
    value: Float,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    // Clamp before using the value in the layout so that invalid inputs (negative fuel, capacity
    // overflow) never crash fillMaxWidth(), which requires a fraction in [0, 1].
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
        // Fill bar: fillMaxWidth(clampedValue) pins the inner box's width to
        // `clampedValue * parentWidth`. The filled portion grows from the leading edge,
        // matching the left-to-right reading direction expected for a progress indicator.
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedValue)
                .height(22.dp)
                // alpha(0.25f) creates a translucent tint instead of a solid fill so that
                // the label text centred over the bar remains legible at all fill levels.
                .background(color.copy(alpha = 0.25f))
        )
        // Label overlaid in the centre of the bar using Box alignment.
        // This sits in the Box stack above the fill bar, so it renders on top.
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
