package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Response for `POST /my/ships/{shipSymbol}/scan/systems`.
 *
 * @property cooldown The scan cooldown.
 * @property systems The nearby systems revealed by the scan.
 */
@Serializable
data class ScanSystemsResponseDto(
    val cooldown: CooldownDto,
    val systems: List<ScannedSystemDto>
)

/**
 * A system revealed by a scan, with its distance from the scanning ship.
 *
 * @property symbol The system identifier.
 * @property sectorSymbol The sector the system belongs to.
 * @property type The system/star type (raw string).
 * @property x Galaxy X coordinate.
 * @property y Galaxy Y coordinate.
 * @property distance Distance from the scanning ship's system.
 */
@Serializable
data class ScannedSystemDto(
    val symbol: String,
    val sectorSymbol: String,
    val type: String,
    val x: Int,
    val y: Int,
    val distance: Int
)

/**
 * Response for `POST /my/ships/{shipSymbol}/scan/waypoints`.
 *
 * Reuses [WaypointDto] for the scanned waypoints — a scan returns the full waypoint objects,
 * including [WaypointDto.traits], so a scan can reveal marketplaces, shipyards, etc.
 *
 * @property cooldown The scan cooldown.
 * @property waypoints The waypoints revealed by the scan.
 */
@Serializable
data class ScanWaypointsResponseDto(
    val cooldown: CooldownDto,
    val waypoints: List<WaypointDto>
)

/**
 * Response for `POST /my/ships/{shipSymbol}/chart`. Charting an uncharted waypoint reveals its
 * traits. Only the revealed [waypoint] is captured; the `chart` metadata is ignored.
 *
 * @property waypoint The now-charted waypoint, with its full traits.
 */
@Serializable
data class ChartResponseDto(
    val waypoint: WaypointDto
)
