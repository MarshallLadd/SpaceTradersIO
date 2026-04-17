package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * The top-level domain model for a ship owned by the authenticated agent.
 *
 * **Pattern:** Decomposed model tree. Rather than flattening all ship data into one large
 * class, `Ship` holds focused sub-models for each concern: navigation, cargo, fuel,
 * registration, and cooldown. In a new project, apply this pattern whenever an API response
 * contains several logically distinct groups of fields — each group becomes its own `data
 * class`. The benefit is surgical updates: when an action only changes one sub-model (e.g.
 * orbit changes only navigation state), the repository can use `ship.copy(nav = newNav)`
 * without touching the other sub-models, which minimises noise in StateFlow emissions and
 * reduces unnecessary UI recomposition.
 *
 * **In this project:** Ships are the player's primary assets. A `Ship` is stored in
 * `ShipStateStore` (keyed by [symbol]), observed by `ShipListViewModel`, and decomposed
 * in the detail UI into separate card sections for nav, cargo, fuel, and registration.
 * API actions such as navigate, dock, orbit, refuel, and extract each return a partial
 * update (captured in result types like [NavigateResult] or [RefuelResult]) that replaces
 * only the relevant sub-model via `copy()`.
 *
 * @property symbol The server-assigned unique identifier for this specific ship, formatted
 *   as `<agentSymbol>-<index>` (e.g. `"MYAGENT-1"`). Used as the primary key in all
 *   ship-related API paths and as the `ShipStateStore` map key.
 * @property registration Immutable metadata about the ship's role and faction affiliation.
 *   Does not change after purchase. See [ShipRegistration].
 * @property nav The ship's current navigation state: which system and waypoint it is at,
 *   its docked/orbiting/in-transit status, flight mode, and active route. Updated by
 *   navigate, orbit, and dock calls. See [ShipNav].
 * @property cargo The current cargo load and hold capacity. Updated after buy, sell,
 *   extract, and jettison operations. See [ShipCargo].
 * @property fuel The current fuel level and tank capacity. Updated after navigate and
 *   refuel operations. See [ShipFuel].
 * @property frameName A human-readable label for the ship's hull frame type
 *   (e.g. `"FRAME_DRONE"`). Informational only — used in the ship list UI to give the
 *   player a quick sense of the ship class without navigating to the full detail screen.
 * @property cooldown The reactor cooldown state after using a reactor-intensive module.
 *   [Cooldown.expiration] is the absolute timestamp when the ship is "ready" again.
 *   The `RefreshScheduler` uses this to automatically re-fetch ship state once the timer
 *   expires. See [Cooldown].
 */
data class Ship(
    val symbol: String,
    val registration: ShipRegistration,
    val nav: ShipNav,
    val cargo: ShipCargo,
    val fuel: ShipFuel,
    // Informational frame label — not parsed into an enum because it is not used in logic.
    val frameName: String,
    val cooldown: Cooldown
)
