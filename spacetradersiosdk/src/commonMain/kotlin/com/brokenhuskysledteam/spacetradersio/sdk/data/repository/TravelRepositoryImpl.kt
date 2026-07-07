package com.brokenhuskysledteam.spacetradersio.sdk.data.repository

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.TravelApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.JumpGate
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SystemPage
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TravelRepository

/** Read-through implementation of [TravelRepository]. */
class TravelRepositoryImpl(private val travelApi: TravelApi) : TravelRepository {

    override suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGate =
        travelApi.getJumpGate(systemSymbol, waypointSymbol).toDomain()

    override suspend fun getSystems(page: Int, limit: Int): SystemPage {
        val response = travelApi.getSystems(page, limit)
        return SystemPage(
            systems = response.data.map { it.toDomain() },
            page = response.meta.page,
            total = response.meta.total
        )
    }
}
