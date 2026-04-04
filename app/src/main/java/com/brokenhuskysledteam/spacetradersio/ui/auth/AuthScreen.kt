package com.brokenhuskysledteam.spacetradersio.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.FactionSymbol

@Composable
fun AuthScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                NavigationTarget.Dashboard -> onNavigateToDashboard()
                else -> {}
            }
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onEvent: (AuthEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
            Tab(
                selected = uiState.selectedTab == AuthTab.NEW_AGENT,
                onClick = { onEvent(AuthEvent.TabSelected(AuthTab.NEW_AGENT)) },
                text = { Text("New Agent") }
            )
            Tab(
                selected = uiState.selectedTab == AuthTab.IMPORT_TOKEN,
                onClick = { onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN)) },
                text = { Text("Import Token") }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.selectedTab) {
                AuthTab.NEW_AGENT -> NewAgentTab(uiState = uiState, onEvent = onEvent)
                AuthTab.IMPORT_TOKEN -> ImportTokenTab(uiState = uiState, onEvent = onEvent)
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Snackbar(
                    action = {
                        TextButton(onClick = { onEvent(AuthEvent.ErrorDismissed) }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(uiState.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewAgentTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    var factionExpanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = uiState.callsign,
        onValueChange = { onEvent(AuthEvent.CallsignChanged(it)) },
        label = { Text("Callsign") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    ExposedDropdownMenuBox(
        expanded = factionExpanded,
        onExpandedChange = { factionExpanded = it }
    ) {
        OutlinedTextField(
            value = uiState.selectedFaction.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Faction") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = factionExpanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = factionExpanded,
            onDismissRequest = { factionExpanded = false }
        ) {
            FactionSymbol.entries.forEach { faction ->
                DropdownMenuItem(
                    text = { Text(faction.name) },
                    onClick = {
                        onEvent(AuthEvent.FactionSelected(faction))
                        factionExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onEvent(AuthEvent.RegisterClicked) },
        enabled = !uiState.isRegistering,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isRegistering) {
            CircularProgressIndicator()
        } else {
            Text("Register")
        }
    }
}

@Composable
private fun ImportTokenTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.token,
        onValueChange = { onEvent(AuthEvent.TokenChanged(it)) },
        label = { Text("Bearer Token") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onEvent(AuthEvent.ImportClicked) },
        enabled = !uiState.isImporting,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (uiState.isImporting) {
            CircularProgressIndicator()
        } else {
            Text("Connect")
        }
    }
}
