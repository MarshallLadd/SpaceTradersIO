package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

enum class ShipNavStatus {
    IN_TRANSIT,  // Ship is currently traveling between waypoints
    IN_ORBIT,    // Ship is orbiting a waypoint — can interact with it
    DOCKED;      // Ship is docked at a waypoint — can trade, refuel, etc.

    companion object {
        fun fromString(value: String): ShipNavStatus =
            entries.firstOrNull { it.name == value } ?: DOCKED
    }
}
