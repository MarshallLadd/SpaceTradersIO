package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship

// Repository interface for ship data. Defined as an interface so the app layer
// and use case tests can substitute a fake without constructing HTTP clients.
interface FleetRepository {
    suspend fun getMyShips(page: Int = 1, limit: Int = 20): List<Ship>
    suspend fun getMyShip(shipSymbol: String): Ship
    suspend fun refreshMyShips(page: Int = 1, limit: Int = 20)
}
