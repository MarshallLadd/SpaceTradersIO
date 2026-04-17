package com.brokenhuskysledteam.spacetradersio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Named color constants for the SpaceTraders retro terminal design system.
 *
 * **Pattern:** Semantic color palette. Define every color used in the app as a named
 * constant here rather than scattering raw hex literals through composables. This makes
 * global rebranding a single-file change and makes usages self-documenting at the call site.
 *
 * **In this project:** The palette is intentionally narrow — a CRT-green primary family,
 * a minimal set of neutrals, and two accent colors for status signals. All values feed
 * into [TerminalColorScheme] in `Theme.kt` and are never used directly in composables
 * except through the theme.
 *
 * ### Color groups
 *
 * **Primary green variants** (`TerminalGreen*`) — the full luminance range of the terminal
 * green used for text, borders, backgrounds, and muted fills. Map to Material 3's
 * `primary`, `secondary`, `surface`, and `surfaceVariant` slots.
 *
 * **Neutrals** (`TerminalBlack`, `TerminalDarkGray`, `TerminalGray`) — background layers
 * that create depth. `TerminalBlack` is the root canvas; `TerminalDarkGray` is used for
 * raised surfaces (cards, panels).
 *
 * **Accents** (`TerminalAmber`, `TerminalRed`) — single-purpose status colors. Amber maps
 * to Material 3's `tertiary` slot (warnings, in-progress states). Red maps to the `error`
 * slot (failures, destructive actions).
 *
 * **Low-alpha overlay variants** (`TerminalGreenGlow`, `TerminalGridLine`) — pre-defined
 * translucent values used for decorative effects without coupling the alpha decision to
 * individual composables. See below for details.
 */

// --- Primary green variants ---
// The four-step luminance ramp covers every role the green can play:
// full-brightness text, dimmed secondary text, deep-shadow backgrounds, and muted fills.

/** Full-brightness terminal green. Primary text color and interactive element highlight. */
val TerminalGreen = Color(0xFF00FF41)

/** Dimmed terminal green (~80% brightness). Secondary text, borders, and inactive states. */
val TerminalGreenDim = Color(0xFF00CC33)

/**
 * Near-black green. Used as a deep background tint where a pure black would feel flat —
 * keeps the "everything is green" illusion even in dark regions.
 */
val TerminalGreenDark = Color(0xFF003B00)

/**
 * Very dark desaturated green. Used for surface fills (e.g. card backgrounds) where the
 * color needs to recede but still read as part of the green family.
 */
val TerminalGreenMuted = Color(0xFF1A3A1A)

// --- Neutrals ---
// Three black/gray steps to layer depth without introducing any hue shift.

/** Root canvas color. The darkest background; applied to `background` and `onPrimary`. */
val TerminalBlack = Color(0xFF0D1117)

/** Slightly lighter dark surface. Applied to raised surfaces such as cards and panels. */
val TerminalDarkGray = Color(0xFF131A13)

/** Mid-tone gray. Used for `outlineVariant` — subtle dividers that don't demand attention. */
val TerminalGray = Color(0xFF2A2A2A)

// --- Accent / status colors ---

/** Amber warning color. Maps to Material 3's `tertiary` slot. Used for caution states. */
val TerminalAmber = Color(0xFFFFAA00)

/** Error red. Maps to Material 3's `error` slot. Used for failures and destructive actions. */
val TerminalRed = Color(0xFFFF3333)

// --- Low-alpha overlay variants ---
// These variants are defined here (not applied ad hoc with .copy(alpha = x)) for two reasons:
//   1. Consistency — every glow effect in the app uses the exact same alpha value, so the
//      visual intensity is uniform without relying on each developer remembering the number.
//   2. Discoverability — naming the intent ("Glow", "GridLine") makes it obvious at the
//      call site what the translucent color is for, whereas a bare `copy(alpha = 0.2f)`
//      carries no semantic meaning.

/**
 * Terminal green at ~20% opacity (alpha 0x33 = 51/255 ≈ 20%).
 * Used for glow halos behind highlighted elements and button press feedback.
 */
val TerminalGreenGlow = Color(0x3300FF41)

/**
 * Terminal green at ~10% opacity (alpha 0x1A = 26/255 ≈ 10%).
 * Used for the decorative scanline / grid overlay drawn on top of content panels.
 * Lower alpha than [TerminalGreenGlow] so it never obscures the text beneath it.
 */
val TerminalGridLine = Color(0x1A00FF41)
