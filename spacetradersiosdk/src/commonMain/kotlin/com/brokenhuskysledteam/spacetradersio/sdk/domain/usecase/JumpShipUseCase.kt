package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.TravelApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Jumps a ship to a connected jump gate and updates its nav and cooldown locally. The stored
 * cooldown schedules an auto-refresh (via `FleetRepositoryImpl.updateShipCooldown`) so the ship
 * refreshes when the jump cooldown clears.
 *
 * @param travelApi Live API client. Requires the ship at a jump gate connected to [waypointSymbol].
 * @param fleetRepository Updated with the post-jump nav and cooldown.
 */
interface JumpShipUseCase {
    /**
     * @param shipSymbol The ship at a jump gate.
     * @param waypointSymbol The destination jump-gate waypoint.
     * @return the [JumpResult] with the new nav and cooldown.
     */
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): JumpResult
}

/** Production implementation of [JumpShipUseCase]. */
class JumpShipUseCaseImpl(
    private val travelApi: TravelApi,
    private val fleetRepository: FleetRepository
) : JumpShipUseCase {
    override suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): JumpResult {
        val result = travelApi.jump(shipSymbol, waypointSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, result.nav)
        fleetRepository.updateShipCooldown(shipSymbol, result.cooldown)
        return result
    }
}
