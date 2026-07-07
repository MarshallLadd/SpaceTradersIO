package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ScanApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanWaypointsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Scans waypoints in the current system (revealing their traits) and updates the ship's cooldown.
 *
 * @param scanApi Live API client. Requires a sensor-array mount.
 * @param fleetRepository Updated with the scan cooldown.
 */
interface ScanWaypointsUseCase {
    suspend operator fun invoke(shipSymbol: String): ScanWaypointsResult
}

/** Production implementation of [ScanWaypointsUseCase]. */
class ScanWaypointsUseCaseImpl(
    private val scanApi: ScanApi,
    private val fleetRepository: FleetRepository
) : ScanWaypointsUseCase {
    override suspend operator fun invoke(shipSymbol: String): ScanWaypointsResult {
        val result = scanApi.scanWaypoints(shipSymbol).toDomain()
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
