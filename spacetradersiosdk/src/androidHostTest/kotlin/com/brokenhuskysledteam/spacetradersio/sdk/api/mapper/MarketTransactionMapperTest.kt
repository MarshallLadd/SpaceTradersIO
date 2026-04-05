package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MarketTransactionMapperTest {

    private val timestampStr = "2025-06-01T10:00:00.000Z"

    private fun dto() = MarketTransactionDto(
        waypointSymbol = "X1-DF55-20250Z",
        shipSymbol = "LADD-1",
        tradeSymbol = "FUEL",
        type = "PURCHASE",
        units = 6,
        pricePerUnit = 75,
        totalPrice = 450,
        timestamp = timestampStr
    )

    @Test
    fun toDomain_waypointSymbolMapsCorrectly() {
        assertEquals("X1-DF55-20250Z", dto().toDomain().waypointSymbol)
    }

    @Test
    fun toDomain_shipSymbolMapsCorrectly() {
        assertEquals("LADD-1", dto().toDomain().shipSymbol)
    }

    @Test
    fun toDomain_tradeSymbolMapsCorrectly() {
        assertEquals("FUEL", dto().toDomain().tradeSymbol)
    }

    @Test
    fun toDomain_typeMapsCorrectly() {
        assertEquals("PURCHASE", dto().toDomain().type)
    }

    @Test
    fun toDomain_unitsMapsCorrectly() {
        assertEquals(6, dto().toDomain().units)
    }

    @Test
    fun toDomain_pricePerUnitMapsCorrectly() {
        assertEquals(75, dto().toDomain().pricePerUnit)
    }

    @Test
    fun toDomain_totalPriceMapsCorrectly() {
        assertEquals(450, dto().toDomain().totalPrice)
    }

    @Test
    fun toDomain_timestampParsedToInstant() {
        assertEquals(Instant.parse(timestampStr), dto().toDomain().timestamp)
    }
}
