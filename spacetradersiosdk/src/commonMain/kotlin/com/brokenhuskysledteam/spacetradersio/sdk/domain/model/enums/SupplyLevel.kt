package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

enum class SupplyLevel {
    SCARCE,
    LIMITED,
    MODERATE,
    HIGH,
    ABUNDANT;

    companion object {
        fun fromString(value: String): SupplyLevel =
            entries.firstOrNull { it.name == value } ?: MODERATE
    }
}
