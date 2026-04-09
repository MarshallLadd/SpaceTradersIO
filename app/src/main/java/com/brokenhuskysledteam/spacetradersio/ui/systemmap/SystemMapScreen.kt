package com.brokenhuskysledteam.spacetradersio.ui.systemmap

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointTraitSymbol
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.WaypointType
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun SystemMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: SystemMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SystemMapScreenContent(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun SystemMapScreenContent(
    uiState: SystemMapUiState,
    onEvent: (SystemMapEvent) -> Unit
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

            uiState.error != null && uiState.waypoints.isEmpty() -> {
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
                            onClick = { onEvent(SystemMapEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = "SYSTEM: ${uiState.systemSymbol}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        uiState.selectedShip?.let { ship ->
                            Text(
                                text = "// ${ship.symbol} @ ${ship.waypointSymbol} | FUEL: ${ship.fuelCurrent}/${ship.fuelCapacity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SortBar(uiState = uiState, onEvent = onEvent)
                        Spacer(modifier = Modifier.height(8.dp))
                        FilterChipRow(uiState = uiState, onEvent = onEvent)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.error != null) {
                        item {
                            TerminalCard(title = "Error", borderColor = MaterialTheme.colorScheme.error) {
                                Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    if (uiState.actionResult != null) {
                        item {
                            ActionResultCard(result = uiState.actionResult, onDismiss = { onEvent(SystemMapEvent.ActionResultDismissed) })
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    items(uiState.waypoints, key = { it.waypoint.symbol }) { node ->
                        WaypointRow(
                            node = node,
                            isCurrentLocation = node.waypoint.symbol == uiState.focusWaypointSymbol,
                            showNavigateButton = uiState.selectedShip != null
                                && node.waypoint.symbol != uiState.selectedShip.waypointSymbol
                                && uiState.selectedShip.navStatus != ShipNavStatus.IN_TRANSIT,
                            isActionInProgress = uiState.isActionInProgress,
                            indentLevel = 0,
                            onNavigate = { onEvent(SystemMapEvent.NavigateToWaypoint(it)) }
                        )
                    }
                }
            }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun SortBar(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TerminalButton(
            text = if (uiState.sortMode == SortMode.NAME) "[NAME]" else "NAME",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.NAME)) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        TerminalButton(
            text = if (uiState.sortMode == SortMode.DISTANCE) "[DISTANCE]" else "DISTANCE",
            onClick = { onEvent(SystemMapEvent.SortModeSelected(SortMode.DISTANCE)) },
            modifier = Modifier.weight(1f)
        )
    }
    if (uiState.sortMode == SortMode.DISTANCE) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SYSTEM_CENTER) "[CENTER]" else "CENTER",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SYSTEM_CENTER)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TerminalButton(
                text = if (uiState.distanceOrigin == DistanceOrigin.SHIP_LOCATION) "[SHIP]" else "SHIP",
                onClick = { onEvent(SystemMapEvent.DistanceOriginSelected(DistanceOrigin.SHIP_LOCATION)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterChipRow(uiState: SystemMapUiState, onEvent: (SystemMapEvent) -> Unit) {
    val typeFilters = listOf(WaypointType.PLANET, WaypointType.GAS_GIANT, WaypointType.ASTEROID_FIELD, WaypointType.JUMP_GATE)
    val traitFilters = listOf(WaypointTraitSymbol.MARKETPLACE, WaypointTraitSymbol.SHIPYARD, WaypointTraitSymbol.UNCHARTED)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        typeFilters.forEach { type ->
            val active = type in uiState.activeTypeFilters
            TerminalButton(
                text = if (active) "[${type.name}]" else type.name,
                onClick = { onEvent(SystemMapEvent.TypeFilterToggled(type)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        traitFilters.forEach { trait ->
            val active = trait in uiState.activeTraitFilters
            TerminalButton(
                text = if (active) "[${trait.name}]" else trait.name,
                onClick = { onEvent(SystemMapEvent.TraitFilterToggled(trait)) }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun WaypointRow(
    node: WaypointNode,
    isCurrentLocation: Boolean,
    showNavigateButton: Boolean,
    isActionInProgress: Boolean,
    indentLevel: Int,
    onNavigate: (String) -> Unit
) {
    val wp = node.waypoint
    TerminalCard(
        title = wp.symbol,
        modifier = Modifier.padding(start = (indentLevel * 24).dp, bottom = 8.dp)
    ) {
        Text(
            text = "TYPE: ${wp.type.name} | DIST: ${"%.1f".format(node.distance)} AU",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "COORDS: (${wp.x}, ${wp.y})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (wp.hasMarketplace || wp.hasShipyard || wp.isUncharted) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                if (wp.hasMarketplace) {
                    Text(text = "[MARKET] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.hasShipyard) {
                    Text(text = "[SHIPYARD] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (wp.isUncharted) {
                    Text(text = "[UNCHARTED] ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (isCurrentLocation) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "> YOU ARE HERE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (showNavigateButton) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = if (isActionInProgress) "..." else "Navigate",
                onClick = { onNavigate(wp.symbol) },
                enabled = !isActionInProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    node.orbitals.forEach { orbital ->
        WaypointRow(
            node = orbital,
            isCurrentLocation = orbital.waypoint.symbol == node.waypoint.symbol,
            showNavigateButton = showNavigateButton,
            isActionInProgress = isActionInProgress,
            indentLevel = indentLevel + 1,
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun ActionResultCard(result: SystemMapActionResult, onDismiss: () -> Unit) {
    val amberColor = MaterialTheme.colorScheme.tertiary
    TerminalCard(title = "Command Output", borderColor = amberColor) {
        when (result) {
            is SystemMapActionResult.NavigationStarted -> {
                Text(text = "> NAVIGATION INITIATED", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  DESTINATION: ${result.destinationSymbol}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL CONSUMED: ${result.fuelConsumed}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
                Text(text = "  FUEL REMAINING: ${result.fuelRemaining}", style = MaterialTheme.typography.bodyMedium, color = amberColor)
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
