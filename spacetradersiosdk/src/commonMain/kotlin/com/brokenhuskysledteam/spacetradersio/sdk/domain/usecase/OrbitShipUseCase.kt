package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav

// Moves a ship into orbit at its current location.
// Defined as an interface so the app layer can substitute a fake for testing.
interface OrbitShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class OrbitShipUseCaseImpl(private val fleetApi: FleetApi) : OrbitShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav =
        fleetApi.orbitShip(shipSymbol).toDomain()
}
