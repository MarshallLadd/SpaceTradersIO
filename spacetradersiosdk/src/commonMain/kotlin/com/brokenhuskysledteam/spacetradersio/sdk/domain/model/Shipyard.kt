package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

data class Shipyard(
    val symbol: String,
    val modificationsFee: Int,
    // null = fog of war: no ship is present at this waypoint.
    // empty list = shipyard present but no ships currently for sale.
    val ships: List<ShipyardShip>?
)
