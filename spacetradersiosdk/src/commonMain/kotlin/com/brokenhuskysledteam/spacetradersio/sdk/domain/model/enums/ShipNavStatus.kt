package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * The current physical state of a ship's navigation, as reported by the API.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new status values in future.
 *
 * **In this project:** Stored on [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav]
 * and used throughout the UI and use-case layer to gate which actions are available. Many API
 * endpoints reject requests if the ship is in the wrong status — check this value before
 * calling navigation, trade, or action endpoints.
 *
 * **Fallback:** Unrecognized API strings resolve to [DOCKED].
 */
enum class ShipNavStatus {
    /**
     * The ship is actively moving between waypoints.
     *
     * Most gameplay actions (trading, mining, scanning, jumping) are unavailable until the
     * ship arrives at its destination. The ETA is available on the nav object's arrival time.
     * Only a small set of read-only actions are permitted in this state.
     */
    IN_TRANSIT,

    /**
     * The ship is in orbit around a waypoint.
     *
     * The ship can navigate to other waypoints, use a jump gate, or perform scans from this
     * state. It cannot trade, refuel, or repair — those require transitioning to [DOCKED].
     * Call the "orbit" endpoint to move a docked ship into this state.
     */
    IN_ORBIT,

    /**
     * The ship is docked at a waypoint.
     *
     * All station-based actions are available: buying/selling cargo, refueling, repairing the
     * hull, managing crew, and purchasing ship modules. The ship cannot navigate until it
     * transitions to [IN_ORBIT] via the "orbit" endpoint.
     */
    DOCKED;

    companion object {
        /**
         * Parses [value] into the enum, returning [DOCKED] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): ShipNavStatus =
            entries.firstOrNull { it.name == value } ?: DOCKED
    }
}
