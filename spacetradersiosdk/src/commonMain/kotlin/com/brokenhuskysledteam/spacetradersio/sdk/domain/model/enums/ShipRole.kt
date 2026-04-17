package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * The registered operational role of a ship, which describes what it is optimized for.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new roles in future.
 *
 * **In this project:** Stored on [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship]
 * and displayed in the ship list and detail screens. The role is assigned at ship purchase time
 * and does not change — it is informational metadata, not a runtime state.
 *
 * **Fallback:** Unrecognized API strings resolve to [COMMAND].
 */
enum class ShipRole {
    /** Manufactures goods from raw or refined inputs. */
    FABRICATOR,

    /** Collects raw resources (e.g., gases, organic materials) from the environment. */
    HARVESTER,

    /** Transports cargo between waypoints; optimized for hold capacity over speed. */
    HAULER,

    /**
     * Fast combat ship designed to intercept and engage other vessels.
     * Prioritizes speed and weapons over cargo capacity.
     */
    INTERCEPTOR,

    /** Mines asteroids and other rocky bodies for raw ore and minerals. */
    EXCAVATOR,

    /**
     * General-purpose cargo transport. Similar to [HAULER] but typically smaller
     * or less specialized.
     */
    TRANSPORT,

    /** Repairs damage on other ships; carries repair drones or a maintenance crew. */
    REPAIR,

    /**
     * Surveys waypoints to identify the type and density of mineable resource deposits.
     * Produces survey data consumed by [EXCAVATOR] ships.
     */
    SURVEYOR,

    /**
     * Flagship or mission-control vessel for a fleet.
     * Often the player's first and most capable general-purpose ship.
     */
    COMMAND,

    /** Transports other ships (e.g., fighters) between systems or waypoints. */
    CARRIER,

    /** Defensive or combat-oriented ship that guards waypoints or escorts fleets. */
    PATROL,

    /**
     * Observation platform. Typically stationary or slow-moving; used to monitor
     * a waypoint or region for intel.
     */
    SATELLITE,

    /** Surveys and charts unknown waypoints and systems. */
    EXPLORER,

    /** Processes raw ore or gases into refined commodities ready for sale or manufacturing. */
    REFINERY;

    companion object {
        /**
         * Parses [value] into the enum, returning [COMMAND] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): ShipRole =
            entries.firstOrNull { it.name == value } ?: COMMAND
    }
}
