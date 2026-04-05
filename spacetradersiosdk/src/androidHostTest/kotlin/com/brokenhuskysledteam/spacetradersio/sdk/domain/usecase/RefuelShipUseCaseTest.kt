package com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase

import com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints.FleetApiImpl
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

class RefuelShipUseCaseTest {

    private fun buildUseCase() = RefuelShipUseCaseImpl(
        FleetApiImpl(buildMockSpaceTradersClient { respond(
            content = REFUEL_RESPONSE,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )})
    )

    @Test
    fun invoke_returnsAgentWithCorrectCredits() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals(148500L, result.agent.credits)
    }

    @Test
    fun invoke_returnsAgentWithCorrectSymbol() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals("LADD", result.agent.symbol)
    }

    @Test
    fun invoke_returnsFuelAtCapacity() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals(400, result.fuel.current)
        assertEquals(400, result.fuel.capacity)
    }

    @Test
    fun invoke_returnsTransactionTotalPrice() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals(450, result.transaction.totalPrice)
    }

    @Test
    fun invoke_returnsTransactionUnits() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals(6, result.transaction.units)
    }

    @Test
    fun invoke_returnsTransactionTradeSymbol() = runTest {
        val result = buildUseCase().invoke("LADD-1")
        assertEquals("FUEL", result.transaction.tradeSymbol)
    }
}
