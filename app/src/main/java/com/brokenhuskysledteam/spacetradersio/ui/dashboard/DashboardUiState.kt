package com.brokenhuskysledteam.spacetradersio.ui.dashboard

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.Agent

data class DashboardUiState(
    val agent: Agent? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed interface DashboardEvent {
    data object RetryClicked : DashboardEvent
    data object LogoutClicked : DashboardEvent
    data object ErrorDismissed : DashboardEvent
}
