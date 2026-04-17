package com.brokenhuskysledteam.spacetradersio.sdk

/**
 * iOS-specific implementation of the `expect fun platform()` declaration in `commonMain`.
 *
 * **Pattern:** `actual` implementation for iOS targets. This file is compiled for
 * both `iosArm64` (physical device) and `iosSimulatorArm64` (Simulator on Apple
 * Silicon) because both targets share the `iosMain` source set. A single `actual`
 * declaration here covers all iOS compilation variants simultaneously.
 *
 * **In this project:** Returns the literal string `"iOS"` for use in log tags and
 * debug output. A richer implementation could call into `platform.UIDevice` or read
 * `NSProcessInfo.processInfo.operatingSystemVersionString` without those Darwin
 * imports appearing in `commonMain`.
 *
 * **Contrast with Android:** The Android actual (`Platform.android.kt`) lives in
 * `androidMain` and is compiled only for the Android target. Both files implement
 * the same `expect` contract; neither knows the other exists.
 */
actual fun platform() = "iOS"
