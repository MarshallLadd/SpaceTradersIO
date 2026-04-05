package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus

data class ShipNav(
    val systemSymbol: String,
    val waypointSymbol: String,
    val status: ShipNavStatus,
    val flightMode: ShipNavFlightMode,
    val route: ShipNavRoute
)
