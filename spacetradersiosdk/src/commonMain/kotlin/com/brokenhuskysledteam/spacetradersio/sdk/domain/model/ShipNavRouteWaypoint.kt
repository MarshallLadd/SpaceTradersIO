package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

data class ShipNavRouteWaypoint(
    val symbol: String,
    val type: WaypointType,
    val systemSymbol: String,
    val x: Int,
    val y: Int
)
