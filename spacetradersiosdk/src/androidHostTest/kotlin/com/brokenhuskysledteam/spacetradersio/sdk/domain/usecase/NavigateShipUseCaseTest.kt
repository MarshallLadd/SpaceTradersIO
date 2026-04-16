package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.scheduler.RefreshScheduler
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import com.brokenhuskysledteam.spacetradersio.sdk.testing.createTestDatabase
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

private val testWaypoint = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
private val testRoute = ShipNavRoute(
    origin = testWaypoint, destination = testWaypoint,
    departureTime = Instant.parse("2025-06-01T10:00:00Z"),
    arrivalTime = Instant.parse("2099-01-01T01:00:00Z")
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

private fun buildNavigateUseCase(
    repo: FleetRepositoryImpl,
    fakeOrbit: OrbitShipUseCase
): NavigateShipUseCaseImpl {
    val client = buildMockSpaceTradersClient { okJson(NAVIGATE_RESPONSE) }
    return NavigateShipUseCaseImpl(FleetApiImpl(client), repo, fakeOrbit)
}

class NavigateShipUseCaseTest {

    @Test
    fun invoke_shipInOrbit_navigatesDirectly() = runTest {
        var orbitInvoked = false
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.saveShip(testShip(ShipNavStatus.IN_ORBIT))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav {
                orbitInvoked = true
                val nav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
                repo.updateShipNav(shipSymbol, nav)
                return nav
            }
        }
        val result = buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
        assertEquals(false, orbitInvoked)
    }

    @Test
    fun invoke_shipDocked_callsOrbitFirst() = runTest {
        var orbitInvoked = false
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.saveShip(testShip(ShipNavStatus.DOCKED))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav {
                orbitInvoked = true
                val nav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
                repo.updateShipNav(shipSymbol, nav)
                return nav
            }
        }
        buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals(true, orbitInvoked)
    }

    @Test
    fun invoke_returnsNavigateResult() = runTest {
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.saveShip(testShip(ShipNavStatus.IN_ORBIT))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav =
                ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
        }
        val result = buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals("X1-DF55-17335A", result.nav.waypointSymbol)
        assertEquals(350, result.fuel.current)
    }

    @Test
    fun invoke_updatesNavInDb() = runTest {
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.saveShip(testShip(ShipNavStatus.IN_ORBIT))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav =
                ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
        }
        buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals(ShipNavStatus.IN_TRANSIT, repo.observeShip("LADD-1").first()?.nav?.status)
    }

    @Test
    fun invoke_updatesFuelInDb() = runTest {
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        repo.saveShip(testShip(ShipNavStatus.IN_ORBIT))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav =
                ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
        }
        buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals(350, repo.observeShip("LADD-1").first()?.fuel?.current)
    }

    @Test
    fun invoke_schedulesTransitRefreshTimer() = runTest {
        val scheduler = RefreshScheduler(backgroundScope)
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), scheduler)
        repo.saveShip(testShip(ShipNavStatus.IN_ORBIT))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav =
                ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
        }
        buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertNotNull(scheduler.activeTimers.value["transit:LADD-1"])
    }

    @Test
    fun invoke_shipNotInDb_skipsOrbit_navigatesAnyway() = runTest {
        var orbitInvoked = false
        val repo = FleetRepositoryImpl(StubFleetApi, createTestDatabase(), RefreshScheduler(backgroundScope))
        val fakeOrbit = object : OrbitShipUseCase {
            override suspend fun invoke(shipSymbol: String): ShipNav {
                orbitInvoked = true
                return ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.IN_ORBIT, ShipNavFlightMode.CRUISE, testRoute)
            }
        }
        val result = buildNavigateUseCase(repo, fakeOrbit).invoke("LADD-1", "X1-DF55-17335A")

        assertEquals(false, orbitInvoked)
        assertEquals(ShipNavStatus.IN_TRANSIT, result.nav.status)
    }
}
