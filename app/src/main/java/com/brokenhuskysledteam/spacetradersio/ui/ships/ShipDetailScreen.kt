package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalProgressBar
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Stateful wrapper — wires the Hilt ViewModel.
@Composable
fun ShipDetailScreen(
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit,
    viewModel: ShipDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShipDetailScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToSystemMap = onNavigateToSystemMap
    )
}

@OptIn(ExperimentalTime::class)
@Composable
fun ShipDetailScreenContent(
    uiState: ShipDetailUiState,
    onEvent: (ShipDetailEvent) -> Unit,
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit = { _, _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.ship == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    TerminalCard(title = "Error") {
                        Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(ShipDetailEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            uiState.ship != null -> {
                val ship = uiState.ship
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = ship.symbol,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "// ${ship.frameName} · ${ship.role.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Navigation card
                    NavigationCard(ship = ship, onNavigateToSystemMap = onNavigateToSystemMap)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Fuel card
                    TerminalCard(title = "Fuel") {
                        ShipDetailDataRow("LEVEL", "${ship.fuelCurrent} / ${ship.fuelCapacity}")
                        Spacer(modifier = Modifier.height(6.dp))
                        val fuelFraction = if (ship.fuelCapacity > 0) {
                            ship.fuelCurrent.toFloat() / ship.fuelCapacity.toFloat()
                        } else 0f
                        TerminalProgressBar(
                            value = fuelFraction,
                            label = "${"%.0f".format(fuelFraction * 100)}%"
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cargo card
                    TerminalCard(title = "Cargo") {
                        ShipDetailDataRow("LOAD", "${ship.cargoUnits} / ${ship.cargoCapacity}")
                        Spacer(modifier = Modifier.height(6.dp))
                        val cargoFraction = if (ship.cargoCapacity > 0) {
                            ship.cargoUnits.toFloat() / ship.cargoCapacity.toFloat()
                        } else 0f
                        TerminalProgressBar(
                            value = cargoFraction,
                            label = "${"%.0f".format(cargoFraction * 100)}%"
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Action error (shown inline, below cargo, above command output)
                    if (uiState.error != null) {
                        TerminalCard(
                            title = "Error",
                            borderColor = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Command output card — amber/tertiary, conditional
                    if (uiState.actionResult != null) {
                        CommandOutputCard(
                            result = uiState.actionResult,
                            onDismiss = { onEvent(ShipDetailEvent.ActionResultDismissed) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Contextual action buttons
                    ActionButtons(
                        navStatus = ship.navStatus,
                        isActionInProgress = uiState.isActionInProgress,
                        onEvent = onEvent
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        ScanlineOverlay()
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun NavigationCard(
    ship: ShipDetail,
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit
) {
    TerminalCard(title = "Navigation") {
        ShipDetailDataRow("STATUS", ship.navStatus.name)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDetailDataRow("FLIGHT MODE", ship.flightMode.name)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDetailDataRow("SYSTEM", ship.systemSymbol)

        if (ship.navStatus == ShipNavStatus.IN_TRANSIT &&
            ship.arrivalTime != null &&
            ship.departureTime != null
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("ORIGIN", "${ship.originSymbol} (${ship.originType.name})")
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("DESTINATION", "${ship.destinationSymbol} (${ship.destinationType.name})")
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = "View System",
                onClick = {
                    onNavigateToSystemMap(ship.systemSymbol, ship.waypointSymbol, ship.symbol)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailTransitProgress(ship = ship)
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("LOCATION", ship.waypointSymbol)
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = "View System",
                onClick = {
                    onNavigateToSystemMap(ship.systemSymbol, ship.waypointSymbol, ship.symbol)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun DetailTransitProgress(ship: ShipDetail) {
    val arrivalTime = ship.arrivalTime ?: return
    val departureTime = ship.departureTime ?: return

    var remainingSeconds by remember(ship.symbol) {
        mutableLongStateOf(
            max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        )
    }

    LaunchedEffect(ship.symbol) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        }
    }

    val totalSeconds = (arrivalTime - departureTime).inWholeSeconds
    val progress = if (totalSeconds > 0) {
        1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
    } else 1f

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val countdownLabel = if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }

    ShipDetailDataRow("ETA", countdownLabel)
    Spacer(modifier = Modifier.height(4.dp))
    TerminalProgressBar(
        value = progress,
        label = "${"%.0f".format(progress * 100)}%"
    )
}

@Composable
private fun CommandOutputCard(result: ActionResult, onDismiss: () -> Unit) {
    val amberColor = MaterialTheme.colorScheme.tertiary
    TerminalCard(title = "Command Output", borderColor = amberColor) {
        when (result) {
            is ActionResult.Orbited -> {
                CommandLine("> ORBIT SUCCESSFUL", amberColor)
                CommandLine("  WAYPOINT: ${result.waypointSymbol}", amberColor)
            }
            is ActionResult.Docked -> {
                CommandLine("> DOCK SUCCESSFUL", amberColor)
                CommandLine("  WAYPOINT: ${result.waypointSymbol}", amberColor)
            }
            is ActionResult.Refueled -> {
                CommandLine("> REFUEL SUCCESSFUL", amberColor)
                CommandLine("  UNITS ADDED: ${result.fuelAdded}", amberColor)
                CommandLine("  COST: ${result.totalCost} CREDITS", amberColor)
                CommandLine("  BALANCE: ${result.newCredits} CREDITS", amberColor)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(
            text = "Dismiss",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActionButtons(
    navStatus: ShipNavStatus,
    isActionInProgress: Boolean,
    onEvent: (ShipDetailEvent) -> Unit
) {
    when (navStatus) {
        ShipNavStatus.DOCKED -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                TerminalButton(
                    text = if (isActionInProgress) "..." else "Orbit",
                    onClick = { onEvent(ShipDetailEvent.OrbitClicked) },
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TerminalButton(
                    text = if (isActionInProgress) "..." else "Refuel",
                    onClick = { onEvent(ShipDetailEvent.RefuelClicked) },
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ShipNavStatus.IN_ORBIT -> {
            TerminalButton(
                text = if (isActionInProgress) "..." else "Dock",
                onClick = { onEvent(ShipDetailEvent.DockClicked) },
                enabled = !isActionInProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ShipNavStatus.IN_TRANSIT -> {
            // No actions available while in transit
        }
    }
}

@Composable
private fun CommandLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}

@Composable
private fun ShipDetailDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
