package com.brokenhuskysledteam.spacetradersio.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
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
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalTextField
import com.brokenhuskysledteam.spacetradersio.ui.theme.TerminalDarkGray

// Stateful wrapper that wires the Hilt-provided ViewModel to the stateless content.
// The LaunchedEffect collects one-shot navigation events from the ViewModel's Channel.
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

// Stateless content composable — receives state and emits events.
// Styled as a system login terminal with scanline overlay.
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onEvent: (AuthEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "SPACETRADERS TERMINAL",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "// SYSTEM ACCESS v2.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tab selector — two TerminalButtons acting as a toggle.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalButton(
                    onClick = { onEvent(AuthEvent.TabSelected(AuthTab.NEW_AGENT)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "NEW AGENT",
                        color = if (uiState.selectedTab == AuthTab.NEW_AGENT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
                TerminalButton(
                    onClick = { onEvent(AuthEvent.TabSelected(AuthTab.IMPORT_TOKEN)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "IMPORT TOKEN",
                        color = if (uiState.selectedTab == AuthTab.IMPORT_TOKEN)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (uiState.selectedTab) {
                AuthTab.NEW_AGENT -> NewAgentTab(uiState = uiState, onEvent = onEvent)
                AuthTab.IMPORT_TOKEN -> ImportTokenTab(uiState = uiState, onEvent = onEvent)
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Snackbar(
                    containerColor = TerminalDarkGray,
                    contentColor = MaterialTheme.colorScheme.error,
                    action = {
                        TerminalButton(
                            text = "Dismiss",
                            onClick = { onEvent(AuthEvent.ErrorDismissed) }
                        )
                    }
                ) {
                    Text(uiState.error)
                }
            }
        }

        ScanlineOverlay()
    }
}

// Registration form wrapped in a TerminalCard.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewAgentTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    var factionExpanded by remember { mutableStateOf(false) }

    TerminalCard(title = "Register Agent") {
        TerminalTextField(
            value = uiState.callsign,
            onValueChange = { onEvent(AuthEvent.CallsignChanged(it)) },
            label = "Callsign",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = factionExpanded,
            onExpandedChange = { factionExpanded = it }
        ) {
            TerminalTextField(
                value = uiState.selectedFaction.name,
                onValueChange = {},
                readOnly = true,
                label = "Faction",
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

        TerminalButton(
            onClick = { onEvent(AuthEvent.RegisterClicked) },
            enabled = !uiState.isRegistering,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isRegistering) {
                CircularProgressIndicator()
            } else {
                Text("REGISTER")
            }
        }
    }
}

// Token import form wrapped in a TerminalCard.
@Composable
private fun ImportTokenTab(uiState: AuthUiState, onEvent: (AuthEvent) -> Unit) {
    TerminalCard(title = "Import Token") {
        TerminalTextField(
            value = uiState.token,
            onValueChange = { onEvent(AuthEvent.TokenChanged(it)) },
            label = "Bearer Token",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        TerminalButton(
            onClick = { onEvent(AuthEvent.ImportClicked) },
            enabled = !uiState.isImporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isImporting) {
                CircularProgressIndicator()
            } else {
                Text("CONNECT")
            }
        }
    }
}
