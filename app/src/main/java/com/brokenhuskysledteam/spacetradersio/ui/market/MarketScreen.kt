package com.brokenhuskysledteam.spacetradersio.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Market
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.MarketTradeGood
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.MarketTradeGoodType
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalTextField

@Composable
fun MarketScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MarketScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

@Composable
fun MarketScreenContent(
    uiState: MarketUiState,
    onEvent: (MarketEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading && uiState.market == null ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.error != null && uiState.market == null ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    MarketHeader(uiState)
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "ERROR") {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "RETRY",
                            onClick = { onEvent(MarketEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

            uiState.market != null ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    MarketHeader(uiState)
                    Spacer(modifier = Modifier.height(16.dp))
                    MarketContent(
                        market = uiState.market,
                        isTradeInProgress = uiState.isTradeInProgress,
                        tradeResult = uiState.tradeResult,
                        onEvent = onEvent
                    )
                }
        }
        ScanlineOverlay()
    }

    val pending = uiState.pendingTrade
    if (pending != null) {
        TradeDialog(
            good = pending.good,
            side = pending.side,
            units = uiState.tradeUnits,
            onUnitsChange = { onEvent(MarketEvent.TradeUnitsChanged(it)) },
            onConfirm = { onEvent(MarketEvent.TradeConfirmed) },
            onDismiss = { onEvent(MarketEvent.TradeDismissed) }
        )
    }
}

@Composable
private fun MarketHeader(uiState: MarketUiState) {
    Text(
        text = "MARKET: ${uiState.waypointSymbol}",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "// CREDITS: ${uiState.credits ?: "----"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun MarketContent(
    market: Market,
    isTradeInProgress: Boolean,
    tradeResult: TradeResult?,
    onEvent: (MarketEvent) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tradeResult != null) {
            item {
                TerminalCard(title = "TRANSACTION RESULT") {
                    when (tradeResult) {
                        is TradeResult.Success -> Text(
                            text = "${tradeResult.side} ${tradeResult.units}x ${tradeResult.tradeSymbol} " +
                                "@ ${tradeResult.totalPrice} CR — BALANCE ${tradeResult.newCredits}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is TradeResult.Failure -> Text(
                            text = "TRADE FAILED: ${tradeResult.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(
                        text = "DISMISS",
                        onClick = { onEvent(MarketEvent.TradeResultDismissed) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (market.tradeGoods.isEmpty()) {
            item {
                TerminalCard(title = "NO LIVE PRICING") {
                    Text(
                        text = "DOCK A SHIP AT THIS WAYPOINT TO VIEW PRICES AND TRADE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "IMPORTS: ${market.imports.joinToString { it.symbol }.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "EXPORTS: ${market.exports.joinToString { it.symbol }.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(market.tradeGoods, key = { it.symbol }) { good ->
                TradeGoodCard(good = good, isTradeInProgress = isTradeInProgress, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun TradeGoodCard(
    good: MarketTradeGood,
    isTradeInProgress: Boolean,
    onEvent: (MarketEvent) -> Unit
) {
    TerminalCard(title = good.symbol) {
        MarketDataRow("TYPE", good.type.name)
        MarketDataRow("SUPPLY", good.supply.name)
        MarketDataRow("BUY", "${good.purchasePrice} CR")
        MarketDataRow("SELL", "${good.sellPrice} CR")
        good.activity?.let { MarketDataRow("ACTIVITY", it) }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Buy is possible for goods the market sells (EXPORT / EXCHANGE).
            if (good.type != MarketTradeGoodType.IMPORT) {
                TerminalButton(
                    text = "BUY",
                    enabled = !isTradeInProgress,
                    onClick = { onEvent(MarketEvent.TradeClicked(good, TradeSide.BUY)) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Sell is possible for goods the market buys (IMPORT / EXCHANGE).
            if (good.type != MarketTradeGoodType.EXPORT) {
                TerminalButton(
                    text = "SELL",
                    enabled = !isTradeInProgress,
                    onClick = { onEvent(MarketEvent.TradeClicked(good, TradeSide.SELL)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TradeDialog(
    good: MarketTradeGood,
    side: TradeSide,
    units: String,
    onUnitsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val unitPrice = if (side == TradeSide.BUY) good.purchasePrice else good.sellPrice
    val count = units.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "$side ${good.symbol}", color = MaterialTheme.colorScheme.primary) },
        text = {
            Column {
                Text(
                    text = "UNIT PRICE: $unitPrice CR  •  MAX/TXN: ${good.tradeVolume}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                TerminalTextField(
                    value = units,
                    onValueChange = onUnitsChange,
                    label = "UNITS"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ESTIMATED TOTAL: ${unitPrice * count} CR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = { TerminalButton(text = "CONFIRM", enabled = count > 0, onClick = onConfirm) },
        dismissButton = { TerminalButton(text = "CANCEL", onClick = onDismiss) }
    )
}

@Composable
private fun MarketDataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End
        )
    }
}
