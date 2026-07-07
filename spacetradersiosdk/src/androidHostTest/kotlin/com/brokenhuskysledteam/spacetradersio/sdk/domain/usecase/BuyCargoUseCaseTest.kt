package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.AgentDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.AgentsApi
import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MarketApiImpl
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

private const val PURCHASE_RESPONSE = """
{
  "data": {
    "cargo": {"capacity": 40, "units": 3, "inventory": [{"symbol": "FOOD", "name": "Galactic Cuisine", "description": "Food.", "units": 3}]},
    "transaction": {"waypointSymbol": "X1-DM91-A1", "shipSymbol": "LADD-1", "tradeSymbol": "FOOD", "type": "PURCHASE", "units": 3, "pricePerUnit": 100, "totalPrice": 300, "timestamp": "2026-07-06T15:47:49.153Z"},
    "agent": {"symbol": "LADD", "headquarters": "X1-DM91-A1", "credits": 174700, "startingFaction": "COSMIC", "shipCount": 2}
  }
}
"""

internal class FakeAgentsApiForTrade : AgentsApi {
    override suspend fun getMyAgent(): AgentDto = throw UnsupportedOperationException()
    override suspend fun getAgent(symbol: String): AgentDto = throw UnsupportedOperationException()
}

private val tradeWaypoint = ShipNavRouteWaypoint("X1-DM91-A1", WaypointType.PLANET, "X1-DM91", 0, 0)
private val tradeRoute = ShipNavRoute(
    origin = tradeWaypoint, destination = tradeWaypoint,
    departureTime = Instant.parse("2025-06-01T10:00:00Z"),
    arrivalTime = Instant.parse("2099-01-01T01:00:00Z")
)
internal fun tradeTestShip(symbol: String) = Ship(
    symbol = symbol,
    registration = ShipRegistration(ShipRole.COMMAND, "COSMIC"),
    nav = ShipNav("X1-DM91", "X1-DM91-A1", ShipNavStatus.DOCKED, ShipNavFlightMode.CRUISE, tradeRoute),
    cargo = ShipCargo(0, 40),
    fuel = ShipFuel(400, 400),
    frameName = "Frigate",
    cooldown = Cooldown(symbol, 0, 0, null)
)

private fun buildMockTradeApi(body: String) = MarketApiImpl(buildMockSpaceTradersClient {
    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class BuyCargoUseCaseTest {

    @Test
    fun invoke_returnsTransactionAndAgentAndCargo() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        val result = BuyCargoUseCaseImpl(buildMockTradeApi(PURCHASE_RESPONSE), fleetRepo, agentRepo)
            .invoke("LADD-1", "FOOD", 3)
        assertEquals(300, result.transaction.totalPrice)
        assertEquals("PURCHASE", result.transaction.type)
        assertEquals(174700L, result.agent.credits)
        assertEquals(3, result.cargo.units)
        assertEquals("FOOD", result.cargo.inventory.single().symbol)
        assertEquals(3, result.cargo.inventory.single().units)
    }

    @Test
    fun invoke_updatesShipCargoInDbIncludingInventory() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        fleetRepo.saveShip(tradeTestShip("LADD-1"))
        BuyCargoUseCaseImpl(buildMockTradeApi(PURCHASE_RESPONSE), fleetRepo, agentRepo)
            .invoke("LADD-1", "FOOD", 3)
        val cargo = fleetRepo.observeShip("LADD-1").first()!!.cargo
        assertEquals(3, cargo.units)
        assertEquals(listOf("FOOD"), cargo.inventory.map { it.symbol })
    }

    @Test
    fun invoke_updatesAgentCreditsInDb() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        BuyCargoUseCaseImpl(buildMockTradeApi(PURCHASE_RESPONSE), fleetRepo, agentRepo)
            .invoke("LADD-1", "FOOD", 3)
        assertEquals(174700L, agentRepo.observeAgent().first()?.credits)
    }
}
