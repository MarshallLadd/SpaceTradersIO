package com.brokenhuskysledteam.spacetradersio.ui.ships

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlin.time.Instant

/**
 * Full UI state snapshot for the ship list screen.
 *
 * **Pattern:** Unidirectional Data Flow (UDF). A single immutable state object replaces
 * individual LiveData/StateFlow fields. To apply in a new project: create one `data class`
 * per screen that holds every piece of information the composable needs to render itself —
 * loading, error, and content states all at once.
 *
 * **In this project:** Emitted by [ShipListViewModel.uiState] and consumed by
 * [ShipListScreenContent]. The `ships` list contains [ShipSummary] UI models, not raw
 * domain [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship] objects.
 *
 * @property ships The list of summarised ship cards to display. Empty by default.
 * @property isLoading `true` while the initial fetch (or a retry) is in progress.
 * @property error Non-null error message when the most recent fetch failed; `null` otherwise.
 */
data class ShipListUiState(
    val ships: List<ShipSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * UI model for a single row in the ship list — contains only the data each card needs.
 *
 * **Pattern:** ViewModel-layer UI model (domain → UI mapping). Rather than forwarding the
 * domain [com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Ship] directly to
 * composables, the ViewModel maps it to a flat, display-ready struct. To apply in a new
 * project: define a separate data class per screen/card, copy only the fields the UI
 * requires, and format any derived values (e.g., formatted strings) at mapping time.
 *
 * Three concrete benefits of this separation:
 * 1. **Minimal coupling** — composables never import domain model types, so domain refactors
 *    don't ripple into UI code.
 * 2. **Flat structure** — deeply nested domain objects (e.g., `ship.nav.route.arrivalTime`)
 *    become direct properties, removing defensive null checks in composables.
 * 3. **Testability** — ViewModel tests can assert on simple data classes without constructing
 *    the full domain object graph.
 *
 * **In this project:** Produced by the private `Ship.toSummary()` extension inside
 * [ShipListViewModel]. The composable [ShipListScreen] only needs the fields declared here.
 *
 * @property symbol Unique identifier for the ship (e.g., `"AGENT-1"`). Used as a stable
 *   key for Compose list items and as the argument passed to the detail navigation route.
 * @property frameName Human-readable ship frame type (e.g., `"FRAME_MINER"`).
 * @property status Current navigation status from the SpaceTraders API.
 * @property waypointSymbol The waypoint the ship is at, orbiting, or travelling to.
 * @property systemSymbol The system the ship currently belongs to.
 * @property arrivalTime Non-null **only** when [status] is [ShipNavStatus.IN_TRANSIT].
 *   Cleared to `null` for docked/orbiting ships so the transit progress UI is never shown
 *   for stationary ships.
 * @property departureTime Non-null **only** when [status] is [ShipNavStatus.IN_TRANSIT].
 *   Paired with [arrivalTime] to compute elapsed-fraction for the progress bar.
 */
data class ShipSummary(
    val symbol: String,
    val frameName: String,
    val status: ShipNavStatus,
    val waypointSymbol: String,
    val systemSymbol: String,
    val arrivalTime: Instant?,
    val departureTime: Instant?
)

/**
 * Events that the ship list screen can raise to its ViewModel.
 *
 * **Pattern:** Sealed event interface for UDF. Every user interaction is represented as a
 * concrete type so the ViewModel's `when` block is exhaustive and new interactions are
 * added explicitly. To apply in a new project: one sealed interface per screen; each
 * distinct action (button tap, item click, retry) is its own `data object` or `data class`.
 *
 * **In this project:** Handled by [ShipListViewModel.onEvent].
 */
sealed interface ShipListEvent {
    /** Fired when the user taps "Retry" after a failed fetch. */
    data object RetryClicked : ShipListEvent

    /**
     * Fired when the user taps a ship card.
     *
     * @property symbol The [ShipSummary.symbol] of the tapped ship; forwarded as the
     *   navigation argument to the detail screen.
     */
    data class ShipSelected(val symbol: String) : ShipListEvent
}
