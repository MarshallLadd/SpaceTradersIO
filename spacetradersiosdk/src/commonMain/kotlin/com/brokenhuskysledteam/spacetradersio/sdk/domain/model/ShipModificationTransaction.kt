package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlin.time.Instant

/**
 * Records a ship-modification fee (installing or removing a mount/module).
 *
 * Distinct from [MarketTransaction]: a modification has no direction/units/unit-price — only a
 * total fee charged by the shipyard.
 *
 * @property waypointSymbol The shipyard waypoint where the modification occurred.
 * @property shipSymbol The ship that was modified.
 * @property tradeSymbol The mount symbol installed/removed.
 * @property totalPrice The modification fee in credits.
 * @property timestamp When the server recorded the modification.
 */
data class ShipModificationTransaction(
    val waypointSymbol: String,
    val shipSymbol: String,
    val tradeSymbol: String,
    val totalPrice: Int,
    val timestamp: Instant
)

/**
 * The outcome of a mount install or remove — the resources a modification touches.
 *
 * @property agent The agent after the modification fee (reduced credits).
 * @property mounts The ship's full mount list after the change.
 * @property cargo The ship's cargo after the change (install consumes the mount from cargo;
 *   remove returns it to cargo).
 * @property transaction The modification-fee receipt.
 */
data class MountModificationResult(
    val agent: Agent,
    val mounts: List<ShipMount>,
    val cargo: ShipCargo,
    val transaction: ShipModificationTransaction
)
