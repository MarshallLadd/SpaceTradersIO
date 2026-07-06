package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import kotlinx.serialization.Serializable

/**
 * A single distinct good occupying space in a ship's cargo hold.
 *
 * **Pattern:** Serializable value object. Unlike most domain models in this SDK (which are
 * plain data classes and rely on separate DTOs for wire format), [CargoItem] is annotated
 * `@Serializable` because the `ship` table stores the whole inventory list as a single JSON
 * column (`cargo_inventory`) rather than a child table. Cargo inventory is a value list bound
 * 1:1 to a ship row, always read and written together with the aggregate cargo counts, and
 * never queried independently — the exact case where a serialized column is cleaner than a
 * relational child table. `ShipDbMapper` encodes/decodes this type directly. The wire format
 * still has its own `CargoItemDto`; this annotation is strictly for local persistence.
 *
 * **In this project:** Rendered as a line in the cargo manifest on the ship detail screen and,
 * from Phase 1 onward, used to decide what a ship can sell at a market and what goods it holds
 * for contract delivery.
 *
 * @property symbol The trade good's stable identifier (e.g. `"IRON_ORE"`, `"FUEL"`). Matches
 *   the `symbol` used by market and contract-delivery endpoints.
 * @property name The good's human-readable display name (e.g. `"Iron Ore"`).
 * @property description A short flavour/description string for the good.
 * @property units The number of units of this good currently in the hold. Always `>= 1` for an
 *   item that appears in the inventory (zero-unit goods are dropped from the list by the API).
 */
@Serializable
data class CargoItem(
    val symbol: String,
    val name: String,
    val description: String,
    val units: Int
)
