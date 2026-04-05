package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

// Stateful wrapper — wires the Hilt ViewModel and collects navigation events.
@Composable
fun DashboardScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToShipList: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                NavigationTarget.Auth -> onNavigateToAuth()
                NavigationTarget.ShipList -> onNavigateToShipList()
                else -> {}
            }
        }
    }

    DashboardScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

// Stateless content styled as a data terminal readout.
// Three visual states: loading, error with retry, agent data cards.
@Composable
fun DashboardScreenContent(
    uiState: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "COMMAND TERMINAL",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TerminalCard(title = "Error") {
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(DashboardEvent.LogoutClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            uiState.agent != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "COMMAND TERMINAL",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "// ACTIVE SESSION",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TerminalCard(title = "Agent Status") {
                        TerminalDataRow("CALLSIGN", uiState.agent.symbol)
                        Spacer(modifier = Modifier.height(4.dp))
                        TerminalDataRow("CREDITS", uiState.agent.credits.toString())
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TerminalCard(title = "Location") {
                        TerminalDataRow("HQ", uiState.agent.headquarters)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TerminalCard(
                        title = "Fleet",
                        modifier = Modifier.clickable { onEvent(DashboardEvent.FleetCardClicked) }
                    ) {
                        TerminalDataRow("SHIPS", uiState.agent.shipCount.toString())
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TerminalButton(
                        text = "Disconnect",
                        onClick = { onEvent(DashboardEvent.LogoutClicked) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ScanlineOverlay()
    }
}

// Label-value pair rendered as "LABEL: VALUE" in monospace, like a terminal readout.
@Composable
private fun TerminalDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
