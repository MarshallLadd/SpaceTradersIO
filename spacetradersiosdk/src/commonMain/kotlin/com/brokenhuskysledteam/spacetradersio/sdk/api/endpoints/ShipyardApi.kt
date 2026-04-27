package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PurchaseShipRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PurchaseShipResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

interface ShipyardApi {
    suspend fun getShipyard(systemSymbol: String, waypointSymbol: String): ShipyardDto
    suspend fun purchaseShip(shipType: String, waypointSymbol: String): PurchaseShipResponseDto
}

class ShipyardApiImpl(private val client: SpaceTradersClient) : ShipyardApi {

    override suspend fun getShipyard(systemSymbol: String, waypointSymbol: String): ShipyardDto =
        client.authenticated
            .get("systems/$systemSymbol/waypoints/$waypointSymbol/shipyard")
            .body<ApiResponse<ShipyardDto>>().data

    override suspend fun purchaseShip(shipType: String, waypointSymbol: String): PurchaseShipResponseDto =
        client.authenticated
            .post("my/ships") {
                setBody(PurchaseShipRequestDto(shipType = shipType, waypointSymbol = waypointSymbol))
            }
            .body<ApiResponse<PurchaseShipResponseDto>>().data
}
