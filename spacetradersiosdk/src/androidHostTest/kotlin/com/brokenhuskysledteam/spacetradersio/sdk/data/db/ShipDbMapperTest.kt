package com.brokenhuskysledteam.spacetradersio.sdk.data.db

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Cooldown
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipFuel
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNav
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRoute
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipNavRouteWaypoint
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipRegistration
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavFlightMode
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipRole
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ShipDbMapperTest {

    private val testShip = Ship(
        symbol = "LADD-1",
        registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
        nav = ShipNav(
            systemSymbol = "X1-DF55",
            waypointSymbol = "X1-DF55-20250Z",
            status = ShipNavStatus.DOCKED,
            flightMode = ShipNavFlightMode.CRUISE,
            route = ShipNavRoute(
                origin = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0),
                destination = ShipNavRouteWaypoint("X1-DF55-17335A", WaypointType.PLANET, "X1-DF55", -21, -16),
                departureTime = Instant.parse("2025-06-01T10:00:00Z"),
                arrivalTime = Instant.parse("2025-06-01T10:30:00Z")
            )
        ),
        cargo = ShipCargo(units = 5, capacity = 40),
        fuel = ShipFuel(current = 350, capacity = 400),
        frameName = "Shuttle Frame",
        cooldown = Cooldown(shipSymbol = "LADD-1", totalSeconds = 60, remainingSeconds = 30, expiration = Instant.parse("2025-06-01T10:01:00Z"))
    )

    @Test
    fun roundTrip_upsertThenSelect_returnsEqualShip() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(testShip, result)
    }

    @Test
    fun roundTrip_nullCooldownExpiration_preservesNull() {
        val db = createTestDatabase()
        val shipNoCooldown = testShip.copy(
            cooldown = Cooldown("LADD-1", 0, 0, null)
        )
        db.shipQueries.upsertShip(shipNoCooldown)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertNull(result.cooldown.expiration)
    }

    @Test
    fun roundTrip_multipleShips_selectAllReturnsAll() {
        val db = createTestDatabase()
        val ship2 = testShip.copy(symbol = "LADD-2", cooldown = testShip.cooldown.copy(shipSymbol = "LADD-2"))
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.upsertShip(ship2)
        val result = db.shipQueries.selectAllShips().executeAsList().map { it.toDomain() }
        assertEquals(2, result.size)
        assertEquals(setOf("LADD-1", "LADD-2"), result.map { it.symbol }.toSet())
    }

    @Test
    fun upsert_sameSymbol_replacesExisting() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val updated = testShip.copy(fuel = ShipFuel(200, 400))
        db.shipQueries.upsertShip(updated)
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(200, result.fuel.current)
    }

    @Test
    fun deleteAllShips_emptiesTable() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.deleteAllShips()
        val result = db.shipQueries.selectAllShips().executeAsList()
        assertEquals(0, result.size)
    }

    @Test
    fun updateShipNav_updatesOnlyNavFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        val newNav = testShip.nav.copy(status = ShipNavStatus.IN_ORBIT, waypointSymbol = "X1-DF55-30A")
        db.shipQueries.updateShipNav(newNav, "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(ShipNavStatus.IN_ORBIT, result.nav.status)
        assertEquals("X1-DF55-30A", result.nav.waypointSymbol)
        assertEquals(350, result.fuel.current) // fuel unchanged
    }

    @Test
    fun updateShipFuel_updatesOnlyFuelFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipFuel(fuel_current = 100, fuel_capacity = 400, symbol = "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(100, result.fuel.current)
        assertEquals(ShipNavStatus.DOCKED, result.nav.status) // nav unchanged
    }

    @Test
    fun updateShipCargo_updatesOnlyCargoFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipCargo(cargo_units = 20, cargo_capacity = 40, symbol = "LADD-1")
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(20, result.cargo.units)
        assertEquals(ShipNavStatus.DOCKED, result.nav.status) // nav unchanged
    }

    @Test
    fun updateShipCooldown_updatesOnlyCooldownFields() {
        val db = createTestDatabase()
        db.shipQueries.upsertShip(testShip)
        db.shipQueries.updateShipCooldown(
            cooldown_total_seconds = 0,
            cooldown_remaining_seconds = 0,
            cooldown_expiration = null,
            symbol = "LADD-1"
        )
        val result = db.shipQueries.selectShipBySymbol("LADD-1").executeAsOne().toDomain()
        assertEquals(0, result.cooldown.totalSeconds)
        assertNull(result.cooldown.expiration)
        assertEquals(350, result.fuel.current) // fuel unchanged
    }
}
