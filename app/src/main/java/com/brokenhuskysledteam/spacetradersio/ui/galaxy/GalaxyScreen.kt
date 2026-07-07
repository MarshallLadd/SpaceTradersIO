package com.brokenhuskysledteam.spacetradersio.ui.galaxy

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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.StarSystem
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard

@Composable
fun GalaxyScreen(
    onNavigateBack: () -> Unit,
    viewModel: GalaxyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.isLoading && uiState.systems.isEmpty() ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.error != null && uiState.systems.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("GALAXY", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "ERROR") {
                        Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(text = "RETRY", onClick = { viewModel.onEvent(GalaxyEvent.RetryClicked) }, modifier = Modifier.fillMaxWidth())
                    }
                }

            else ->
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("GALAXY", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("// ${uiState.systems.size} / ${uiState.total} SYSTEMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.systems, key = { it.symbol }) { system -> SystemRow(system) }
                        if (uiState.canLoadMore) {
                            item {
                                TerminalButton(text = "LOAD MORE", onClick = { viewModel.onEvent(GalaxyEvent.LoadMoreClicked) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
        }
        ScanlineOverlay()
    }
}

@Composable
private fun SystemRow(system: StarSystem) {
    TerminalCard(title = system.symbol) {
        Text("${system.type} • (${system.x}, ${system.y})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
