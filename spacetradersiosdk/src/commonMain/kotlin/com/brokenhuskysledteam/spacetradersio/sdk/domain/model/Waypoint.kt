package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

/**
 * A specific navigable location within a star system.
 *
 * **Pattern:** Domain location model. In a new project, represent discrete navigable locations as
 * plain data classes with typed enums for classification, a list of capability descriptors (traits),
 * and raw coordinates for distance calculations — keeping all derived queries (has marketplace?
 * distance from here?) as extension functions or use-case logic rather than baking them into the model.
 *
 * **In this project:** `Waypoint` is the canonical destination type used throughout the SDK.
 * Navigation use cases accept waypoint symbols, route-planning logic uses `x`/`y` for Euclidean
 * distance, and UI layers query [traits] to decide which action buttons to display (e.g., "Buy
 * Ships" only if there is a shipyard trait).
 *
 * A waypoint symbol follows the pattern `"SYSTEM-SECTOR-ID"`, e.g., `"X1-AB12-C3"`. The symbol
 * is the primary key used in every API call that references a location.
 *
 * @property symbol Unique identifier for this waypoint, e.g. `"X1-AB12-C3"`. Used as the primary
 *   key in navigation, market, and construction API calls.
 * @property type Classification of this waypoint (planet, asteroid field, jump gate, etc.).
 *   Determines which operations are available here.
 * @property systemSymbol Symbol of the parent star system that contains this waypoint,
 *   e.g. `"X1-AB12"`. All waypoints in the same system share this prefix.
 * @property x X-coordinate relative to the system center (0, 0). Used with [y] to calculate
 *   travel distance via Euclidean distance — longer distances require more fuel.
 * @property y Y-coordinate relative to the system center (0, 0). See [x].
 * @property orbits Symbol of the parent waypoint this waypoint orbits, or `null` if this waypoint
 *   does not orbit another body (i.e., it orbits the star directly).
 * @property orbitals Symbols of any child waypoints that orbit this waypoint (e.g., moons).
 * @property traits List of capabilities present at this waypoint. Traits drive game decisions:
 *   a [WaypointTrait] with symbol `MARKETPLACE` means goods can be traded here; `SHIPYARD` means
 *   ships can be purchased here. A waypoint may have multiple traits simultaneously.
 * @property isUnderConstruction When `true`, certain operations (e.g., jump gate usage) are
 *   unavailable until construction completes. The API returns explicit construction errors
 *   ([SpaceTradersError.ShipOperationError.JumpOriginUnderConstruction]) for blocked actions.
 */
data class Waypoint(
    val symbol: String,
    val type: WaypointType,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    val orbits: String?,
    val orbitals: List<String>,
    val traits: List<WaypointTrait>,
    val isUnderConstruction: Boolean
)
