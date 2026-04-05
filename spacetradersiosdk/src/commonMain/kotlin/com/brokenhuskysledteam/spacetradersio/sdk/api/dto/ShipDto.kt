package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShipDto(
    val symbol: String,
    val registration: ShipRegistrationDto,
    val frame: ShipFrameSummaryDto,
    val nav: ShipNavDto,
    val cargo: ShipCargoDto,
    val fuel: ShipFuelDto,
    val cooldown: CooldownDto
)

@Serializable
data class ShipRegistrationDto(
    val name: String,
    val factionSymbol: String,
    val role: String
)

@Serializable
data class ShipFrameSummaryDto(
    val symbol: String,
    val name: String
)

// The API also returns condition, integrity, description, moduleSlots,
// mountingPoints, fuelCapacity, requirements, and quality inside the frame
// object — all ignored here via ignoreUnknownKeys = true on the JSON config.

@Serializable
data class ShipNavDto(
    val systemSymbol: String,
    val waypointSymbol: String,
    val route: ShipNavRouteDto,
    val status: String,
    val flightMode: String
)

@Serializable
data class ShipNavRouteDto(
    val destination: ShipNavRouteWaypointDto,
    val origin: ShipNavRouteWaypointDto,
    val departureTime: String,
    // The API field is "arrival", not "arrivalTime". The mapper exposes it as arrivalTime.
    val arrival: String
)

@Serializable
data class ShipNavRouteWaypointDto(
    val symbol: String,
    val type: String,
    val systemSymbol: String,
    val x: Int,
    val y: Int
)

@Serializable
data class ShipCargoDto(
    val capacity: Int,
    val units: Int
)

// The API also returns an "inventory" array inside cargo — ignored via ignoreUnknownKeys.

@Serializable
data class ShipFuelDto(
    val current: Int,
    val capacity: Int
)

// The API optionally returns a "consumed" object inside fuel — ignored via ignoreUnknownKeys.

@Serializable
data class CooldownDto(
    val shipSymbol: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val expiration: String? = null
)
