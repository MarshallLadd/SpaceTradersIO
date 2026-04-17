package com.brokenhuskysledteam.spacetradersio.ui.ships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brokenhuskysledteam.spacetradersio.navigation.NavigationTarget
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship
import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import com.brokenhuskysledteam.spacetradersio.sdk.domain.repository.FleetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the ship list screen.
 *
 * **Pattern:** UDF ViewModel with `combine`-based state assembly. Three independent
 * streams — a repository-backed `Flow`, a loading flag, and an error string — are merged
 * into a single [ShipListUiState] via [combine]. To apply in a new project: keep each
 * orthogonal piece of mutable state in its own `MutableStateFlow`, then combine them into
 * the published `uiState`. This avoids nested `copy()` chains and lets each stream update
 * independently without race conditions.
 *
 * **In this project:** Fetches the ship list via [FleetRepository.refreshMyShips] on init
 * and on retry, while observing live updates through [FleetRepository.observeShips].
 * Navigation to the detail screen is routed through a [Channel] so the event is consumed
 * exactly once even across configuration changes.
 *
 * @param fleetRepository Source of truth for the ship collection; observed as a `Flow`.
 */
@HiltViewModel
class ShipListViewModel @Inject constructor(
    private val fleetRepository: FleetRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * Single source of truth for the ship list UI, built by merging three streams.
     *
     * **Pattern:** Three-stream `combine`. The same pattern appears in `DashboardViewModel`:
     * `combine(repositoryFlow, _isLoading, _error) { data, loading, err -> ... }`. Each
     * stream is independent — a new error does not accidentally clear `isLoading`, for
     * example. `SharingStarted.Eagerly` ensures the combine graph starts immediately so
     * that tests reading `.value` directly always see the current state.
     */
    val uiState: StateFlow<ShipListUiState> = combine(
        fleetRepository.observeShips(),
        _isLoading,
        _error
    ) { ships, isLoading, error ->
        ShipListUiState(
            // Domain Ship objects are mapped to UI models here, inside the combine lambda,
            // so composables never receive or import domain types.
            ships = ships.map { it.toSummary() },
            isLoading = isLoading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShipListUiState())

    /**
     * One-shot navigation events sent from this ViewModel to the composable.
     *
     * **Pattern:** `Channel`-backed navigation. Using a `Channel` (not `SharedFlow`) means
     * an event is buffered and delivered exactly once, even if the collector restarts after
     * a configuration change. `receiveAsFlow()` exposes it as a cold `Flow` for the
     * `LaunchedEffect` collector in [ShipListScreen].
     */
    private val _navigationEvent = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    init {
        // Trigger the initial fetch as soon as the ViewModel is created.
        loadShips()
    }

    /**
     * Single entry point for all UI interactions.
     *
     * @param event The user action to handle.
     */
    fun onEvent(event: ShipListEvent) {
        when (event) {
            is ShipListEvent.RetryClicked -> loadShips()
            is ShipListEvent.ShipSelected -> viewModelScope.launch {
                _navigationEvent.send(NavigationTarget.ShipDetail(event.symbol))
            }
        }
    }

    /**
     * Triggers a remote fetch of the ship list, updating loading and error state
     * around the network call.
     */
    private fun loadShips() {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                fleetRepository.refreshMyShips()
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message ?: "Failed to load ships"
            }
        }
    }
}

/**
 * Maps a domain [Ship] to the [ShipSummary] UI model.
 *
 * **Pattern:** Private ViewModel-layer mapper extension function. The function is declared
 * at file scope (outside the class) but is `private`, so it is only accessible within this
 * file. This placement is intentional:
 * - **Not in the domain layer** — the domain has no knowledge of Android or UI concerns.
 * - **Not in the composable** — composables should receive already-transformed data, not
 *   perform data shaping.
 * - **In the ViewModel file** — the ViewModel is responsible for preparing data for the UI,
 *   so the mapper lives alongside the code that calls it.
 *
 * To apply in a new project: place `private fun DomainType.toUiModel(): UiModel` in the
 * same file as the ViewModel that uses it.
 *
 * **In this project:** Called inside the `combine` lambda so every new emission from
 * `observeShips()` automatically produces fresh [ShipSummary] objects.
 */
private fun Ship.toSummary(): ShipSummary {
    // Only populate transit timing fields when the ship is actively travelling;
    // null signals the UI not to render the transit progress bar.
    val inTransit = nav.status == ShipNavStatus.IN_TRANSIT
    return ShipSummary(
        symbol = symbol,
        frameName = frameName,
        status = nav.status,
        waypointSymbol = nav.waypointSymbol,
        systemSymbol = nav.systemSymbol,
        arrivalTime = if (inTransit) nav.route.arrivalTime else null,
        departureTime = if (inTransit) nav.route.departureTime else null
    )
}
