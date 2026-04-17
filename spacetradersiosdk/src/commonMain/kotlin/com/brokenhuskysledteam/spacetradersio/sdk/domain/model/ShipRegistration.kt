package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole

/**
 * The immutable identity and classification metadata for a ship.
 *
 * **Pattern:** Immutable sub-model for write-once data. In a decomposed model tree, not all
 * sub-models change at runtime. Separating write-once registration data from frequently
 * updated sub-models (nav, fuel, cargo) makes it visually clear that this data will never
 * need a `copy()` call — and ensures that no API action accidentally clobbers it. In a new
 * project, apply this separation whenever an API response mixes static identity fields with
 * dynamic operational fields.
 *
 * **In this project:** [ShipRegistration] is set once when the ship is purchased and never
 * updated by any subsequent API call. It is displayed in the ship detail UI to show the
 * player the ship's purpose (its role) and its faction of origin. Because it is `val` on
 * `Ship` and never replaced via `copy()`, it effectively acts as a compile-time signal that
 * this data is stable.
 *
 * @property role The functional classification of the ship — determines its primary purpose
 *   and which modules it can be fitted with (e.g. `EXCAVATOR` for mining, `HAULER` for
 *   transport, `COMMAND` for the flagship). See [ShipRole] for the full enumeration.
 * @property factionSymbol The faction that manufactured or registered this ship
 *   (e.g. `"COSMIC"`). Informational in the current SDK version; future game mechanics
 *   may use faction affiliation to gate access to faction-restricted waypoints or markets.
 */
data class ShipRegistration(
    val role: ShipRole,
    val factionSymbol: String
)
