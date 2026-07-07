package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.api.client.SpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ApiResponse
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ChartResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ScanSystemsResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ScanWaypointsResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * Endpoints for scanning and charting (SpaceTraders "Fleet" tag). Isolated interface + Impl.
 *
 * **In this project:** Scanning reveals nearby systems/waypoints and requires a sensor-array
 * mount; each scan incurs a cooldown. Charting an uncharted waypoint reveals its traits (e.g. a
 * hidden marketplace or shipyard).
 */
interface ScanApi {

    /**
     * Scans nearby systems via `POST /my/ships/{shipSymbol}/scan/systems`.
     *
     * @return [ScanSystemsResponseDto] with the revealed systems and a cooldown.
     */
    suspend fun scanSystems(shipSymbol: String): ScanSystemsResponseDto

    /**
     * Scans waypoints in the current system via `POST /my/ships/{shipSymbol}/scan/waypoints`.
     * Returns full waypoint detail (including traits), revealing marketplaces/shipyards.
     *
     * @return [ScanWaypointsResponseDto] with the revealed waypoints and a cooldown.
     */
    suspend fun scanWaypoints(shipSymbol: String): ScanWaypointsResponseDto

    /**
     * Charts the ship's current (uncharted) waypoint via `POST /my/ships/{shipSymbol}/chart`.
     *
     * @return [ChartResponseDto] with the now-charted waypoint.
     */
    suspend fun chartWaypoint(shipSymbol: String): ChartResponseDto
}

/** Production implementation of [ScanApi]. */
class ScanApiImpl(private val client: SpaceTradersClient) : ScanApi {

    override suspend fun scanSystems(shipSymbol: String): ScanSystemsResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/scan/systems") { setBody("{}") }
            .body<ApiResponse<ScanSystemsResponseDto>>().data

    override suspend fun scanWaypoints(shipSymbol: String): ScanWaypointsResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/scan/waypoints") { setBody("{}") }
            .body<ApiResponse<ScanWaypointsResponseDto>>().data

    override suspend fun chartWaypoint(shipSymbol: String): ChartResponseDto =
        client.authenticated.post("my/ships/$shipSymbol/chart") { setBody("{}") }
            .body<ApiResponse<ChartResponseDto>>().data
}
