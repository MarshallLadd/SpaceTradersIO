package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * The composite result returned by a refuel API call (`POST /my/ships/{shipSymbol}/refuel`).
 *
 * **Pattern:** Composite result model. The same pattern used by [NavigateResult] — a single
 * API call simultaneously mutates multiple resources (the agent's credits, the ship's fuel
 * level, and the market's transaction log). Wrapping all three in one result type lets the
 * repository distribute each piece to the right store in a single, atomic update step
 * rather than issuing separate refresh requests.
 *
 * **In this project:** The refuel use case returns this object; the repository then:
 * 1. Saves the updated [agent] to `AgentStateStore` (credits decreased by fuel cost).
 * 2. Saves the updated [fuel] back into the `Ship` object in `ShipStateStore`.
 * 3. Optionally exposes [transaction] to the UI as a receipt of the purchase.
 *
 * @property agent The agent's updated state after credits have been deducted for the fuel
 *   purchase. Always reflects the balance shown in the server's response — never computed
 *   client-side — to respect the API as the single source of truth.
 * @property fuel The ship's updated fuel tank after the refuel operation. [ShipFuel.current]
 *   will be at or near [ShipFuel.capacity] depending on how much capacity was empty.
 * @property transaction The market receipt for the refuel purchase. Contains the per-unit
 *   fuel price, quantity, and total cost at the moment of the transaction, which can be
 *   shown to the user as a "last purchase" confirmation.
 */
data class RefuelResult(
    val agent: Agent,
    val fuel: ShipFuel,
    val transaction: MarketTransaction
)
