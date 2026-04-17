package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

/**
 * Immutable snapshot of everything the dashboard screen needs to render itself.
 *
 * **Pattern:** UDF (Unidirectional Data Flow) state holder. In a new project, define one
 * `data class` per screen that captures every piece of observable state. The ViewModel
 * produces new snapshots; the composable consumes them read-only.
 *
 * **In this project:** Produced by `DashboardViewModel.uiState` via a `combine()` of three
 * upstream sources — the repository's agent stream, a loading flag, and an error string.
 * The UI treats each field combination as a distinct visual state (see
 * [DashboardScreenContent]).
 *
 * @property agent The loaded agent data, or `null` when not yet available. A `null` value
 *   is valid during initial load and should not be treated as an error on its own — check
 *   [isLoading] and [error] to distinguish the two cases.
 * @property isLoading `true` while an API refresh is in flight. The UI shows a progress
 *   indicator and suppresses the agent card during this time.
 * @property error A human-readable error message from the most recent failed refresh, or
 *   `null` when no error is present. Cleared when the user dismisses the banner or retries.
 */
data class DashboardUiState(
    val agent: Agent? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * All user-initiated interactions on the dashboard screen.
 *
 * **Pattern:** Sealed event interface for UDF. In a new project, collect every entry point
 * into the ViewModel — button taps, card clicks, dismiss gestures — as variants of a single
 * sealed type. The composable calls `onEvent(DashboardEvent.Foo)` and the ViewModel handles
 * routing in one `when` block, keeping all business logic out of the UI layer.
 *
 * **In this project:** Passed as a lambda from [DashboardScreen] down to [DashboardScreenContent]
 * and forwarded to `DashboardViewModel.onEvent`. Using events (rather than direct method
 * references per action) means the ViewModel's public surface stays a single function, which
 * simplifies testing and future refactors.
 */
sealed interface DashboardEvent {
    /**
     * Emitted when the user taps the "Retry" button on the error card.
     * Triggers a fresh [AgentRepository.refreshAgent] call and clears the existing error.
     */
    data object RetryClicked : DashboardEvent

    /**
     * Emitted when the user taps the "Disconnect" button.
     *
     * Logout is an event (not a direct `viewModel.logout()` call) so that all ViewModel
     * entry points remain unified through `onEvent`. This keeps the composable's
     * dependency surface to a single `(DashboardEvent) -> Unit` lambda, which is easier
     * to preview and test in isolation.
     */
    data object LogoutClicked : DashboardEvent

    /**
     * Emitted when the user dismisses the error banner without retrying.
     * The ViewModel clears `_error` to `null`, collapsing the error card.
     */
    data object ErrorDismissed : DashboardEvent

    /**
     * Emitted when the user taps the Fleet card.
     * The ViewModel sends a [NavigationTarget.ShipList] navigation event in response,
     * which the stateful [DashboardScreen] collects and forwards to the NavController.
     */
    data object FleetCardClicked : DashboardEvent
}
