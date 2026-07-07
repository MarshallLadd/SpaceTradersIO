package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * A jump gate and the gates it connects to. Jump gates are the backbone of fast inter-system
 * travel: a ship at one gate can jump to any connected gate.
 *
 * @property symbol This jump gate's waypoint symbol.
 * @property connections The waypoint symbols of connected jump gates (often in other systems).
 */
data class JumpGate(
    val symbol: String,
    val connections: List<String>
)

/**
 * A star system summary for galaxy browsing.
 *
 * @property symbol The system identifier (e.g. `"X1-DM91"`).
 * @property sectorSymbol The sector the system belongs to.
 * @property type The system/star type (raw string; the set may grow).
 * @property x Galaxy X coordinate.
 * @property y Galaxy Y coordinate.
 */
data class StarSystem(
    val symbol: String,
    val sectorSymbol: String,
    val type: String,
    val x: Int,
    val y: Int
)

/**
 * A page of star systems from galaxy browsing.
 *
 * @property systems The systems on this page.
 * @property page The 1-based page index.
 * @property total The total number of systems across all pages.
 */
data class SystemPage(
    val systems: List<StarSystem>,
    val page: Int,
    val total: Int
)

/**
 * The outcome of a jump: the ship's new nav state and the jump cooldown.
 *
 * @property nav The ship's nav after the jump.
 * @property cooldown The jump cooldown.
 */
data class JumpResult(
    val nav: ShipNav,
    val cooldown: Cooldown
)
