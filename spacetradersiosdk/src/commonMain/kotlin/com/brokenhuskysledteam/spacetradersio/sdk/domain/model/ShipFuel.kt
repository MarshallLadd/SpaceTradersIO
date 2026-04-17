package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * Represents the current fuel level and tank capacity of a ship.
 *
 * **Pattern:** Focused sub-model. Part of the [Ship] decomposed model tree. Because fuel
 * is updated independently of navigation and cargo (navigate depletes fuel; refuel
 * replenishes it), keeping it in its own `data class` allows precise `ship.copy(fuel =
 * newFuel)` updates that do not trigger unnecessary recomposition of unrelated UI sections.
 *
 * **In this project:** Returned as part of [NavigateResult] (fuel consumed after a trip
 * was committed) and [RefuelResult] (fuel restored after purchasing at a market). Rendered
 * in the ship detail UI as a numeric readout and a progress bar.
 *
 * @property current The number of fuel units currently in the tank. Decreases with each
 *   navigate call; the exact amount consumed depends on distance and the ship's active
 *   flight mode. Flight mode costs, from most to least consumption:
 *   `BURN` > `CRUISE` > `STEALTH` > `DRIFT`.
 * @property capacity The maximum number of fuel units the tank can hold. Determined by
 *   the ship's frame and installed reactor; does not change at runtime. A refuel call
 *   restores [current] up to this limit.
 */
data class ShipFuel(
    val current: Int,
    val capacity: Int
)
