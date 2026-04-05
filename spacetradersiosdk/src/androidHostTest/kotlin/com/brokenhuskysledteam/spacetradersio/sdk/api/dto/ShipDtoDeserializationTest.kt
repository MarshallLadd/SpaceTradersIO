package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShipDtoDeserializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Full ship JSON as returned by GET /my/ships/{shipSymbol}.
    // Includes crew, frame internals, reactor, engine, modules, mounts — all
    // fields the DTO deliberately omits. Verifies ignoreUnknownKeys handles them.
    private val fullShipJson = """
        {
          "symbol": "TEST-1",
          "registration": {
            "name": "Test Ship One",
            "factionSymbol": "COSMIC",
            "role": "COMMAND"
          },
          "nav": {
            "systemSymbol": "X1-DF55",
            "waypointSymbol": "X1-DF55-20250Z",
            "route": {
              "destination": {
                "symbol": "X1-DF55-17335A",
                "type": "PLANET",
                "systemSymbol": "X1-DF55",
                "x": -21,
                "y": -16
              },
              "origin": {
                "symbol": "X1-DF55-20250Z",
                "type": "MOON",
                "systemSymbol": "X1-DF55",
                "x": -15,
                "y": 12
              },
              "departureTime": "2025-06-01T10:00:00.000Z",
              "arrival": "2025-06-01T10:30:00.000Z"
            },
            "status": "DOCKED",
            "flightMode": "CRUISE"
          },
          "crew": {
            "current": 2, "required": 1, "capacity": 80,
            "rotation": "STRICT", "morale": 100, "wages": 0
          },
          "frame": {
            "symbol": "FRAME_SHUTTLE", "name": "Shuttle Frame",
            "condition": 1.0, "integrity": 1.0,
            "description": "A shuttle frame.",
            "moduleSlots": 8, "mountingPoints": 3, "fuelCapacity": 400,
            "requirements": {"power": 1, "crew": 2, "slots": 0}, "quality": 0
          },
          "reactor": {
            "symbol": "REACTOR_SOLAR_I", "name": "Solar Reactor I",
            "condition": 1.0, "integrity": 1.0, "description": "Solar panels.",
            "powerOutput": 3, "requirements": {"power": 0, "crew": 0, "slots": 1}, "quality": 0
          },
          "engine": {
            "symbol": "ENGINE_IMPULSE_DRIVE_I", "name": "Impulse Drive I",
            "condition": 1.0, "integrity": 1.0, "description": "Basic drive.",
            "speed": 2, "requirements": {"power": 1, "crew": 1, "slots": 1}, "quality": 0
          },
          "cooldown": {
            "shipSymbol": "TEST-1",
            "totalSeconds": 0,
            "remainingSeconds": 0
          },
          "modules": [],
          "mounts": [],
          "cargo": { "capacity": 40, "units": 10, "inventory": [] },
          "fuel": { "current": 340, "capacity": 400 }
        }
    """.trimIndent()

    @Test
    fun deserialize_shipSymbol() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("TEST-1", dto.symbol)
    }

    @Test
    fun deserialize_registration_role() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("COMMAND", dto.registration.role)
    }

    @Test
    fun deserialize_registration_factionSymbol() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("COSMIC", dto.registration.factionSymbol)
    }

    @Test
    fun deserialize_frameName() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("Shuttle Frame", dto.frame.name)
    }

    @Test
    fun deserialize_nav_systemSymbol() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("X1-DF55", dto.nav.systemSymbol)
    }

    @Test
    fun deserialize_nav_status() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("DOCKED", dto.nav.status)
    }

    @Test
    fun deserialize_nav_flightMode() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("CRUISE", dto.nav.flightMode)
    }

    // Critical: the API field is "arrival", not "arrivalTime".
    @Test
    fun deserialize_nav_route_arrivalFieldName() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("2025-06-01T10:30:00.000Z", dto.nav.route.arrival)
    }

    @Test
    fun deserialize_nav_route_departureTime() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("2025-06-01T10:00:00.000Z", dto.nav.route.departureTime)
    }

    @Test
    fun deserialize_nav_route_destination_type() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("PLANET", dto.nav.route.destination.type)
    }

    @Test
    fun deserialize_nav_route_origin_type() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("MOON", dto.nav.route.origin.type)
    }

    @Test
    fun deserialize_nav_route_destination_coordinates() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals(-21, dto.nav.route.destination.x)
        assertEquals(-16, dto.nav.route.destination.y)
    }

    @Test
    fun deserialize_fuel_current() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals(340, dto.fuel.current)
    }

    @Test
    fun deserialize_cargo_units() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals(10, dto.cargo.units)
    }

    @Test
    fun deserialize_cooldown_shipSymbol() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertEquals("TEST-1", dto.cooldown.shipSymbol)
    }

    @Test
    fun deserialize_cooldown_nullExpiration_whenAbsent() {
        val dto = json.decodeFromString<ShipDto>(fullShipJson)
        assertNull(dto.cooldown.expiration)
    }

    // Verifies that all the fields the DTO deliberately ignores (crew, reactor,
    // engine, modules, mounts, and frame internals) do not cause deserialization to fail.
    @Test
    fun deserialize_ignoresUnknownTopLevelFields() {
        val jsonWithExtra = fullShipJson.replace(
            "\"mounts\": [],",
            "\"mounts\": [], \"unknownField\": \"should be ignored\","
        )
        val dto = json.decodeFromString<ShipDto>(jsonWithExtra)
        assertEquals("TEST-1", dto.symbol)
    }
}
