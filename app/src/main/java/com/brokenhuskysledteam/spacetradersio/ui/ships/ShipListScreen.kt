package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalProgressBar
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun ShipListScreen(
    onNavigateToShipDetail: (String) -> Unit,
    viewModel: ShipListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                is NavigationTarget.ShipDetail -> onNavigateToShipDetail(target.shipSymbol)
                else -> {}
            }
        }
    }

    ShipListScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun ShipListScreenContent(
    uiState: ShipListUiState,
    onEvent: (ShipListEvent) -> Unit
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

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "FLEET MANIFEST",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "Error") {
                        Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(ShipListEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "FLEET MANIFEST",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "// ${uiState.ships.size} VESSEL(S) ON RECORD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    LazyColumn {
                        items(uiState.ships) { ship ->
                            ShipSummaryCard(
                                ship = ship,
                                onClick = { onEvent(ShipListEvent.ShipSelected(ship.symbol)) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        ScanlineOverlay()
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ShipSummaryCard(ship: ShipSummary, onClick: () -> Unit) {
    TerminalCard(
        title = ship.symbol,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        ShipDataRow("FRAME", ship.frameName)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDataRow("STATUS", ship.status.name)
        Spacer(modifier = Modifier.height(4.dp))

        if (ship.status == ShipNavStatus.IN_TRANSIT && ship.arrivalTime != null && ship.departureTime != null) {
            ShipDataRow("DESTINATION", "${ship.systemSymbol} / ${ship.waypointSymbol}")
            Spacer(modifier = Modifier.height(8.dp))
            TransitProgress(ship = ship)
        } else {
            ShipDataRow("LOCATION", "${ship.systemSymbol} / ${ship.waypointSymbol}")
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun TransitProgress(ship: ShipSummary) {
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

    ShipDataRow("ETA", countdownLabel)
    Spacer(modifier = Modifier.height(4.dp))
    TerminalProgressBar(
        value = progress,
        label = "${"%.0f".format(progress * 100)}%"
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
