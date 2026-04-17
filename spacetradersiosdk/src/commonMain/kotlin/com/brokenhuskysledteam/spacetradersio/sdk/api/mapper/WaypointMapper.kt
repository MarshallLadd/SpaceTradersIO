package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: WaypointDto → Waypoint, WaypointTraitDto → WaypointTrait
// All DTO-to-domain conversions for the Waypoint entity live here.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.WaypointTraitDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.WaypointTrait
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType

/**
 * Maps this [WaypointDto] to a [Waypoint] domain model.
 *
 * **Pattern:** Extension function mapper with list transformation. When a DTO field is a list
 * of sub-objects, call `map { it.toDomain() }` to convert each element using its own mapper.
 * This keeps the per-element conversion logic in [WaypointTraitDto.toDomain] while the
 * parent mapper stays focused on the top-level field assignments. In a new project, prefer
 * `map { it.toDomain() }` over inline lambda bodies for list fields — it's more readable and
 * testable independently.
 *
 * **In this project:** [WaypointDto] is returned by waypoint-listing endpoints. Each waypoint
 * carries a list of [WaypointTraitDto] objects describing what is available there (market,
 * shipyard, mineral deposits, etc.).
 *
 * **Derived data from traits:** The domain model does not expose a dedicated boolean flag
 * like `hasMarketplace`. Instead, callers inspect the `traits` list for the presence of
 * [WaypointTraitSymbol.MARKETPLACE]. This avoids redundant state — if the API adds new
 * gameplay-relevant traits, the domain model does not need to change.
 *
 * **Orbital flattening:** [WaypointDto.orbitals] is a list of [WaypointOrbitalDto] objects
 * that each carry only a `symbol` string. The mapper flattens these to `List<String>` via
 * `map { it.symbol }` because the domain model only needs the symbol — the nesting in the
 * DTO is purely a JSON schema artifact.
 *
 * @return The domain model built from this DTO's data.
 */
fun WaypointDto.toDomain(): Waypoint = Waypoint(
    symbol = symbol,
    // WaypointType.fromString uses safe parsing — unrecognized type strings produce
    // WaypointType.UNKNOWN rather than throwing on new API additions.
    type = WaypointType.fromString(type),
    systemSymbol = systemSymbol,
    x = x,
    y = y,
    orbits = orbits,
    // Flatten: each orbital DTO wraps a single symbol string; the domain model wants strings.
    orbitals = orbitals.map { it.symbol },
    // Each WaypointTraitDto is converted by its own mapper, keeping trait conversion isolated.
    traits = traits.map { it.toDomain() },
    isUnderConstruction = isUnderConstruction
)

/**
 * Maps this [WaypointTraitDto] to a [WaypointTrait] domain model.
 *
 * **Pattern:** Sub-mapper with safe enum parsing. Converting the raw API string to a typed
 * [WaypointTraitSymbol] happens here, once, rather than at every call site in the UI or
 * business logic. In a new project, always convert raw strings to enums at the mapper
 * boundary so the domain layer and UI never touch raw strings.
 *
 * **In this project:** [WaypointTraitSymbol.fromString] is a safe parser that returns
 * [WaypointTraitSymbol.UNCHARTED] for any string the enum does not recognize. The API has
 * 50+ trait symbols; using a safe fallback means new trait symbols added by the game server
 * don't crash the client — they map to `UNCHARTED` and are effectively ignored until the
 * client is updated to handle them.
 *
 * @return The domain model built from this DTO's trait data.
 */
fun WaypointTraitDto.toDomain(): WaypointTrait = WaypointTrait(
    // WaypointTraitSymbol.fromString returns UNCHARTED for any unrecognized symbol string,
    // preventing crashes when the API introduces new trait values between client releases.
    symbol = WaypointTraitSymbol.fromString(symbol),
    name = name,
    description = description
)
