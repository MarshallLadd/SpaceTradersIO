package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import kotlinx.coroutines.flow.Flow

interface FleetRepository {
    fun observeShips(): Flow<List<Ship>>
    fun observeShip(shipSymbol: String): Flow<Ship?>
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
    suspend fun refreshMyShip(shipSymbol: String)
    suspend fun saveShip(ship: Ship)
    suspend fun updateShipNav(shipSymbol: String, nav: ShipNav)
    suspend fun updateShipFuel(shipSymbol: String, fuel: ShipFuel)
    suspend fun updateShipCargo(shipSymbol: String, cargo: ShipCargo)
    suspend fun updateShipCooldown(shipSymbol: String, cooldown: Cooldown)
    suspend fun clearAll()
}
