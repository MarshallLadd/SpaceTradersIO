package com.brokenhuskysledteam.spacetradersio.sdk.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format representation of the SpaceTraders API `Market` schema, returned by
 * `GET /systems/{systemSymbol}/waypoints/{waypointSymbol}/market`.
 *
 * **Pattern:** Partial + conditional-field DTO. The catalogue fields ([imports], [exports],
 * [exchange]) are always present. The live-pricing fields ([tradeGoods]) and recent
 * [transactions] are **only** returned when the agent has a ship present at the waypoint —
 * otherwise the API omits them. Both are modelled with an `emptyList()` default so the
 * response deserializes whether or not a ship is present.
 *
 * @property symbol The waypoint symbol this market belongs to (e.g. `"X1-DM91-A1"`).
 * @property imports Goods the market imports (i.e. buys from you). See [TradeGoodDto].
 * @property exports Goods the market exports (i.e. sells to you). See [TradeGoodDto].
 * @property exchange Goods freely exchanged at the market (e.g. `FUEL`). See [TradeGoodDto].
 * @property tradeGoods Live per-good pricing/supply, present only when a ship is at the
 *   waypoint. See [MarketTradeGoodDto].
 * @property transactions Recent trade records at this market, present only when a ship is at
 *   the waypoint. See [MarketTransactionDto].
 */
@Serializable
data class MarketDto(
    val symbol: String,
    val imports: List<TradeGoodDto> = emptyList(),
    val exports: List<TradeGoodDto> = emptyList(),
    val exchange: List<TradeGoodDto> = emptyList(),
    val tradeGoods: List<MarketTradeGoodDto> = emptyList(),
    val transactions: List<MarketTransactionDto> = emptyList()
)

/**
 * A good listed in a market's import/export/exchange catalogue (no pricing).
 *
 * @property symbol The trade good's stable identifier (e.g. `"FOOD"`).
 * @property name The good's human-readable display name.
 * @property description A short description of the good.
 */
@Serializable
data class TradeGoodDto(
    val symbol: String,
    val name: String,
    val description: String
)

/**
 * Live pricing and supply for a single good at a market, present only when a ship is at the
 * waypoint.
 *
 * @property symbol The trade good's identifier (e.g. `"FOOD"`).
 * @property type The good's market role as a raw string: `"IMPORT"`, `"EXPORT"`, or
 *   `"EXCHANGE"`. The mapper converts this to the typed `MarketTradeGoodType` enum.
 * @property tradeVolume The maximum units tradeable in a single transaction at the current price.
 * @property supply The supply level as a raw string (`"SCARCE"`..`"ABUNDANT"`). The mapper
 *   converts this to the typed `SupplyLevel` enum.
 * @property purchasePrice The credits paid per unit to buy this good from the market.
 * @property sellPrice The credits received per unit to sell this good to the market.
 * @property activity The market activity level (`"WEAK"`, `"GROWING"`, `"STRONG"`,
 *   `"RESTRICTED"`). **Absent for `EXCHANGE` goods** (e.g. `FUEL`), hence nullable.
 */
@Serializable
data class MarketTradeGoodDto(
    val symbol: String,
    val type: String,
    val tradeVolume: Int,
    val supply: String,
    val purchasePrice: Int,
    val sellPrice: Int,
    val activity: String? = null
)

/**
 * Request body for `POST /my/ships/{shipSymbol}/purchase` and `.../sell`.
 *
 * @property symbol The trade good to buy or sell (e.g. `"FOOD"`).
 * @property units The number of units to trade.
 */
@Serializable
data class TradeCargoRequestDto(
    val symbol: String,
    val units: Int
)

/**
 * Response body for both `POST /my/ships/{shipSymbol}/purchase` and `.../sell`.
 *
 * **Pattern:** Multi-resource action response. A single trade touches three domain resources:
 * the ship's updated [cargo], the agent's new credit balance ([agent]), and the [transaction]
 * receipt. The use case distributes these to the fleet and agent repositories — mirroring the
 * refuel flow.
 *
 * @property cargo The ship's cargo hold after the trade, including the full item inventory.
 * @property transaction The completed trade record (price, units, timestamp).
 * @property agent The agent after the trade, with the updated credit balance.
 */
@Serializable
data class TradeCargoResponseDto(
    val cargo: ShipCargoDto,
    val transaction: MarketTransactionDto,
    val agent: AgentDto
)
