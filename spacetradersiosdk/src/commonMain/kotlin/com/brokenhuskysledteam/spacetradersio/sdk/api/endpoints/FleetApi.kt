package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.OrbitDockResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.RefuelResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

// Endpoints under the "Fleet" tag in the OpenAPI spec.
// All endpoints require an authenticated client (AgentToken).
interface FleetApi {
    suspend fun getMyShips(page: Int = 1, limit: Int = 20): PaginatedResponse<ShipDto>
    suspend fun getMyShip(shipSymbol: String): ShipDto
    suspend fun orbitShip(shipSymbol: String): ShipNavDto
    suspend fun dockShip(shipSymbol: String): ShipNavDto
    suspend fun refuelShip(shipSymbol: String): RefuelResponseDto
    suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto
}

class FleetApiImpl(private val client: SpaceTradersClient) : FleetApi {

    // GET /my/ships — returns a paginated list of all ships owned by the agent.
    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> =
        client.authenticated.get("my/ships") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    // GET /my/ships/{shipSymbol} — fetches details for a single ship.
    override suspend fun getMyShip(shipSymbol: String): ShipDto =
        client.authenticated.get("my/ships/$shipSymbol").body<ApiResponse<ShipDto>>().data

    // POST /my/ships/{shipSymbol}/orbit — moves the ship into orbit.
    // Returns the updated nav state. Idempotent if already in orbit.
    override suspend fun orbitShip(shipSymbol: String): ShipNavDto =
        client.authenticated.post("my/ships/$shipSymbol/orbit") { setBody("{}") }
            .body<ApiResponse<OrbitDockResponseDto>>().data.nav

    // POST /my/ships/{shipSymbol}/dock — docks the ship at its current waypoint.
    // Returns the updated nav state. Idempotent if already docked.
    override suspend fun dockShip(shipSymbol: String): ShipNavDto =
        client.authenticated.post("my/ships/$shipSymbol/dock") { setBody("{}") }
            .body<ApiResponse<OrbitDockResponseDto>>().data.nav

    // POST /my/ships/{shipSymbol}/refuel — refuels the ship to max capacity
    // from the local market. Ship must be docked at a waypoint with Marketplace.
    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/refuel") { setBody("{}") }
            .body<ApiResponse<RefuelResponseDto>>().data

    // POST /my/ships/{shipSymbol}/navigate — sends the ship to the given waypoint.
    // Ship must be in orbit. Returns updated nav and fuel state.
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/navigate") {
            setBody(NavigateRequestDto(waypointSymbol))
        }.body<ApiResponse<NavigateResponseDto>>().data
}
