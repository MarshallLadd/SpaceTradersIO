package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * The composite result returned by a navigate API call (`POST /my/ships/{shipSymbol}/navigate`).
 *
 * **Pattern:** Composite result model. In any project where a single API action mutates
 * more than one resource simultaneously, wrap the updated sub-models in a dedicated result
 * `data class` rather than making multiple follow-up requests to fetch each piece. The
 * server returns both changes in one response body, and this type captures them together.
 * The caller then distributes each part to its own store or ViewModel property.
 *
 * **In this project:** Returned by the navigate use case and split by the repository: the
 * updated [nav] is saved to `ShipStateStore` (updating the ship's location and route), and
 * the updated [fuel] replaces the ship's previous fuel snapshot. Only the affected
 * sub-models are swapped — the rest of the `Ship` object (cargo, registration, cooldown)
 * is left untouched, which keeps state updates precise and avoids unnecessary recomposition.
 *
 * @property nav The ship's updated navigation state, including the new [ShipNavRoute] with
 *   departure time, destination, and expected arrival time. Replaces the previous [ShipNav]
 *   on the [Ship] object after a successful navigate call.
 * @property fuel The ship's updated fuel level after the transit has been committed. Fuel
 *   is consumed at departure; the amount deducted depends on distance and the active
 *   [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode].
 */
data class NavigateResult(
    val nav: ShipNav,
    val fuel: ShipFuel
)
