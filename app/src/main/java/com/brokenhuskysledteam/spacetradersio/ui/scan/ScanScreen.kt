package com.brokenhuskysledteam.spacetradersio.ui.scan

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ScannedSystem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Waypoint
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScanScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

@Composable
fun ScanScreenContent(
    uiState: ScanUiState,
    onEvent: (ScanEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("SCAN: ${uiState.shipSymbol}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.onCooldown) "// SENSORS COOLING DOWN" else "// SENSORS READY",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Body(uiState = uiState, onEvent = onEvent)
        }
        ScanlineOverlay()
    }
}

@Composable
private fun Body(uiState: ScanUiState, onEvent: (ScanEvent) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TerminalCard(title = "ACTIONS") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton(text = "SYSTEMS", enabled = uiState.canScan, onClick = { onEvent(ScanEvent.ScanSystemsClicked) }, modifier = Modifier.weight(1f))
                    TerminalButton(text = "WAYPOINTS", enabled = uiState.canScan, onClick = { onEvent(ScanEvent.ScanWaypointsClicked) }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(text = "CHART HERE", enabled = !uiState.isBusy, onClick = { onEvent(ScanEvent.ChartClicked) }, modifier = Modifier.fillMaxWidth())
            }
        }

        uiState.result?.let { result ->
            item {
                TerminalCard(title = "RESULT") {
                    Text(resultLine(result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is ScanResult.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(text = "DISMISS", onClick = { onEvent(ScanEvent.ResultDismissed) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (uiState.waypoints.isNotEmpty()) {
            item { Text("WAYPOINTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
            items(uiState.waypoints, key = { "wp-${it.symbol}" }) { wp -> WaypointRow(wp) }
        }
        if (uiState.systems.isNotEmpty()) {
            item { Text("SYSTEMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
            items(uiState.systems, key = { "sys-${it.symbol}" }) { s -> SystemRow(s) }
        }
    }
}

private fun resultLine(r: ScanResult): String = when (r) {
    is ScanResult.SystemsScanned -> "SCANNED ${r.count} SYSTEM(S)"
    is ScanResult.WaypointsScanned -> "SCANNED ${r.count} WAYPOINT(S)"
    is ScanResult.Charted -> "CHARTED ${r.waypointSymbol}"
    is ScanResult.Failure -> "FAILED: ${r.message}"
}

@Composable
private fun WaypointRow(wp: Waypoint) {
    TerminalCard(title = wp.symbol) {
        Text(wp.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (wp.traits.isNotEmpty()) {
            Text(
                text = wp.traits.joinToString { it.symbol.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun SystemRow(s: ScannedSystem) {
    TerminalCard(title = s.symbol) {
        Text("${s.type} • DIST ${s.distance}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
