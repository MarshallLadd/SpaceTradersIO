package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

enum class ShipType {
    SHIP_PROBE,
    SHIP_MINING_DRONE,
    SHIP_SIPHON_DRONE,
    SHIP_INTERCEPTOR,
    SHIP_LIGHT_HAULER,
    SHIP_COMMAND_FRIGATE,
    SHIP_EXPLORER,
    SHIP_HEAVY_FREIGHTER,
    SHIP_LIGHT_SHUTTLE,
    SHIP_ORE_HOUND,
    SHIP_REFINING_FREIGHTER,
    SHIP_SURVEYOR,
    SHIP_BULK_FREIGHTER;

    companion object {
        fun fromString(value: String): ShipType =
            entries.firstOrNull { it.name == value } ?: SHIP_PROBE
    }
}
