package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

// The current flight mode of a ship — controls speed vs fuel consumption trade-off.
// CRUISE is the default. BURN is fastest but uses more fuel.
enum class ShipNavFlightMode {
    DRIFT,   // Slowest, minimum fuel consumption
    STEALTH, // Reduced sensor profile at the cost of speed
    CRUISE,  // Balanced default
    BURN;    // Fastest, highest fuel consumption

    companion object {
        // Safe fallback for unknown values returned by future API versions.
        fun fromString(value: String): ShipNavFlightMode =
            entries.firstOrNull { it.name == value } ?: CRUISE
    }
}
