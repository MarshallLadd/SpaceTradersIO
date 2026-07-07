package com.brokenhuskysledteam.spacetradersio.ui.jump

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
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun JumpScreen(
    onNavigateBack: () -> Unit,
    viewModel: JumpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JumpScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

@Composable
fun JumpScreenContent(
    uiState: JumpUiState,
    onEvent: (JumpEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.isLoading && uiState.connections.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.error != null && uiState.connections.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Header(uiState)
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "ERROR") {
                        Text(uiState.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(text = "RETRY", onClick = { onEvent(JumpEvent.RetryClicked) }, modifier = Modifier.fillMaxWidth())
                    }
                }

            else ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Header(uiState)
                    Spacer(modifier = Modifier.height(16.dp))
                    Body(uiState = uiState, onEvent = onEvent)
                }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun Header(s: JumpUiState) {
    Text("JUMP GATE: ${s.gateSymbol}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (s.onCooldown) "// COOLDOWN ACTIVE — JUMP UNAVAILABLE" else "// SELECT A CONNECTED GATE TO JUMP",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun Body(uiState: JumpUiState, onEvent: (JumpEvent) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        uiState.result?.let { result ->
            item {
                TerminalCard(title = "JUMP RESULT") {
                    Text(
                        when (result) {
                            is JumpResultUi.Success -> "JUMPED TO ${result.destination}"
                            is JumpResultUi.Failure -> "FAILED: ${result.message}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is JumpResultUi.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(text = "DISMISS", onClick = { onEvent(JumpEvent.ResultDismissed) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (uiState.connections.isEmpty()) {
            item {
                TerminalCard(title = "NO CONNECTIONS") {
                    Text("THIS JUMP GATE HAS NO CHARTED CONNECTIONS.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        } else {
            items(uiState.connections, key = { it }) { dest ->
                TerminalCard(title = dest) {
                    TerminalButton(
                        text = "JUMP",
                        enabled = uiState.canJump,
                        onClick = { onEvent(JumpEvent.JumpClicked(dest)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
