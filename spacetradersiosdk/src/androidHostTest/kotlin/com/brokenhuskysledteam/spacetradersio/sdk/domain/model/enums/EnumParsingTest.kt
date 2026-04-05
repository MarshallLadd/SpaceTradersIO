package com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums

import kotlin.test.Test
import kotlin.test.assertEquals

class EnumParsingTest {

    // ── FactionSymbol ────────────────────────────────────────────────────────

    @Test
    fun factionSymbol_knownValue_returnsCorrectEntry() {
        assertEquals(FactionSymbol.VOID, FactionSymbol.fromString("VOID"))
    }

    @Test
    fun factionSymbol_unknownValue_fallsBackToCosmic() {
        assertEquals(FactionSymbol.COSMIC, FactionSymbol.fromString("FUTURE_FACTION"))
    }

    @Test
    fun factionSymbol_caseSensitive_unknownLowercaseFallsBack() {
        assertEquals(FactionSymbol.COSMIC, FactionSymbol.fromString("cosmic"))
    }

    // ── ContractType ─────────────────────────────────────────────────────────

    @Test
    fun contractType_knownValue_returnsCorrectEntry() {
        assertEquals(ContractType.TRANSPORT, ContractType.fromString("TRANSPORT"))
    }

    @Test
    fun contractType_unknownValue_fallsBackToProcurement() {
        assertEquals(ContractType.PROCUREMENT, ContractType.fromString("UNKNOWN_TYPE"))
    }

    // ── ShipNavStatus ────────────────────────────────────────────────────────

    @Test
    fun shipNavStatus_knownValue_returnsCorrectEntry() {
        assertEquals(ShipNavStatus.IN_TRANSIT, ShipNavStatus.fromString("IN_TRANSIT"))
    }

    @Test
    fun shipNavStatus_unknownValue_fallsBackToDocked() {
        assertEquals(ShipNavStatus.DOCKED, ShipNavStatus.fromString("UNKNOWN_STATUS"))
    }

    // ── ShipNavFlightMode ────────────────────────────────────────────────────

    @Test
    fun shipNavFlightMode_knownValue_returnsCorrectEntry() {
        assertEquals(ShipNavFlightMode.BURN, ShipNavFlightMode.fromString("BURN"))
    }

    @Test
    fun shipNavFlightMode_unknownValue_fallsBackToCruise() {
        assertEquals(ShipNavFlightMode.CRUISE, ShipNavFlightMode.fromString("FUTURE_MODE"))
    }

    // ── ShipRole ─────────────────────────────────────────────────────────────

    @Test
    fun shipRole_knownValue_returnsCorrectEntry() {
        assertEquals(ShipRole.EXCAVATOR, ShipRole.fromString("EXCAVATOR"))
    }

    @Test
    fun shipRole_unknownValue_fallsBackToCommand() {
        assertEquals(ShipRole.COMMAND, ShipRole.fromString("UNKNOWN_ROLE"))
    }

    // ── WaypointType ─────────────────────────────────────────────────────────

    @Test
    fun waypointType_knownValue_returnsCorrectEntry() {
        assertEquals(WaypointType.JUMP_GATE, WaypointType.fromString("JUMP_GATE"))
    }

    @Test
    fun waypointType_unknownValue_fallsBackToPlanet() {
        assertEquals(WaypointType.PLANET, WaypointType.fromString("UNKNOWN_TYPE"))
    }
}
