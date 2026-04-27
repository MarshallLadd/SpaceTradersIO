package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

import kotlin.test.Test
import kotlin.test.assertEquals

class ShipTypeTest {
    @Test
    fun fromString_knownValue_returnsCorrectEnum() {
        assertEquals(ShipType.SHIP_MINING_DRONE, ShipType.fromString("SHIP_MINING_DRONE"))
    }

    @Test
    fun fromString_unknownValue_returnsDefaultShipProbe() {
        assertEquals(ShipType.SHIP_PROBE, ShipType.fromString("SHIP_UNKNOWN_FUTURE_TYPE"))
    }

    @Test
    fun fromString_allKnownTypes_roundTrip() {
        ShipType.entries.forEach { type ->
            assertEquals(type, ShipType.fromString(type.name))
        }
    }
}

class SupplyLevelTest {
    @Test
    fun fromString_knownValue_returnsCorrectEnum() {
        assertEquals(SupplyLevel.ABUNDANT, SupplyLevel.fromString("ABUNDANT"))
    }

    @Test
    fun fromString_unknownValue_returnsModerate() {
        assertEquals(SupplyLevel.MODERATE, SupplyLevel.fromString("UNKNOWN_SUPPLY"))
    }
}
