package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MiningApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ExtractResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Extracts resources from an asteroid and updates the ship's cargo and cooldown locally so the
 * UI reflects the new hold and the cooldown countdown immediately. The stored cooldown also
 * schedules an auto-refresh (see `FleetRepositoryImpl.updateShipCooldown`) that re-fetches the
 * ship when the cooldown clears.
 *
 * @param miningApi Live API client. Requires the ship in orbit at an asteroid with a mining mount.
 * @param fleetRepository Updated with the post-extraction cargo and cooldown.
 */
interface ExtractResourcesUseCase {
    /** @param shipSymbol The orbiting, mining-equipped ship. @return the extraction [ExtractResult]. */
    suspend operator fun invoke(shipSymbol: String): ExtractResult
}

/** Production implementation of [ExtractResourcesUseCase]. */
class ExtractResourcesUseCaseImpl(
    private val miningApi: MiningApi,
    private val fleetRepository: FleetRepository
) : ExtractResourcesUseCase {
    override suspend operator fun invoke(shipSymbol: String): ExtractResult {
        val result = miningApi.extract(shipSymbol).toDomain()
        fleetRepository.updateShipCargo(shipSymbol, result.cargo)
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
