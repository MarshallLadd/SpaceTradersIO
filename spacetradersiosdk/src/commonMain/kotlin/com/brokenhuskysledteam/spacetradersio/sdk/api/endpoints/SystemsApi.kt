package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface SystemsApi {
    suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int = 1,
        limit: Int = 20
    ): PaginatedResponse<WaypointDto>
}

class SystemsApiImpl(private val client: SpaceTradersClient) : SystemsApi {

    override suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int,
        limit: Int
    ): PaginatedResponse<WaypointDto> =
        client.authenticated.get("systems/$systemSymbol/waypoints") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()
}
