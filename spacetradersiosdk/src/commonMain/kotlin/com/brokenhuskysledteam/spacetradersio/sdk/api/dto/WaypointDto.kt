package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Waypoint` schema.
 *
 * **Pattern:** Data Transfer Object (DTO). DTOs are `@Serializable` data classes that carry
 * the API wire format. A mapper converts Dto → Domain at the API boundary, keeping
 * serialization concerns out of business logic. In a new project, create one DTO per
 * API response/request schema and a corresponding mapper.
 *
 * **In this project:** `WaypointDto` is returned by `GET /systems/{system}/waypoints`
 * (inside [PaginatedResponse]) and `GET /systems/{system}/waypoints/{waypoint}`. Waypoints
 * are locations within a star system where ships can navigate, dock, trade, mine, and
 * interact with the world. The mapper lives at `api/mapper/WaypointMapper.kt`.
 *
 * **Orbital vs. flat list design:** The SpaceTraders API returns all waypoints in a star
 * system as a flat list. Moons and orbital stations are included in the same flat list as
 * planets — the parent-child relationship is expressed via [orbits] (this waypoint orbits
 * the named parent) and [orbitals] (symbols of waypoints that orbit this one). The full
 * detail of each orbital is always available by looking it up in the same flat list; only
 * its symbol is stored here to avoid circular nesting.
 *
 * @property symbol The waypoint's unique identifier (e.g. `"X1-OE-PM"`), in
 *   `<system>-<waypoint>` format.
 * @property type The waypoint's classification as a raw string (e.g. `"PLANET"`,
 *   `"ASTEROID_FIELD"`, `"ORBITAL_STATION"`). The mapper converts this to the typed
 *   `WaypointType` enum so the domain layer never works with magic strings.
 * @property systemSymbol The star system this waypoint belongs to (e.g. `"X1-OE"`).
 * @property x The waypoint's X coordinate within its star system. Used for distance
 *   calculations when estimating travel time and fuel cost.
 * @property y The waypoint's Y coordinate within its star system.
 * @property orbits The symbol of the parent waypoint this waypoint orbits (e.g. the
 *   planet a moon circles). `null` for top-level waypoints (stars, planets, asteroid
 *   fields) that do not orbit another body in the system.
 * @property orbitals The list of child waypoints that orbit this waypoint (e.g. moons of
 *   a planet, stations around a gas giant). Each entry is a [WaypointOrbitalDto] containing
 *   only the child's symbol — full details are in the flat waypoints list. Defaults to an
 *   empty list for waypoints with no children.
 * @property traits Gameplay-relevant attributes of this waypoint: what resources it has,
 *   what services it offers (marketplace, shipyard, etc.). See [WaypointTraitDto]. Defaults
 *   to an empty list for waypoints that have no traits in the API response.
 * @property isUnderConstruction `true` if this waypoint is currently being built and not
 *   yet operational. Ships may be unable to dock or trade here. Defaults to `false` when
 *   the field is absent from the JSON.
 */
@Serializable
data class WaypointDto(
    val symbol: String,
    // Raw string enum: e.g. "PLANET", "ASTEROID_FIELD". Mapper converts to WaypointType.
    val type: String,
    val systemSymbol: String,
    val x: Int,
    val y: Int,
    // Null for top-level waypoints that do not orbit another body.
    val orbits: String? = null,
    val orbitals: List<WaypointOrbitalDto> = emptyList(),
    val traits: List<WaypointTraitDto> = emptyList(),
    val isUnderConstruction: Boolean = false
)

/**
 * A lightweight orbital reference — a child waypoint that orbits a parent waypoint.
 *
 * **Pattern:** Reference DTO (symbol-only). Because the SpaceTraders API returns waypoints
 * as a flat list, embedding the full details of each orbital inside its parent would
 * require deep nesting and create duplicated data. Instead, orbitals are represented by
 * symbol alone; the caller looks up the full [WaypointDto] in the same flat list by symbol.
 * In a new project, use this pattern for any list that references items also present in a
 * sibling list — store only the key, not the full object.
 *
 * **In this project:** The list of [WaypointOrbitalDto] in [WaypointDto.orbitals] tells
 * the mapper which waypoints in the page are children of this parent, enabling tree
 * reconstruction without a second API call.
 *
 * @property symbol The unique identifier of the orbital waypoint (e.g. `"X1-OE-PM01"`).
 *   Look this up in the parent response's flat waypoint list for full details.
 */
@Serializable
data class WaypointOrbitalDto(val symbol: String)

/**
 * A single trait describing a waypoint's gameplay-relevant characteristics.
 *
 * **Pattern:** List-item DTO. When a parent DTO contains a list of structured sub-items,
 * each item gets its own DTO type. This keeps the parent clean and makes the mapper easy
 * to extend if trait-level filtering or icon-lookup logic is needed later.
 *
 * **In this project:** Traits indicate what a waypoint offers — e.g. `"MARKETPLACE"`,
 * `"SHIPYARD"`, `"MINERAL_DEPOSITS"`. The UI and use cases check for specific trait
 * symbols to decide what actions are available at a waypoint. The mapper converts the list
 * to `List<WaypointTrait>` in the domain model.
 *
 * @property symbol The stable trait identifier (e.g. `"MARKETPLACE"`, `"SHIPYARD"`).
 *   Used programmatically to determine available actions at this waypoint.
 * @property name The trait's short display name (e.g. `"Marketplace"`).
 * @property description Longer flavour text explaining what the trait means in gameplay.
 */
@Serializable
data class WaypointTraitDto(
    val symbol: String,
    val name: String,
    val description: String
)
