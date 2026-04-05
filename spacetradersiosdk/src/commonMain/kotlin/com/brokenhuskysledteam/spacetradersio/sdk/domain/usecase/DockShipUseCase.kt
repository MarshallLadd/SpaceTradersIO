package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav

// Docks a ship at its current waypoint.
// Defined as an interface so the app layer can substitute a fake for testing.
interface DockShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class DockShipUseCaseImpl(private val fleetApi: FleetApi) : DockShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav =
        fleetApi.dockShip(shipSymbol).toDomain()
}
