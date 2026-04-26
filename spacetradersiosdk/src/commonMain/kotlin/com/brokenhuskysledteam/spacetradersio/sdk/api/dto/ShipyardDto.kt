package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShipyardDto(
    val symbol: String,
    val modificationsFee: Int,
    // null when no ship is present at the waypoint (fog of war).
    val ships: List<ShipyardShipDto>? = null
)

@Serializable
data class ShipyardShipDto(
    val type: String,
    val name: String,
    val description: String,
    val purchasePrice: Int,
    val supply: String,
    val frame: ShipyardFrameSummaryDto,
    val engine: ShipyardEngineSummaryDto,
    val reactor: ShipyardReactorSummaryDto,
    val crew: ShipyardCrewDto
)

// Partial DTOs — only capture fields needed for the purchase decision screen.
// Additional API fields are silently dropped by ignoreUnknownKeys = true.
@Serializable
data class ShipyardFrameSummaryDto(val name: String)

@Serializable
data class ShipyardEngineSummaryDto(val speed: Int)

@Serializable
data class ShipyardReactorSummaryDto(val powerOutput: Int)

@Serializable
data class ShipyardCrewDto(val required: Int, val capacity: Int)

@Serializable
data class ShipyardTransactionDto(
    val waypointSymbol: String,
    val shipType: String,
    val price: Int,
    val agentSymbol: String,
    val timestamp: String
)

@Serializable
data class PurchaseShipRequestDto(
    val shipType: String,
    val waypointSymbol: String
)

@Serializable
data class PurchaseShipResponseDto(
    val ship: ShipDto,
    val agent: AgentDto,
    val transaction: ShipyardTransactionDto
)
