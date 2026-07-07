package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.TravelApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

/**
 * Warps a ship to a waypoint in another system and updates its nav and fuel locally (warp
 * returns the same nav+fuel shape as navigate). If the ship is now in transit, the fleet
 * repository schedules a transit-complete refresh.
 *
 * @param travelApi Live API client. Requires the ship in orbit with sufficient fuel; the
 *   destination must be within warp range.
 * @param fleetRepository Updated with the post-warp nav and fuel.
 */
interface WarpShipUseCase {
    /**
     * @param shipSymbol The ship to warp.
     * @param waypointSymbol The destination waypoint in another system.
     * @return the [NavigateResult] with the new nav and fuel.
     */
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

/** Production implementation of [WarpShipUseCase]. */
class WarpShipUseCaseImpl(
    private val travelApi: TravelApi,
    private val fleetRepository: FleetRepository
) : WarpShipUseCase {
    override suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult {
        val result = travelApi.warp(shipSymbol, waypointSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, result.nav)
        fleetRepository.updateShipFuel(shipSymbol, result.fuel)
        return result
    }
}
