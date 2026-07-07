package com.brokenhuskysledteam.spacetradersio.ui.mounts

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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.ShipMount
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun MountsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MountsScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

@Composable
fun MountsScreenContent(
    uiState: MountsUiState,
    onEvent: (MountsEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.isLoading && uiState.mounts.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.error != null && uiState.mounts.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Header(uiState.shipSymbol, uiState.hasShipyard)
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "ERROR") {
                        Text(uiState.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(text = "RETRY", onClick = { onEvent(MountsEvent.RetryClicked) }, modifier = Modifier.fillMaxWidth())
                    }
                }

            else ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Header(uiState.shipSymbol, uiState.hasShipyard)
                    Spacer(modifier = Modifier.height(16.dp))
                    MountsList(uiState = uiState, onEvent = onEvent)
                }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun Header(shipSymbol: String, hasShipyard: Boolean) {
    Text("MOUNTS: $shipSymbol", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (hasShipyard) "// SHIPYARD PRESENT — INSTALL/REMOVE AVAILABLE" else "// DOCK AT A SHIPYARD TO MODIFY MOUNTS",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun MountsList(uiState: MountsUiState, onEvent: (MountsEvent) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.modResult?.let { result ->
            item {
                TerminalCard(title = "MODIFICATION RESULT") {
                    when (result) {
                        is MountModResult.Success -> Text(
                            "${result.action} ${result.mountSymbol} — FEE ${result.fee} CR — BALANCE ${result.newCredits}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                        )
                        is MountModResult.Failure -> Text(
                            "FAILED: ${result.message}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(text = "DISMISS", onClick = { onEvent(MountsEvent.ResultDismissed) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item { Text("INSTALLED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
        items(uiState.mounts, key = { it.symbol }) { mount ->
            MountCard(mount = mount, canRemove = uiState.hasShipyard && !uiState.isModifying,
                onRemove = { onEvent(MountsEvent.RemoveClicked(mount.symbol)) })
        }

        if (uiState.installable.isNotEmpty()) {
            item { Text("INSTALLABLE (IN CARGO)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
            items(uiState.installable, key = { "install-${it.symbol}" }) { item ->
                InstallableCard(item = item, canInstall = uiState.hasShipyard && !uiState.isModifying,
                    onInstall = { onEvent(MountsEvent.InstallClicked(item.symbol)) })
            }
        }
    }
}

@Composable
private fun MountCard(mount: ShipMount, canRemove: Boolean, onRemove: () -> Unit) {
    TerminalCard(title = mount.symbol) {
        Text(mount.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        mount.strength?.let {
            Text("STRENGTH: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (mount.deposits.isNotEmpty()) {
            Text("DEPOSITS: ${mount.deposits.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (canRemove) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(text = "REMOVE", onClick = onRemove, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InstallableCard(item: CargoItem, canInstall: Boolean, onInstall: () -> Unit) {
    TerminalCard(title = item.symbol) {
        Text("${item.name} x${item.units}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (canInstall) {
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(text = "INSTALL", onClick = onInstall, modifier = Modifier.fillMaxWidth())
        }
    }
}
