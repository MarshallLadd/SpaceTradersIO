package com.brokenhuskysledteam.spacetradersio.sdk.api.mapper

// Mapper: MarketDto → Market (and nested trade-good sub-types).
// Reuses MarketTransactionDto.toDomain() (MarketTransactionMapper.kt) for the transactions list.

import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTradeGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.api.dto.TradeGoodDto
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTradeGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.TradeGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel

/**
 * Maps this [MarketDto] to a [Market] domain model.
 *
 * **Pattern:** Recursive mapper tree. Each catalogue list and the live-pricing list delegate
 * to their own leaf mappers; the transactions list reuses the existing
 * [com.brokenhuskysledteam.spacetradersio.sdk.api.dto.MarketTransactionDto] mapper so trade
 * records convert identically wherever they appear (market view, refuel, buy/sell).
 *
 * @return The [Market] domain model with all catalogue and pricing sub-objects converted.
 */
fun MarketDto.toDomain(): Market = Market(
    symbol = symbol,
    imports = imports.map { it.toDomain() },
    exports = exports.map { it.toDomain() },
    exchange = exchange.map { it.toDomain() },
    tradeGoods = tradeGoods.map { it.toDomain() },
    transactions = transactions.map { it.toDomain() }
)

/**
 * Maps this [TradeGoodDto] to a [TradeGood] domain model. Leaf mapper — plain field pass-through.
 */
fun TradeGoodDto.toDomain(): TradeGood = TradeGood(
    symbol = symbol,
    name = name,
    description = description
)

/**
 * Maps this [MarketTradeGoodDto] to a [MarketTradeGood] domain model.
 *
 * Converts the raw [type] and [supply] strings to their typed enums via safe `fromString`
 * parsing (unknown values fall back rather than throwing). [activity] stays a nullable string —
 * it is absent for `EXCHANGE` goods and the set of values may grow.
 */
fun MarketTradeGoodDto.toDomain(): MarketTradeGood = MarketTradeGood(
    symbol = symbol,
    type = MarketTradeGoodType.fromString(type),
    tradeVolume = tradeVolume,
    supply = SupplyLevel.fromString(supply),
    purchasePrice = purchasePrice,
    sellPrice = sellPrice,
    activity = activity
)
