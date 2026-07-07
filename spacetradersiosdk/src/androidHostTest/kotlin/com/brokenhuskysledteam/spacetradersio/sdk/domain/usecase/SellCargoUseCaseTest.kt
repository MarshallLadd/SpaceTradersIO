package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.MarketApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.AgentRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.data.repository.FleetRepositoryImpl
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipCargo
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

private const val SELL_RESPONSE = """
{
  "data": {
    "cargo": {"capacity": 40, "units": 0, "inventory": []},
    "transaction": {"waypointSymbol": "X1-DM91-A1", "shipSymbol": "LADD-1", "tradeSymbol": "IRON_ORE", "type": "SELL", "units": 5, "pricePerUnit": 50, "totalPrice": 250, "timestamp": "2026-07-06T15:47:49.153Z"},
    "agent": {"symbol": "LADD", "headquarters": "X1-DM91-A1", "credits": 175250, "startingFaction": "COSMIC", "shipCount": 2}
  }
}
"""

private fun buildMockSellApi() = MarketApiImpl(buildMockSpaceTradersClient {
    respond(SELL_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
})

class SellCargoUseCaseTest {

    @Test
    fun invoke_returnsSellTransactionAndUpdatedCredits() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        val result = SellCargoUseCaseImpl(buildMockSellApi(), fleetRepo, agentRepo)
            .invoke("LADD-1", "IRON_ORE", 5)
        assertEquals("SELL", result.transaction.type)
        assertEquals(250, result.transaction.totalPrice)
        assertEquals(175250L, result.agent.credits)
        assertEquals(0, result.cargo.units)
        assertTrue(result.cargo.inventory.isEmpty())
    }

    @Test
    fun invoke_emptiesShipCargoInDbAfterSellingAll() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        // Ship starts holding 5 IRON_ORE; selling all should leave an empty hold.
        fleetRepo.saveShip(
            tradeTestShip("LADD-1").let {
                it.copy(cargo = ShipCargo(5, 40, listOf(CargoItem("IRON_ORE", "Iron Ore", "Ore.", 5))))
            }
        )
        SellCargoUseCaseImpl(buildMockSellApi(), fleetRepo, agentRepo).invoke("LADD-1", "IRON_ORE", 5)
        val cargo = fleetRepo.observeShip("LADD-1").first()!!.cargo
        assertEquals(0, cargo.units)
        assertTrue(cargo.inventory.isEmpty())
    }

    @Test
    fun invoke_updatesAgentCreditsInDb() = runTest {
        val db = createTestDatabase()
        val fleetRepo = FleetRepositoryImpl(StubFleetApi, db, RefreshScheduler(backgroundScope))
        val agentRepo = AgentRepositoryImpl(FakeAgentsApiForTrade(), db)
        SellCargoUseCaseImpl(buildMockSellApi(), fleetRepo, agentRepo).invoke("LADD-1", "IRON_ORE", 5)
        assertEquals(175250L, agentRepo.observeAgent().first()?.credits)
    }
}
