package com.brokenhuskysledteam.spacetradersio.sdk

/**
 * Returns a human-readable string identifying the current runtime platform.
 *
 * **Pattern:** KMP `expect`/`actual` function — the canonical minimal example of
 * Kotlin Multiplatform's platform-abstraction mechanism. The `expect` keyword here
 * declares the function's signature in `commonMain` without providing a body. The
 * Kotlin compiler then requires every compiled target (Android, iOS arm64, iOS
 * simulator) to supply a matching `actual fun platform()` in its respective source
 * set. If any target is missing an `actual`, the build fails.
 *
 * **In this project:** Used for logging and debugging — `Napier` log lines can call
 * `platform()` to tag output with the current platform. It is also the entry point
 * for understanding the expect/actual mechanism before reading the more complex
 * `SqlDriverFactory` expect/actual pair.
 *
 * **Applying in a new project:** This is the simplest possible `expect`/`actual` unit.
 * Start here when learning the pattern, then graduate to `expect class` (see
 * [SqlDriverFactory]) when you need platform-specific constructor parameters or
 * stateful objects.
 *
 * Currently used only for identification; may be extended for platform-specific
 * behavior (e.g. different cache strategies on Android vs iOS).
 *
 * @return A platform name such as `"Android"` or `"iOS"`.
 */
expect fun platform(): String
