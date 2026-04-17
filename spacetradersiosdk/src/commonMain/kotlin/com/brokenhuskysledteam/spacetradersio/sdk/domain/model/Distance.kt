package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes the straight-line (Euclidean) distance between two waypoints on a 2-D star map.
 *
 * **Pattern:** Pure top-level utility function — no class or object wrapper needed when a
 * function has no state and no dependencies beyond its parameters. In a new project, prefer
 * package-level functions for stateless math helpers; they are easier to test and import
 * individually than functions buried inside a utility singleton.
 *
 * **In this project:** The SpaceTraders universe is a 2-D coordinate plane. Every waypoint
 * exposes integer `x`/`y` coordinates within its star system. Travel time and fuel
 * consumption both scale with distance, so this function is used to estimate trip cost
 * before committing to a navigate call. It is called with the coordinates stored on
 * [ShipNavRouteWaypoint] — e.g. `euclideanDistance(origin.x, origin.y, dest.x, dest.y)`.
 *
 * This function lives in `commonMain` so it is available on both Android and iOS without
 * any platform-specific code. It uses `kotlin.math` (not `java.lang.Math`) for the same
 * reason.
 *
 * @param x1 The X coordinate of the origin waypoint.
 * @param y1 The Y coordinate of the origin waypoint.
 * @param x2 The X coordinate of the destination waypoint.
 * @param y2 The Y coordinate of the destination waypoint.
 * @return The Euclidean distance as a `Double`. The result is in "map units" — the same
 *   scale used by the SpaceTraders coordinate system — not real-world units.
 */
fun euclideanDistance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
    // Cast to Double before squaring to avoid Int overflow on large coordinate differences.
    sqrt((x2.toDouble() - x1).pow(2) + (y2.toDouble() - y1).pow(2))
