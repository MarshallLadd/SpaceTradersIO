package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val aShipyardShip = ShipyardShip(
    type = ShipType.SHIP_MINING_DRONE,
    name = "Mining Drone",
    description = "Mines ore.",
    purchasePrice = 50000,
    supply = SupplyLevel.MODERATE,
    frameName = "Drone Frame",
    engineSpeed = 10,
    reactorPowerOutput = 3,
    crewRequired = 0,
    crewCapacity = 0
)

class ShipyardDbMapperTest {

    @Test
    fun upsertAndRead_roundTripsAllFields() = runTest {
        val db = createTestDatabase()
        db.shipyardQueries.upsert(symbol = "X1-DF55-20250Z", modifications_fee = 1000L)
        db.shipyardShipQueries.upsert(waypoint_symbol = "X1-DF55-20250Z", ship = aShipyardShip)
        val dbRow = db.shipyardShipQueries.selectByWaypoint("X1-DF55-20250Z").executeAsList().first()
        val domain = dbRow.toDomain()
        assertEquals(ShipType.SHIP_MINING_DRONE, domain.type)
        assertEquals("Mining Drone", domain.name)
        assertEquals(50000, domain.purchasePrice)
        assertEquals(SupplyLevel.MODERATE, domain.supply)
        assertEquals("Drone Frame", domain.frameName)
        assertEquals(10, domain.engineSpeed)
        assertEquals(3, domain.reactorPowerOutput)
        assertEquals(0, domain.crewRequired)
        assertEquals(0, domain.crewCapacity)
    }

    @Test
    fun dbShipyard_withShips_toDomain_populatesList() = runTest {
        val db = createTestDatabase()
        db.shipyardQueries.upsert(symbol = "X1-DF55-20250Z", modifications_fee = 500L)
        db.shipyardShipQueries.upsert(waypoint_symbol = "X1-DF55-20250Z", ship = aShipyardShip)

        val meta = db.shipyardQueries.selectBySymbol("X1-DF55-20250Z").executeAsOneOrNull()!!
        val ships = db.shipyardShipQueries.selectByWaypoint("X1-DF55-20250Z")
            .executeAsList().map { it.toDomain() }

        val domain = meta.toDomain(ships)
        assertEquals("X1-DF55-20250Z", domain.symbol)
        assertEquals(500, domain.modificationsFee)
        assertEquals(1, domain.ships?.size)
    }

    @Test
    fun dbShipyard_emptyShips_toDomain_emptyList() = runTest {
        val db = createTestDatabase()
        db.shipyardQueries.upsert(symbol = "X1-DF55-20250Z", modifications_fee = 500L)
        val meta = db.shipyardQueries.selectBySymbol("X1-DF55-20250Z").executeAsOneOrNull()!!
        val domain = meta.toDomain(emptyList())
        assertEquals(emptyList(), domain.ships)
    }

    @Test
    fun dbShipyard_nullShips_toDomain_fogOfWar() = runTest {
        val db = createTestDatabase()
        db.shipyardQueries.upsert(symbol = "X1-DF55-20250Z", modifications_fee = 500L)
        val meta = db.shipyardQueries.selectBySymbol("X1-DF55-20250Z").executeAsOneOrNull()!!
        val domain = meta.toDomain(null)
        assertNull(domain.ships)
    }
}
