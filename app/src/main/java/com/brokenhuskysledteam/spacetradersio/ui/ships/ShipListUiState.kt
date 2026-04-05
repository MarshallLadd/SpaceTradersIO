package com.brokenhuskysledteam.spacetradersio.ui.ships

import com.brokenhuskysledteam.spacetradersio.sdk.domain.model.enums.ShipNavStatus
import kotlin.time.Instant

data class ShipListUiState(
    val ships: List<ShipSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

// Lightweight UI model for the ship list — contains only what each card needs.
// arrivalTime and departureTime are non-null only when the ship is IN_TRANSIT.
data class ShipSummary(
    val symbol: String,
    val frameName: String,
    val status: ShipNavStatus,
    val waypointSymbol: String,
    val systemSymbol: String,
    val arrivalTime: Instant?,
    val departureTime: Instant?
)

sealed interface ShipListEvent {
    data object RetryClicked : ShipListEvent
    data class ShipSelected(val symbol: String) : ShipListEvent
}
