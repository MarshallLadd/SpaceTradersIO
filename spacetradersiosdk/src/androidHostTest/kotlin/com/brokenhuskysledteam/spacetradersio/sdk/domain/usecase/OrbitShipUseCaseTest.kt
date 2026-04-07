package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.state.FleetStateStore
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val ORBIT_RESPONSE = """
{
  "data": {
    "nav": {
      "systemSymbol": "X1-DF55",
      "waypointSymbol": "X1-DF55-20250Z",
      "route": {
        "destination": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "origin": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "departureTime": "2025-06-01T10:00:00.000Z",
        "arrival": "2025-06-01T10:00:00.000Z"
      },
      "status": "IN_ORBIT",
      "flightMode": "CRUISE"
    }
  }
}
"""

private val testWaypoint = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
private val testRoute = ShipNavRoute(
    origin = testWaypoint, destination = testWaypoint,
    departureTime = Instant.parse("2025-06-01T10:00:00Z"),
    arrivalTime = Instant.parse("2025-06-01T10:00:00Z")
)
private val dockedNav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.DOCKED, ShipNavFlightMode.CRUISE, testRoute)
private fun testShip(symbol: String) = Ship(
    symbol = symbol,
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = dockedNav,
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown(symbol, 0, 0, null)
)

class OrbitShipUseCaseTest {

    private fun buildUseCase(store: FleetStateStore = FleetStateStore()) = OrbitShipUseCaseImpl(
        FleetApiImpl(buildMockSpaceTradersClient { respond(
            content = ORBIT_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}),
        store
    )

    @Test
    fun invoke_returnsNavWithInOrbitStatus() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals(ShipNavStatus.IN_ORBIT, nav.status)
    }

    @Test
    fun invoke_returnsNavWithCorrectWaypointSymbol() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals("X1-DF55-20250Z", nav.waypointSymbol)
    }

    @Test
    fun invoke_returnsNavWithCorrectSystemSymbol() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals("X1-DF55", nav.systemSymbol)
    }

    @Test
    fun invoke_updatesNavInStoreWhenShipExists() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip("LADD-1"))
        buildUseCase(store).invoke("LADD-1")
        assertEquals(ShipNavStatus.IN_ORBIT, store.entities.value["LADD-1"]?.nav?.status)
    }
}
