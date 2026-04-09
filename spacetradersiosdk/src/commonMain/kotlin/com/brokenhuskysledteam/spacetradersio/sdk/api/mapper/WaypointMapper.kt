package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

fun WaypointDto.toDomain(): Waypoint = Waypoint(
    symbol = symbol,
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x,
    y = y,
    orbits = orbits,
    orbitals = orbitals.map { it.symbol },
    traits = traits.map { it.toDomain() },
    isUnderConstruction = isUnderConstruction
)

fun WaypointTraitDto.toDomain(): WaypointTrait = WaypointTrait(
    symbol = WaypointTraitSymbol.fromString(symbol),
    name = name,
    description = description
)
