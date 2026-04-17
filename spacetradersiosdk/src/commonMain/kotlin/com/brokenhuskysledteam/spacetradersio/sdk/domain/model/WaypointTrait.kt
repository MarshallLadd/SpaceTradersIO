package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol

/**
 * A single characteristic that describes a capability or feature of a [Waypoint].
 *
 * **Pattern:** Capability descriptor model. In a new project where locations have variable sets
 * of capabilities, model each capability as a small data class (typed symbol + human-readable
 * metadata) and store them as a `List<Trait>` on the location. This lets UI and business logic
 * query capabilities with a simple `traits.any { it.symbol == X }` rather than maintaining
 * parallel boolean flags for every possible capability.
 *
 * **In this project:** `WaypointTrait` instances come directly from the API response. The [symbol]
 * is the machine-readable key used in code decisions (e.g., does this waypoint have a
 * `MARKETPLACE`?), while [name] and [description] are displayed in the UI to inform the player
 * what each trait means in the game world. A single waypoint can carry multiple traits
 * simultaneously — for example, an asteroid field might have `MINERAL_DEPOSITS`,
 * `COMMON_METAL_DEPOSITS`, and `PRECIOUS_METAL_DEPOSITS` all at once.
 *
 * Traits that materially affect gameplay decisions include:
 * - `MARKETPLACE` — goods can be bought and sold here.
 * - `SHIPYARD` — new ships can be purchased here.
 * - `JUMP_GATE` — inter-system travel is available (relevant only when the waypoint
 *   is also of type `JUMP_GATE`).
 * - `UNCHARTED` — the waypoint has not been surveyed; a chart action is required
 *   before full data is available.
 *
 * @property symbol Typed enum identifying the kind of trait. Use this for programmatic decisions
 *   (e.g., showing a "Trade" button only when `symbol == WaypointTraitSymbol.MARKETPLACE`).
 * @property name Human-readable display name for this trait, e.g. `"Marketplace"`. Intended for
 *   rendering in the UI.
 * @property description Extended human-readable explanation of what this trait means in the game
 *   world. Suitable for a tooltip or detail screen.
 */
data class WaypointTrait(
    val symbol: WaypointTraitSymbol,
    val name: String,
    val description: String
)
