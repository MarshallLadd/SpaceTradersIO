package com.brokenhuskysledteam.spacetradersio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Theme values that extend Material 3's built-in color scheme slots.
 *
 * **Pattern:** Custom MaterialTheme extension via [CompositionLocal]. Material 3's
 * [MaterialTheme] only exposes a fixed set of semantic color slots. When a design system
 * needs values that don't map cleanly to those slots — glow intensities, decorative line
 * colors, custom spacing — the idiomatic Compose approach is:
 *
 * 1. Create an `@Immutable` data class that holds the extra values.
 * 2. Wrap it in a `staticCompositionLocalOf` with a safe default.
 * 3. Expose it as an extension property on [MaterialTheme] so call sites look consistent
 *    with the rest of the theme API (`MaterialTheme.terminalExtras.glowColor`).
 *
 * To apply this pattern in a new project: copy the data class, the `CompositionLocal`, and
 * the extension property; swap in your own values; then provide the local inside your
 * theme composable alongside [MaterialTheme].
 *
 * **In this project:** Holds the green glow and grid-line overlay colors (both low-alpha
 * variants defined in `Color.kt`) plus a standard border stroke width. These values are
 * used by [TerminalCard] and [ScanlineOverlay] to paint decorative effects.
 *
 * @property glowColor Translucent green used for halo/glow effects behind highlighted elements.
 * @property gridLineColor Very low-opacity green used for the scanline overlay drawn on panels.
 * @property borderWidth Stroke width for all terminal-style bordered components.
 */
@Immutable
data class TerminalThemeExtras(
    val glowColor: Color = TerminalGreenGlow,
    val gridLineColor: Color = TerminalGridLine,
    val borderWidth: Dp = 1.dp
)

/**
 * [ProvidableCompositionLocal] that carries [TerminalThemeExtras] down the composition tree.
 *
 * **Why `staticCompositionLocalOf` instead of `compositionLocalOf`?**
 * `staticCompositionLocalOf` triggers a full recompose of every consumer when its value
 * changes, rather than a scoped recompose. This is the correct choice for theme objects
 * because they are provided once at the root and never change during the app's lifetime.
 * Using the static variant avoids the overhead of slot-table bookkeeping that the dynamic
 * variant incurs for a value that will never actually change.
 *
 * The lambda default `{ TerminalThemeExtras() }` ensures that any composable which
 * accidentally runs outside [SpaceTradersIOTheme] still gets safe default values rather
 * than throwing.
 */
private val LocalTerminalExtras = staticCompositionLocalOf { TerminalThemeExtras() }

/**
 * Extension property on [MaterialTheme] that exposes [TerminalThemeExtras] to any composable.
 *
 * **Why an extension on MaterialTheme?** Composables that consume theme values already
 * import `MaterialTheme` to access colors and typography. Hanging custom extras off the
 * same object keeps all theme access syntactically uniform:
 *
 * ```kotlin
 * val color    = MaterialTheme.colorScheme.primary      // built-in
 * val glow     = MaterialTheme.terminalExtras.glowColor // custom
 * ```
 *
 * The [@ReadOnlyComposable] annotation tells the compiler that this getter only reads
 * composition state and never causes side-effects, enabling minor recomposition optimisations.
 */
val MaterialTheme.terminalExtras: TerminalThemeExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalTerminalExtras.current

/**
 * Dark-only Material 3 color scheme mapping the terminal palette to semantic slots.
 *
 * No light variant is defined because the retro CRT aesthetic is fundamentally a
 * dark-background design — bright green on a light background inverts the visual metaphor
 * entirely. Forcing dark-only at the theme level means no composable ever needs to
 * branch on `isSystemInDarkTheme()`.
 */
private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,
    secondary = TerminalGreenDim,
    onSecondary = TerminalBlack,
    tertiary = TerminalAmber,         // Amber = warning / in-progress signals.
    onTertiary = TerminalBlack,
    background = TerminalBlack,
    onBackground = TerminalGreen,
    surface = TerminalDarkGray,       // Slightly lighter than background to show card depth.
    onSurface = TerminalGreen,
    surfaceVariant = TerminalGreenMuted,
    onSurfaceVariant = TerminalGreenDim,
    error = TerminalRed,
    onError = TerminalBlack,
    outline = TerminalGreenDim,       // Border color for outlined components.
    outlineVariant = TerminalGray     // Subtle divider variant; less prominent than outline.
)

/**
 * Sharp-corner shape override applied to every Material 3 shape slot.
 *
 * Material 3 defaults to rounded corners at various radii. CRT terminals have hard,
 * right-angle edges, so all five shape slots are set to `RoundedCornerShape(0.dp)`.
 *
 * **Gotcha:** Material 3's `Shapes` slots are typed as [androidx.compose.ui.graphics.Shape],
 * but their actual required type is [androidx.compose.foundation.shape.CornerBasedShape].
 * `RectangleShape` is a generic `Shape` and will not compile here. `RoundedCornerShape(0.dp)`
 * is a `CornerBasedShape` that renders identically to a rectangle.
 */
private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

/**
 * Root theme composable for the SpaceTraders Android app.
 *
 * **Pattern:** Dark-only app theme with a custom [CompositionLocal] extension. Wrap the
 * entire app's content with this composable (typically in `MainActivity` or the root
 * navigation host). All child composables can then read colors, typography, shapes, and
 * custom extras through `MaterialTheme.*` without accepting theme parameters explicitly.
 *
 * **In this project:** This composable:
 * - Provides [TerminalThemeExtras] via [CompositionLocalProvider] so decorative values
 *   are available via `MaterialTheme.terminalExtras`.
 * - Applies [TerminalColorScheme] (dark-only) and [TerminalShapes] (zero-radius) to
 *   [MaterialTheme], overriding all Material 3 component defaults.
 * - Passes the monospace [Typography] scale so all `Text` composables inherit the
 *   terminal font family unless they opt out explicitly.
 *
 * **Dark-only enforcement:** No `isSystemInDarkTheme()` branch exists because only one
 * color scheme is defined. Passing a light scheme is unnecessary here, but in projects
 * that do support both themes, you would select between `lightColorScheme` and
 * `darkColorScheme` at this layer and nowhere else.
 *
 * @param content The composable subtree that will inherit this theme.
 */
@Composable
fun SpaceTradersIOTheme(content: @Composable () -> Unit) {
    // CompositionLocalProvider must wrap MaterialTheme so that LocalTerminalExtras is
    // available to any composable that calls MaterialTheme.terminalExtras. Reversing the
    // nesting order would work at runtime in most cases, but is conceptually wrong —
    // the extras are part of the theme, so they should be established before the theme.
    CompositionLocalProvider(LocalTerminalExtras provides TerminalThemeExtras()) {
        MaterialTheme(
            colorScheme = TerminalColorScheme,
            typography = Typography,
            shapes = TerminalShapes,
            content = content
        )
    }
}
