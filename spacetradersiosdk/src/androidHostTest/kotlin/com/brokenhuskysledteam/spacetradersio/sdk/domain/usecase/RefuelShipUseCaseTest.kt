package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImpl
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
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val REFUEL_RESPONSE = """
{
  "data": {
    "agent": {
      "symbol": "LADD",
      "headquarters": "X1-DF55-20250Z",
      "credits": 148500,
      "startingFaction": "COSMIC",
      "shipCount": 2
    },
    "fuel": {"current": 400, "capacity": 400},
    "transaction": {
      "waypointSymbol": "X1-DF55-20250Z",
      "shipSymbol": "LADD-1",
      "tradeSymbol": "FUEL",
      "type": "PURCHASE",
      "units": 6,
      "pricePerUnit": 75,
      "totalPrice": 450,
      "timestamp": "2025-06-01T10:00:00.000Z"
    }
  }
}
"""

private val testWaypoint = ShipNavRouteWaypoint("X1-DF55-20250Z", WaypointType.MOON, "X1-DF55", 0, 0)
private val testRoute = ShipNavRoute(
    origin = testWaypoint, destination = testWaypoint,
    departureTime = Instant.parse("2025-06-01T10:00:00Z"),
    arrivalTime = Instant.parse("2099-01-01T01:00:00Z")
)
private val dockedNav = ShipNav("X1-DF55", "X1-DF55-20250Z", ShipNavStatus.DOCKED, ShipNavFlightMode.CRUISE, testRoute)
private fun testShip(symbol: String) = Ship(
    symbol = symbol,
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = dockedNav,
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(100, 400),   // starts with low fuel to verify update
    frameName = "Shuttle Frame",
    cooldown = Cooldown(symbol, 0, 0, null)
)

private class FakeAgentsApi : AgentsApi {
    override suspend fun getMyAgent(): AgentDto = throw UnsupportedOperationException()
    override suspend fun getAgent(symbol: String): AgentDto = throw UnsupportedOperationException()
}

private fun buildMockRefuelApi() = FleetApiImpl(buildMockSpaceTradersClient {
    respond(
        content = REFUEL_RESPONSE,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )
})

class RefuelShipUseCaseTest {

    @Test
    fun invoke_returnsAgentWithCorrectCredits() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(148500L, result.agent.credits)
    }

    @Test
    fun invoke_returnsAgentWithCorrectSymbol() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals("LADD", result.agent.symbol)
    }

    @Test
    fun invoke_returnsFuelAtCapacity() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(400, result.fuel.current)
        assertEquals(400, result.fuel.capacity)
    }

    @Test
    fun invoke_returnsTransactionTotalPrice() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(450, result.transaction.totalPrice)
    }

    @Test
    fun invoke_returnsTransactionUnits() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(6, result.transaction.units)
    }

    @Test
    fun invoke_returnsTransactionTradeSymbol() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        val result = RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals("FUEL", result.transaction.tradeSymbol)
    }

    @Test
    fun invoke_updatesFuelInDbWhenShipExists() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        fleetRepo.saveShip(testShip("LADD-1"))
        RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(400, fleetRepo.observeShip("LADD-1").first()?.fuel?.current)
    }

    @Test
    fun invoke_updatesAgentInDbWithNewCredits() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApi(), db)
        RefuelShipUseCaseImpl(buildMockRefuelApi(), fleetRepo, agentRepo).invoke("LADD-1")
        assertEquals(148500L, agentRepo.observeAgent().first()?.credits)
    }
}
