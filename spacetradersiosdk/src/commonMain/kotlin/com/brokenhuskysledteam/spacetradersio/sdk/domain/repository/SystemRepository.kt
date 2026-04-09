package com.brokenhuskysledteam.spacetradersio.sdk.domain.repository

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint

interface SystemRepository {
    suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint>
}
