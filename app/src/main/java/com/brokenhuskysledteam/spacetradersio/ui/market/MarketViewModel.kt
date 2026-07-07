package com.brokenhuskysledteam.spacetradersio.ui.market

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.AgentRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.MarketRepository
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.BuyCargoUseCase
import com.brokenhuskysledteam.spacetradersio.sdk.domain.usecase.SellCargoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the market screen.
 *
 * **UDF:** Market data is read-through (not a reactive DB flow), so it is fetched once into
 * [LocalState.market]. The agent's credit balance IS reactive — it is combined from
 * [AgentRepository.observeAgent] so the displayed balance updates the instant a buy/sell use
 * case writes the new agent to the repository.
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val marketRepository: MarketRepository,
    private val agentRepository: AgentRepository,
    private val buyCargoUseCase: BuyCargoUseCase,
    private val sellCargoUseCase: SellCargoUseCase
) : ViewModel() {

    private val systemSymbol: String = checkNotNull(savedStateHandle["systemSymbol"])
    private val waypointSymbol: String = checkNotNull(savedStateHandle["waypointSymbol"])
    private val shipSymbol: String = checkNotNull(savedStateHandle["shipSymbol"])

    private val _localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<MarketUiState> = combine(
        agentRepository.observeAgent(),
        _localState
    ) { agent, local ->
        MarketUiState(
            waypointSymbol = waypointSymbol,
            market = local.market,
            credits = agent?.credits,
            isLoading = local.isLoading,
            isTradeInProgress = local.isTradeInProgress,
            pendingTrade = local.pendingTrade,
            tradeUnits = local.tradeUnits,
            tradeResult = local.tradeResult,
            error = local.error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MarketUiState(waypointSymbol = waypointSymbol))

    init {
        load()
    }

    fun onEvent(event: MarketEvent) {
        when (event) {
            is MarketEvent.RetryClicked -> load()
            is MarketEvent.TradeClicked ->
                _localState.update { it.copy(pendingTrade = PendingTrade(event.good, event.side), tradeUnits = "1") }
            is MarketEvent.TradeUnitsChanged ->
                _localState.update { it.copy(tradeUnits = event.units.filter { c -> c.isDigit() }) }
            is MarketEvent.TradeConfirmed -> confirmTrade()
            is MarketEvent.TradeDismissed ->
                _localState.update { it.copy(pendingTrade = null, tradeUnits = "") }
            is MarketEvent.TradeResultDismissed ->
                _localState.update { it.copy(tradeResult = null) }
        }
    }

    private fun load() {
        _localState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val market = marketRepository.getMarket(systemSymbol, waypointSymbol)
                _localState.update { it.copy(market = market, isLoading = false) }
            } catch (e: Exception) {
                _localState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load market") }
            }
        }
    }

    private fun confirmTrade() {
        val pending = _localState.value.pendingTrade ?: return
        val units = _localState.value.tradeUnits.toIntOrNull()?.takeIf { it > 0 } ?: return
        _localState.update { it.copy(isTradeInProgress = true, pendingTrade = null, tradeUnits = "") }
        viewModelScope.launch {
            try {
                val result = when (pending.side) {
                    TradeSide.BUY -> buyCargoUseCase(shipSymbol, pending.good.symbol, units)
                    TradeSide.SELL -> sellCargoUseCase(shipSymbol, pending.good.symbol, units)
                }
                _localState.update {
                    it.copy(
                        tradeResult = TradeResult.Success(
                            side = pending.side,
                            tradeSymbol = result.transaction.tradeSymbol,
                            units = result.transaction.units,
                            totalPrice = result.transaction.totalPrice,
                            newCredits = result.agent.credits
                        )
                    )
                }
                // Refresh market so prices/supply reflect the trade just made.
                runCatching { marketRepository.getMarket(systemSymbol, waypointSymbol) }
                    .onSuccess { fresh -> _localState.update { it.copy(market = fresh) } }
            } catch (e: Exception) {
                _localState.update { it.copy(tradeResult = TradeResult.Failure(e.message ?: "Trade failed")) }
            } finally {
                _localState.update { it.copy(isTradeInProgress = false) }
            }
        }
    }

    private data class LocalState(
        val market: Market? = null,
        val isLoading: Boolean = true,
        val isTradeInProgress: Boolean = false,
        val pendingTrade: PendingTrade? = null,
        val tradeUnits: String = "",
        val tradeResult: TradeResult? = null,
        val error: String? = null
    )
}
