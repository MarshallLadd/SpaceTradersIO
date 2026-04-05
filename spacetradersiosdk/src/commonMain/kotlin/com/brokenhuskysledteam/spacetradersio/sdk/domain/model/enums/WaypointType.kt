package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

// The type of a waypoint in the universe. Determines what activities are available there
// (e.g., only PLANET/MOON/ORBITAL_STATION waypoints typically have markets or shipyards).
enum class WaypointType {
    PLANET,
    GAS_GIANT,
    MOON,
    ORBITAL_STATION,
    JUMP_GATE,
    ASTEROID_FIELD,
    ASTEROID,
    ENGINEERED_ASTEROID,
    ASTEROID_BASE,
    NEBULA,
    DEBRIS_FIELD,
    GRAVITY_WELL,
    ARTIFICIAL_GRAVITY_WELL,
    FUEL_STATION;

    companion object {
        // Safe fallback for unknown values returned by future API versions.
        fun fromString(value: String): WaypointType =
            entries.firstOrNull { it.name == value } ?: PLANET
    }
}
