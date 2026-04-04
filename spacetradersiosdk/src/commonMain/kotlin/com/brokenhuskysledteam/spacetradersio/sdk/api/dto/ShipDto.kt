package com.brokenhuskysledteam.spacetraders.api.dto

import kotlinx.serialization.Serializable

// Simplified ship DTO — only the fields needed for dashboard display.
// The full Ship schema has many more nested objects (reactor, engine,
// modules, mounts) that will be added when ship-detail screens are built.
@Serializable
data class ShipDto(
    val symbol: String,
    val frame: ShipFrameSummaryDto,
    val nav: ShipNavDto,
    val cargo: ShipCargoDto,
    val fuel: ShipFuelDto
)

@Serializable
data class ShipFrameSummaryDto(
    val symbol: String,
    val name: String
)

@Serializable
data class ShipNavDto(
    val systemSymbol: String,
    val waypointSymbol: String,
    val status: String,
    val flightMode: String
)

@Serializable
data class ShipCargoDto(
    val capacity: Int,
    val units: Int
)

@Serializable
data class ShipFuelDto(
    val current: Int,
    val capacity: Int
)
