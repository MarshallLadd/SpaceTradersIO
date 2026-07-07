package com.brokenhuskysledteam.spacetradersio.sdk.domain.model

/**
 * The outcome of a buy or sell at a market — the three resources a single trade touches.
 *
 * **Pattern:** Multi-resource action result, mirroring [RefuelResult]. A trade updates the
 * ship's [cargo], the agent's credit balance ([agent]), and produces a [transaction] receipt.
 * The buy/sell use cases distribute [cargo] to the fleet repository and [agent] to the agent
 * repository, then return this to the ViewModel for a "trade complete" summary.
 *
 * @property agent The agent after the trade, with the updated credit balance.
 * @property cargo The ship's cargo hold after the trade, including the full item inventory.
 * @property transaction The completed trade record (good, units, price, timestamp).
 */
data class CargoTradeResult(
    val agent: Agent,
    val cargo: ShipCargo,
    val transaction: MarketTransaction
)
