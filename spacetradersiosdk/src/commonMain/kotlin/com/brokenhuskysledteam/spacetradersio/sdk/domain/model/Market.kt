package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.SupplyLevel

/**
 * A marketplace at a waypoint: its catalogue of tradeable goods and, when a ship is present,
 * live per-good pricing.
 *
 * **In this project:** Fetched by `MarketRepository.getMarket` and rendered on the market
 * screen. [tradeGoods] drives the buy/sell UI (prices, supply); it is empty when no ship is at
 * the waypoint, in which case only the catalogue ([imports]/[exports]/[exchange]) is shown.
 *
 * @property symbol The waypoint symbol this market belongs to.
 * @property imports Goods the market buys from you.
 * @property exports Goods the market sells to you.
 * @property exchange Goods freely exchanged (e.g. `FUEL`).
 * @property tradeGoods Live pricing/supply per good; empty unless a ship is at the waypoint.
 * @property transactions Recent trade records; empty unless a ship is at the waypoint.
 */
data class Market(
    val symbol: String,
    val imports: List<TradeGood>,
    val exports: List<TradeGood>,
    val exchange: List<TradeGood>,
    val tradeGoods: List<MarketTradeGood>,
    val transactions: List<MarketTransaction>
)

/**
 * A good in a market catalogue (identity only, no pricing).
 *
 * @property symbol The trade good's identifier (e.g. `"FOOD"`).
 * @property name Human-readable display name.
 * @property description Short description of the good.
 */
data class TradeGood(
    val symbol: String,
    val name: String,
    val description: String
)

/**
 * Live pricing and supply for a good at a market.
 *
 * @property symbol The trade good's identifier.
 * @property type The good's market role. See [MarketTradeGoodType].
 * @property tradeVolume Max units tradeable in one transaction at the current price.
 * @property supply The current supply level. See [SupplyLevel].
 * @property purchasePrice Credits paid per unit to buy from the market.
 * @property sellPrice Credits received per unit to sell to the market.
 * @property activity Market activity level, or `null` for `EXCHANGE` goods that have none.
 */
data class MarketTradeGood(
    val symbol: String,
    val type: MarketTradeGoodType,
    val tradeVolume: Int,
    val supply: SupplyLevel,
    val purchasePrice: Int,
    val sellPrice: Int,
    val activity: String?
)
