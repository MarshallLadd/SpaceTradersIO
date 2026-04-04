package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Response from POST /my/contracts/{id}/accept and POST /my/contracts/{id}/fulfill.
// Both operations return the updated contract alongside the updated agent (credits change).
@Serializable
data class ContractActionResponseDto(
    val contract: ContractDto,
    val agent: AgentDto
)

// Response from POST /my/contracts/{id}/deliver.
// Returns the updated contract and the delivering ship's updated cargo.
@Serializable
data class DeliverCargoResponseDto(
    val contract: ContractDto,
    val cargo: ShipCargoDto
)

// Request body for POST /my/contracts/{id}/deliver.
@Serializable
data class DeliverCargoRequestDto(
    val shipSymbol: String,
    val tradeSymbol: String,
    val units: Int
)
