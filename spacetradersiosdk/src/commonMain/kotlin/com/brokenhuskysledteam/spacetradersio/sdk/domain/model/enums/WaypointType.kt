package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * The structural type of a waypoint, which determines what activities are available there
 * and how it fits into the system's geography.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new waypoint types in future.
 *
 * **In this project:** Displayed in the system map as the primary classification of each
 * waypoint. The type is combined with [WaypointTraitSymbol] entries to determine which
 * icons and action prompts appear in the UI.
 *
 * **Fallback:** Unrecognized API strings resolve to [PLANET].
 */
enum class WaypointType {
    /** A habitable or tradeable world; the most common location for markets and factions. */
    PLANET,

    /**
     * A large gas planet, often surrounded by orbital stations.
     * Typically has gas-harvesting or industrial activity rather than a surface market.
     */
    GAS_GIANT,

    /** A smaller body orbiting a planet; can host outposts and small markets. */
    MOON,

    /** A space station in orbit around a planet or other body; common trading hub. */
    ORBITAL_STATION,

    /**
     * An interstellar fast-travel hub.
     *
     * The only way to reach another star system without a ship-mounted jump drive.
     * Navigate to this waypoint (must be in orbit), then call the jump endpoint to
     * travel instantly to a connected system.
     */
    JUMP_GATE,

    /**
     * A dense region of asteroids — the primary mining zone in most systems.
     * Ships with [ShipRole.EXCAVATOR] role are optimized for working here.
     */
    ASTEROID_FIELD,

    /** An individual, discrete asteroid body; mineable like an [ASTEROID_FIELD] but smaller. */
    ASTEROID,

    /**
     * An asteroid that has been modified or built upon by players or factions.
     * May host unique facilities not found on natural asteroids.
     */
    ENGINEERED_ASTEROID,

    /** A station constructed on or inside an asteroid; can have markets and shipyards. */
    ASTEROID_BASE,

    /**
     * A stellar nebula region.
     * May contain unique resources or lore content; gameplay mechanics vary by nebula type.
     */
    NEBULA,

    /**
     * A field of wreckage and debris from destroyed ships or structures.
     * A scavenging zone — can yield salvageable materials.
     */
    DEBRIS_FIELD,

    /**
     * A natural gravitational anomaly.
     * Ships can become trapped here if they lack sufficient thrust to escape.
     * Navigate with caution.
     */
    GRAVITY_WELL,

    /**
     * A gravity well created by artificial means.
     * Behaves like [GRAVITY_WELL] but is player- or faction-constructed; may have tactical use.
     */
    ARTIFICIAL_GRAVITY_WELL,

    /**
     * A standalone refueling point, not attached to a full station.
     * Cheaper and faster than docking at a [PLANET] or [ORBITAL_STATION] for fuel only.
     */
    FUEL_STATION;

    companion object {
        /**
         * Parses [value] into the enum, returning [PLANET] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): WaypointType =
            entries.firstOrNull { it.name == value } ?: PLANET
    }
}
