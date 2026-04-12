package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class SqlDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(SpaceTradersDatabase.Schema, "spacetraders.db")
}
