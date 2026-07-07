package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MiningApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Jettisons cargo from a ship and updates the local cargo so the manifest reflects the dump
 * immediately.
 *
 * @param miningApi Live API client.
 * @param fleetRepository Updated with the post-jettison cargo.
 */
interface JettisonCargoUseCase {
    /**
     * @param shipSymbol The ship to dump cargo from.
     * @param tradeSymbol The good to jettison.
     * @param units The number of units to jettison.
     * @return the ship's [ShipCargo] after jettisoning.
     */
    suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): ShipCargo
}

/** Production implementation of [JettisonCargoUseCase]. */
class JettisonCargoUseCaseImpl(
    private val miningApi: MiningApi,
    private val fleetRepository: FleetRepository
) : JettisonCargoUseCase {
    override suspend operator fun invoke(shipSymbol: String, tradeSymbol: String, units: Int): ShipCargo {
        val cargo = miningApi.jettison(shipSymbol, tradeSymbol, units).cargo.toDomain()
        fleetRepository.updateShipCargo(shipSymbol, cargo)
        return cargo
    }
}
