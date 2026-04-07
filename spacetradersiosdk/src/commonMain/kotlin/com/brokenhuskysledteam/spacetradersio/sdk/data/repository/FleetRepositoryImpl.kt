package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore

class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val fleetStateStore: FleetStateStore,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    override suspend fun getMyShips(page: Int, limit: Int): List<Ship> {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        fleetStateStore.putAll(ships.associateBy { it.symbol })
        ships.forEach { registerTimersForShip(it) }
        return ships
    }

    override suspend fun getMyShip(shipSymbol: String): Ship {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
        fleetStateStore.put(shipSymbol, ship)
        registerTimersForShip(ship)
        return ship
    }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        fleetStateStore.putAll(ships.associateBy { it.symbol })
        ships.forEach { registerTimersForShip(it) }
    }

    private fun registerTimersForShip(ship: Ship) {
        if (ship.nav.status == ShipNavStatus.IN_TRANSIT) {
            refreshScheduler.schedule(
                id = "transit:${ship.symbol}",
                expiresAt = ship.nav.route.arrivalTime,
                action = { getMyShip(ship.symbol) }
            )
        }
        ship.cooldown.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { getMyShip(ship.symbol) }
            )
        }
    }
}
