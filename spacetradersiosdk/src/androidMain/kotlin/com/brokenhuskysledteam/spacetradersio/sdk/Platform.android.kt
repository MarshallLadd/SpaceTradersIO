package com.brokenhuskysledteam.spacetradersio.sdk

/**
 * Android-specific implementation of the `expect fun platform()` declaration in `commonMain`.
 *
 * **Pattern:** `actual` implementation. The `actual` keyword tells the Kotlin compiler
 * that this function fulfills the `expect fun platform()` contract for the Android
 * target. This file exists only in `androidMain` and is compiled exclusively when
 * building for Android — it is invisible to iOS targets and to any shared
 * `commonMain` code that calls `platform()`.
 *
 * **Compiler enforcement:** Every `expect` declaration must have an `actual` in each
 * compiled target. If this file were deleted, the Android build would fail with
 * "Expected function 'platform' has no actual declaration in module." This
 * enforcement is what makes the expect/actual pattern safe: you cannot accidentally
 * ship a target with a missing implementation.
 *
 * **In this project:** Returns the literal string `"Android"` for use in log tags
 * and debug output. No Android SDK imports are needed here because the value is
 * hard-coded; a richer implementation could call
 * `android.os.Build.VERSION.RELEASE` or similar without leaking those imports into
 * `commonMain`.
 */
actual fun platform() = "Android"
