package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ShipyardApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.SpaceTradersDatabase
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.data.db.upsert
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.ShipyardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ShipyardRepositoryImpl(
    private val shipyardApi: ShipyardApi,
    private val fleetRepository: FleetRepository,
    private val agentRepository: AgentRepository,
    private val database: SpaceTradersDatabase
) : ShipyardRepository {

    override fun observeShipyard(waypointSymbol: String): Flow<Shipyard?> =
        combine(
            database.shipyardQueries.selectBySymbol(waypointSymbol)
                .asFlow().mapToOneOrNull(Dispatchers.Default),
            database.shipyardShipQueries.selectByWaypoint(waypointSymbol)
                .asFlow().mapToList(Dispatchers.Default)
        ) { meta, ships ->
            meta?.toDomain(if (meta.ships_cached == 1L) ships.map { it.toDomain() } else null)
        }

    override suspend fun refreshShipyard(systemSymbol: String, waypointSymbol: String) {
        val dto = shipyardApi.getShipyard(systemSymbol, waypointSymbol)
        database.transaction {
            database.shipyardQueries.upsert(
                symbol = dto.symbol,
                modifications_fee = dto.modificationsFee.toLong(),
                ships_cached = if (dto.ships != null) 1L else 0L
            )
            database.shipyardShipQueries.deleteByWaypoint(waypointSymbol)
            dto.ships?.forEach { shipDto ->
                database.shipyardShipQueries.upsert(
                    waypoint_symbol = waypointSymbol,
                    ship = shipDto.toDomain()
                )
            }
        }
    }

    override suspend fun purchaseShip(shipType: ShipType, waypointSymbol: String) {
        val response = shipyardApi.purchaseShip(shipType.name, waypointSymbol)
        fleetRepository.saveShip(response.ship.toDomain())
        agentRepository.updateCredits(response.agent.symbol, response.agent.credits)
    }

    override suspend fun clearAll() {
        database.shipyardQueries.deleteAll()
        database.shipyardShipQueries.deleteAll()
    }
}
