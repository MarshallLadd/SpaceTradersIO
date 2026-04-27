package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.FakeTokenRepository
import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SHIPYARD_RESPONSE_WITH_SHIPS = """
{
  "data": {
    "symbol": "X1-DF55-20250Z",
    "modificationsFee": 1000,
    "shipTypes": [{"type": "SHIP_MINING_DRONE"}],
    "ships": [
      {
        "type": "SHIP_MINING_DRONE",
        "name": "Mining Drone",
        "description": "Mines ore.",
        "purchasePrice": 50000,
        "supply": "MODERATE",
        "activity": "STRONG",
        "frame": {"symbol": "FRAME_DRONE", "name": "Drone Frame", "condition": 1.0, "integrity": 1.0, "moduleSlots": 1, "mountingPoints": 1, "fuelCapacity": 100, "requirements": {"power": 1, "crew": 0}},
        "engine": {"symbol": "ENGINE_ION_DRIVE_I", "name": "Ion Drive I", "condition": 1.0, "integrity": 1.0, "speed": 10, "requirements": {"power": 1, "crew": 0}},
        "reactor": {"symbol": "REACTOR_SOLAR_I", "name": "Solar Reactor I", "condition": 1.0, "integrity": 1.0, "powerOutput": 3, "requirements": {"crew": 0}},
        "crew": {"required": 0, "capacity": 0},
        "mounts": [],
        "modules": []
      }
    ]
  }
}
"""

private const val SHIPYARD_RESPONSE_NO_SHIPS = """
{
  "data": {
    "symbol": "X1-DF55-20250Z",
    "modificationsFee": 1000,
    "shipTypes": [{"type": "SHIP_MINING_DRONE"}]
  }
}
"""

private const val PURCHASE_SHIP_RESPONSE = """
{
  "data": {
    "ship": {
      "symbol": "LADD-2",
      "registration": {"name": "LADD-2", "factionSymbol": "COSMIC", "role": "EXCAVATOR"},
      "frame": {"symbol": "FRAME_DRONE", "name": "Drone Frame"},
      "nav": {
        "systemSymbol": "X1-DF55",
        "waypointSymbol": "X1-DF55-20250Z",
        "route": {
          "destination": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": 0, "y": 0},
          "origin": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": 0, "y": 0},
          "departureTime": "2025-06-01T10:00:00.000Z",
          "arrival": "2099-01-01T01:00:00.000Z"
        },
        "status": "DOCKED",
        "flightMode": "CRUISE"
      },
      "cargo": {"capacity": 15, "units": 0},
      "fuel": {"current": 100, "capacity": 100},
      "cooldown": {"shipSymbol": "LADD-2", "totalSeconds": 0, "remainingSeconds": 0}
    },
    "agent": {
      "accountId": "acc-abc",
      "symbol": "LADD",
      "headquarters": "X1-DF55-20250Z",
      "credits": 100000,
      "startingFaction": "COSMIC",
      "shipCount": 2
    },
    "transaction": {
      "waypointSymbol": "X1-DF55-20250Z",
      "shipType": "SHIP_MINING_DRONE",
      "price": 50000,
      "agentSymbol": "LADD",
      "timestamp": "2025-06-01T10:00:00.000Z"
    }
  }
}
"""

class ShipyardApiTest {

    private var lastRequestPath: String? = null
    private var lastRequestMethod: HttpMethod? = null
    private var lastRequestBody: String? = null
    private var lastAuthHeader: String? = null

    private fun buildApi(responseBody: String, statusCode: HttpStatusCode = HttpStatusCode.OK): ShipyardApi {
        val client = buildMockSpaceTradersClient(
            tokenRepository = FakeTokenRepository(storedToken = "agent-token")
        ) { request ->
            lastRequestPath = request.url.encodedPath
            lastRequestMethod = request.method
            lastRequestBody = (request.body as? OutgoingContent.ByteArrayContent)
                ?.bytes()?.decodeToString() ?: ""
            lastAuthHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = responseBody,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return ShipyardApiImpl(client)
    }

    @Test
    fun getShipyard_requestsCorrectPath() = runTest {
        buildApi(SHIPYARD_RESPONSE_WITH_SHIPS).getShipyard("X1-DF55", "X1-DF55-20250Z")
        assertEquals("/systems/X1-DF55/waypoints/X1-DF55-20250Z/shipyard", lastRequestPath)
    }

    @Test
    fun getShipyard_sendsAuthHeader() = runTest {
        buildApi(SHIPYARD_RESPONSE_WITH_SHIPS).getShipyard("X1-DF55", "X1-DF55-20250Z")
        assertEquals("Bearer agent-token", lastAuthHeader)
    }

    @Test
    fun getShipyard_withShips_returnsShipList() = runTest {
        val result = buildApi(SHIPYARD_RESPONSE_WITH_SHIPS).getShipyard("X1-DF55", "X1-DF55-20250Z")
        assertEquals("X1-DF55-20250Z", result.symbol)
        assertEquals(1000, result.modificationsFee)
        assertEquals(1, result.ships?.size)
        assertEquals("SHIP_MINING_DRONE", result.ships?.first()?.type)
    }

    @Test
    fun getShipyard_noShipsField_returnsNullShips() = runTest {
        val result = buildApi(SHIPYARD_RESPONSE_NO_SHIPS).getShipyard("X1-DF55", "X1-DF55-20250Z")
        assertNull(result.ships)
    }

    @Test
    fun purchaseShip_requestsCorrectPath() = runTest {
        buildApi(PURCHASE_SHIP_RESPONSE, HttpStatusCode.Created)
            .purchaseShip("SHIP_MINING_DRONE", "X1-DF55-20250Z")
        assertEquals("/my/ships", lastRequestPath)
        assertEquals(HttpMethod.Post, lastRequestMethod)
    }

    @Test
    fun purchaseShip_returnsNewShip() = runTest {
        val result = buildApi(PURCHASE_SHIP_RESPONSE, HttpStatusCode.Created)
            .purchaseShip("SHIP_MINING_DRONE", "X1-DF55-20250Z")
        assertEquals("LADD-2", result.ship.symbol)
        assertEquals(100000L, result.agent.credits)
        assertEquals(50000, result.transaction.price)
    }

    @Test
    fun purchaseShip_sendsCorrectRequestBody() = runTest {
        buildApi(PURCHASE_SHIP_RESPONSE, HttpStatusCode.Created)
            .purchaseShip("SHIP_MINING_DRONE", "X1-DF55-20250Z")
        assertTrue(lastRequestBody!!.contains("SHIP_MINING_DRONE"))
        assertTrue(lastRequestBody!!.contains("X1-DF55-20250Z"))
    }
}
