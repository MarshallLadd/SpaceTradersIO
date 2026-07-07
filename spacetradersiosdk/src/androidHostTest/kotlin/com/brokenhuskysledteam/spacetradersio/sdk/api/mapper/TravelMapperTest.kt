package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.JumpGateDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.SystemDto
import kotlin.test.Test
import kotlin.test.assertEquals

class TravelMapperTest {

    @Test
    fun jumpGate_toDomain_mapsSymbolAndConnections() {
        val dto = JumpGateDto("X1-DM91-I52", listOf("X1-AB12-I10", "X1-CD34-I88"))
        val gate = dto.toDomain()
        assertEquals("X1-DM91-I52", gate.symbol)
        assertEquals(listOf("X1-AB12-I10", "X1-CD34-I88"), gate.connections)
    }

    @Test
    fun system_toDomain_mapsAllFields() {
        val dto = SystemDto(symbol = "X1-DM91", sectorSymbol = "X1", type = "RED_STAR", x = -120, y = 340)
        val system = dto.toDomain()
        assertEquals("X1-DM91", system.symbol)
        assertEquals("X1", system.sectorSymbol)
        assertEquals("RED_STAR", system.type)
        assertEquals(-120, system.x)
        assertEquals(340, system.y)
    }
}
