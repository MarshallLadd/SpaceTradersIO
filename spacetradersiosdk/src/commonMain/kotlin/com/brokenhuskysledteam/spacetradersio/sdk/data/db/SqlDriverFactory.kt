package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-agnostic factory that creates a [SqlDriver] for the local SQLDelight database.
 *
 * **Pattern:** KMP `expect` class. Declare the class contract once in `commonMain`
 * with no implementation body; each target platform (`androidMain`, `iosMain`)
 * provides an `actual class SqlDriverFactory` with platform-appropriate construction
 * and a concrete `create()` body. Code elsewhere in `commonMain` (e.g. DI wiring,
 * repository initialization) depends on this type without importing any
 * platform-specific API.
 *
 * **In this project:** Used to initialize the single [SpaceTradersDatabase] instance.
 * The Android actual requires an `android.content.Context` constructor parameter to
 * locate the app's sandboxed storage; the iOS actual requires no parameters because
 * the Darwin driver locates the documents directory automatically. The `expect`
 * declaration here deliberately has no constructor parameters so that `commonMain`
 * code that receives a fully constructed factory can call `create()` uniformly.
 *
 * **Applying in a new project:** Copy this pattern whenever you need to initialize a
 * platform-specific resource (database, file system path, system service) from shared
 * code. Declare the `expect` class with only the API surface that `commonMain` needs;
 * hide constructor differences inside each `actual` class.
 */
expect class SqlDriverFactory {

    /**
     * Creates and returns the platform-specific [SqlDriver] for the SQLDelight database.
     *
     * The [SqlDriver] interface is provided by SQLDelight and is identical across all
     * platforms. Callers in `commonMain` use it to construct a [SpaceTradersDatabase]
     * without knowing whether they are running on Android or iOS.
     *
     * @return A ready-to-use [SqlDriver] connected to the `spacetraders.db` file.
     */
    fun create(): SqlDriver
}
