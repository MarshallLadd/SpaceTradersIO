package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.sdk.api.mapper.toDomain
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MARKET_RESPONSE = """
{
  "data": {
    "symbol": "X1-DM91-A1",
    "imports": [{"symbol": "FOOD", "name": "Galactic Cuisine", "description": "Food."}],
    "exports": [],
    "exchange": [{"symbol": "FUEL", "name": "Fuel", "description": "Fuel."}],
    "tradeGoods": [
      {"symbol": "FOOD", "type": "IMPORT", "tradeVolume": 60, "supply": "SCARCE", "purchasePrice": 4826, "sellPrice": 2388, "activity": "WEAK"},
      {"symbol": "FUEL", "type": "EXCHANGE", "tradeVolume": 180, "supply": "MODERATE", "purchasePrice": 72, "sellPrice": 68}
    ],
    "transactions": []
  }
}
"""

private const val TRADE_RESPONSE = """
{
  "data": {
    "cargo": {"capacity": 40, "units": 1, "inventory": [{"symbol": "FOOD", "name": "Galactic Cuisine", "description": "Food.", "units": 1}]},
    "transaction": {"waypointSymbol": "X1-DM91-A1", "shipSymbol": "LADD-1", "tradeSymbol": "FOOD", "type": "PURCHASE", "units": 1, "pricePerUnit": 4826, "totalPrice": 4826, "timestamp": "2026-07-06T15:47:49.153Z"},
    "agent": {"symbol": "LADD", "headquarters": "X1-DM91-A1", "credits": 170174, "startingFaction": "COSMIC", "shipCount": 2}
  }
}
"""

class MarketApiImplTest {

    private fun apiRespondingWith(
        body: String,
        capture: MutableList<HttpRequestData> = mutableListOf()
    ): Pair<MarketApiImpl, MutableList<HttpRequestData>> {
        val api = MarketApiImpl(buildMockSpaceTradersClient { request ->
            capture.add(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        })
        return api to capture
    }

    @Test
    fun getMarket_parsesTradeGoodsAndCatalogue() = runTest {
        val (api, _) = apiRespondingWith(MARKET_RESPONSE)
        val market = api.getMarket("X1-DM91", "X1-DM91-A1").toDomain()
        assertEquals("X1-DM91-A1", market.symbol)
        assertEquals(2, market.tradeGoods.size)
        assertEquals(4826, market.tradeGoods.first { it.symbol == "FOOD" }.purchasePrice)
        assertEquals(MarketTradeGoodType.EXCHANGE, market.tradeGoods.first { it.symbol == "FUEL" }.type)
        assertEquals(listOf("FOOD"), market.imports.map { it.symbol })
    }

    @Test
    fun getMarket_hitsCorrectEndpoint() = runTest {
        val (api, requests) = apiRespondingWith(MARKET_RESPONSE)
        api.getMarket("X1-DM91", "X1-DM91-A1")
        assertEquals(HttpMethod.Get, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("systems/X1-DM91/waypoints/X1-DM91-A1/market"))
    }

    @Test
    fun purchaseCargo_parsesResponseAndPostsToPurchase() = runTest {
        val (api, requests) = apiRespondingWith(TRADE_RESPONSE)
        val response = api.purchaseCargo("LADD-1", "FOOD", 1)
        assertEquals(1, response.cargo.units)
        assertEquals("FOOD", response.cargo.inventory.single().symbol)
        assertEquals(170174L, response.agent.credits)
        assertEquals(4826, response.transaction.totalPrice)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/purchase"))
    }

    @Test
    fun sellCargo_postsToSellEndpoint() = runTest {
        val (api, requests) = apiRespondingWith(TRADE_RESPONSE)
        api.sellCargo("LADD-1", "FOOD", 1)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertTrue(requests.single().url.encodedPath.endsWith("my/ships/LADD-1/sell"))
    }
}
