package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipCargoDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipFuelDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipNavDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ShipMapperTest {

    private fun minimalDto(navStatus: String = "DOCKED") = ShipDto(
        symbol = "COMMANDER-1",
        frame = ShipFrameSummaryDto(symbol = "FRAME_PROBE", name = "Probe Frame"),
        nav = ShipNavDto(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-20250Z",
            status = navStatus,
            flightMode = "CRUISE"
        ),
        cargo = ShipCargoDto(capacity = 40, units = 10),
        fuel = ShipFuelDto(current = 800, capacity = 1200)
    )

    @Test
    fun toDomain_allFieldsMapCorrectly() {
        val domain = minimalDto().toDomain()

        assertEquals("COMMANDER-1", domain.symbol)
        assertEquals("FRAME_PROBE", domain.frameSymbol)
        assertEquals("Probe Frame", domain.frameName)
        assertEquals("X1-DF55", domain.currentSystemSymbol)
        assertEquals("X1-DF55-20250Z", domain.currentWaypointSymbol)
        assertEquals(ShipNavStatus.DOCKED, domain.navStatus)
        assertEquals(10, domain.cargoUsed)
        assertEquals(40, domain.cargoCapacity)
        assertEquals(800, domain.fuelCurrent)
        assertEquals(1200, domain.fuelCapacity)
    }

    @Test
    fun toDomain_navStatusInOrbit_mapsCorrectly() {
        assertEquals(ShipNavStatus.IN_ORBIT, minimalDto("IN_ORBIT").toDomain().navStatus)
    }

    @Test
    fun toDomain_navStatusInTransit_mapsCorrectly() {
        assertEquals(ShipNavStatus.IN_TRANSIT, minimalDto("IN_TRANSIT").toDomain().navStatus)
    }

    @Test
    fun toDomain_unknownNavStatus_fallsBackToDocked() {
        assertEquals(ShipNavStatus.DOCKED, minimalDto("FUTURE_STATUS").toDomain().navStatus)
    }
}
