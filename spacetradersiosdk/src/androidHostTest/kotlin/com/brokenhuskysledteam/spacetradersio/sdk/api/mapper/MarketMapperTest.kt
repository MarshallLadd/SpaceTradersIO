package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTradeGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.TradeGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketMapperTest {

    private fun good(symbol: String = "FOOD") =
        TradeGoodDto(symbol = symbol, name = "Galactic Cuisine", description = "Food.")

    private fun tradeGood(
        symbol: String = "FOOD",
        type: String = "IMPORT",
        supply: String = "SCARCE",
        activity: String? = "WEAK"
    ) = MarketTradeGoodDto(
        symbol = symbol,
        type = type,
        tradeVolume = 60,
        supply = supply,
        purchasePrice = 4826,
        sellPrice = 2388,
        activity = activity
    )

    private fun fullMarketDto() = MarketDto(
        symbol = "X1-DM91-A1",
        imports = listOf(good("FOOD")),
        exports = listOf(good("MACHINERY")),
        exchange = listOf(good("FUEL")),
        tradeGoods = listOf(tradeGood()),
        transactions = listOf(
            MarketTransactionDto(
                waypointSymbol = "X1-DM91-A1", shipSymbol = "CLD-1", tradeSymbol = "FOOD",
                type = "PURCHASE", units = 1, pricePerUnit = 4826, totalPrice = 4826,
                timestamp = "2026-07-06T15:47:49.153Z"
            )
        )
    )

    @Test
    fun market_toDomain_symbolMapsCorrectly() {
        assertEquals("X1-DM91-A1", fullMarketDto().toDomain().symbol)
    }

    @Test
    fun market_toDomain_catalogueListsMap() {
        val market = fullMarketDto().toDomain()
        assertEquals(listOf("FOOD"), market.imports.map { it.symbol })
        assertEquals(listOf("MACHINERY"), market.exports.map { it.symbol })
        assertEquals(listOf("FUEL"), market.exchange.map { it.symbol })
    }

    @Test
    fun tradeGood_toDomain_nameAndDescriptionMap() {
        val g = fullMarketDto().toDomain().imports.first()
        assertEquals("Galactic Cuisine", g.name)
        assertEquals("Food.", g.description)
    }

    @Test
    fun marketTradeGood_toDomain_pricesAndVolumeMap() {
        val tg = fullMarketDto().toDomain().tradeGoods.first()
        assertEquals(4826, tg.purchasePrice)
        assertEquals(2388, tg.sellPrice)
        assertEquals(60, tg.tradeVolume)
    }

    @Test
    fun marketTradeGood_toDomain_typeAndSupplyMapToEnums() {
        val tg = tradeGood(type = "EXPORT", supply = "ABUNDANT").toDomain()
        assertEquals(MarketTradeGoodType.EXPORT, tg.type)
        assertEquals(SupplyLevel.ABUNDANT, tg.supply)
    }

    @Test
    fun marketTradeGood_toDomain_unknownType_fallsBackToExchange() {
        assertEquals(MarketTradeGoodType.EXCHANGE, tradeGood(type = "FUTURE_TYPE").toDomain().type)
    }

    @Test
    fun marketTradeGood_toDomain_unknownSupply_fallsBackToModerate() {
        assertEquals(SupplyLevel.MODERATE, tradeGood(supply = "FUTURE_SUPPLY").toDomain().supply)
    }

    @Test
    fun marketTradeGood_toDomain_activityPreserved() {
        assertEquals("GROWING", tradeGood(activity = "GROWING").toDomain().activity)
    }

    @Test
    fun marketTradeGood_toDomain_nullActivityPreserved() {
        assertNull(tradeGood(activity = null).toDomain().activity)
    }

    @Test
    fun market_toDomain_transactionsMap() {
        val tx = fullMarketDto().toDomain().transactions.first()
        assertEquals("FOOD", tx.tradeSymbol)
        assertEquals(4826, tx.totalPrice)
    }

    @Test
    fun market_toDomain_emptyPricingWhenNoShipPresent() {
        val catalogueOnly = MarketDto(symbol = "X1-DM91-A1", imports = listOf(good()))
        val market = catalogueOnly.toDomain()
        assertTrue(market.tradeGoods.isEmpty())
        assertTrue(market.transactions.isEmpty())
    }
}
