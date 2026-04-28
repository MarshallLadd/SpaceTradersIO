package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.ui.components.ScanlineOverlay
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalButton
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalCard
import com.brokenhuskysledteam.spacetradersio.ui.components.TerminalProgressBar
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Stateful entry-point composable for the ship detail screen.
 *
 * **Pattern:** Stateful/stateless composable split. This composable owns the Hilt ViewModel
 * and collects [ShipDetailUiState] from it, then immediately delegates all rendering to the
 * stateless [ShipDetailScreenContent]. The split means [ShipDetailScreenContent] is testable
 * in isolation (pass any [ShipDetailUiState] directly; no Hilt needed) and previewable
 * without a running ViewModel. In a new project, always introduce this split when a screen
 * has a ViewModel.
 *
 * @param onNavigateToSystemMap Callback invoked when the user taps "View System". Receives the
 *   system symbol, waypoint symbol, and ship symbol to pass to the system map destination.
 * @param viewModel Provided by Hilt via `hiltViewModel()`; override in tests or previews.
 */
// Stateful wrapper — wires the Hilt ViewModel.
@Composable
fun ShipDetailScreen(
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit,
    onNavigateToShipyard: (systemSymbol: String, waypointSymbol: String) -> Unit = { _, _ -> },
    viewModel: ShipDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShipDetailScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToSystemMap = onNavigateToSystemMap,
        onNavigateToShipyard = onNavigateToShipyard
    )
}

/**
 * Stateless content composable for the ship detail screen.
 *
 * **Pattern:** Stateless composable. Receives all state as parameters and emits all user
 * interactions via [onEvent] and [onNavigateToSystemMap]. Has no knowledge of where state
 * comes from, making it straightforward to preview and test. The three rendering branches
 * (loading, error-with-no-data, data-available) mirror the three meaningful combinations of
 * [ShipDetailUiState.isLoading], [ShipDetailUiState.error], and [ShipDetailUiState.ship].
 *
 * **In this project:** The "error with ship non-null" case (action failure) is handled inline
 * within the data-available branch — an error card is shown above the action buttons without
 * replacing the ship data. Only the "error with ship null" case (initial load failure) shows
 * the full-screen error card.
 *
 * @param uiState Current UI state from [ShipDetailViewModel].
 * @param onEvent Callback for all user interactions; forwarded to [ShipDetailViewModel.onEvent].
 * @param onNavigateToSystemMap Callback for "View System" navigation. Defaults to a no-op so
 *   the stateless composable can be previewed without a real NavController.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun ShipDetailScreenContent(
    uiState: ShipDetailUiState,
    onEvent: (ShipDetailEvent) -> Unit,
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit = { _, _, _ -> },
    onNavigateToShipyard: (systemSymbol: String, waypointSymbol: String) -> Unit = { _, _ -> }
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

            uiState.error != null && uiState.ship == null -> {
                // Full-screen error: initial load failed and no cached data is available.
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
                            onClick = { onEvent(ShipDetailEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            uiState.ship != null -> {
                val ship = uiState.ship
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = ship.symbol,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "// ${ship.frameName} · ${ship.role.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Navigation card
                    NavigationCard(
                        ship = ship,
                        hasShipyard = uiState.hasShipyard,
                        onNavigateToSystemMap = onNavigateToSystemMap,
                        onNavigateToShipyard = { systemSymbol, waypointSymbol ->
                            onEvent(ShipDetailEvent.ViewShipyardClicked(systemSymbol, waypointSymbol))
                            onNavigateToShipyard(systemSymbol, waypointSymbol)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Fuel card
                    TerminalCard(title = "Fuel") {
                        ShipDetailDataRow("LEVEL", "${ship.fuelCurrent} / ${ship.fuelCapacity}")
                        Spacer(modifier = Modifier.height(6.dp))
                        val fuelFraction = if (ship.fuelCapacity > 0) {
                            ship.fuelCurrent.toFloat() / ship.fuelCapacity.toFloat()
                        } else 0f
                        TerminalProgressBar(
                            value = fuelFraction,
                            label = "${"%.0f".format(fuelFraction * 100)}%"
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Cargo card
                    TerminalCard(title = "Cargo") {
                        ShipDetailDataRow("LOAD", "${ship.cargoUnits} / ${ship.cargoCapacity}")
                        Spacer(modifier = Modifier.height(6.dp))
                        val cargoFraction = if (ship.cargoCapacity > 0) {
                            ship.cargoUnits.toFloat() / ship.cargoCapacity.toFloat()
                        } else 0f
                        TerminalProgressBar(
                            value = cargoFraction,
                            label = "${"%.0f".format(cargoFraction * 100)}%"
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Action error (shown inline, below cargo, above command output)
                    if (uiState.error != null) {
                        TerminalCard(
                            title = "Error",
                            borderColor = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = uiState.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Command output card — amber/tertiary, conditional
                    if (uiState.actionResult != null) {
                        CommandOutputCard(
                            result = uiState.actionResult,
                            onDismiss = { onEvent(ShipDetailEvent.ActionResultDismissed) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Contextual action buttons
                    ActionButtons(
                        navStatus = ship.navStatus,
                        isActionInProgress = uiState.isActionInProgress,
                        onEvent = onEvent
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        ScanlineOverlay()
    }
}

/**
 * Displays the ship's current navigation state inside a terminal-styled card.
 *
 * Shows nav status, flight mode, and current system at all times. When the ship is
 * `IN_TRANSIT`, additionally shows the origin, destination, and a live countdown progress
 * bar via [DetailTransitProgress]. When not in transit, shows the current waypoint location
 * instead. Both branches include a "View System" button to open the system map.
 *
 * @param ship The flattened UI model containing nav data.
 * @param onNavigateToSystemMap Callback forwarded to the "View System" button.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun NavigationCard(
    ship: ShipDetail,
    hasShipyard: Boolean,
    onNavigateToSystemMap: (systemSymbol: String, waypointSymbol: String, shipSymbol: String) -> Unit,
    onNavigateToShipyard: (systemSymbol: String, waypointSymbol: String) -> Unit
) {
    TerminalCard(title = "Navigation") {
        ShipDetailDataRow("STATUS", ship.navStatus.name)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDetailDataRow("FLIGHT MODE", ship.flightMode.name)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDetailDataRow("SYSTEM", ship.systemSymbol)

        if (ship.navStatus == ShipNavStatus.IN_TRANSIT &&
            ship.arrivalTime != null &&
            ship.departureTime != null
        ) {
            // In-transit branch: show route endpoints and a live countdown timer.
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("ORIGIN", "${ship.originSymbol} (${ship.originType.name})")
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("DESTINATION", "${ship.destinationSymbol} (${ship.destinationType.name})")
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = "View System",
                onClick = {
                    onNavigateToSystemMap(ship.systemSymbol, ship.waypointSymbol, ship.symbol)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailTransitProgress(ship = ship)
        } else {
            // Docked/orbiting branch: show the current waypoint location.
            Spacer(modifier = Modifier.height(4.dp))
            ShipDetailDataRow("LOCATION", ship.waypointSymbol)
            Spacer(modifier = Modifier.height(8.dp))
            TerminalButton(
                text = "View System",
                onClick = {
                    onNavigateToSystemMap(ship.systemSymbol, ship.waypointSymbol, ship.symbol)
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (hasShipyard) {
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(
                    text = "VIEW SHIPYARD",
                    onClick = { onNavigateToShipyard(ship.systemSymbol, ship.waypointSymbol) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Live countdown progress bar for a ship that is currently in transit.
 *
 * Computes a 0..1 progress fraction from the elapsed time between [ShipDetail.departureTime]
 * and [ShipDetail.arrivalTime], then ticks a `remainingSeconds` counter down every second
 * via a [LaunchedEffect] coroutine. The countdown label is formatted as `MM:SS` for trips
 * under an hour, or `HH:MM:SS` for longer ones.
 *
 * The state key for both `remember` and `LaunchedEffect` is `ship.symbol`. This ensures that
 * if the user somehow navigates between detail screens without leaving the composition, the
 * countdown resets to the new ship's correct starting value rather than retaining the old one.
 *
 * Early-returns if either time value is null (which should not occur when [ShipNavStatus] is
 * `IN_TRANSIT`, but the nullable contract from [ShipDetail] requires the guard).
 *
 * @param ship The flattened UI model. [ShipDetail.arrivalTime] and [ShipDetail.departureTime]
 *   must be non-null for this composable to render anything.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun DetailTransitProgress(ship: ShipDetail) {
    val arrivalTime = ship.arrivalTime ?: return
    val departureTime = ship.departureTime ?: return

    // Initialise the countdown from real clock time so the display is correct even if the
    // composable is first composed mid-transit (e.g. after a config change).
    var remainingSeconds by remember(ship.symbol) {
        mutableLongStateOf(
            max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        )
    }

    LaunchedEffect(ship.symbol) {
        while (remainingSeconds > 0) {
            delay(1000L)
            // Re-read the clock each tick rather than decrementing by 1 to avoid drift from
            // coroutine scheduling jitter on slower devices.
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

    ShipDetailDataRow("ETA", countdownLabel)
    Spacer(modifier = Modifier.height(4.dp))
    TerminalProgressBar(
        value = progress,
        label = "${"%.0f".format(progress * 100)}%"
    )
}

/**
 * Amber terminal panel that displays the result of the most recently completed ship command.
 *
 * **Pattern:** Transient feedback card. This composable is only visible when
 * [ShipDetailUiState.actionResult] is non-null and disappears when the user taps "Dismiss"
 * (which fires [ShipDetailEvent.ActionResultDismissed] and clears the result in the ViewModel).
 *
 * **Why amber?** The normal informational panels (Navigation, Fuel, Cargo) use the primary
 * green terminal colour. Amber (`MaterialTheme.colorScheme.tertiary`) visually distinguishes
 * this panel as an ephemeral response to a command rather than a persistent data display —
 * echoing classic terminal output conventions where command results were printed in a different
 * colour or style from the status display.
 *
 * Each [ActionResult] subtype renders a small set of [CommandLine] entries summarising what
 * changed, so the user gets confirmation without needing to re-read the fuel or credit balance
 * cards.
 *
 * @param result The action outcome to display. The `when` expression is exhaustive over
 *   all [ActionResult] subtypes.
 * @param onDismiss Called when the user taps "Dismiss". Should emit
 *   [ShipDetailEvent.ActionResultDismissed] to the ViewModel.
 */
@Composable
private fun CommandOutputCard(result: ActionResult, onDismiss: () -> Unit) {
    val amberColor = MaterialTheme.colorScheme.tertiary
    TerminalCard(title = "Command Output", borderColor = amberColor) {
        when (result) {
            is ActionResult.Orbited -> {
                CommandLine("> ORBIT SUCCESSFUL", amberColor)
                CommandLine("  WAYPOINT: ${result.waypointSymbol}", amberColor)
            }
            is ActionResult.Docked -> {
                CommandLine("> DOCK SUCCESSFUL", amberColor)
                CommandLine("  WAYPOINT: ${result.waypointSymbol}", amberColor)
            }
            is ActionResult.Refueled -> {
                CommandLine("> REFUEL SUCCESSFUL", amberColor)
                CommandLine("  UNITS ADDED: ${result.fuelAdded}", amberColor)
                CommandLine("  COST: ${result.totalCost} CREDITS", amberColor)
                CommandLine("  BALANCE: ${result.newCredits} CREDITS", amberColor)
            }
            is ActionResult.NegotiatedContract -> {
                CommandLine("> CONTRACT NEGOTIATED", amberColor)
                CommandLine("  ID: ${result.contractId}", amberColor)
                CommandLine("  TYPE: ${result.type}", amberColor)
                CommandLine("  UPFRONT: ${result.upfrontPayment} CR", amberColor)
            }
            is ActionResult.DeliveredCargo -> {
                CommandLine("> CARGO DELIVERED", amberColor)
                CommandLine("  GOOD: ${result.tradeSymbol}", amberColor)
                CommandLine("  DELIVERED: ${result.unitsFulfilled}/${result.unitsRequired}", amberColor)
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

/**
 * Contextual action buttons driven by the ship's current [ShipNavStatus].
 *
 * **Pattern:** Status-driven conditional UI. The available commands depend entirely on where
 * the ship currently is:
 * - `DOCKED` → "Orbit" + "Refuel" (two actions available from a dock)
 * - `IN_ORBIT` → "Dock" (one action available from orbit)
 * - `IN_TRANSIT` → nothing (no commands can be issued to a moving ship)
 *
 * This mapping is derived from [ShipDetail.navStatus] rather than individual boolean flags,
 * so the set of visible buttons changes atomically when nav status changes. When [isActionInProgress]
 * is `true`, all buttons disable and show `"..."` as a minimal in-place loading indicator —
 * this avoids layout shifts that a spinner or replaced button would cause.
 *
 * @param navStatus The ship's current navigation status; determines which buttons to render.
 * @param isActionInProgress Whether an action coroutine is currently running. Disables buttons
 *   while `true` to prevent duplicate requests.
 * @param onEvent Callback for emitting [ShipDetailEvent.OrbitClicked], [ShipDetailEvent.DockClicked],
 *   or [ShipDetailEvent.RefuelClicked].
 */
@Composable
private fun ActionButtons(
    navStatus: ShipNavStatus,
    isActionInProgress: Boolean,
    onEvent: (ShipDetailEvent) -> Unit
) {
    when (navStatus) {
        ShipNavStatus.DOCKED -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                TerminalButton(
                    text = if (isActionInProgress) "..." else "Orbit",
                    onClick = { onEvent(ShipDetailEvent.OrbitClicked) },
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TerminalButton(
                    text = if (isActionInProgress) "..." else "Refuel",
                    onClick = { onEvent(ShipDetailEvent.RefuelClicked) },
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ShipNavStatus.IN_ORBIT -> {
            TerminalButton(
                text = if (isActionInProgress) "..." else "Dock",
                onClick = { onEvent(ShipDetailEvent.DockClicked) },
                enabled = !isActionInProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ShipNavStatus.IN_TRANSIT -> {
            // No actions available while in transit
        }
    }
}

/**
 * A single line of terminal-style command output text.
 *
 * Used exclusively inside [CommandOutputCard] to render each line of action feedback in the
 * amber command-output colour. The leading `>` or `  ` prefix is part of the [text] string
 * passed by the caller, preserving the visual indentation of a terminal session without
 * requiring the composable to know the structure of the output.
 *
 * @param text The full text of this output line, including any leading prefix characters.
 * @param color The colour to apply — callers pass the amber tertiary colour from the theme.
 */
@Composable
private fun CommandLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}

/**
 * A single `LABEL: value` data row in terminal style.
 *
 * **Pattern:** Primitive display component. This composable has no logic — it simply formats
 * a label/value pair in the primary terminal green colour with the screen's body text style.
 * Extracting it into a named composable eliminates formatting duplication across all data
 * rows on the screen and makes the card composables read like a list of semantic entries
 * rather than a wall of `Text()` calls. In a new project, extract a similar helper as soon
 * as you have three or more identically-formatted label/value pairs.
 *
 * @param label The uppercase field name displayed before the colon (e.g. `"STATUS"`).
 * @param value The field value displayed after the colon (e.g. `"DOCKED"`).
 */
@Composable
private fun ShipDetailDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
