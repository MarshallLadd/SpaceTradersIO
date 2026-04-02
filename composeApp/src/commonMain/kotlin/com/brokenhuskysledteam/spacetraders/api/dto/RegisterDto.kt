package com.brokenhuskysledteam.spacetraders.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val symbol: String,
    val faction: String
)

// The register response returns everything needed to start playing in one shot:
// the bearer token, the new agent, their starting faction, their first contract,
// and their starting ships.
@Serializable
data class RegisterResponseDto(
    val token: String,
    val agent: AgentDto,
    val faction: FactionDto,
    val contract: ContractDto,
    val ships: List<ShipDto>
)
