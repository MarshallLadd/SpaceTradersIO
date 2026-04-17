package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * The category of a contract issued by the SpaceTraders API.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new contract types in future.
 *
 * **In this project:** Used in [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Contract]
 * to describe what the player is being asked to do. The contract UI can display a type-specific
 * label or icon by switching on this value.
 *
 * **Fallback:** Unrecognized API strings resolve to [PROCUREMENT] — the most common contract type.
 */
enum class ContractType {
    /** Collect a specific quantity of goods from any source and deliver them to a target waypoint. */
    PROCUREMENT,

    /** Pick up existing cargo from one waypoint and move it to another. */
    TRANSPORT,

    /** Transport passengers or personnel between waypoints. */
    SHUTTLE;

    companion object {
        /**
         * Parses [value] into the enum, returning [PROCUREMENT] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): ContractType =
            entries.firstOrNull { it.name == value } ?: PROCUREMENT
    }
}
