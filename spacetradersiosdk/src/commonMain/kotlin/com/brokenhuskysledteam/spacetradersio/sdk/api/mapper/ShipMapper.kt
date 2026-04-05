package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.CooldownDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavRouteWaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipRegistrationDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import kotlin.time.Instant

fun ShipDto.toDomain(): Ship = Ship(
    symbol = symbol,
    registration = registration.toDomain(),
    nav = nav.toDomain(),
    cargo = cargo.toDomain(),
    fuel = fuel.toDomain(),
    frameName = frame.name,
    cooldown = cooldown.toDomain()
)

fun ShipRegistrationDto.toDomain(): ShipRegistration = ShipRegistration(
    role = ShipRole.fromString(role),
    factionSymbol = factionSymbol
)

fun ShipNavDto.toDomain(): ShipNav = ShipNav(
    systemSymbol = systemSymbol,
    waypointSymbol = waypointSymbol,
    status = ShipNavStatus.fromString(status),
    flightMode = ShipNavFlightMode.fromString(flightMode),
    route = route.toDomain()
)

fun ShipNavRouteDto.toDomain(): ShipNavRoute = ShipNavRoute(
    origin = origin.toDomain(),
    destination = destination.toDomain(),
    departureTime = Instant.parse(departureTime),
    // The API field is "arrival"; the domain model exposes it as "arrivalTime".
    arrivalTime = Instant.parse(arrival)
)

fun ShipNavRouteWaypointDto.toDomain(): ShipNavRouteWaypoint = ShipNavRouteWaypoint(
    symbol = symbol,
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x,
    y = y
)

fun ShipFuelDto.toDomain(): ShipFuel = ShipFuel(
    current = current,
    capacity = capacity
)

fun ShipCargoDto.toDomain(): ShipCargo = ShipCargo(
    units = units,
    capacity = capacity
)

fun CooldownDto.toDomain(): Cooldown = Cooldown(
    shipSymbol = shipSymbol,
    totalSeconds = totalSeconds,
    remainingSeconds = remainingSeconds,
    expiration = expiration?.let { Instant.parse(it) }
)
