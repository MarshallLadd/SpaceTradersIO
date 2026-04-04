package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

// Immutable snapshot of the dashboard screen's UI state.
// Starts in a loading state; resolves to either an agent or an error.
data class DashboardUiState(
    val agent: Agent? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

// All user interactions on the dashboard screen.
sealed interface DashboardEvent {
    data object RetryClicked : DashboardEvent
    data object LogoutClicked : DashboardEvent
    data object ErrorDismissed : DashboardEvent
}
