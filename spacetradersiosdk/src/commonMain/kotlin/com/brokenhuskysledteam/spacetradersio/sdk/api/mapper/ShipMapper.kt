package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus

fun ShipDto.toDomain(): Ship = Ship(
    symbol = symbol,
    frameSymbol = frame.symbol,
    frameName = frame.name,
    currentSystemSymbol = nav.systemSymbol,
    currentWaypointSymbol = nav.waypointSymbol,
    navStatus = ShipNavStatus.fromString(nav.status),
    cargoUsed = cargo.units,
    cargoCapacity = cargo.capacity,
    fuelCurrent = fuel.current,
    fuelCapacity = fuel.capacity
)
