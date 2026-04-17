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

/**
 * Endpoints under the "Fleet" tag in the SpaceTraders OpenAPI spec.
 *
 * **Pattern:** Interface + Impl for API clients. Separating the contract
 * ([FleetApi]) from the network implementation ([FleetApiImpl]) lets the SDK's
 * repository and use-case tests inject a hand-written fake without hitting the
 * network. In tests, implement [FleetApi] directly with controlled return values;
 * in production, bind [FleetApiImpl] through the DI graph.
 *
 * **In this project:** Fleet is the most active API surface — ships navigate,
 * orbit, dock, and refuel constantly. Several endpoints (`orbitShip`, `dockShip`,
 * `refuelShip`) have no request body in the OpenAPI spec but still require
 * `setBody("{}")` because the global `Content-Type: application/json` set in
 * [SpaceTradersClient]'s `defaultRequest` causes the server to expect a valid
 * JSON body. See the individual method docs for details.
 */
interface FleetApi {

    /**
     * Returns a paginated list of all ships owned by the authenticated agent.
     *
     * @param page 1-based page index. Defaults to 1.
     * @param limit Items per page. Defaults to 20 (API maximum).
     * @return [PaginatedResponse] wrapping a list of [ShipDto] and pagination metadata.
     */
    suspend fun getMyShips(page: Int = 1, limit: Int = 20): PaginatedResponse<ShipDto>

    /**
     * Fetches the full details of a single ship.
     *
     * @param shipSymbol The ship's call-sign (e.g. `"MYAGENT-1"`).
     * @return [ShipDto] with current nav, fuel, cargo, and module state.
     */
    suspend fun getMyShip(shipSymbol: String): ShipDto

    /**
     * Commands a ship to enter orbit at its current waypoint.
     *
     * @param shipSymbol The call-sign of the ship to orbit.
     * @return [ShipNavDto] reflecting the new `ORBIT` nav status.
     */
    suspend fun orbitShip(shipSymbol: String): ShipNavDto

    /**
     * Commands a ship to dock at its current waypoint.
     *
     * @param shipSymbol The call-sign of the ship to dock.
     * @return [ShipNavDto] reflecting the new `DOCKED` nav status.
     */
    suspend fun dockShip(shipSymbol: String): ShipNavDto

    /**
     * Refuels a ship to its maximum fuel capacity from the local market.
     *
     * @param shipSymbol The call-sign of the ship to refuel.
     * @return [RefuelResponseDto] with updated fuel state and the transaction record.
     */
    suspend fun refuelShip(shipSymbol: String): RefuelResponseDto

    /**
     * Sends a ship to the specified waypoint.
     *
     * @param shipSymbol The call-sign of the ship to navigate.
     * @param waypointSymbol The destination waypoint (e.g. `"X1-DF55-20250Z"`).
     * @return [NavigateResponseDto] with updated nav state and fuel consumption.
     */
    suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto
}

/**
 * Production implementation of [FleetApi] backed by the real SpaceTraders HTTP API.
 *
 * All methods use [SpaceTradersClient.authenticated] which carries the stored
 * AgentToken as a Bearer header. Responses are unwrapped from the
 * [ApiResponse] envelope common to all SpaceTraders endpoints.
 *
 * @param client The shared [SpaceTradersClient]. A valid AgentToken must be stored
 *   in the [com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.TokenRepository]
 *   before any method is called.
 */
class FleetApiImpl(private val client: SpaceTradersClient) : FleetApi {

    /**
     * Fetches all ships via `GET /my/ships` with pagination.
     *
     * Pagination works identically to other list endpoints: pass [page] = 1 to start,
     * then use `PaginatedResponse.meta.total` and [limit] to compute the number of
     * remaining pages. The response is a [PaginatedResponse] deserialized directly
     * by Ktor's [ContentNegotiation] — no `.data` unwrap needed because
     * [PaginatedResponse] is the envelope for paginated results.
     */
    override suspend fun getMyShips(page: Int, limit: Int): PaginatedResponse<ShipDto> =
        client.authenticated.get("my/ships") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    /**
     * Fetches a single ship via `GET /my/ships/{shipSymbol}`.
     *
     * Uses the standard [ApiResponse] envelope unwrap (`.data`) unlike the list
     * endpoint which uses [PaginatedResponse] directly. Both are valid SpaceTraders
     * response shapes — single-item endpoints use `{ "data": {...} }` while list
     * endpoints use `{ "data": [...], "meta": {...} }`.
     */
    override suspend fun getMyShip(shipSymbol: String): ShipDto =
        client.authenticated.get("my/ships/$shipSymbol").body<ApiResponse<ShipDto>>().data

    /**
     * Commands a ship into orbit via `POST /my/ships/{shipSymbol}/orbit`.
     *
     * The endpoint is idempotent — calling it on an already-orbiting ship succeeds
     * without error. The response is wrapped in [OrbitDockResponseDto] (shared with
     * `dockShip`) which carries the updated [ShipNavDto]; only `.data.nav` is
     * returned to callers since the rest of the ship state is unchanged.
     *
     * **GOTCHA — empty-body POST:** `setBody("{}")` is required even though the
     * OpenAPI spec defines no request body. The global `Content-Type: application/json`
     * header set in `defaultRequest` causes the API to expect a JSON body. Without
     * `setBody("{}")` the server returns HTTP 422. This same pattern applies to
     * [dockShip] and [refuelShip].
     */
    override suspend fun orbitShip(shipSymbol: String): ShipNavDto =
        client.authenticated.post("my/ships/$shipSymbol/orbit") { setBody("{}") }
            .body<ApiResponse<OrbitDockResponseDto>>().data.nav

    /**
     * Docks a ship at its current waypoint via `POST /my/ships/{shipSymbol}/dock`.
     *
     * Idempotent — docking an already-docked ship succeeds without error. The ship
     * must be at a waypoint that has a docking facility; attempting to dock in open
     * space returns a [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersError.ShipOperationError].
     *
     * **GOTCHA — empty-body POST:** `setBody("{}")` is required for the same reason
     * as [orbitShip]. See that method's documentation for a full explanation.
     */
    override suspend fun dockShip(shipSymbol: String): ShipNavDto =
        client.authenticated.post("my/ships/$shipSymbol/dock") { setBody("{}") }
            .body<ApiResponse<OrbitDockResponseDto>>().data.nav

    /**
     * Refuels a ship to maximum capacity via `POST /my/ships/{shipSymbol}/refuel`.
     *
     * Prerequisites: the ship must be docked (`DOCKED` nav status) at a waypoint
     * that has a `MARKETPLACE` trait and trades `FUEL`. Credits are deducted from
     * the agent's balance; the response includes the updated fuel state and the
     * market transaction record for bookkeeping.
     *
     * **GOTCHA — empty-body POST:** `setBody("{}")` is required here too. See
     * [orbitShip] for the full explanation.
     */
    override suspend fun refuelShip(shipSymbol: String): RefuelResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/refuel") { setBody("{}") }
            .body<ApiResponse<RefuelResponseDto>>().data

    /**
     * Sends a ship to a destination waypoint via `POST /my/ships/{shipSymbol}/navigate`.
     *
     * Prerequisites: the ship must be in orbit (`IN_ORBIT` nav status). The waypoint
     * must be within the ship's current system — inter-system travel uses a separate
     * warp/jump endpoint not yet implemented here.
     *
     * Unlike the other fleet action endpoints, this POST has a real request body:
     * [NavigateRequestDto] wrapping the destination [waypointSymbol]. The response
     * includes the updated nav state (status transitions to `IN_TRANSIT`, arrival time
     * set) and the fuel consumed, allowing the UI to schedule a refresh when the ship
     * arrives.
     *
     * @param shipSymbol The call-sign of the ship to navigate.
     * @param waypointSymbol The full waypoint symbol in the current system
     *   (e.g. `"X1-DF55-20250Z"`).
     * @return [NavigateResponseDto] with updated [ShipNavDto] and fuel delta.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the ship is docked, the waypoint is in a different system, or the ship
     *   lacks sufficient fuel for the journey.
     */
    override suspend fun navigateShip(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/navigate") {
            setBody(NavigateRequestDto(waypointSymbol))
        }.body<ApiResponse<NavigateResponseDto>>().data
}
