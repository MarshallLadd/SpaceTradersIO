package com.brokenhuskysledteam.spacetradersio.ui.market

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTradeGood

/**
 * Top-level UI state for the market screen (UDF).
 *
 * @property waypointSymbol The marketplace waypoint being shown.
 * @property market The fetched market, or `null` while loading / on error. Its `tradeGoods` list
 *   (live pricing) is populated only when the trading ship is present at the waypoint.
 * @property credits The agent's current credit balance (observed live), or `null` before loaded.
 * @property isLoading `true` during the initial market fetch.
 * @property isTradeInProgress `true` while a buy/sell request is executing.
 * @property pendingTrade A trade the user is configuring (choosing units) in the dialog, or `null`.
 * @property tradeResult Transient result of the last completed trade, or `null`.
 * @property error Human-readable error, or `null`.
 */
data class MarketUiState(
    val waypointSymbol: String = "",
    val market: Market? = null,
    val credits: Long? = null,
    val isLoading: Boolean = true,
    val isTradeInProgress: Boolean = false,
    val pendingTrade: PendingTrade? = null,
    val tradeUnits: String = "",
    val tradeResult: TradeResult? = null,
    val error: String? = null
)

/** The direction of a trade the user is configuring or has completed. */
enum class TradeSide { BUY, SELL }

/**
 * A trade the user has initiated from a good's Buy/Sell button and is configuring in the dialog.
 *
 * @property good The market good being traded.
 * @property side Whether the user is buying from or selling to the market.
 */
data class PendingTrade(
    val good: MarketTradeGood,
    val side: TradeSide
)

/** Transient feedback shown after a trade completes. */
sealed interface TradeResult {
    data class Success(
        val side: TradeSide,
        val tradeSymbol: String,
        val units: Int,
        val totalPrice: Int,
        val newCredits: Long
    ) : TradeResult

    data class Failure(val message: String) : TradeResult
}

/** User-initiated events for the market screen. */
sealed interface MarketEvent {
    data object RetryClicked : MarketEvent
    data class TradeClicked(val good: MarketTradeGood, val side: TradeSide) : MarketEvent
    data class TradeUnitsChanged(val units: String) : MarketEvent
    data object TradeConfirmed : MarketEvent
    data object TradeDismissed : MarketEvent
    data object TradeResultDismissed : MarketEvent
}
