package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FleetResponseDtoDeserializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── OrbitDockResponseDto ──────────────────────────────────────────────────

    @Test
    fun orbitDockResponse_deserialize_navStatus() {
        val body = """
            {
              "nav": {
                "systemSymbol": "X1-DF55",
                "waypointSymbol": "X1-DF55-20250Z",
                "route": {
                  "destination": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": -15, "y": 12},
                  "origin": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": -15, "y": 12},
                  "departureTime": "2025-06-01T10:00:00.000Z",
                  "arrival": "2025-06-01T10:00:00.000Z"
                },
                "status": "IN_ORBIT",
                "flightMode": "CRUISE"
              }
            }
        """.trimIndent()
        val dto = json.decodeFromString<OrbitDockResponseDto>(body)
        assertEquals("IN_ORBIT", dto.nav.status)
    }

    @Test
    fun orbitDockResponse_deserialize_waypointSymbol() {
        val body = """
            {
              "nav": {
                "systemSymbol": "X1-DF55",
                "waypointSymbol": "X1-DF55-20250Z",
                "route": {
                  "destination": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": 0, "y": 0},
                  "origin": {"symbol": "X1-DF55-20250Z", "type": "MOON", "systemSymbol": "X1-DF55", "x": 0, "y": 0},
                  "departureTime": "2025-06-01T10:00:00.000Z",
                  "arrival": "2025-06-01T10:00:00.000Z"
                },
                "status": "DOCKED",
                "flightMode": "CRUISE"
              }
            }
        """.trimIndent()
        val dto = json.decodeFromString<OrbitDockResponseDto>(body)
        assertEquals("X1-DF55-20250Z", dto.nav.waypointSymbol)
    }

    // ── RefuelResponseDto ─────────────────────────────────────────────────────

    private val refuelResponseJson = """
        {
          "agent": {
            "symbol": "LADD",
            "headquarters": "X1-DF55-20250Z",
            "credits": 148500,
            "startingFaction": "COSMIC",
            "shipCount": 2
          },
          "fuel": {
            "current": 400,
            "capacity": 400,
            "consumed": {
              "amount": 0,
              "timestamp": "2025-06-01T10:00:00.000Z"
            }
          },
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
    """.trimIndent()

    @Test
    fun refuelResponse_deserialize_agentCredits() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals(148500L, dto.agent.credits)
    }

    @Test
    fun refuelResponse_deserialize_agentSymbol() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals("LADD", dto.agent.symbol)
    }

    @Test
    fun refuelResponse_deserialize_fuelCurrent() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals(400, dto.fuel.current)
    }

    @Test
    fun refuelResponse_deserialize_fuelCapacity() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals(400, dto.fuel.capacity)
    }

    @Test
    fun refuelResponse_deserialize_transactionTotalPrice() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals(450, dto.transaction.totalPrice)
    }

    @Test
    fun refuelResponse_deserialize_transactionUnits() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals(6, dto.transaction.units)
    }

    @Test
    fun refuelResponse_deserialize_transactionType() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertEquals("PURCHASE", dto.transaction.type)
    }

    @Test
    fun refuelResponse_deserialize_cargoIsNullWhenAbsent() {
        val dto = json.decodeFromString<RefuelResponseDto>(refuelResponseJson)
        assertNull(dto.cargo)
    }

    @Test
    fun refuelResponse_deserialize_cargoWhenPresent() {
        val withCargo = refuelResponseJson.replace(
            "\"transaction\":",
            "\"cargo\": {\"capacity\": 40, \"units\": 5, \"inventory\": []}, \"transaction\":"
        )
        val dto = json.decodeFromString<RefuelResponseDto>(withCargo)
        val cargo = assertNotNull(dto.cargo)
        assertEquals(5, cargo.units)
    }
}
