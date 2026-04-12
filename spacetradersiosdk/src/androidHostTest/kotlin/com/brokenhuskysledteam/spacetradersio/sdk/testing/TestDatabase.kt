package com.brokenhuskysledteam.spacetradersio.sdk.testing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase

fun createTestDatabase(): SpaceTradersDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    SpaceTradersDatabase.Schema.create(driver)
    return SpaceTradersDatabase(driver)
}
