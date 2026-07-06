package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MarketApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoTradeResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Sells cargo at a market and atomically updates the ship's cargo and the agent's credit balance
 * in their local repositories. The sell counterpart of [BuyCargoUseCase] — same multi-repository
 * update pattern, opposite credit direction.
 *
 * @param marketApi Live API client. The sell endpoint requires the ship to be docked at a market
 *   that buys the good and to hold at least [units] of it.
 * @param fleetRepository Updated with the ship's reduced cargo (including inventory) after the sale.
 * @param agentRepository Updated with the agent's increased credit balance.
 */
interface SellCargoUseCase {
    /**
     * @param shipSymbol The docked ship to sell cargo from.
     * @param tradeSymbol The good to sell (e.g. `"IRON_ORE"`).
     * @param units The number of units to sell.
     * @return A [CargoTradeResult] with the updated agent, cargo, and the trade receipt.
     */
    suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult
}

/** Production implementation of [SellCargoUseCase]. */
class SellCargoUseCaseImpl(
    private val marketApi: MarketApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository
) : SellCargoUseCase {
    override suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): CargoTradeResult {
        val response = marketApi.sellCargo(shipSymbol, tradeSymbol, units)
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
