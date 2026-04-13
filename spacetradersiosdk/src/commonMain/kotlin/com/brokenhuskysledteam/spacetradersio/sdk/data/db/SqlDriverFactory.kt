package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import app.cash.sqldelight.db.SqlDriver

expect class SqlDriverFactory {
    fun create(): SqlDriver
}
