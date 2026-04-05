package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

data class ShipNavRoute(
    val origin: ShipNavRouteWaypoint,
    val destination: ShipNavRouteWaypoint,
    val departureTime: Instant,
    val arrivalTime: Instant
)
