package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ScanApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanSystemsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Scans nearby systems and updates the ship's cooldown locally (the scan cooldown auto-refreshes
 * the ship when it clears, via `FleetRepositoryImpl.updateShipCooldown`).
 *
 * @param scanApi Live API client. Requires a sensor-array mount.
 * @param fleetRepository Updated with the scan cooldown.
 */
interface ScanSystemsUseCase {
    suspend operator fun invoke(shipSymbol: String): ScanSystemsResult
}

/** Production implementation of [ScanSystemsUseCase]. */
class ScanSystemsUseCaseImpl(
    private val scanApi: ScanApi,
    private val fleetRepository: FleetRepository
) : ScanSystemsUseCase {
    override suspend operator fun invoke(shipSymbol: String): ScanSystemsResult {
        val result = scanApi.scanSystems(shipSymbol).toDomain()
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
