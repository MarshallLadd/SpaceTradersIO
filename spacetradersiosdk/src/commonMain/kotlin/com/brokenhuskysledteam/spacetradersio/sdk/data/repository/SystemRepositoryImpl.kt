package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.SystemsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.SystemRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.WaypointStateStore

class SystemRepositoryImpl(
    private val systemsApi: SystemsApi,
    private val waypointStateStore: WaypointStateStore
) : SystemRepository {

    override suspend fun getSystemWaypoints(systemSymbol: String): List<Waypoint> {
        val allWaypoints = fetchAllPages(systemSymbol)
        waypointStateStore.putAll(allWaypoints.associateBy { it.symbol })
        return allWaypoints
    }

    private suspend fun fetchAllPages(systemSymbol: String): List<Waypoint> {
        val result = mutableListOf<Waypoint>()
        var page = 1
        do {
            val response = systemsApi.getSystemWaypoints(systemSymbol, page = page, limit = 20)
            result.addAll(response.data.map { it.toDomain() })
            val total = response.meta.total
            page++
        } while (result.size < total)
        return result
    }
}
