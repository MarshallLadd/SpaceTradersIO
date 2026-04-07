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

private const val DOCK_RESPONSE = """
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
      "status": "DOCKED",
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
private val orbitNav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
private fun testShip(symbol: String) = Ship(
    symbol = symbol,
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = orbitNav,
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown(symbol, 0, 0, null)
)

class DockShipUseCaseTest {

    private fun buildUseCase(store: FleetStateStore = FleetStateStore()) = DockShipUseCaseImpl(
        FleetApiImpl(buildMockSpaceTradersClient { respond(
            content = DOCK_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )}),
        store
    )

    @Test
    fun invoke_returnsNavWithDockedStatus() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals(ShipNavStatus.DOCKED, nav.status)
    }

    @Test
    fun invoke_returnsNavWithCorrectWaypointSymbol() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals("X1-DF55-20250Z", nav.waypointSymbol)
    }

    @Test
    fun invoke_returnsNavWithCruiseFlightMode() = runTest {
        val nav = buildUseCase().invoke("LADD-1")
        assertEquals(ShipNavFlightMode.CRUISE, nav.flightMode)
    }

    @Test
    fun invoke_updatesNavInStoreWhenShipExists() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip("LADD-1"))
        buildUseCase(store).invoke("LADD-1")
        assertEquals(ShipNavStatus.DOCKED, store.entities.value["LADD-1"]?.nav?.status)
    }
}
