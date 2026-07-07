package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * A system revealed by a scan, with distance from the scanning ship.
 *
 * @property symbol The system identifier.
 * @property type The system/star type (raw string).
 * @property x Galaxy X coordinate.
 * @property y Galaxy Y coordinate.
 * @property distance Distance from the scanning ship's system (for sorting by proximity).
 */
data class ScannedSystem(
    val symbol: String,
    val type: String,
    val x: Int,
    val y: Int,
    val distance: Int
)

/**
 * The outcome of a system scan.
 *
 * @property cooldown The scan cooldown.
 * @property systems The revealed systems (sortable by [ScannedSystem.distance]).
 */
data class ScanSystemsResult(
    val cooldown: Cooldown,
    val systems: List<ScannedSystem>
)

/**
 * The outcome of a waypoint scan.
 *
 * @property cooldown The scan cooldown.
 * @property waypoints The revealed waypoints (full detail, including traits).
 */
data class ScanWaypointsResult(
    val cooldown: Cooldown,
    val waypoints: List<Waypoint>
)

/**
 * The outcome of charting a waypoint.
 *
 * @property waypoint The now-charted waypoint, with its revealed traits.
 */
data class ChartResult(
    val waypoint: Waypoint
)
