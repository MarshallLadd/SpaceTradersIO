package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mappers for scanning & charting. Reuses WaypointDto.toDomain() (WaypointMapper) and
// CooldownDto.toDomain() (ShipMapper).

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ChartResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ScanSystemsResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ScanWaypointsResponseDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ScannedSystemDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ChartResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanSystemsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScanWaypointsResult
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScannedSystem

/** Maps a [ScannedSystemDto] to a [ScannedSystem]. */
fun ScannedSystemDto.toDomain(): ScannedSystem = ScannedSystem(
    symbol = symbol,
    type = type,
    x = x,
    y = y,
    distance = distance
)

/** Maps a [ScanSystemsResponseDto] to a [ScanSystemsResult]. */
fun ScanSystemsResponseDto.toDomain(): ScanSystemsResult = ScanSystemsResult(
    cooldown = cooldown.toDomain(),
    systems = systems.map { it.toDomain() }
)

/** Maps a [ScanWaypointsResponseDto] to a [ScanWaypointsResult] (waypoints reuse the waypoint mapper). */
fun ScanWaypointsResponseDto.toDomain(): ScanWaypointsResult = ScanWaypointsResult(
    cooldown = cooldown.toDomain(),
    waypoints = waypoints.map { it.toDomain() }
)

/** Maps a [ChartResponseDto] to a [ChartResult]. */
fun ChartResponseDto.toDomain(): ChartResult = ChartResult(
    waypoint = waypoint.toDomain()
)
