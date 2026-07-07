package com.brokenhuskysledteam.spacetradersio.ui.mining

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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.CargoItem
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Survey
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun MiningScreen(
    onNavigateBack: () -> Unit,
    viewModel: MiningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MiningScreenContent(uiState = uiState, onEvent = viewModel::onEvent, onNavigateBack = onNavigateBack)
}

@Composable
fun MiningScreenContent(
    uiState: MiningUiState,
    onEvent: (MiningEvent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("MINING: ${uiState.shipSymbol}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLine(uiState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            MiningBody(uiState = uiState, onEvent = onEvent)
        }
        ScanlineOverlay()
    }
}

private fun statusLine(s: MiningUiState): String = when {
    !s.inOrbit -> "// SHIP MUST BE IN ORBIT AT AN ASTEROID TO MINE"
    s.onCooldown -> "// COOLDOWN ACTIVE (${s.cooldownRemainingSeconds}s) — HOLD ${s.cargoUnits}/${s.cargoCapacity}"
    else -> "// READY — HOLD ${s.cargoUnits}/${s.cargoCapacity}"
}

@Composable
private fun MiningBody(uiState: MiningUiState, onEvent: (MiningEvent) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TerminalCard(title = "ACTIONS") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton(text = "EXTRACT", enabled = uiState.canAct, onClick = { onEvent(MiningEvent.ExtractClicked) }, modifier = Modifier.weight(1f))
                    TerminalButton(text = "SURVEY", enabled = uiState.canAct, onClick = { onEvent(MiningEvent.SurveyClicked) }, modifier = Modifier.weight(1f))
                }
            }
        }

        uiState.result?.let { result ->
            item {
                TerminalCard(title = "RESULT") {
                    Text(resultLine(result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is MiningResult.Failure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalButton(text = "DISMISS", onClick = { onEvent(MiningEvent.ResultDismissed) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (uiState.surveys.isNotEmpty()) {
            item { Text("SURVEYS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
            items(uiState.surveys, key = { it.signature }) { survey ->
                SurveyCard(survey = survey, enabled = uiState.canAct, onExtract = { onEvent(MiningEvent.ExtractWithSurveyClicked(survey)) })
            }
        }

        if (uiState.inventory.isNotEmpty()) {
            item { Text("CARGO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
            items(uiState.inventory, key = { "cargo-${it.symbol}" }) { item ->
                CargoRow(item = item, enabled = !uiState.isBusy, onJettison = { onEvent(MiningEvent.JettisonClicked(item.symbol, item.units)) })
            }
        }
    }
}

private fun resultLine(r: MiningResult): String = when (r) {
    is MiningResult.Extracted -> "EXTRACTED ${r.units}x ${r.yieldSymbol}"
    is MiningResult.Surveyed -> "CREATED ${r.count} SURVEY(S)"
    is MiningResult.Jettisoned -> "JETTISONED ${r.units}x ${r.tradeSymbol}"
    is MiningResult.Failure -> "FAILED: ${r.message}"
}

@Composable
private fun SurveyCard(survey: Survey, enabled: Boolean, onExtract: () -> Unit) {
    TerminalCard(title = "${survey.size} • ${survey.deposits.joinToString()}") {
        Text(survey.signature, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(text = "EXTRACT WITH SURVEY", enabled = enabled, onClick = onExtract, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CargoRow(item: CargoItem, enabled: Boolean, onJettison: () -> Unit) {
    TerminalCard(title = item.symbol) {
        Text("${item.name} x${item.units}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        TerminalButton(text = "JETTISON", enabled = enabled, onClick = onJettison, modifier = Modifier.fillMaxWidth())
    }
}
