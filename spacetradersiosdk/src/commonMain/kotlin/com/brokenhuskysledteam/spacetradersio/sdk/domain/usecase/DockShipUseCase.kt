package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

interface DockShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class DockShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : DockShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.dockShip(shipSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
