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

// Extra theme values that don't fit into Material 3's color scheme slots.
// Access via MaterialTheme.terminalExtras in any composable.
@Immutable
data class TerminalThemeExtras(
    val glowColor: Color = TerminalGreenGlow,
    val gridLineColor: Color = TerminalGridLine,
    val borderWidth: Dp = 1.dp
)

private val LocalTerminalExtras = staticCompositionLocalOf { TerminalThemeExtras() }

val MaterialTheme.terminalExtras: TerminalThemeExtras
    @Composable
    @ReadOnlyComposable
    get() = LocalTerminalExtras.current

// Dark-only color scheme — no light variant. The retro terminal aesthetic
// only works on dark backgrounds.
private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,
    secondary = TerminalGreenDim,
    onSecondary = TerminalBlack,
    tertiary = TerminalAmber,
    onTertiary = TerminalBlack,
    background = TerminalBlack,
    onBackground = TerminalGreen,
    surface = TerminalDarkGray,
    onSurface = TerminalGreen,
    surfaceVariant = TerminalGreenMuted,
    onSurfaceVariant = TerminalGreenDim,
    error = TerminalRed,
    onError = TerminalBlack,
    outline = TerminalGreenDim,
    outlineVariant = TerminalGray
)

// Sharp corners everywhere — CRT terminals don't have rounded edges.
// Shapes slots require CornerBasedShape, so we use 0dp rounded corners
// instead of RectangleShape (which is a generic Shape).
private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun SpaceTradersIOTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTerminalExtras provides TerminalThemeExtras()) {
        MaterialTheme(
            colorScheme = TerminalColorScheme,
            typography = Typography,
            shapes = TerminalShapes,
            content = content
        )
    }
}
