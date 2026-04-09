package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.NavigateResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

interface NavigateShipUseCase {
    suspend operator fun invoke(shipSymbol: String, waypointSymbol: String): NavigateResult
}

class NavigateShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val orbitShipUseCase: OrbitShipUseCase
) : NavigateShipUseCase {

    override suspend operator fun invoke(
        shipSymbol: String,
        waypointSymbol: String
    ): NavigateResult {
        val ship = fleetStateStore.entities.value[shipSymbol]
        if (ship?.nav?.status == ShipNavStatus.DOCKED) {
            orbitShipUseCase(shipSymbol)
        }

        val response = fleetApi.navigateShip(shipSymbol, waypointSymbol).toDomain()

        fleetStateStore.update(shipSymbol) { s ->
            s.copy(nav = response.nav, fuel = response.fuel)
        }

        return response
    }
}
