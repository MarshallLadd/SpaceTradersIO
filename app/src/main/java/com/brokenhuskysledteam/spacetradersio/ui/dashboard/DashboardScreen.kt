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

/**
 * Stateful entry-point composable for the dashboard screen.
 *
 * **Pattern:** Stateful/stateless composable split. In a new project, keep one thin stateful
 * composable at the top that owns the ViewModel and navigation wiring, then delegate all
 * rendering to a stateless inner composable. This makes the inner composable fully
 * previewable and testable without a ViewModel.
 *
 * **In this project:** This composable obtains the Hilt ViewModel, collects `uiState` with
 * lifecycle awareness, and listens on `navigationEvent` via `LaunchedEffect`. All rendering
 * is delegated to [DashboardScreenContent].
 *
 * @param onNavigateToAuth Callback invoked when the user logs out or an auth error occurs.
 * @param onNavigateToShipList Callback invoked when the user taps the Fleet card.
 * @param viewModel Injected by Hilt; overridable in tests or previews.
 */
@Composable
fun DashboardScreen(
    onNavigateToAuth: () -> Unit,
    onNavigateToShipList: () -> Unit,
    onNavigateToContracts: () -> Unit,
    onNavigateToGalaxy: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Collect one-shot navigation events for the lifetime of this composable.
    // LaunchedEffect(Unit) ensures this coroutine is started once and cancelled when the
    // composable leaves the composition — preventing duplicate navigation on recomposition.
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                NavigationTarget.Auth -> onNavigateToAuth()
                NavigationTarget.ShipList -> onNavigateToShipList()
                NavigationTarget.ContractsScreen -> onNavigateToContracts()
                // Other targets are not handled by this screen; ignore them.
                else -> {}
            }
        }
    }

    DashboardScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onGalaxyClick = onNavigateToGalaxy
    )
}

/**
 * Stateless content composable for the dashboard screen, styled as a retro data terminal.
 *
 * **Pattern:** Three-state UI skeleton (loading / error / content). In a new project, always
 * handle all three async states explicitly with a `when` block rather than defaulting to
 * hiding views. This prevents the "flash of empty content" and makes each state visually
 * intentional. The order matters: check `isLoading` first so a loading-with-stale-data
 * condition shows the spinner rather than outdated content.
 *
 * **In this project:** Renders one of three branches based on [DashboardUiState]:
 * - `isLoading == true` → centered [CircularProgressIndicator].
 * - `error != null` → error card with a retry action.
 * - `agent != null` → scrollable agent data cards plus a logout button.
 *
 * @param uiState The current UI snapshot produced by [DashboardViewModel].
 * @param onEvent Dispatcher for all user interactions; forwarded to [DashboardViewModel.onEvent].
 */
@Composable
fun DashboardScreenContent(
    uiState: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    onGalaxyClick: () -> Unit = {}
) {
    // Box is the root so ScanlineOverlay can be stacked on top of all content branches
    // using a single overlay declaration rather than repeating it in each branch.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            // --- State 1: Loading ---
            // Show a centered spinner while the initial or retry fetch is in flight.
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // --- State 2: Error ---
            // Display the error message and a retry button. The screen header is still
            // rendered to maintain visual continuity with the loaded state.
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
                        // TODO: this button dispatches LogoutClicked instead of RetryClicked —
                        //  the label says "Retry" but the action logs the user out. Should be
                        //  onEvent(DashboardEvent.RetryClicked).
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(DashboardEvent.LogoutClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- State 3: Content ---
            // Agent data is available; render scrollable info cards and the logout button.
            uiState.agent != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        // verticalScroll allows the content to extend past the screen
                        // on small devices without clipping the logout button.
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

                    Spacer(modifier = Modifier.height(12.dp))

                    TerminalCard(
                        title = "Contracts",
                        modifier = Modifier.clickable { onEvent(DashboardEvent.ContractsCardClicked) }
                    ) {
                        TerminalDataRow("STATUS", "TAP TO VIEW")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TerminalCard(
                        title = "Galaxy",
                        modifier = Modifier.clickable { onGalaxyClick() }
                    ) {
                        TerminalDataRow("STATUS", "BROWSE SYSTEMS")
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

        // ScanlineOverlay is drawn last (on top of all branches) to apply the retro CRT
        // aesthetic across the entire screen, including the loading and error states.
        ScanlineOverlay()
    }
}

/**
 * A single label-value row rendered in monospace terminal style.
 *
 * **Pattern:** Private screen-scoped helper composable. In a new project, extract repeated
 * layout fragments into private composables within the same file rather than into a shared
 * component library, unless the fragment is needed in more than one screen. Keeping it
 * `private` signals that it is an implementation detail of this screen only.
 *
 * **In this project:** Used for every data field in the agent, location, and fleet cards.
 * Extracting it keeps the card content blocks readable by reducing visual noise from
 * repeated `Text(text = "$label: $value", style = ..., color = ...)` calls.
 *
 * @param label The uppercase field name shown before the colon (e.g. `"CALLSIGN"`).
 * @param value The string value to display after the colon.
 */
@Composable
private fun TerminalDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
