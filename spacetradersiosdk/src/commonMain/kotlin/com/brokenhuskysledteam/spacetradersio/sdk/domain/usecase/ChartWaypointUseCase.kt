package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ScanApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ChartResult

/**
 * Charts the ship's current uncharted waypoint, revealing its traits. Charting has no cooldown
 * and touches no cached ship state, so this use case simply returns the revealed waypoint.
 *
 * @param scanApi Live API client. Requires the ship to be at an uncharted waypoint.
 */
interface ChartWaypointUseCase {
    suspend operator fun invoke(shipSymbol: String): ChartResult
}

/** Production implementation of [ChartWaypointUseCase]. */
class ChartWaypointUseCaseImpl(private val scanApi: ScanApi) : ChartWaypointUseCase {
    override suspend operator fun invoke(shipSymbol: String): ChartResult =
        scanApi.chartWaypoint(shipSymbol).toDomain()
}
