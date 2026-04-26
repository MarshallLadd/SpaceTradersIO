package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardCrewDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardEngineSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardFrameSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardReactorSummaryDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.ShipyardShipDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val minimalShipDto = ShipyardShipDto(
    type = "SHIP_MINING_DRONE",
    name = "Mining Drone",
    description = "A small mining vessel.",
    purchasePrice = 50000,
    supply = "MODERATE",
    frame = ShipyardFrameSummaryDto(name = "Drone Frame"),
    engine = ShipyardEngineSummaryDto(speed = 10),
    reactor = ShipyardReactorSummaryDto(powerOutput = 3),
    crew = ShipyardCrewDto(required = 0, capacity = 0)
)

class ShipyardMapperTest {

    @Test
    fun shipyardDto_withShips_mapsCorrectly() {
        val dto = ShipyardDto(
            symbol = "X1-DF55-20250Z",
            modificationsFee = 1000,
            ships = listOf(minimalShipDto)
        )
        val domain = dto.toDomain()
        assertEquals("X1-DF55-20250Z", domain.symbol)
        assertEquals(1000, domain.modificationsFee)
        assertEquals(1, domain.ships?.size)
    }

    @Test
    fun shipyardDto_nullShips_fogOfWar() {
        val dto = ShipyardDto(symbol = "X1-DF55-20250Z", modificationsFee = 500, ships = null)
        val domain = dto.toDomain()
        assertNull(domain.ships)
    }

    @Test
    fun shipyardDto_emptyShips_emptyList() {
        val dto = ShipyardDto(symbol = "X1-DF55-20250Z", modificationsFee = 500, ships = emptyList())
        val domain = dto.toDomain()
        assertEquals(emptyList(), domain.ships)
    }

    @Test
    fun shipyardShipDto_mapsAllFields() {
        val domain = minimalShipDto.toDomain()
        assertEquals(ShipType.SHIP_MINING_DRONE, domain.type)
        assertEquals("Mining Drone", domain.name)
        assertEquals("A small mining vessel.", domain.description)
        assertEquals(50000, domain.purchasePrice)
        assertEquals(SupplyLevel.MODERATE, domain.supply)
        assertEquals("Drone Frame", domain.frameName)
        assertEquals(10, domain.engineSpeed)
        assertEquals(3, domain.reactorPowerOutput)
        assertEquals(0, domain.crewRequired)
        assertEquals(0, domain.crewCapacity)
    }

    @Test
    fun shipyardShipDto_unknownType_defaultsToShipProbe() {
        val domain = minimalShipDto.copy(type = "FUTURE_SHIP_TYPE").toDomain()
        assertEquals(ShipType.SHIP_PROBE, domain.type)
    }

    @Test
    fun shipyardShipDto_unknownSupply_defaultsToModerate() {
        val domain = minimalShipDto.copy(supply = "UNKNOWN_SUPPLY").toDomain()
        assertEquals(SupplyLevel.MODERATE, domain.supply)
    }
}
