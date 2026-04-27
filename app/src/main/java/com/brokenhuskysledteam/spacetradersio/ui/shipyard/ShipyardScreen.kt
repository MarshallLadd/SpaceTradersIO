package com.brokenhuskysledteam.spacetradersio.ui.shipyard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Shipyard
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipyardShip
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun ShipyardScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShipyardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShipyardScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ShipyardScreenContent(
    uiState: ShipyardUiState,
    onEvent: (ShipyardEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isRefreshing && uiState.shipyard == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.shipyard == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "SHIPYARD: ${uiState.waypointSymbol}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                            onClick = { onEvent(ShipyardEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            uiState.shipyard != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "SHIPYARD: ${uiState.waypointSymbol}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "// MOD FEE: ${uiState.shipyard.modificationsFee} CREDITS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    ShipyardContent(
                        shipyard = uiState.shipyard,
                        isPurchasing = uiState.isPurchasing,
                        purchaseResult = uiState.purchaseResult,
                        onEvent = onEvent
                    )
                }
            }
        }

        ScanlineOverlay()
    }

    if (uiState.pendingPurchase != null) {
        PurchaseConfirmationDialog(
            ship = uiState.pendingPurchase,
            onConfirm = { onEvent(ShipyardEvent.PurchaseConfirmed) },
            onDismiss = { onEvent(ShipyardEvent.PurchaseDismissed) }
        )
    }
}

@Composable
private fun ShipyardContent(
    shipyard: Shipyard,
    isPurchasing: Boolean,
    purchaseResult: PurchaseResult?,
    onEvent: (ShipyardEvent) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (purchaseResult != null) {
            item {
                TerminalCard(title = "TRANSACTION RESULT") {
                    when (purchaseResult) {
                        is PurchaseResult.Success -> Text(
                            text = "PURCHASE SUCCESSFUL — ${purchaseResult.shipSymbol} ACQUIRED",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is PurchaseResult.Failure -> Text(
                            text = "PURCHASE FAILED: ${purchaseResult.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(
                        text = "DISMISS",
                        onClick = { onEvent(ShipyardEvent.PurchaseResultDismissed) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        val ships = shipyard.ships
        if (ships == null) {
            item {
                TerminalCard(title = "NO DATA") {
                    Text(
                        text = "A SHIP MUST BE PRESENT AT THIS LOCATION TO ACCESS MARKET LISTINGS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        } else if (ships.isEmpty()) {
            item {
                TerminalCard(title = "MARKET") {
                    Text(
                        text = "NO SHIPS CURRENTLY FOR SALE AT THIS SHIPYARD.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        } else {
            items(ships, key = { it.type.name }) { ship ->
                ShipListingCard(
                    ship = ship,
                    isPurchasing = isPurchasing,
                    onPurchaseClicked = { onEvent(ShipyardEvent.PurchaseShipClicked(ship)) }
                )
            }
        }
    }
}

@Composable
private fun ShipListingCard(
    ship: ShipyardShip,
    isPurchasing: Boolean,
    onPurchaseClicked: () -> Unit
) {
    TerminalCard(title = ship.name.uppercase()) {
        ShipDataRow("TYPE", ship.type.name)
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("PRICE", "${ship.purchasePrice} CREDITS")
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("SUPPLY", ship.supply.name)
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("FRAME", ship.frameName)
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("ENGINE SPD", ship.engineSpeed.toString())
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("REACTOR", "${ship.reactorPowerOutput}W")
        Spacer(modifier = Modifier.height(2.dp))
        ShipDataRow("CREW", "${ship.crewRequired}-${ship.crewCapacity}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = ship.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(12.dp))
        TerminalButton(
            text = if (isPurchasing) "PROCESSING..." else "PURCHASE",
            enabled = !isPurchasing,
            onClick = onPurchaseClicked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PurchaseConfirmationDialog(
    ship: ShipyardShip,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "CONFIRM PURCHASE",
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(
                text = "Purchase ${ship.name} for ${ship.purchasePrice} CREDITS?",
                color = MaterialTheme.colorScheme.primary
            )
        },
        confirmButton = {
            TerminalButton(text = "CONFIRM", onClick = onConfirm)
        },
        dismissButton = {
            TerminalButton(text = "CANCEL", onClick = onDismiss)
        }
    )
}

@Composable
private fun ShipDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
