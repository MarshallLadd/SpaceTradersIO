package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

/**
 * Controls the speed-versus-fuel trade-off for a ship's next navigation action.
 *
 * **Pattern:** Safe enum parsing. All enums sourced from API string values define a
 * [fromString] companion function that returns a sensible default instead of throwing on
 * unrecognized values. This prevents crashes when the API adds new flight modes in future.
 *
 * **In this project:** Stored on [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav]
 * and displayed in the ship detail UI. The player can change a ship's flight mode before
 * navigating; the choice persists until changed again.
 *
 * **Fallback:** Unrecognized API strings resolve to [CRUISE] — the balanced default mode.
 */
enum class ShipNavFlightMode {
    /**
     * Slowest travel speed with near-zero fuel consumption.
     *
     * Use when fuel reserves are critically low and reaching the destination matters more
     * than arrival time. The ship will still arrive — it just takes significantly longer.
     */
    DRIFT,

    /**
     * Reduced speed and fuel consumption; lowers the ship's sensor profile.
     *
     * Useful for avoiding detection by scan-heavy waypoints or hostile ships.
     * A middle ground between [DRIFT] and [CRUISE] in both speed and fuel cost.
     */
    STEALTH,

    /**
     * Balanced default mode — standard speed and standard fuel consumption.
     *
     * Appropriate for the majority of travel. Switch to [BURN] only when arrival time
     * is critical, or to [DRIFT]/[STEALTH] when conserving fuel.
     */
    CRUISE,

    /**
     * Maximum travel speed at the highest fuel cost.
     *
     * Use when time matters more than fuel — e.g., racing to a contract deadline or
     * responding to a time-sensitive market opportunity. Ensure the ship has enough
     * fuel to complete the journey before engaging BURN mode.
     */
    BURN;

    companion object {
        /**
         * Parses [value] into the enum, returning [CRUISE] for unrecognized strings.
         *
         * Prefer this over [enumValueOf] for API-sourced strings — [enumValueOf] throws
         * [IllegalArgumentException] on unknown values, which would crash on new API additions.
         */
        fun fromString(value: String): ShipNavFlightMode =
            entries.firstOrNull { it.name == value } ?: CRUISE
    }
}
