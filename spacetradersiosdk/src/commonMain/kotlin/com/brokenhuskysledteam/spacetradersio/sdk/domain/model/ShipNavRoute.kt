package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

/**
 * Describes a single navigate leg — where a ship came from, where it is going, and when.
 *
 * **Pattern:** Nested value object with dual timestamps. In any project where an operation
 * has a known start time and an expected end time, store both absolute [kotlinx.datetime.Instant]
 * values. The departure timestamp lets you display "departed N minutes ago"; the arrival
 * timestamp lets you display "arrives in N minutes" and schedule a refresh. Using absolute
 * instants rather than durations makes the data correct even if the user puts the app in
 * the background between requests.
 *
 * **In this project:** [ShipNavRoute] is embedded in [ShipNav] and is the primary input
 * for the in-transit countdown displayed on the ship detail screen. The `RefreshScheduler`
 * watches [arrivalTime] and automatically re-fetches ship state once that instant has
 * passed, transitioning the ship from `IN_TRANSIT` back to `IN_ORBIT` in the UI without
 * any manual user action.
 *
 * @property origin A lightweight snapshot of the waypoint the ship departed from. Provides
 *   the name and coordinates of the starting point for display and distance calculation.
 *   See [ShipNavRouteWaypoint].
 * @property destination A lightweight snapshot of the waypoint the ship is travelling to.
 *   Provides the name and coordinates of the destination. See [ShipNavRouteWaypoint].
 * @property departureTime The absolute instant when the ship left [origin]. Can be
 *   combined with [arrivalTime] to render a progress bar showing how far through the
 *   journey the ship currently is (`elapsed / totalDuration`).
 * @property arrivalTime The absolute instant when the ship is expected to reach
 *   [destination]. A value in the past means the ship has arrived but the local cache has
 *   not been refreshed yet — the `RefreshScheduler` handles this case by triggering a
 *   re-fetch at the [arrivalTime] moment.
 */
data class ShipNavRoute(
    val origin: ShipNavRouteWaypoint,
    val destination: ShipNavRouteWaypoint,
    val departureTime: Instant,
    // Past arrivalTime = ship has arrived but cache is stale. RefreshScheduler corrects this.
    val arrivalTime: Instant
)
