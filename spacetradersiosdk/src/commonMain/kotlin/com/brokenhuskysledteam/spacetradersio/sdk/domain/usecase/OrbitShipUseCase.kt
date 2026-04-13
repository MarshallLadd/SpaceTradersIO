package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

interface OrbitShipUseCase {
    suspend operator fun invoke(shipSymbol: String): ShipNav
}

class OrbitShipUseCaseImpl(
    private val fleetApi: FleetApi,
    private val fleetRepository: FleetRepository
) : OrbitShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): ShipNav {
        val nav = fleetApi.orbitShip(shipSymbol).toDomain()
        fleetRepository.updateShipNav(shipSymbol, nav)
        return nav
    }
}
