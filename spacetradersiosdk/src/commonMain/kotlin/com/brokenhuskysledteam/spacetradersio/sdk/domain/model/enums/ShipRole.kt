package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

// The registered operational role of a ship, which determines what tasks it is optimized for.
enum class ShipRole {
    FABRICATOR,
    HARVESTER,
    HAULER,
    INTERCEPTOR,
    EXCAVATOR,
    TRANSPORT,
    REPAIR,
    SURVEYOR,
    COMMAND,
    CARRIER,
    PATROL,
    SATELLITE,
    EXPLORER,
    REFINERY;

    companion object {
        // Safe fallback for unknown values returned by future API versions.
        fun fromString(value: String): ShipRole =
            entries.firstOrNull { it.name == value } ?: COMMAND
    }
}
