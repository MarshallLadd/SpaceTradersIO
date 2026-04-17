package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Endpoints under the "Systems" tag in the SpaceTraders OpenAPI spec.
 *
 * **Pattern:** Interface + Impl for API clients. Defining [SystemsApi] as an
 * interface allows repository tests to inject a fake implementation that returns
 * controlled waypoint lists without network access. See [AgentsApi] for a full
 * explanation of why this split is valuable.
 *
 * **In this project:** Systems data (waypoints, traits, type) drives the system
 * map UI and informs ship routing decisions. Waypoints are fetched once per system
 * and cached in the local database; the total count from [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto]
 * drives the paginated fetch loop in the repository.
 */
interface SystemsApi {

    /**
     * Returns a paginated list of waypoints within the given star system.
     *
     * Calls `GET /systems/{systemSymbol}/waypoints`. Each [WaypointDto] describes
     * a location within the system: its type (PLANET, MOON, ASTEROID_FIELD, etc.),
     * orbital traits (MARKETPLACE, SHIPYARD, etc.), coordinates, and orbiting bodies.
     *
     * **Pagination:** Pages are 1-based. The API maximum [limit] is 20. To fetch all
     * waypoints in a system, call this method repeatedly, incrementing [page], until
     * the cumulative item count reaches `PaginatedResponse.meta.total`. Example fetch
     * loop in a repository:
     *
     * ```kotlin
     * var page = 1
     * val all = mutableListOf<WaypointDto>()
     * do {
     *     val response = systemsApi.getSystemWaypoints(systemSymbol, page, limit = 20)
     *     all += response.data
     *     page++
     * } while (all.size < response.meta.total)
     * ```
     *
     * @param systemSymbol The star system identifier (e.g. `"X1-DF55"`). Obtainable
     *   from the agent's `headquarters` field or from any waypoint's symbol prefix.
     * @param page 1-based page index. Defaults to 1 (first page).
     * @param limit Items per page. Defaults to 20 (the API maximum). Lower values
     *   reduce individual response sizes but require more round-trips.
     * @return [PaginatedResponse] wrapping a list of [WaypointDto] and a
     *   [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MetaDto] with `total`,
     *   `page`, and `limit` for computing remaining pages.
     * @throws com.brokenhuskysledteam.spacetradersio.sdk.domain.model.SpaceTradersApiException
     *   if the system symbol is invalid or does not exist in the game universe.
     */
    suspend fun getSystemWaypoints(
        systemSymbol: String,
        page: Int = 1,
        limit: Int = 20
    ): PaginatedResponse<WaypointDto>
}

/**
 * Production implementation of [SystemsApi] backed by the real SpaceTraders HTTP API.
 *
 * @param client The shared [SpaceTradersClient]. A valid AgentToken must be stored
 *   before calling any method — the Systems endpoints require authentication even
 *   though the data is not agent-specific.
 */
class SystemsApiImpl(private val client: SpaceTradersClient) : SystemsApi {

    /**
     * Fetches a page of waypoints via `GET /systems/{systemSymbol}/waypoints`.
     *
     * Ktor's `parameter("page", page)` appends `?page=1&limit=20` to the URL.
     * The response is deserialized directly as [PaginatedResponse]`<`[WaypointDto]`>`
     * — no `.data` unwrap is needed because [PaginatedResponse] IS the top-level
     * JSON object for paginated endpoints (it contains both `data` and `meta` fields).
     */
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
