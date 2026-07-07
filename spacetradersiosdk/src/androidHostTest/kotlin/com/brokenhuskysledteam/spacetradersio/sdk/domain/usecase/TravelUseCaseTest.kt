package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.TravelApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NAV = """
{"systemSymbol":"X1-AB12","waypointSymbol":"X1-AB12-I10","status":"IN_ORBIT","flightMode":"CRUISE",
 "route":{"destination":{"symbol":"X1-AB12-I10","type":"JUMP_GATE","systemSymbol":"X1-AB12","x":5,"y":5},
          "origin":{"symbol":"X1-DM91-I52","type":"JUMP_GATE","systemSymbol":"X1-DM91","x":0,"y":0},
          "departureTime":"2026-07-06T00:00:00.000Z","arrival":"2026-07-06T00:00:00.000Z"}}
"""
private val WARP_RESPONSE = """{"data":{"nav":$NAV,"fuel":{"current":120,"capacity":400}}}"""
private val JUMP_RESPONSE = """{"data":{"nav":$NAV,"cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"}}}"""

private fun travelApi(body: String) = TravelApiImpl(buildMockSpaceTradersClient {
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class TravelUseCaseTest {

    @Test
    fun warp_updatesShipNavAndFuel() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = WarpShipUseCaseImpl(travelApi(WARP_RESPONSE), fleet).invoke("LADD-1", "X1-AB12-I10")

        assertEquals("X1-AB12", result.nav.systemSymbol)
        val ship = fleet.observeShip("LADD-1").first()!!
        assertEquals("X1-AB12", ship.nav.systemSymbol)
        assertEquals(ShipNavStatus.IN_ORBIT, ship.nav.status)
        assertEquals(120, ship.fuel.current)
    }

    @Test
    fun jump_updatesShipNavAndCooldown() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = JumpShipUseCaseImpl(travelApi(JUMP_RESPONSE), fleet).invoke("LADD-1", "X1-AB12-I10")

        assertEquals(60, result.cooldown.totalSeconds)
        val ship = fleet.observeShip("LADD-1").first()!!
        assertEquals("X1-AB12", ship.nav.systemSymbol)
        assertEquals(60, ship.cooldown.totalSeconds)
    }
}
