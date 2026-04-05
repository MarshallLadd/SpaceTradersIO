package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

// Response shape for POST /my/ships/{symbol}/refuel.
// agent, fuel, and transaction are always present; cargo is only included
// when fromCargo = true in the request (not used in this app).
@Serializable
data class RefuelResponseDto(
    val agent: AgentDto,
    val fuel: ShipFuelDto,
    val transaction: MarketTransactionDto,
    val cargo: ShipCargoDto? = null
)
