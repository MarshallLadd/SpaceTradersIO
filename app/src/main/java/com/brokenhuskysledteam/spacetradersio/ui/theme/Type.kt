package com.brokenhuskysledteam.spacetradersio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 Typography scale for the SpaceTraders retro terminal design system.
 *
 * **Pattern:** Centralized typography scale. Define a single [Typography] instance that
 * overrides all 15 Material 3 text roles in one place, then pass it to [MaterialTheme].
 * Composables reference roles by name (`MaterialTheme.typography.bodyMedium`) rather than
 * hard-coding sizes and weights directly, so typographic adjustments are a single-file change.
 *
 * **In this project:** Every role uses [FontFamily.Monospace] to enforce the retro CRT
 * terminal aesthetic. The display/headline/title tier also applies bold weight and wide
 * letter spacing to produce the blocky, stenciled look from the design inspiration images.
 *
 * ### Material 3 typography tier overview
 *
 * Material 3 groups its 15 roles into five semantic tiers. Understanding when to use each
 * tier is more important than memorising the exact sizes:
 *
 * | Tier | Roles | Typical use |
 * |---|---|---|
 * | **display** | Large / Medium / Small | Hero text, splash screens, giant numbers |
 * | **headline** | Large / Medium / Small | Screen titles, modal headings |
 * | **title** | Large / Medium / Small | Card headings, list section labels, toolbar text |
 * | **body** | Large / Medium / Small | Paragraph content, descriptions, data values |
 * | **label** | Large / Medium / Small | Buttons, tags, captions, metadata |
 *
 * ### Design decisions
 *
 * **Why monospace across all tiers?** Variable-width fonts (the Material 3 default) feel
 * modern and clean, but monospace fonts mimic the character-cell output of a real terminal.
 * Using [FontFamily.Monospace] everywhere is the single most impactful choice for selling
 * the CRT aesthetic.
 *
 * **Why bold + wide letter spacing on display/headline/title?** The inspiration images show
 * uppercase, widely-spaced headers that look stamped or stenciled — a common stylistic
 * choice in retro sci-fi UI. Bold weight gives visual mass; letter spacing above 1.sp
 * opens the characters up to read as individual glyphs rather than a flowing word.
 *
 * **Why normal weight + tight spacing on body?** Body text is read in volume. Bold monospace
 * at small sizes becomes dense and fatiguing. Dropping to [FontWeight.Normal] and near-zero
 * letter spacing preserves readability for longer data displays.
 *
 * **Why medium weight on label?** Labels sit between body and title — slightly more visual
 * weight than prose but not as loud as a heading. [FontWeight.Medium] hits that middle
 * ground. The wider letter spacing (1–1.5.sp) distinguishes them from body lines at a glance.
 */
val Typography = Typography(
    // -------------------------------------------------------------------------
    // Display tier — hero / splash text. Rarely used at runtime; mostly for
    // loading screens or dramatic single-number readouts (e.g. credit balance).
    // -------------------------------------------------------------------------

    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 3.sp   // Extra-wide spacing reinforces the stenciled look at large sizes.
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 2.5.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 2.sp
    ),

    // -------------------------------------------------------------------------
    // Headline tier — screen-level and section-level titles.
    // Used for the top title of each screen and major card headings.
    // -------------------------------------------------------------------------

    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 2.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 1.5.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.5.sp
    ),

    // -------------------------------------------------------------------------
    // Title tier — component-level labels such as card headers, list items,
    // toolbar titles, and dialog headings.
    // -------------------------------------------------------------------------

    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp
    ),

    // -------------------------------------------------------------------------
    // Body tier — paragraph text, data values, descriptions.
    // Normal weight and minimal letter spacing maximize readability in lists.
    // -------------------------------------------------------------------------

    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // -------------------------------------------------------------------------
    // Label tier — buttons, tags, status chips, captions.
    // Medium weight and wider spacing make short strings stand out in compact UI.
    // -------------------------------------------------------------------------

    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.5.sp  // Widest spacing in label tier — suits uppercase button text.
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    )
)
