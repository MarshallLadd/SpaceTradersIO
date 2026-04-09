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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val NOW = Instant.parse("2025-06-01T10:00:00.000Z")

private val testWaypoint = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
private val testRoute = ShipNavRoute(
    origin = testWaypoint, destination = testWaypoint,
    departureTime = NOW, arrivalTime = NOW
)

private fun testShip(status: ShipNavStatus) = Ship(
    symbol = "LADD-1",
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = ShipNav("X1-DF55", "X1-DF55-20250Z", status, ShipNavFlightMode.CRUISE, testRoute),
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Shuttle Frame",
    cooldown = Cooldown("LADD-1", 0, 0, null)
)

private const val NAVIGATE_RESPONSE = """
{
  "data": {
    "nav": {
      "systemSymbol": "X1-DF55",
      "waypointSymbol": "X1-DF55-17335A",
      "route": {
        "destination": {"symbol":"X1-DF55-17335A","type":"PLANET","systemSymbol":"X1-DF55","x":-21,"y":-16},
        "origin": {"symbol":"X1-DF55-20250Z","type":"MOON","systemSymbol":"X1-DF55","x":0,"y":0},
        "departureTime": "2025-06-01T10:00:00.000Z",
        "arrival": "2025-06-01T10:30:00.000Z"
      },
      "status": "IN_TRANSIT",
      "flightMode": "CRUISE"
    },
    "fuel": {"current": 350, "capacity": 400},
    "events": []
  }
}
"""

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

class NavigateShipUseCaseTest {

    private var orbitInvoked = false

    private fun buildUseCase(
        store: FleetStateStore = FleetStateStore(),
        fakeOrbit: OrbitShipUseCase = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav {
                orbitInvoked = true
                val nav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
                store.update(shipSymbol) { it.copy(nav = nav) }
                return nav
            }
        }
    ): Pair<NavigateShipUseCaseImpl, FleetStateStore> {
        val client = buildMockSpaceTradersClient { okJson(NAVIGATE_RESPONSE) }
        val fleetApi = FleetApiImpl(client)
        return NavigateShipUseCaseImpl(fleetApi, store, fakeOrbit) to store
    }

    @Test
    fun invoke_shipInOrbit_navigatesDirectly() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
        assertEquals(false, orbitInvoked)
    }

    @Test
    fun invoke_shipDocked_callsOrbitFirst() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.DOCKED))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(true, orbitInvoked)
    }

    @Test
    fun invoke_returnsNavigateResult() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals("X1-DF55-17335A", result.nav.waypointSymbol)
        assertEquals(350, result.fuel.current)
    }

    @Test
    fun invoke_updatesFleetStoreNavStatus() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, store.entities.value["LADD-1"]?.nav?.status)
    }

    @Test
    fun invoke_updatesFleetStoreFuel() = runTest {
        val store = FleetStateStore()
        store.put("LADD-1", testShip(ShipNavStatus.IN_ORBIT))
        val (useCase, _) = buildUseCase(store)

        useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(350, store.entities.value["LADD-1"]?.fuel?.current)
    }

    @Test
    fun invoke_shipNotInStore_skipsOrbit_navigatesAnyway() = runTest {
        orbitInvoked = false
        val store = FleetStateStore()
        val (useCase, _) = buildUseCase(store)

        val result = useCase("LADD-1", "X1-DF55-17335A")

        assertEquals(false, orbitInvoked)
        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
    }
}
