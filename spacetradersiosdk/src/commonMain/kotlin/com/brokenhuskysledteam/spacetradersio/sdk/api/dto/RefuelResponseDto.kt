package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format response from `POST /my/ships/{symbol}/refuel`.
 *
 * **Pattern:** Composite response DTO. Some API actions atomically modify multiple resources
 * and return all affected state in a single response. Rather than forcing the caller to make
 * follow-up requests to discover what changed, the API bundles the updated resources together.
 * In a new project, model the full response as a single DTO whose properties mirror each
 * returned resource — the mapper then fans them out to whichever domain models need updating.
 *
 * **In this project:** Refuelling deducts credits from the agent, updates the ship's fuel
 * levels, and records a market transaction — three independent domain objects in one round
 * trip. The SDK's `ShipsApi.refuel()` function maps this DTO to a `RefuelResult` domain model
 * that carries all three in one place, so the caller (ViewModel or use case) can update all
 * affected state stores in a single `Result` callback.
 *
 * @property agent The agent's updated state, primarily reflecting the credit cost of the fuel
 * deducted from the agent's balance.
 * @property fuel The ship's updated fuel levels after refuelling. `current` will equal
 * `capacity` for a full refuel, or reflect a partial fill if the market had limited stock.
 * @property transaction The market transaction record for the fuel purchase, including the
 * price per unit, total price, and timestamp.
 * @property cargo Present only when `fromCargo = true` was sent in the request body, indicating
 * the ship used cargo-held fuel cells instead of buying from the market. This app always
 * refuels from the market, so this field will always be `null` here.
 */
@Serializable
data class RefuelResponseDto(
    val agent: AgentDto,
    val fuel: ShipFuelDto,
    val transaction: MarketTransactionDto,
    // cargo is only returned when fromCargo=true is sent in the request body.
    // It is modelled as nullable with a default so the standard refuel path
    // does not require special handling in the deserializer.
    val cargo: ShipCargoDto? = null
)
