package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.ScanApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
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
import kotlin.test.assertTrue

private const val SCAN_SYSTEMS = """
{"data":{"cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"},
  "systems":[{"symbol":"X1-AB12","sectorSymbol":"X1","type":"RED_STAR","x":10,"y":20,"distance":42}]}}
"""
private const val SCAN_WAYPOINTS = """
{"data":{"cooldown":{"shipSymbol":"LADD-1","totalSeconds":60,"remainingSeconds":60,"expiration":"2099-01-01T00:00:00Z"},
  "waypoints":[{"symbol":"X1-DM91-C1","type":"PLANET","systemSymbol":"X1-DM91","x":3,"y":4,"traits":[]}]}}
"""

private fun scanApi(body: String) = ScanApiImpl(buildMockSpaceTradersClient {
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class ScanUseCaseTest {

    @Test
    fun scanSystems_returnsSystems_andUpdatesCooldown() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = ScanSystemsUseCaseImpl(scanApi(SCAN_SYSTEMS), fleet).invoke("LADD-1")

        assertEquals("X1-AB12", result.systems.single().symbol)
        assertEquals(60, fleet.observeShip("LADD-1").first()!!.cooldown.totalSeconds)
    }

    @Test
    fun scanWaypoints_returnsWaypoints_andUpdatesCooldown() = runTest {
        val db = createTestDatabase()
        val fleet = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        fleet.saveShip(tradeTestShip("LADD-1"))
        val result = ScanWaypointsUseCaseImpl(scanApi(SCAN_WAYPOINTS), fleet).invoke("LADD-1")

        assertTrue(result.waypoints.isNotEmpty())
        assertEquals("X1-DM91-C1", result.waypoints.single().symbol)
        assertEquals(60, fleet.observeShip("LADD-1").first()!!.cooldown.totalSeconds)
    }
}
