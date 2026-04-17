package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
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
 * Stateful entry-point composable for the ship list screen.
 *
 * **Pattern:** Stateful/stateless composable split. This function owns the ViewModel and
 * navigation side-effects; [ShipListScreenContent] owns only rendering. To apply in a new
 * project: the stateful wrapper calls `hiltViewModel()`, collects state with lifecycle
 * awareness, and handles `LaunchedEffect`-based side-effects (e.g., navigation). Pass the
 * result down to a stateless content composable so the content is independently previewable
 * and testable.
 *
 * **In this project:** Registered in the NavHost for the ship list route. Navigation events
 * from the ViewModel's `Channel` are collected here and forwarded to the host lambda.
 *
 * @param onNavigateToShipDetail Lambda provided by the NavHost; called with the ship's
 *   symbol when the user selects a card.
 * @param viewModel Hilt-injected ViewModel; overridable in tests.
 */
@Composable
fun ShipListScreen(
    onNavigateToShipDetail: (String) -> Unit,
    viewModel: ShipListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Collect one-shot navigation events. LaunchedEffect(Unit) ensures this coroutine
    // lives for the full lifetime of the composable and is not restarted on recomposition.
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { target ->
            when (target) {
                is NavigationTarget.ShipDetail -> onNavigateToShipDetail(target.shipSymbol)
                else -> {}
            }
        }
    }

    ShipListScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

/**
 * Stateless content composable for the ship list screen.
 *
 * **Pattern:** Stateless composable. Receives all data it needs via parameters and raises
 * events via a lambda. No ViewModel reference — safe to preview and unit-test by constructing
 * a [ShipListUiState] directly.
 *
 * **In this project:** Renders three distinct states driven by [ShipListUiState]:
 * loading spinner, full-screen error with retry, and the scrollable ship list.
 *
 * @param uiState Current state snapshot from the ViewModel.
 * @param onEvent Callback invoked for every user interaction.
 */
@Composable
fun ShipListScreenContent(
    uiState: ShipListUiState,
    onEvent: (ShipListEvent) -> Unit
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

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "FLEET MANIFEST",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TerminalCard(title = "Error") {
                        Text(text = uiState.error, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        TerminalButton(
                            text = "Retry",
                            onClick = { onEvent(ShipListEvent.RetryClicked) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = "FLEET MANIFEST",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "// ${uiState.ships.size} VESSEL(S) ON RECORD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // LazyColumn virtualises the list — only visible cards are composed,
                    // which matters when the player owns a large fleet. Each item is a
                    // ShipSummaryCard; the ship's symbol is used as a stable key so
                    // Compose can reuse slot-table entries when items are reordered.
                    LazyColumn {
                        items(uiState.ships) { ship ->
                            ShipSummaryCard(
                                ship = ship,
                                onClick = { onEvent(ShipListEvent.ShipSelected(ship.symbol)) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // The scanline overlay sits above all content at full-screen size, providing
        // the retro CRT aesthetic without affecting touch targets.
        ScanlineOverlay()
    }
}

/**
 * A single ship card in the fleet manifest list.
 *
 * Conditionally renders either static location data or a live [TransitProgress] widget
 * depending on the ship's navigation status.
 *
 * @param ship The UI model for this card.
 * @param onClick Forwarded to the card's click modifier; raises [ShipListEvent.ShipSelected].
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun ShipSummaryCard(ship: ShipSummary, onClick: () -> Unit) {
    TerminalCard(
        title = ship.symbol,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        ShipDataRow("FRAME", ship.frameName)
        Spacer(modifier = Modifier.height(4.dp))
        ShipDataRow("STATUS", ship.status.name)
        Spacer(modifier = Modifier.height(4.dp))

        // Transit ships show destination + live countdown; stationary ships show location.
        // arrivalTime/departureTime are only non-null when IN_TRANSIT (set by the mapper).
        if (ship.status == ShipNavStatus.IN_TRANSIT && ship.arrivalTime != null && ship.departureTime != null) {
            ShipDataRow("DESTINATION", "${ship.systemSymbol} / ${ship.waypointSymbol}")
            Spacer(modifier = Modifier.height(8.dp))
            TransitProgress(ship = ship)
        } else {
            ShipDataRow("LOCATION", "${ship.systemSymbol} / ${ship.waypointSymbol}")
        }
    }
}

/**
 * Live countdown timer and progress bar for a ship that is currently in transit.
 *
 * **Pattern:** Timer-driven local state with `LaunchedEffect`. The idiomatic Compose
 * approach for state that must update on a wall-clock tick is:
 * 1. Store the derived value (remaining seconds) in a `remember`ed `MutableState`.
 * 2. Drive updates from a `LaunchedEffect` coroutine that loops with `delay(1000)`.
 * 3. Re-read the clock on each tick rather than decrementing a counter, so the display
 *    stays accurate even if the coroutine wakes late.
 *
 * Compose's fine-grained recomposition means only this composable (and its parent card)
 * re-renders on each tick — the rest of the `LazyColumn` is unaffected. To apply in a
 * new project: use this `remember` + `LaunchedEffect` loop pattern any time you need
 * UI state that updates at a fixed interval without a dedicated ViewModel.
 *
 * **In this project:** Keyed on `ship.symbol` so that if the list reorders, Compose
 * restarts the effect and resets the remembered value for each ship independently.
 *
 * @param ship The transit ship whose [ShipSummary.arrivalTime] and
 *   [ShipSummary.departureTime] drive the countdown. Early-returns if either is null
 *   (defensive guard; the caller already checked, but the compiler requires it).
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun TransitProgress(ship: ShipSummary) {
    val arrivalTime = ship.arrivalTime ?: return
    val departureTime = ship.departureTime ?: return

    // Seed the state with the current remaining seconds so the first frame is accurate
    // without waiting for the first delay(1000) tick.
    var remainingSeconds by remember(ship.symbol) {
        mutableLongStateOf(
            max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        )
    }

    // The loop body re-reads the clock after each delay rather than doing
    // `remainingSeconds -= 1` to stay accurate under Doze or slow coroutine dispatch.
    LaunchedEffect(ship.symbol) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = max(0L, (arrivalTime - Clock.System.now()).inWholeSeconds)
        }
    }

    // Progress fraction: 0.0 = just departed, 1.0 = arrived.
    val totalSeconds = (arrivalTime - departureTime).inWholeSeconds
    val progress = if (totalSeconds > 0) {
        1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
    } else 1f

    // Format the countdown as HH:MM:SS for long transits, or MM:SS for short ones.
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val countdownLabel = if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }

    ShipDataRow("ETA", countdownLabel)
    Spacer(modifier = Modifier.height(4.dp))
    TerminalProgressBar(
        value = progress,
        label = "${"%.0f".format(progress * 100)}%"
    )
}

/**
 * Renders a single `LABEL: value` data row in the terminal style.
 *
 * @param label Uppercase field name (e.g., `"FRAME"`, `"STATUS"`).
 * @param value Display value for the field.
 */
@Composable
private fun ShipDataRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
