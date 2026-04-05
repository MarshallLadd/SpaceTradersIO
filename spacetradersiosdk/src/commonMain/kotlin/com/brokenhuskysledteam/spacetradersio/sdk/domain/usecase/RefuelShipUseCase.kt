package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.RefuelResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel

// Refuels a ship to maximum capacity from the local market.
// Returns the updated agent, fuel state, and the market transaction — all three
// are needed by the UI to display the command output (cost + new balance).
// Defined as an interface so the app layer can substitute a fake for testing.
interface RefuelShipUseCase {
    suspend operator fun invoke(shipSymbol: String): RefuelResult
}

class RefuelShipUseCaseImpl(private val fleetApi: FleetApi) : RefuelShipUseCase {
    override suspend operator fun invoke(shipSymbol: String): RefuelResult {
        val response = fleetApi.refuelShip(shipSymbol)
        return RefuelResult(
            agent = response.agent.toDomain(),
            fuel = ShipFuel(current = response.fuel.current, capacity = response.fuel.capacity),
            transaction = response.transaction.toDomain()
        )
    }
}
