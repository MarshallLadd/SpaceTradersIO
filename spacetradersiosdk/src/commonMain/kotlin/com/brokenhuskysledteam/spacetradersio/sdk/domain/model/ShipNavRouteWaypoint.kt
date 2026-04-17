package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

/**
 * A lightweight, immutable snapshot of a waypoint embedded inside a navigation route.
 *
 * **Pattern:** Projection model (subset/snapshot). Not every context needs the full richness
 * of a domain object. In a new project, when an API response embeds a stripped-down version
 * of a related resource, model it as a separate, purpose-built type. This avoids forcing
 * optional fields onto the canonical model and makes it clear to readers that this is a
 * read-only snapshot captured at route-creation time, not a live reference.
 *
 * **In this project:** [ShipNavRouteWaypoint] appears as [ShipNavRoute.origin] and
 * [ShipNavRoute.destination]. It is NOT the full `Waypoint` domain model — it carries no
 * traits, marketplace data, or orbital body list. Its [x]/[y] coordinates are used by
 * [euclideanDistance] to compute trip distance for display purposes.
 *
 * @property symbol The unique waypoint identifier in `<system>-<waypoint>` format
 *   (e.g. `"X1-OE-PM"`). Can be used to look up the full `Waypoint` from the waypoints
 *   repository if more detail is needed.
 * @property type The category of location — planet, asteroid, jump gate, etc. Influences
 *   which actions are available at the waypoint (e.g. only markets offer trade).
 *   See [WaypointType] for the full enumeration.
 * @property systemSymbol The star system containing this waypoint, in `<sector>-<id>`
 *   format (e.g. `"X1-OE"`). Included here so the route can be interpreted without a
 *   separate system lookup.
 * @property x The waypoint's horizontal position on the star system's 2-D coordinate plane.
 *   Used with [y] in [euclideanDistance] to calculate route length.
 * @property y The waypoint's vertical position on the star system's 2-D coordinate plane.
 *   Used with [x] in [euclideanDistance] to calculate route length.
 */
data class ShipNavRouteWaypoint(
    val symbol: String,
    val type: WaypointType,
    val systemSymbol: String,
    // x/y are integer map coordinates used for distance calculations, not real-world units.
    val x: Int,
    val y: Int
)
