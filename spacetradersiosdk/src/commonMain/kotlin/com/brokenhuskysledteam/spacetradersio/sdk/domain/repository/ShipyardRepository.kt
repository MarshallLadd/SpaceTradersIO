package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import kotlinx.coroutines.flow.Flow

interface ShipyardRepository {
    fun observeShipyard(waypointSymbol: String): Flow<Shipyard?>
    suspend fun refreshShipyard(systemSymbol: String, waypointSymbol: String)
    suspend fun purchaseShip(shipType: ShipType, waypointSymbol: String)
    suspend fun clearAll()
}
