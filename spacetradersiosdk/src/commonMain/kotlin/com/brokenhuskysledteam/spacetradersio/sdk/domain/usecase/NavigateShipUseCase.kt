package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import kotlinx.coroutines.flow.first

interface NavigateShipUseCase {
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

class NavigateShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository,
    private val orbitShipUseCase: OrbitShipUseCase
) : NavigateShipUseCase {

    override suspend operator fun invoke(
        shipSymbol: String,
        waypointSymbol: String
    ): NavigateResult {
        val ship = fleetRepository.observeShip(shipSymbol).first()
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        fleetRepository.updateShipNav(shipSymbol, response.nav)
        fleetRepository.updateShipFuel(shipSymbol, response.fuel)

        return response
    }
}
