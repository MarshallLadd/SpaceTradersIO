package com.brokenhuskysledteam.spacetradersio.sdk.api.endpoints

import com.brokenhuskysledteam.spacetradersio.sdk.testing.buildMockSpaceTradersClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Shared JSON fixtures used across multiple tests.
private const val SHIP_JSON = """
{
  "symbol": "LADD-1",
  "registration": { "name": "LADD-1", "factionSymbol": "COSMIC", "role": "COMMAND" },
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
  },
  "crew": {"current":2,"required":1,"capacity":80,"rotation":"STRICT","morale":100,"wages":0},
  "frame": {"symbol":"FRAME_SHUTTLE","name":"Shuttle Frame","condition":1.0,"integrity":1.0,"description":"","moduleSlots":8,"mountingPoints":3,"fuelCapacity":400,"requirements":{},"quality":0},
  "reactor": {"symbol":"REACTOR_SOLAR_I","name":"Solar I","condition":1.0,"integrity":1.0,"description":"","powerOutput":3,"requirements":{},"quality":0},
  "engine": {"symbol":"ENGINE_IMPULSE_DRIVE_I","name":"Impulse I","condition":1.0,"integrity":1.0,"description":"","speed":2,"requirements":{},"quality":0},
  "cooldown": {"shipSymbol":"LADD-1","totalSeconds":0,"remainingSeconds":0},
  "modules": [],
  "mounts": [],
  "cargo": {"capacity":40,"units":0,"inventory":[]},
  "fuel": {"current":400,"capacity":400}
}
"""

private val SHIP_LIST_RESPONSE = """
{
  "data": [$SHIP_JSON],
  "meta": {"total":1,"page":1,"limit":10}
}
"""

private val SHIP_RESPONSE = """{"data": $SHIP_JSON}"""

private const val ORBIT_DOCK_RESPONSE = """
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
    "fuel": {"current":400,"capacity":400},
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

private fun MockRequestHandleScope.okJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

class FleetApiImplTest {

    // ── getMyShips ────────────────────────────────────────────────────────────

    @Test
    fun getMyShips_sendsGetRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(SHIP_LIST_RESPONSE)
        }
        FleetApiImpl(client).getMyShips()
        assertEquals(HttpMethod.Get, capturedMethod)
    }

    @Test
    fun getMyShips_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(SHIP_LIST_RESPONSE)
        }
        FleetApiImpl(client).getMyShips()
        assertEquals("/my/ships", capturedPath)
    }

    @Test
    fun getMyShips_sendsPaginationParams() = runTest {
        var capturedPage: String? = null
        var capturedLimit: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPage = request.url.parameters["page"]
            capturedLimit = request.url.parameters["limit"]
            okJson(SHIP_LIST_RESPONSE)
        }
        FleetApiImpl(client).getMyShips(page = 2, limit = 5)
        assertEquals("2", capturedPage)
        assertEquals("5", capturedLimit)
    }

    @Test
    fun getMyShips_returnsShipsWithCorrectSymbol() = runTest {
        val client = buildMockSpaceTradersClient { okJson(SHIP_LIST_RESPONSE) }
        val result = FleetApiImpl(client).getMyShips()
        assertEquals("LADD-1", result.data.first().symbol)
    }

    @Test
    fun getMyShips_returnsPaginationMeta() = runTest {
        val client = buildMockSpaceTradersClient { okJson(SHIP_LIST_RESPONSE) }
        val result = FleetApiImpl(client).getMyShips()
        assertEquals(1, result.meta.total)
    }

    // ── getMyShip ─────────────────────────────────────────────────────────────

    @Test
    fun getMyShip_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(SHIP_RESPONSE)
        }
        FleetApiImpl(client).getMyShip("LADD-1")
        assertEquals("/my/ships/LADD-1", capturedPath)
    }

    @Test
    fun getMyShip_returnsShipWithCorrectSymbol() = runTest {
        val client = buildMockSpaceTradersClient { okJson(SHIP_RESPONSE) }
        val result = FleetApiImpl(client).getMyShip("LADD-1")
        assertEquals("LADD-1", result.symbol)
    }

    @Test
    fun getMyShip_returnsShipWithCorrectFuelCurrent() = runTest {
        val client = buildMockSpaceTradersClient { okJson(SHIP_RESPONSE) }
        val result = FleetApiImpl(client).getMyShip("LADD-1")
        assertEquals(400, result.fuel.current)
    }

    // ── orbitShip ─────────────────────────────────────────────────────────────

    @Test
    fun orbitShip_sendsPostRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(ORBIT_DOCK_RESPONSE)
        }
        FleetApiImpl(client).orbitShip("LADD-1")
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    @Test
    fun orbitShip_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(ORBIT_DOCK_RESPONSE)
        }
        FleetApiImpl(client).orbitShip("LADD-1")
        assertEquals("/my/ships/LADD-1/orbit", capturedPath)
    }

    @Test
    fun orbitShip_returnsNavWithInOrbitStatus() = runTest {
        val client = buildMockSpaceTradersClient { okJson(ORBIT_DOCK_RESPONSE) }
        val result = FleetApiImpl(client).orbitShip("LADD-1")
        assertEquals("IN_ORBIT", result.status)
    }

    // ── dockShip ──────────────────────────────────────────────────────────────

    @Test
    fun dockShip_sendsPostRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(DOCK_RESPONSE)
        }
        FleetApiImpl(client).dockShip("LADD-1")
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    @Test
    fun dockShip_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(DOCK_RESPONSE)
        }
        FleetApiImpl(client).dockShip("LADD-1")
        assertEquals("/my/ships/LADD-1/dock", capturedPath)
    }

    @Test
    fun dockShip_returnsNavWithDockedStatus() = runTest {
        val client = buildMockSpaceTradersClient { okJson(DOCK_RESPONSE) }
        val result = FleetApiImpl(client).dockShip("LADD-1")
        assertEquals("DOCKED", result.status)
    }

    // ── refuelShip ────────────────────────────────────────────────────────────

    @Test
    fun refuelShip_sendsPostRequest() = runTest {
        var capturedMethod: HttpMethod? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedMethod = request.method
            okJson(REFUEL_RESPONSE)
        }
        FleetApiImpl(client).refuelShip("LADD-1")
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    @Test
    fun refuelShip_sendsToCorrectPath() = runTest {
        var capturedPath: String? = null
        val client = buildMockSpaceTradersClient { request ->
            capturedPath = request.url.encodedPath
            okJson(REFUEL_RESPONSE)
        }
        FleetApiImpl(client).refuelShip("LADD-1")
        assertEquals("/my/ships/LADD-1/refuel", capturedPath)
    }

    @Test
    fun refuelShip_returnsAgentWithUpdatedCredits() = runTest {
        val client = buildMockSpaceTradersClient { okJson(REFUEL_RESPONSE) }
        val result = FleetApiImpl(client).refuelShip("LADD-1")
        assertEquals(148500L, result.agent.credits)
    }

    @Test
    fun refuelShip_returnsFuelAtCapacity() = runTest {
        val client = buildMockSpaceTradersClient { okJson(REFUEL_RESPONSE) }
        val result = FleetApiImpl(client).refuelShip("LADD-1")
        assertEquals(400, result.fuel.current)
    }

    @Test
    fun refuelShip_returnsTransactionTotalPrice() = runTest {
        val client = buildMockSpaceTradersClient { okJson(REFUEL_RESPONSE) }
        val result = FleetApiImpl(client).refuelShip("LADD-1")
        assertEquals(450, result.transaction.totalPrice)
    }

    @Test
    fun refuelShip_returnsNullCargoWhenAbsent() = runTest {
        val client = buildMockSpaceTradersClient { okJson(REFUEL_RESPONSE) }
        val result = FleetApiImpl(client).refuelShip("LADD-1")
        assertNull(result.cargo)
    }
}
