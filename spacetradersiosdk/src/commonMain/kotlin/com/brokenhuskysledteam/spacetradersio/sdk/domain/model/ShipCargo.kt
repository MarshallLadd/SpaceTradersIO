package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * Represents the current load and maximum capacity of a ship's cargo hold.
 *
 * **Pattern:** Focused sub-model. In a decomposed model tree, each sub-model owns exactly
 * one concern. `ShipCargo` owns only the cargo hold — it knows nothing about fuel, nav, or
 * registration. When a buy/sell/extract/jettison API call completes, only this object is
 * replaced via `ship.copy(cargo = newCargo)`, leaving all other sub-models unchanged.
 *
 * **In this project:** Rendered as a filled/empty bar in the ship detail UI to show how
 * much hold space remains, plus an itemised manifest of [inventory]. The UI derives `isFull`
 * as `units >= capacity` directly from these fields — no extra state needed.
 *
 * @property units The number of cargo units currently in the hold. Each trade good or
 *   mining output item occupies one unit. Updated after every trade or extraction.
 * @property capacity The maximum number of units the hold can contain. Determined by the
 *   ship's installed cargo module and does not change between API calls.
 * @property inventory The distinct goods currently in the hold, each with its own unit count.
 *   The sum of `inventory[i].units` equals [units]. Defaults to an empty list so callers that
 *   only care about aggregate capacity (and older cached rows) construct without change.
 */
data class ShipCargo(
    val units: Int,
    val capacity: Int,
    val inventory: List<CargoItem> = emptyList()
)
