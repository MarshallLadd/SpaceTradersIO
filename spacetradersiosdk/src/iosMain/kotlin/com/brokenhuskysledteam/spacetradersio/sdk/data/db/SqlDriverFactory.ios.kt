package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS implementation of the [SqlDriverFactory] `expect` class declared in `commonMain`.
 *
 * **Pattern:** `actual class` with no constructor parameters. Unlike the Android
 * actual, the iOS implementation needs no injected context — the SQLDelight
 * `NativeSqliteDriver` locates the app's sandboxed documents directory automatically
 * using the Darwin/iOS file system APIs. This keeps the iOS factory simple and
 * constructor-compatible with the parameterless `expect` class declaration.
 *
 * **Database file location:** On iOS, `NativeSqliteDriver` places `spacetraders.db`
 * in the app's sandboxed documents directory (typically
 * `<app-container>/Documents/spacetraders.db`). This location is private to the app,
 * persists across launches, and is included in iCloud backups by default. No path
 * configuration is required in this class.
 *
 * **SQLDelight multiplatform value:** The `.sq` schema files and the generated
 * `SpaceTradersDatabase` / `AgentQueries` / `ShipQueries` interfaces are identical
 * to those used on Android. Only the driver changes — `NativeSqliteDriver` here
 * vs. `AndroidSqliteDriver` in `SqlDriverFactory.android.kt`. Schema migrations,
 * query logic, and mapper code are fully shared.
 *
 * **In this project:** Will be constructed by the iOS dependency graph (Swift/Xcode
 * side) and passed into the shared SDK initializer. Because no `Context` is needed,
 * Swift can instantiate this with `SqlDriverFactory()` directly.
 */
actual class SqlDriverFactory {

    /**
     * Creates a [NativeSqliteDriver] backed by the iOS app's sandboxed documents directory.
     *
     * [SpaceTradersDatabase.Schema] carries the current schema version and the
     * `create` / `migrate` callbacks that SQLDelight invokes automatically on first
     * open or when a schema upgrade is needed. The filename `"spacetraders.db"` must
     * match the value used in `SqlDriverFactory.android.kt` — both refer to the same
     * logical database, just stored in each platform's private sandbox.
     *
     * @return A ready-to-use [SqlDriver] connected to `spacetraders.db` in the iOS
     *   app's documents directory.
     */
    actual fun create(): SqlDriver =
        NativeSqliteDriver(SpaceTradersDatabase.Schema, "spacetraders.db")
}
