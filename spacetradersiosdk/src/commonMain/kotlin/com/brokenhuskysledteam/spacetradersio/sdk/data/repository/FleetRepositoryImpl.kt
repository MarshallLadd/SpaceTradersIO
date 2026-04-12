package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.updateShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsertShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FleetRepositoryImpl(
    private val fleetApi: FleetApi,
    private val database: SpaceTradersDatabase,
    private val refreshScheduler: RefreshScheduler
) : FleetRepository {

    private val queries get() = database.shipQueries

    override fun observeShips(): Flow<List<Ship>> =
        queries.selectAllShips()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeShip(shipSymbol: String): Flow<Ship?> =
        queries.selectShipBySymbol(shipSymbol)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomain() }

    override suspend fun refreshMyShips(page: Int, limit: Int) {
        val ships = fleetApi.getMyShips(page, limit).data.map { it.toDomain() }
        database.transaction {
            ships.forEach { queries.upsertShip(it) }
        }
        ships.forEach { registerTimersForShip(it) }
    }

    override suspend fun refreshMyShip(shipSymbol: String) {
        val ship = fleetApi.getMyShip(shipSymbol).toDomain()
        queries.upsertShip(ship)
        registerTimersForShip(ship)
    }

    override suspend fun saveShip(ship: Ship) {
        queries.upsertShip(ship)
    }

    override suspend fun updateShipNav(shipSymbol: String, nav: ShipNav) {
        queries.updateShipNav(nav, shipSymbol)
    }

    override suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel) {
        queries.updateShipFuel(
            fuel_current = fuel.current.toLong(),
            fuel_capacity = fuel.capacity.toLong(),
            symbol = shipSymbol
        )
    }

    override suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo) {
        queries.updateShipCargo(
            cargo_units = cargo.units.toLong(),
            cargo_capacity = cargo.capacity.toLong(),
            symbol = shipSymbol
        )
    }

    override suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown) {
        queries.updateShipCooldown(
            cooldown_total_seconds = cooldown.totalSeconds.toLong(),
            cooldown_remaining_seconds = cooldown.remainingSeconds.toLong(),
            cooldown_expiration = cooldown.expiration?.toString(),
            symbol = shipSymbol
        )
    }

    override suspend fun clearAll() {
        queries.deleteAllShips()
    }

    private fun registerTimersForShip(ship: Ship) {
        if (ship.nav.status == ShipNavStatus.IN_TRANSIT) {
            refreshScheduler.schedule(
                id = "transit:${ship.symbol}",
                expiresAt = ship.nav.route.arrivalTime,
                action = { refreshMyShip(ship.symbol) }
            )
        }
        ship.cooldown.expiration?.let { expiry ->
            refreshScheduler.schedule(
                id = "cooldown:${ship.symbol}",
                expiresAt = expiry,
                action = { refreshMyShip(ship.symbol) }
            )
        }
    }
}
