package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MarketApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoTradeResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Buys cargo at a market and atomically updates the ship's cargo and the agent's credit balance
 * in their local repositories.
 *
 * **Pattern:** Multi-repository use case (see `RefuelShipUseCase`). One `POST /purchase` returns
 * data belonging to two repositories — the ship's new [cargo][CargoTradeResult.cargo] →
 * [FleetRepository], and the agent's reduced credits → [AgentRepository] — plus a transaction
 * receipt. The use case maps everything first (so a mapping failure leaves nothing partially
 * written), then updates both repositories, whose `StateFlow`s propagate the change to any
 * observing ViewModel (cargo manifest, credit balance) automatically.
 *
 * @param marketApi Live API client. The purchase endpoint requires the ship to be docked at a
 *   market that sells the good, and the agent to have enough credits.
 * @param fleetRepository Updated with the ship's new cargo (including inventory) after the buy.
 * @param agentRepository Updated with the agent's new credit balance.
 */
interface BuyCargoUseCase {
    /**
     * @param shipSymbol The docked ship to load cargo onto.
     * @param tradeSymbol The good to buy (e.g. `"FOOD"`).
     * @param units The number of units to buy.
     * @return A [CargoTradeResult] with the updated agent, cargo, and the trade receipt.
     */
    suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult
}

/** Production implementation of [BuyCargoUseCase]. */
class BuyCargoUseCaseImpl(
    private val marketApi: MarketApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : BuyCargoUseCase {
    override suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult {
        val response = marketApi.purchaseCargo(shipSymbol, tradeSymbol, units)
        val result = CargoTradeResult(
            agent = response.agent.toDomain(),
            cargo = response.cargo.toDomain(),
            transaction = response.transaction.toDomain()
        )
        fleetRepository.updateShipCargo(shipSymbol, result.cargo)
        agentRepository.saveAgent(result.agent)
        return result
    }
}
