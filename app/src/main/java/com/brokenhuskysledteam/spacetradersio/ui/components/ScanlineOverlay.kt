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

/**
 * Draws faint horizontal scanlines across its entire composable area, mimicking the phosphor
 * line pattern visible on old CRT monitors.
 *
 * **Pattern:** Decorative overlay composable. This is a separate composable rather than a theme
 * attribute or a modifier because it needs to cover the full screen above all other content but
 * must NOT intercept touch events. Keeping it as a standalone composable makes the layering
 * explicit: callers place it as the last child inside a `Box`, giving it Z-order priority over
 * all content while the absence of any `pointerInput`, `clickable`, or `indication` modifier
 * ensures all gestures fall through to the composables beneath it.
 *
 * If this effect were baked into the theme or into individual screens, every screen would either
 * duplicate the Canvas loop or be forced to inherit the overlay with no way to opt out.
 * A standalone composable lets each screen decide whether to include it.
 *
 * **Canvas drawing mechanics:** Inside the [Canvas] lambda, `size` is the measured pixel
 * dimensions of the composable. The loop increments `y` by [lineSpacing] converted to pixels
 * (`lineSpacing.toPx()`), drawing one 1-pixel-wide horizontal line from `x = 0` to
 * `x = size.width` at each `y` position. Because `lineSpacing` is expressed in `dp`, the stripe
 * density is independent of screen density — a 4.dp spacing produces visually consistent
 * scanlines on both low-density and high-density screens.
 *
 * **In this project:** Placed on the auth screen and the dashboard screen to reinforce the
 * retro terminal aesthetic. Because it is purely visual and pass-through, adding or removing it
 * has no impact on functionality or testability.
 *
 * @param modifier Modifier applied to the [Canvas]. In practice callers should pass
 *   `Modifier.matchParentSize()` (inside a `Box`) so the overlay fills exactly the same area as
 *   the content beneath it, or omit the modifier entirely and let [fillMaxSize] inside this
 *   composable expand it to fill the available space. Do NOT add `Modifier.clickable` or
 *   `Modifier.pointerInput` here — doing so would swallow touch events and make all interactive
 *   content beneath the overlay unreachable.
 * @param lineSpacing Vertical distance between scanlines in density-independent pixels. Smaller
 *   values produce a denser, more opaque grid; larger values produce sparse, barely-visible lines.
 *   Defaults to 4.dp, which matches the visual weight of the inspiration images.
 * @param lineColor Color of each scanline. Defaults to `terminalExtras.gridLineColor`, a
 *   very-low-alpha white defined in the theme. Overriding this allows a tinted overlay (e.g.
 *   green-tinted lines on a map screen) without changing the theme.
 */
@Composable
fun ScanlineOverlay(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 4.dp,
    lineColor: Color = MaterialTheme.terminalExtras.gridLineColor
) {
    // fillMaxSize() is applied inside rather than delegated to the caller so that ScanlineOverlay
    // works correctly when placed in a Box with no explicit size — it always covers its parent.
    Canvas(modifier = modifier.fillMaxSize()) {
        // Convert the dp spacing to pixels once, outside the loop, to avoid redundant toPx()
        // conversions on every iteration.
        val spacingPx = lineSpacing.toPx()
        var y = 0f
        // Iterate from the top of the canvas to the bottom, drawing one horizontal line per
        // stride. The loop condition `y < size.height` ensures we never draw outside the bounds.
        while (y < size.height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                // strokeWidth = 1f draws a single physical pixel regardless of density.
                // Using 1f (not 1.dp.toPx()) is intentional: at high density, 1 physical pixel
                // is sub-dp thin, which keeps the lines subtle rather than visually prominent.
                strokeWidth = 1f
            )
            y += spacingPx
        }
    }
}
