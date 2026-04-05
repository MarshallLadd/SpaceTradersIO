package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository

class FleetRepositoryImpl(private val fleetApi: FleetApi) : FleetRepository {

    override suspend fun getMyShips(page: Int, limit: Int): List<Ship> =
        fleetApi.getMyShips(page, limit).data.map { it.toDomain() }

    override suspend fun getMyShip(shipSymbol: String): Ship =
        fleetApi.getMyShip(shipSymbol).toDomain()
}
