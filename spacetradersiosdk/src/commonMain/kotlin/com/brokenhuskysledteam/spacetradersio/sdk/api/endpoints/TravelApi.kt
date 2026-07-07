package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JumpGateDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JumpResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateRequestDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.NavigateResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.PaginatedResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SystemDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints for inter-system travel: warping, jumping, jump-gate connections, and galaxy
 * browsing. Isolated interface + Impl (like [MarketApi]/[MiningApi]) so it does not force the
 * many [FleetApi]/[SystemsApi] test stubs to grow.
 *
 * **In this project:** Warp moves a ship to a waypoint in another system under its own power
 * (fuel-costly, slow) — same request/response shape as navigate. Jump moves a ship instantly to
 * a **connected jump gate** (`getJumpGate` lists the connections) and incurs a cooldown.
 */
interface TravelApi {

    /**
     * Warps a ship to a waypoint in another system via `POST /my/ships/{shipSymbol}/warp`.
     *
     * @param shipSymbol The ship to warp (must be in orbit with sufficient fuel).
     * @param waypointSymbol The destination waypoint (in another system, within warp range).
     * @return [NavigateResponseDto] — the same nav+fuel shape as navigate.
     */
    suspend fun warp(shipSymbol: String, waypointSymbol: String): NavigateResponseDto

    /**
     * Jumps a ship to a connected jump gate via `POST /my/ships/{shipSymbol}/jump`.
     *
     * @param shipSymbol The ship at a jump gate.
     * @param waypointSymbol The destination jump-gate waypoint (must be in [getJumpGate]'s connections).
     * @return [JumpResponseDto] with the new nav state and jump cooldown.
     */
    suspend fun jump(shipSymbol: String, waypointSymbol: String): JumpResponseDto

    /**
     * Fetches a jump gate's connections via
     * `GET /systems/{systemSymbol}/waypoints/{waypointSymbol}/jump-gate`.
     *
     * @return [JumpGateDto] listing the connected jump-gate waypoints.
     */
    suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGateDto

    /**
     * Lists star systems via `GET /systems` (paginated) for galaxy browsing.
     *
     * @param page 1-based page index.
     * @param limit Items per page (API max 20).
     */
    suspend fun getSystems(page: Int = 1, limit: Int = 20): PaginatedResponse<SystemDto>

    /** Fetches a single system via `GET /systems/{systemSymbol}`. */
    suspend fun getSystem(systemSymbol: String): SystemDto
}

/** Production implementation of [TravelApi]. */
class TravelApiImpl(private val client: SpaceTradersClient) : TravelApi {

    override suspend fun warp(shipSymbol: String, waypointSymbol: String): NavigateResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/warp") {
            setBody(NavigateRequestDto(waypointSymbol))
        }.body<ApiResponse<NavigateResponseDto>>().data

    override suspend fun jump(shipSymbol: String, waypointSymbol: String): JumpResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/jump") {
            setBody(NavigateRequestDto(waypointSymbol))
        }.body<ApiResponse<JumpResponseDto>>().data

    override suspend fun getJumpGate(systemSymbol: String, waypointSymbol: String): JumpGateDto =
        client.authenticated.get("systems/$systemSymbol/waypoints/$waypointSymbol/jump-gate")
            .body<ApiResponse<JumpGateDto>>().data

    override suspend fun getSystems(page: Int, limit: Int): PaginatedResponse<SystemDto> =
        client.authenticated.get("systems") {
            parameter("page", page)
            parameter("limit", limit)
        }.body()

    override suspend fun getSystem(systemSymbol: String): SystemDto =
        client.authenticated.get("systems/$systemSymbol").body<ApiResponse<SystemDto>>().data
}
