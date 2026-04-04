package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus

// Simplified ship model scoped to dashboard display needs.
// Full ship details (modules, mounts, reactor, engine) will be added
// when the ships list / ship detail screens are built.
data class Ship(
    val symbol: String,
    val frameSymbol: String,
    val frameName: String,
    val currentSystemSymbol: String,
    val currentWaypointSymbol: String,
    val navStatus: ShipNavStatus,
    val cargoUsed: Int,
    val cargoCapacity: Int,
    val fuelCurrent: Int,
    val fuelCapacity: Int
)
